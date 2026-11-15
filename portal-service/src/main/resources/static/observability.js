const { formatDate, requestJson } = window.portalCommon;
const healthOverall = document.querySelector("#health-overall");
const healthOverview = document.querySelector("#health-overview");
const healthServices = document.querySelector("#health-services");
const healthInfrastructure = document.querySelector("#health-infrastructure");
const healthCheckedAt = document.querySelector("#health-checked-at");
const kafkaEvents = document.querySelector("#kafka-events");
const redisStatus = document.querySelector("#redis-status");

const healthGroups = [
  { name: "Portal web", section: "services" },
  { name: "Serviço de Transações", section: "services" },
  { name: "Serviço de Análise", section: "services" },
  { name: "RabbitMQ", section: "infrastructure" },
  { name: "Redis", section: "infrastructure" },
  { name: "Kafka", section: "infrastructure" },
];

function healthCategory(status) {
  switch (String(status).toUpperCase()) {
    case "UP":
      return "up";
    case "DOWN":
    case "OUT_OF_SERVICE":
      return "down";
    case "DEGRADED":
    case "WARN":
    case "WARNING":
      return "attention";
    default:
      return "unknown";
  }
}

function categoryLabel(category) {
  return {
    up: "Operacional",
    attention: "Atenção",
    down: "Indisponível",
    unknown: "Desconhecido",
  }[category];
}

function getGroupCategory(components) {
  const categories = components.map((component) => healthCategory(component.status));
  if (categories.includes("down")) return "down";
  if (categories.includes("attention")) return "attention";
  if (categories.includes("unknown")) return "unknown";
  return "up";
}

function createHealthCheck(component) {
  const category = healthCategory(component.status);
  const item = document.createElement("li");
  item.className = "health-check";
  item.title = component.description || "";

  const dot = document.createElement("span");
  dot.className = `health-dot dot-${category}`;
  dot.setAttribute("aria-hidden", "true");

  const label = document.createElement("span");
  label.className = "health-label";
  label.textContent = component.name.includes(" · ")
    ? component.name.slice(component.name.indexOf(" · ") + 3)
    : component.name;

  const status = document.createElement("strong");
  status.className = `health-text-${category}`;
  status.textContent = categoryLabel(category);

  item.append(dot, label, status);
  return item;
}

function createHealthGroup(group, components) {
  const category = getGroupCategory(components);
  const card = document.createElement("article");
  card.className = `health-group group-${category}`;

  const heading = document.createElement("div");
  heading.className = "health-group-heading";
  const title = document.createElement("h4");
  title.textContent = group.name;
  const status = document.createElement("span");
  status.className = `health-status status-${category}`;
  status.textContent = categoryLabel(category);
  heading.append(title, status);

  const count = document.createElement("p");
  count.className = "health-group-count";
  count.textContent = `${components.length} ${components.length === 1 ? "verificação" : "verificações"}`;

  const checks = document.createElement("ul");
  checks.className = "health-check-list";
  for (const component of components) {
    checks.append(createHealthCheck(component));
  }

  card.append(heading, count, checks);
  return card;
}

function renderHealth(components) {
  const counts = { up: 0, attention: 0, down: 0, unknown: 0 };
  for (const component of components) {
    counts[healthCategory(component.status)] += 1;
  }

  healthOverview.replaceChildren();
  for (const [category, label] of [
    ["up", "Operacionais"],
    ["attention", "Atenção"],
    ["down", "Indisponíveis"],
    ["unknown", "Desconhecidos"],
  ]) {
    const card = document.createElement("div");
    card.className = `health-metric metric-${category}`;
    const value = document.createElement("strong");
    value.textContent = String(counts[category]);
    const caption = document.createElement("span");
    caption.textContent = label;
    card.append(value, caption);
    healthOverview.append(card);
  }

  healthServices.replaceChildren();
  healthInfrastructure.replaceChildren();
  const rendered = new Set();
  for (const group of healthGroups) {
    const groupComponents = components.filter((component) =>
      component.name === group.name || component.name.startsWith(`${group.name} · `));
    if (groupComponents.length === 0) continue;

    for (const component of groupComponents) rendered.add(component);
    const target = group.section === "services" ? healthServices : healthInfrastructure;
    target.append(createHealthGroup(group, groupComponents));
  }

  const otherComponents = components.filter((component) => !rendered.has(component));
  if (otherComponents.length > 0) {
    healthInfrastructure.append(createHealthGroup({ name: "Outras verificações" }, otherComponents));
  }

  return counts;
}

async function loadHealth() {
  try {
    const health = await requestJson("/api/portal/health");
    const counts = renderHealth(health.components);
    const componentCategory = getGroupCategory(health.components);
    const overallCategory = health.status === "UP"
      ? componentCategory
      : componentCategory === "up" ? "attention" : componentCategory;
    const isUp = health.status === "UP" && overallCategory === "up";
    healthOverall.textContent = isUp ? "Todos operacionais" : "Atenção necessária";
    healthOverall.className = `health-status status-${isUp ? "up" : overallCategory}`;
    healthCheckedAt.textContent =
      `${health.components.length} verificações · Última verificação: ${formatDate(health.checkedAt)} · Atualização automática a cada 15 segundos.`;
    document.querySelector("#connection-label").textContent =
      isUp ? "Serviços conectados" : "Atenção à saúde dos serviços";
    document.querySelector(".live-dot").classList.toggle(
      "offline",
      !isUp || counts.down > 0 || counts.attention > 0,
    );
  } catch (error) {
    healthOverall.textContent = "Verificação indisponível";
    healthOverall.className = "health-status status-down";
    healthOverview.replaceChildren();
    const summaryMessage = document.createElement("span");
    summaryMessage.className = "health-placeholder error-text";
    summaryMessage.textContent = "Resumo indisponível";
    healthOverview.append(summaryMessage);
    healthServices.replaceChildren();
    healthInfrastructure.replaceChildren();
    const message = document.createElement("span");
    message.className = "health-placeholder error-text";
    message.textContent = error.message;
    healthServices.append(message);
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
