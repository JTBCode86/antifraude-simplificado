const { formatDate, requestJson } = window.portalCommon;
const healthOverall = document.querySelector("#health-overall");
const healthComponents = document.querySelector("#health-components");
const healthCheckedAt = document.querySelector("#health-checked-at");
const kafkaEvents = document.querySelector("#kafka-events");
const redisStatus = document.querySelector("#redis-status");

async function loadHealth() {
  try {
    const health = await requestJson("/api/portal/health");
    const isUp = health.status === "UP";
    healthOverall.textContent = isUp ? "Todos operacionais" : "Atenção necessária";
    healthOverall.className = `health-status ${isUp ? "status-up" : "status-down"}`;
    healthComponents.replaceChildren();
    for (const component of health.components) {
      const item = document.createElement("div");
      item.className = "health-item";
      const dot = document.createElement("span");
      dot.className = `health-dot ${component.status === "UP" ? "dot-up" : "dot-down"}`;
      const label = document.createElement("span");
      label.className = "health-label";
      label.textContent = component.name;
      const status = document.createElement("strong");
      status.className = component.status === "UP" ? "health-text-up" : "health-text-down";
      status.textContent = component.status;
      item.title = component.description || "";
      item.append(dot, label, status);
      healthComponents.append(item);
    }
    healthCheckedAt.textContent =
      `Última verificação: ${formatDate(health.checkedAt)} · Atualização automática a cada 15 segundos.`;
    document.querySelector("#connection-label").textContent =
      isUp ? "Serviços conectados" : "Atenção à saúde dos serviços";
    document.querySelector(".live-dot").classList.toggle("offline", !isUp);
  } catch (error) {
    healthOverall.textContent = "Verificação indisponível";
    healthOverall.className = "health-status status-down";
    healthComponents.replaceChildren();
    const message = document.createElement("span");
    message.className = "health-placeholder";
    message.textContent = error.message;
    healthComponents.append(message);
    healthCheckedAt.textContent = "Não foi possível consultar a saúde dos serviços.";
    document.querySelector("#connection-label").textContent = "Sem conexão com os serviços";
    document.querySelector(".live-dot").classList.add("offline");
  }
}

async function loadKafkaEvents() {
  try {
    const events = await requestJson("/api/portal/kafka/events?limit=8");
    kafkaEvents.replaceChildren();
    if (events.length === 0) {
      const empty = document.createElement("p");
      empty.className = "health-placeholder";
      empty.textContent = "Nenhum evento encontrado no tópico.";
      kafkaEvents.append(empty);
      return;
    }
    for (const event of events) {
      const item = document.createElement("details");
      item.className = "event-item";
      const summary = document.createElement("summary");
      const key = document.createElement("strong");
      key.textContent = event.key || "Sem chave";
      const time = document.createElement("span");
      time.textContent = formatDate(event.timestamp);
      const payload = document.createElement("pre");
      payload.textContent = event.value;
      summary.append(key, time);
      item.append(summary, payload);
      kafkaEvents.append(item);
    }
  } catch (error) {
    kafkaEvents.replaceChildren();
    const message = document.createElement("p");
    message.className = "health-placeholder error-text";
    message.textContent = `Não foi possível consultar eventos: ${error.message}`;
    kafkaEvents.append(message);
  }
}

async function loadRedisStatus() {
  try {
    const status = await requestJson("/api/portal/redis");
    redisStatus.replaceChildren();
    const badge = document.createElement("span");
    badge.className = `health-status ${status.status === "UP" ? "status-up" : "status-down"}`;
    badge.textContent = status.status === "UP" ? "Conectado" : "Indisponível";
    const count = document.createElement("strong");
    count.textContent = String(status.cachedAnalyses);
    const countLabel = document.createElement("span");
    countLabel.textContent = "análises no cache";
    const memory = document.createElement("span");
    memory.className = "redis-memory";
    memory.textContent = `Memória Redis: ${status.memoryUsed}`;
    redisStatus.append(badge, count, countLabel, memory);
  } catch (error) {
    redisStatus.replaceChildren();
    const message = document.createElement("span");
    message.className = "health-placeholder error-text";
    message.textContent = `Não foi possível consultar o cache: ${error.message}`;
    redisStatus.append(message);
  }
}

function refreshObservability() {
  return Promise.all([loadHealth(), loadKafkaEvents(), loadRedisStatus()]).then(() => {
    document.querySelector("#last-updated").textContent =
      `Atualizado às ${new Date().toLocaleTimeString("pt-BR")}`;
  });
}

document.querySelector("#refresh-observability").addEventListener("click", refreshObservability);
document.querySelector("#health-refresh").addEventListener("click", loadHealth);
document.querySelector("#events-refresh").addEventListener("click", loadKafkaEvents);
document.querySelector("#redis-refresh").addEventListener("click", loadRedisStatus);

refreshObservability();
window.setInterval(loadHealth, 15000);
window.setInterval(loadKafkaEvents, 10000);
window.setInterval(loadRedisStatus, 15000);
