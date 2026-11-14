const { formatDate, requestJson } = window.portalCommon;
const form = document.querySelector("#transaction-form");
const merchantInput = document.querySelector("#merchant");
const amountInput = document.querySelector("#amount");
const submitButton = document.querySelector("#submit-button");
const formMessage = document.querySelector("#form-message");
const refreshButton = document.querySelector("#refresh-button");
const idempotencyButton = document.querySelector("#idempotency-button");
const searchInput = document.querySelector("#search-input");
const rowsContainer = document.querySelector("#transaction-rows");
const emptyState = document.querySelector("#empty-state");
const errorMessage = document.querySelector("#error-message");
const connectionLabel = document.querySelector("#connection-label");
const reviewDialog = document.querySelector("#review-dialog");
const reviewForm = document.querySelector("#review-form");
const reviewError = document.querySelector("#review-error");
const transactionsById = new Map();
let refreshInProgress = false;
let reviewTarget = null;

const moneyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL"
});

function appendCell(row, content, className) {
  const cell = document.createElement("td");
  if (className) cell.className = className;
  if (content instanceof Node) cell.append(content);
  else cell.textContent = content;
  row.append(cell);
  return cell;
}

function makePill(label, className) {
  const pill = document.createElement("span");
  pill.className = `status-pill ${className}`;
  pill.textContent = label;
  return pill;
}

function makeDetails(transaction) {
  const details = document.createElement("details");
  details.className = "details";
  const summary = document.createElement("summary");
  summary.textContent = "Ver detalhes";
  const body = document.createElement("div");
  body.className = "details-body";

  const information = [
    ["ID", transaction.id],
    ["Recebida em", formatDate(transaction.createdAt)],
    ["Analisada em", formatDate(transaction.analyzedAt)],
    ["Resultado automático", transaction.analysisStatus || "Aguardando análise."],
    ["Motivo da análise", transaction.analysisReason || "Aguardando conclusão da análise."],
    ["Decisão manual", transaction.manualDecision || "Não decidida"],
    ["Responsável", transaction.reviewedBy || "—"],
    ["Justificativa manual", transaction.reviewReason || "—"],
    ["Decidida em", formatDate(transaction.reviewedAt)]
  ];

  for (const [label, value] of information) {
    const line = document.createElement("div");
    const strong = document.createElement("strong");
    strong.textContent = `${label}: `;
    line.append(strong, document.createTextNode(value));
    body.append(line);
  }

  details.append(summary, body);
  return details;
}

function renderTransactions() {
  const query = searchInput.value.trim().toLocaleLowerCase("pt-BR");
  const allTransactions = Array.from(transactionsById.values());
  const filtered = allTransactions.filter((transaction) =>
    `${transaction.merchant} ${transaction.id}`.toLocaleLowerCase("pt-BR").includes(query)
  );
  rowsContainer.replaceChildren();

  for (const transaction of filtered) {
    const row = document.createElement("tr");
    const idCell = document.createElement("div");
    idCell.className = "transaction-id";
    idCell.textContent = transaction.id.slice(0, 8).toUpperCase();
    const createdCell = document.createElement("span");
    createdCell.className = "transaction-time";
    createdCell.textContent = formatDate(transaction.createdAt);
    const identifier = document.createElement("div");
    identifier.append(idCell, createdCell);
    appendCell(row, identifier);
    appendCell(row, transaction.merchant, "merchant-name");
    appendCell(row, moneyFormatter.format(Number(transaction.amount)), "amount-value");

    const stage = transaction.status === "ANALYZED"
      ? makePill("Analisada", "status-analyzed")
      : makePill("Em processamento", "status-received");
    appendCell(row, stage);

    const result = transaction.analysisStatus;
    const resultCell = result
      ? makePill(result === "REVIEW" ? "Requer revisão" : "Aprovada", result === "REVIEW" ? "result-review" : "result-approved")
      : Object.assign(document.createElement("span"), { className: "result-empty", textContent: "Aguardando" });
    appendCell(row, resultCell);

    let decisionCell;
    if (transaction.manualDecision) {
      const approved = transaction.manualDecision === "APPROVED";
      decisionCell = makePill(
        approved ? "Aprovada manualmente" : "Rejeitada",
        approved ? "decision-approved" : "decision-rejected"
      );
    } else if (transaction.analysisStatus === "REVIEW") {
      decisionCell = document.createElement("div");
      decisionCell.className = "review-actions";
      const approveButton = document.createElement("button");
      approveButton.className = "review-action approve-action";
      approveButton.type = "button";
      approveButton.dataset.transactionId = transaction.id;
      approveButton.dataset.decision = "APPROVED";
      approveButton.textContent = "Aprovar";
      const rejectButton = document.createElement("button");
      rejectButton.className = "review-action reject-action";
      rejectButton.type = "button";
      rejectButton.dataset.transactionId = transaction.id;
      rejectButton.dataset.decision = "REJECTED";
      rejectButton.textContent = "Rejeitar";
      decisionCell.append(approveButton, rejectButton);
    } else {
      decisionCell = Object.assign(document.createElement("span"), {
        className: "result-empty",
        textContent: transaction.status === "ANALYZED" ? "Automática" : "Aguardando"
      });
    }
    appendCell(row, decisionCell);
    appendCell(row, makeDetails(transaction));
    rowsContainer.append(row);
  }

  emptyState.hidden = filtered.length !== 0;
  document.querySelector("#result-count").textContent =
    query ? `${filtered.length} resultado(s) · ${allTransactions.length} recentes` : `${allTransactions.length} transação(ões) · últimas 100`;
  updateMetrics(allTransactions);
}

