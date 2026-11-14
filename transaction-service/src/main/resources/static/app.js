const form = document.querySelector("#transaction-form");
const merchantInput = document.querySelector("#merchant");
const amountInput = document.querySelector("#amount");
const submitButton = document.querySelector("#submit-button");
const formMessage = document.querySelector("#form-message");
const refreshButton = document.querySelector("#refresh-button");
const searchInput = document.querySelector("#search-input");
const rowsContainer = document.querySelector("#transaction-rows");
const emptyState = document.querySelector("#empty-state");
const errorMessage = document.querySelector("#error-message");
const connectionLabel = document.querySelector("#connection-label");
const transactionsById = new Map();
let refreshInProgress = false;

const moneyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL"
});

const dateFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short"
});

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : dateFormatter.format(date);
}

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
    ["Motivo", transaction.analysisReason || "Aguardando conclusão da análise."]
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
      ? makePill(result === "REVIEW" ? "Revisão" : "Aprovada", result === "REVIEW" ? "result-review" : "result-approved")
      : Object.assign(document.createElement("span"), { className: "result-empty", textContent: "Aguardando" });
    appendCell(row, resultCell);
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
    String(analyzed.filter((item) => item.analysisStatus === "APPROVED").length);
  document.querySelector("#metric-review").textContent =
    String(analyzed.filter((item) => item.analysisStatus === "REVIEW").length);
}

async function loadTransactions() {
  if (refreshInProgress) return;
  refreshInProgress = true;
  refreshButton.disabled = true;
  try {
    const response = await fetch("/api/transacoes", { headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error(`O serviço respondeu com HTTP ${response.status}.`);
    const transactions = await response.json();
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
    const response = await fetch("/api/transacoes", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        valor: Number(amountInput.value),
        estabelecimento: merchantInput.value.trim()
      })
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      const message = result.message || result.detail || `O serviço respondeu com HTTP ${response.status}.`;
      throw new Error(message);
    }

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

refreshButton.addEventListener("click", loadTransactions);
searchInput.addEventListener("input", renderTransactions);
loadTransactions();
window.setInterval(loadTransactions, 4000);
