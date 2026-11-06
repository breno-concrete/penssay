"use strict";

// Everything the model writes is shown with textContent, never innerHTML, so an answer cannot inject markup.

const MIN_WORDS = 70;           // same rule as EvaluationService.MIN_WORDS
const MAX_PHOTO_SIDE = 2000;    // px; enough to read handwriting and keeps uploads small on mobile data
const COMPETENCIES = [
  "Norma culta",
  "Tema e repertório",
  "Argumentação",
  "Coesão",
  "Proposta de intervenção",
];
const ZERO_REASONS = {
  TOO_SHORT: "Nota zero: o texto tem menos de 70 palavras (cerca de 7 linhas).",
  OFF_TOPIC: "Nota zero: a redação fugiu ao tema.",
  NOT_ARGUMENTATIVE: "Nota zero: o texto não é dissertativo-argumentativo.",
};
const LOADING_STEPS = [
  "Lendo sua redação…",
  "Avaliando as 5 competências…",
  "Conferindo argumentos e coesão…",
  "Escrevendo as dicas…",
];

const $ = (id) => document.getElementById(id);
const form = $("essay-form");
const theme = $("theme");
const text = $("text");
const photo = $("photo");
const photoButton = $("photo-button");
const photoLabel = $("photo-label");
const submit = $("submit");
const formError = $("form-error");

function show(step) {
  for (const el of document.querySelectorAll(".step")) {
    el.classList.toggle("is-visible", el === step);
  }
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function showError(message) {
  formError.textContent = message;
  formError.hidden = false;
}

function clearError() {
  formError.hidden = true;
}

function countWords(value) {
  const trimmed = value.trim();
  return trimmed ? trimmed.split(/\s+/).length : 0;
}

function refreshForm() {
  const words = countWords(text.value);
  const wordCount = $("word-count");
  wordCount.textContent = `${words} ${words === 1 ? "palavra" : "palavras"}`;
  wordCount.classList.toggle("is-ok", words >= MIN_WORDS);
  submit.disabled = !theme.value.trim() || words === 0;
}

// Turns an HTTP failure into the server's message; Retry-After becomes a readable wait.
async function errorMessage(response, fallback) {
  let message = fallback;
  try {
    const body = await response.json();
    if (body && typeof body.message === "string") message = body.message;
  } catch {
    // no JSON body (e.g. a proxy error page): keep the fallback
  }
  const retryAfter = Number(response.headers.get("Retry-After"));
  if (response.status === 429 && retryAfter > 0) {
    const minutes = Math.ceil(retryAfter / 60);
    message += minutes >= 60
      ? ` Libera em cerca de ${Math.ceil(minutes / 60)} h.`
      : ` Libera em cerca de ${minutes} min.`;
  }
  return message;
}

// Shrinks a phone photo before upload. If the browser cannot decode it, the original goes as is.
async function shrink(file) {
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_PHOTO_SIDE / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size < 3 * 1024 * 1024) return file;
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext("2d").drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
    return blob || file;
  } catch {
    return file;
  }
}

photo.addEventListener("change", async () => {
  const file = photo.files[0];
  photo.value = ""; // lets the same photo be picked again after an error
  if (!file) return;

  clearError();
  photoButton.classList.add("is-busy");
  photoLabel.textContent = "Lendo a foto…";
  try {
    const data = new FormData();
    data.append("photo", await shrink(file), "redacao.jpg");
    const response = await fetch("/api/transcriptions", { method: "POST", body: data });
    if (!response.ok) {
      showError(await errorMessage(response, "Não conseguimos ler a foto. Tente de novo."));
      return;
    }
    const body = await response.json();
    text.value = body.text || "";
    $("read-hint").hidden = false;
    refreshForm();
    text.focus({ preventScroll: true });
    text.scrollIntoView({ behavior: "smooth", block: "center" });
  } catch {
    showError("Sem conexão. Confira sua internet e tente de novo.");
  } finally {
    photoButton.classList.remove("is-busy");
    photoLabel.textContent = "Tirar outra foto";
  }
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearError();
  if (!theme.value.trim()) {
    showError("Informe o tema da redação.");
    theme.focus();
    return;
  }

  show($("loading"));
  let step = 0;
  const loadingText = $("loading-text");
  loadingText.textContent = LOADING_STEPS[0];
  const ticker = setInterval(() => {
    step = Math.min(step + 1, LOADING_STEPS.length - 1);
    loadingText.textContent = LOADING_STEPS[step];
  }, 3500);

  try {
    const response = await fetch("/api/evaluations", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ theme: theme.value.trim(), text: text.value }),
    });
    if (!response.ok) {
      show(form);
      showError(await errorMessage(response, "Não conseguimos concluir a correção. Tente de novo."));
      return;
    }
    renderResult(await response.json());
  } catch {
    show(form);
    showError("Sem conexão. Confira sua internet e tente de novo.");
  } finally {
    clearInterval(ticker);
  }
});

function renderResult(evaluation) {
  const list = $("competencies");
  list.replaceChildren();
  const template = $("competency-template");

  const competencies = [...(evaluation.competencies || [])].sort((a, b) => a.number - b.number);
  competencies.forEach((c, index) => {
    const item = template.content.firstElementChild.cloneNode(true);
    item.style.animationDelay = `${0.15 + index * 0.08}s`;
    item.querySelector(".c-number").textContent = `C${c.number}`;
    item.querySelector(".c-name").textContent = COMPETENCIES[c.number - 1] || "";
    item.querySelector(".c-score").textContent = c.score;
    item.querySelector(".c-justification").textContent = c.justification || "";
    const tips = item.querySelector(".c-tips");
    for (const tip of c.tips || []) {
      const li = document.createElement("li");
      li.textContent = tip;
      tips.append(li);
    }
    const bar = item.querySelector(".c-bar span");
    requestAnimationFrame(() => requestAnimationFrame(() => {
      bar.style.width = `${(c.score / 200) * 100}%`;
    }));
    list.append(item);
  });

  const zeroReason = $("zero-reason");
  zeroReason.textContent = ZERO_REASONS[evaluation.zeroReason] || "";
  zeroReason.hidden = !zeroReason.textContent;

  show($("result"));
  animateTotal(evaluation.totalScore || 0);
}

function animateTotal(total) {
  const number = $("total-score");
  const ring = $("ring-bar");
  const length = 2 * Math.PI * 52;
  ring.style.strokeDashoffset = length;
  requestAnimationFrame(() => requestAnimationFrame(() => {
    ring.style.strokeDashoffset = length * (1 - total / 1000);
  }));

  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduce) {
    number.textContent = total;
    return;
  }
  const start = performance.now();
  const duration = 1200;
  const tick = (now) => {
    const progress = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - progress, 3);
    number.textContent = Math.round(total * eased);
    if (progress < 1) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

$("again").addEventListener("click", () => {
  text.value = "";
  $("read-hint").hidden = true;
  photoLabel.textContent = "Tirar ou enviar foto";
  refreshForm();
  show(form);
  text.focus({ preventScroll: true });
});

theme.addEventListener("input", refreshForm);
text.addEventListener("input", () => {
  $("read-hint").hidden = true;
  refreshForm();
});
refreshForm();