function updateMetrics(transactions) {
  const analyzed = transactions.filter((item) => item.status === "ANALYZED");
  document.querySelector("#metric-total").textContent = String(transactions.length);
  document.querySelector("#metric-pending").textContent = String(transactions.length - analyzed.length);
  document.querySelector("#metric-approved").textContent =
    String(analyzed.filter((item) => item.analysisStatus === "APPROVED" || item.manualDecision === "APPROVED").length);
  document.querySelector("#metric-review").textContent =
    String(analyzed.filter((item) => item.analysisStatus === "REVIEW" && !item.manualDecision).length);
  updateCharts(transactions);
}

function updateCharts(transactions) {
  const approved = transactions.filter((item) =>
    item.analysisStatus === "APPROVED" || item.manualDecision === "APPROVED"
  ).length;
  const review = transactions.filter((item) =>
    item.analysisStatus === "REVIEW" && !item.manualDecision
  ).length;
  const rejected = transactions.filter((item) => item.manualDecision === "REJECTED").length;
  const pending = transactions.filter((item) => item.status !== "ANALYZED").length;
  const total = transactions.length;
  const segments = [
    { key: "approved", count: approved, color: "var(--chart-approved)" },
    { key: "review", count: review, color: "var(--chart-review)" },
    { key: "rejected", count: rejected, color: "var(--chart-rejected)" },
    { key: "pending", count: pending, color: "var(--chart-pending)" }
  ];
  let start = 0;
  const stops = segments.map((segment) => {
    const end = start + (total ? segment.count / total * 100 : 0);
    const stop = `${segment.color} ${start}% ${end}%`;
    start = end;
    return stop;
  });
  const statusChart = document.querySelector("#status-chart");
  statusChart.style.background = total
    ? `conic-gradient(${stops.join(", ")})`
    : "conic-gradient(#e9edf3 0% 100%)";
  statusChart.setAttribute(
    "aria-label",
    `Distribuição das últimas ${total} transações: ${approved} aprovadas, ${review} aguardando revisão, ${rejected} rejeitadas e ${pending} em processamento.`
  );
  document.querySelector("#status-chart-total").textContent = String(total);

  for (const segment of segments) {
    const percentage = total ? Math.round(segment.count / total * 100) : 0;
    document.querySelector(`#chart-${segment.key}`).textContent = `${segment.count} · ${percentage}%`;
  }

  const bucketCounts = Array(6).fill(0);
  const now = Date.now();
  const fourHours = 4 * 60 * 60 * 1000;
  for (const transaction of transactions) {
    const createdAt = new Date(transaction.createdAt).getTime();
    const age = now - createdAt;
    if (Number.isFinite(createdAt) && age >= 0 && age < 24 * 60 * 60 * 1000) {
      bucketCounts[Math.floor(age / fourHours)] += 1;
    }
  }

  const maximum = Math.max(1, ...bucketCounts);
  const columns = document.querySelectorAll(".bar-column");
  bucketCounts.forEach((count, index) => {
    const column = columns[index];
    column.querySelector(".bar-count").textContent = String(count);
    column.querySelector(".bar-fill").style.height = `${count ? Math.max(8, count / maximum * 132) : 3}px`;
  });
  const inLast24Hours = bucketCounts.reduce((sum, count) => sum + count, 0);
  document.querySelector("#volume-summary").textContent =
    `${inLast24Hours} ${inLast24Hours === 1 ? "transação" : "transações"} nas últimas 24 horas · dentro das 100 mais recentes.`;
  document.querySelector("#volume-chart").setAttribute(
    "aria-label",
    `Volume das últimas 100 transações por intervalos de quatro horas nas últimas 24 horas: ${bucketCounts.join(", ")}.`
  );
}

async function loadTransactions() {
  if (refreshInProgress) return;
  refreshInProgress = true;
  refreshButton.disabled = true;
  try {
    const transactions = await requestJson("/api/transacoes");
    if (!Array.isArray(transactions)) throw new Error("A resposta do serviço tem um formato inesperado.");

    transactionsById.clear();
    for (const transaction of transactions) transactionsById.set(transaction.id, transaction);
    renderTransactions();
    errorMessage.hidden = true;
    connectionLabel.textContent = "Serviços conectados";
    document.querySelector(".live-dot").classList.remove("offline");
    document.querySelector("#last-updated").textContent = `Atualizado às ${new Date().toLocaleTimeString("pt-BR")}`;
  } catch (error) {
    errorMessage.textContent = `Não foi possível carregar as transações. ${error.message}`;
    errorMessage.hidden = false;
    connectionLabel.textContent = "Sem conexão com o serviço";
    document.querySelector(".live-dot").classList.add("offline");
    if (transactionsById.size === 0) {
      document.querySelector("#result-count").textContent = "Não foi possível carregar as transações.";
      updateMetrics([]);
    }
  } finally {
    refreshInProgress = false;
    refreshButton.disabled = false;
  }
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  formMessage.classList.remove("error");
  formMessage.textContent = "";
  submitButton.disabled = true;

  try {
    const result = await requestJson("/api/transacoes", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        valor: Number(amountInput.value),
        estabelecimento: merchantInput.value.trim()
      })
    });
    formMessage.textContent = `Transação ${result.id} recebida. Acompanhando o processamento...`;
    form.reset();
    await loadTransactions();
    merchantInput.focus();
  } catch (error) {
    formMessage.classList.add("error");
    formMessage.textContent = `Não foi possível criar a transação. ${error.message}`;
  } finally {
    submitButton.disabled = false;
  }
});

idempotencyButton.addEventListener("click", async () => {
  formMessage.classList.remove("error");
  formMessage.textContent = "";
  idempotencyButton.disabled = true;
  submitButton.disabled = true;
  try {
    const result = await requestJson("/api/portal/demo/idempotency", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        valor: Number(amountInput.value),
        estabelecimento: merchantInput.value.trim()
      })
    });
    formMessage.textContent =
      `Teste concluído: ${result.duplicateMessagesPublished} mensagens repetidas, ${result.analysisCount} análise para ${result.transactionId} (${result.analysisStatus}).`;
    form.reset();
    await loadTransactions();
  } catch (error) {
    formMessage.classList.add("error");
    formMessage.textContent = `Falha no teste de idempotência. ${error.message}`;
  } finally {
    idempotencyButton.disabled = false;
    submitButton.disabled = false;
  }
});

rowsContainer.addEventListener("click", (event) => {
  const button = event.target.closest("button[data-transaction-id]");
  if (!button) return;
  reviewTarget = {
    transactionId: button.dataset.transactionId,
    decision: button.dataset.decision
  };
  const approving = reviewTarget.decision === "APPROVED";
  document.querySelector("#review-dialog-title").textContent =
    approving ? "Aprovar transação" : "Rejeitar transação";
  document.querySelector("#review-confirm").textContent =
    approving ? "Confirmar aprovação" : "Confirmar rejeição";
  reviewError.textContent = "";
  reviewForm.reset();
  reviewDialog.showModal();
});

reviewForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!reviewTarget) return;
  const confirmButton = document.querySelector("#review-confirm");
  confirmButton.disabled = true;
  reviewError.textContent = "";
  try {
    await requestJson(`/api/transacoes/${reviewTarget.transactionId}/review-decision`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        decision: reviewTarget.decision,
        reviewedBy: document.querySelector("#reviewed-by").value.trim(),
        reason: document.querySelector("#review-reason").value.trim()
      })
    });
    reviewDialog.close();
    await loadTransactions();
  } catch (error) {
    reviewError.textContent = `Não foi possível registrar a decisão. ${error.message}`;
  } finally {
    confirmButton.disabled = false;
  }
});

document.querySelector("#review-cancel").addEventListener("click", () => reviewDialog.close());
refreshButton.addEventListener("click", loadTransactions);
searchInput.addEventListener("input", renderTransactions);
loadTransactions();
window.setInterval(loadTransactions, 4000);
