/* ============================================================
 * main.js — UI wiring: instance panel, tabs, controls
 * ============================================================ */
"use strict";

const $ = (id) => document.getElementById(id);

let activeTab = "exact";

/* ---------------- renderer + layout loop ---------------- */
const renderer = new Renderer($("canvas"));
window.layout = null;

function frame() {
  if (window.layout && window.layout.alpha > 0.025) {
    window.layout.tick();
    // second tick settles faster, but skip it on big graphs (O(n^2) repulsion)
    if (!renderer.graph || renderer.graph.n <= 800) window.layout.tick();
  }
  renderer.draw();
  requestAnimationFrame(frame);
}
window.addEventListener("resize", () => renderer.resize());

/* ---------------- drivers ---------------- */
const acoUI = {
  phase(txt, idx) {
    if (activeTab === "aco") $("phase-banner").textContent = txt;
    document.querySelectorAll("#aco-steps .step").forEach((el) => {
      el.classList.toggle("active", Number(el.dataset.i) === idx);
    });
  },
  stats(s) {
    $("aco-iter").textContent = s.iteration;
    $("aco-best").textContent = s.best;
    $("aco-best-iter").textContent = s.best > 0 ? s.bestIter : "–";
  },
  log(txt) {
    const el = document.createElement("div");
    el.textContent = txt;
    $("aco-log").appendChild(el);
    $("aco-log").scrollTop = $("aco-log").scrollHeight;
  },
  clearLog() { $("aco-log").innerHTML = ""; },
  playState(b) { setPlayLabel("aco", b); },
};

const gaUI = {
  phase(txt) {
    if (activeTab === "ga") $("phase-banner").textContent = txt;
  },
  stats(s) {
    $("ga-gen").textContent = s.generation;
    $("ga-best").textContent = s.best;
    $("ga-best-gen").textContent = s.best > 0 ? s.bestGen : "–";
    $("ga-pop-count").textContent = `(${s.popSize})`;
  },
  renderPop() { renderPopulation(); },
  chart() { drawChart(); },
  playState(b) { setPlayLabel("ga", b); },
};

const exUI = {
  phase(txt) {
    if (activeTab === "exact") $("phase-banner").textContent = txt;
  },
  stats(s) {
    $("ex-start").textContent = s.start;
    $("ex-best").textContent = s.best;
    $("ex-dead").textContent = s.deadEnds.toLocaleString();
    const b = $("ex-budget");
    if (s.variant === "hlip") {
      const close = s.since > s.budget * 0.8;
      b.innerHTML = `paths since last improvement: ` +
        `<span class="${close ? "warn" : ""}">${s.since.toLocaleString()}</span>` +
        ` / ${s.budget.toLocaleString()}`;
    } else {
      b.textContent = "complete search — runs until every path is enumerated";
    }
  },
  log(txt) {
    const el = document.createElement("div");
    el.textContent = txt;
    $("ex-log").appendChild(el);
    $("ex-log").scrollTop = $("ex-log").scrollHeight;
  },
  clearLog() { $("ex-log").innerHTML = ""; },
  playState(b) { setPlayLabel("ex", b); },
};

// assigned below; UI callbacks fire during construction, so guard for undefined
let aco, ga, ex;
aco = new ACODriver(renderer, acoUI);
ga = new GADriver(renderer, gaUI);
ex = new ExactDriver(renderer, exUI);

/* ---------------- instance generation ---------------- */
const GEN_PANELS = { planted: "gen-planted", random: "gen-random", cube: "gen-cube", library: "gen-library", import: "gen-import" };

// fill the benchmark dropdown (INSTANCE_LIBRARY is sorted by vertex count)
for (const [name, inst] of Object.entries(INSTANCE_LIBRARY)) {
  const opt = document.createElement("option");
  opt.value = name;
  opt.textContent = `${name} — ${inst.n} vertices, ${inst.edges.length / 2} edges`;
  $("library-select").appendChild(opt);
}

function buildLibraryGraph(name) {
  const inst = INSTANCE_LIBRARY[name];
  const g = new DemoGraph(inst.n);
  for (let i = 0; i < inst.edges.length; i += 2)
    g.addEdge(inst.edges[i], inst.edges[i + 1]);
  if (inst.pos) g.fixedPos = inst.pos; // geographic layout (e.g. usair = USA map)
  return g;
}

/** Scale fixed [0..1] coordinates to the canvas, preserving aspect ratio. */
function applyFixedPositions(g) {
  const pad = 60;
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  for (let i = 0; i < g.n; i++) {
    const x = g.fixedPos[2 * i], y = g.fixedPos[2 * i + 1];
    if (x < minX) minX = x;
    if (x > maxX) maxX = x;
    if (y < minY) minY = y;
    if (y > maxY) maxY = y;
  }
  const bw = Math.max(1e-6, maxX - minX), bh = Math.max(1e-6, maxY - minY);
  const s = Math.min((renderer.w - 2 * pad) / bw, (renderer.h - 2 * pad) / bh);
  const ox = (renderer.w - s * bw) / 2, oy = (renderer.h - s * bh) / 2;
  for (let i = 0; i < g.n; i++) {
    g.nodes[i].x = ox + (g.fixedPos[2 * i] - minX) * s;
    g.nodes[i].y = oy + (g.fixedPos[2 * i + 1] - minY) * s;
    g.nodes[i].vx = 0;
    g.nodes[i].vy = 0;
  }
}

$("gen-type").addEventListener("change", () => {
  const t = $("gen-type").value;
  for (const [k, id] of Object.entries(GEN_PANELS))
    $(id).classList.toggle("hidden", k !== t);
});

function bindSlider(id, valId, fmt) {
  const el = $(id);
  const upd = () => { $(valId).textContent = fmt ? fmt(el.value) : el.value; };
  el.addEventListener("input", upd);
  upd();
  return el;
}

bindSlider("planted-n", "planted-n-val");
bindSlider("planted-l", "planted-l-val");
bindSlider("planted-deg", "planted-deg-val");
bindSlider("rand-n", "rand-n-val");
bindSlider("rand-d", "rand-d-val", (v) => (v / 10).toFixed(1));
bindSlider("cube-d", "cube-d-val");

// keep path length <= vertices
$("planted-n").addEventListener("input", () => {
  const n = Number($("planted-n").value);
  const l = $("planted-l");
  l.max = Math.max(5, Math.floor(n * 0.7));
  if (Number(l.value) > Number(l.max)) l.value = l.max;
  $("planted-l-val").textContent = l.value;
});
$("planted-n").dispatchEvent(new Event("input")); // apply the cap to the initial value

$("btn-generate").addEventListener("click", () => {
  const type = $("gen-type").value;
  let g;
  try {
    if (type === "planted") {
      const n = Number($("planted-n").value);
      const l = Math.min(Number($("planted-l").value), n - 1);
      g = generatePlanted(n, l, Number($("planted-deg").value));
    } else if (type === "random") {
      g = generateRandom(Number($("rand-n").value), Number($("rand-d").value) / 10);
    } else if (type === "cube") {
      g = generateCube(Number($("cube-d").value));
    } else if (type === "library") {
      g = buildLibraryGraph($("library-select").value);
    } else {
      const text = $("import-text").value.trim();
      if (!text) { alert("Paste a DIMACS or GraphML instance first."); return; }
      g = parseInstance(text);
    }
  } catch (err) {
    alert("Could not build graph: " + err.message);
    return;
  }
  setGraph(g);
});

function setGraph(g) {
  aco.pause(); ga.pause();
  renderer.resize();
  renderer.setGraph(g);
  window.layout = new ForceLayout(g, renderer.w, renderer.h,
    Number($("spacing").value) / 100);
  if (g.fixedPos) {
    applyFixedPositions(g);
    window.layout.frozen = true;
    window.layout.alpha = 0;
  }
  aco.reset();
  ga.reset();
  ex.reset();
  resyncTab();

  $("graph-info").textContent =
    `${g.n} vertices · ${g.edges.length} edges · avg degree ${(2 * g.edges.length / g.n).toFixed(1)}` +
    (g.plantedPath ? ` · planted path: ${g.plantedPath.length}` : "");
  $("planted-info").textContent = g.plantedPath
    ? `★ hidden optimal path: ${g.plantedPath.length} vertices` : "";
  $("planted-info").classList.toggle("hidden", !g.plantedPath);
  $("planted-reveal-wrap").style.display = g.plantedPath ? "" : "none";
  $("show-planted").checked = false;
  renderer.showPlanted = false;
}

$("show-planted").addEventListener("change", (e) => {
  renderer.showPlanted = e.target.checked;
});

/* hide the phase banner over the graph (on by default) */
$("hide-banner").addEventListener("change", (e) => {
  $("phase-banner").classList.toggle("hidden", e.target.checked);
});
$("phase-banner").classList.add("hidden");

/* vertex spacing: retunes the layout live */
bindSlider("spacing", "spacing-val", (v) => v + "%");
$("spacing").addEventListener("input", (e) => {
  if (!window.layout) return;
  window.layout.spacing = Number(e.target.value) / 100;
  window.layout.alpha = Math.max(window.layout.alpha, 0.5); // re-settle
});

/* ---------------- tabs ---------------- */
document.querySelectorAll(".tab").forEach((btn) => {
  btn.addEventListener("click", () => {
    if (btn.dataset.tab === activeTab) return;
    activeTab = btn.dataset.tab;
    document.querySelectorAll(".tab").forEach((b) => b.classList.toggle("active", b === btn));
    $("tab-aco").classList.toggle("hidden", activeTab !== "aco");
    $("tab-ga").classList.toggle("hidden", activeTab !== "ga");
    $("tab-exact").classList.toggle("hidden", activeTab !== "exact");
    aco.pause(); ga.pause(); ga.cancelDemo(); ex.pause();
    resyncTab();
  });
});

function resyncTab() {
  $("legend-phero-row").style.opacity = activeTab === "aco" ? "1" : "0.3";
  document.querySelectorAll(".exact-legend").forEach((el) =>
    el.classList.toggle("hidden", activeTab !== "exact"));
  if (!renderer.graph) {
    $("phase-banner").textContent = "generate a graph to begin";
    return;
  }
  renderer.ants = [];
  renderer.flashEdges = null;
  renderer.overlayPaths = [];
  renderer.nodeMarks = null;
  renderer.blockedNodes = null;
  renderer.candNodes = null;
  renderer.hotBlockedNodes = null;
  renderer.headEdges = null;
  if (activeTab === "aco") {
    renderer.pheromone = aco.tau;
    renderer.highlightPath = aco.bestPath.length > 1 ? aco.bestPath : null;
    renderer.highlightColor = COLOR_BEST;
    renderer.secondaryPath = null;
    $("phase-banner").textContent =
      aco.iteration > 0
        ? `ACO — iteration ${aco.iteration}, best = ${aco.bestPath.length}`
        : "ACO ready — press ▶ Run and watch the ants";
  } else if (activeTab === "ga") {
    renderer.pheromone = null;
    ga._syncHighlight();
    $("phase-banner").textContent =
      ga.generation > 0
        ? `GA — generation ${ga.generation}, best = ${ga.best ? ga.best.path.length : 0}`
        : "GA ready — population of random induced paths created";
  } else {
    renderer.pheromone = null;
    ex._syncVisuals();
    $("phase-banner").textContent = ex.done
      ? `exact search finished — best = ${ex.best.length}`
      : ex.deadEnds > 0
        ? `exact search — ${ex.deadEnds.toLocaleString()} dead ends, best = ${ex.best.length}`
        : "exact backtracking ready — press ▶ Run or ⏭ Step";
  }
}

/* ---------------- speed sliders (0.1x .. 10x, log scale) ---------------- */
function speedFromSlider(v) { return 0.1 * Math.pow(10, v / 50); }
function bindSpeed(id, valId, driver) {
  const el = $(id);
  const upd = () => {
    driver.speed = speedFromSlider(Number(el.value));
    $(valId).textContent = driver.speed >= 3
      ? Math.round(driver.speed) + "×"
      : driver.speed.toFixed(driver.speed < 1 ? 2 : 1) + "×";
  };
  el.addEventListener("input", upd);
  upd();
}
bindSpeed("aco-speed", "aco-speed-val", aco);
bindSpeed("ga-speed", "ga-speed-val", ga);
bindSpeed("ex-speed", "ex-speed-val", ex);
$("ex-speed").addEventListener("input", () => {
  $("ex-turbo-hint").textContent = ex.speed >= 9 ? "(turbo: batches of steps)" : "";
});

/* ---------------- play / step / reset ---------------- */
function setPlayLabel(which, playing) {
  const btn = $(which + "-play");
  btn.textContent = playing ? "⏸ Pause" : "▶ Run";
  btn.classList.toggle("playing", playing);
}

$("aco-play").addEventListener("click", () => {
  if (!renderer.graph) return;
  if (aco.playing) aco.pause();
  else aco.play();
});
$("aco-step").addEventListener("click", () => aco.step());
$("aco-reset").addEventListener("click", () => { aco.reset(); resyncTab(); });

$("ga-play").addEventListener("click", () => {
  if (!renderer.graph) return;
  if (ga.playing) ga.pause();
  else ga.play();
});
$("ga-step").addEventListener("click", () => { if (!ga.playing && !ga._demoBusy) ga.stepGeneration(); });
$("ga-reset").addEventListener("click", () => { ga.reset(); resyncTab(); });
$("ga-demo-mut").addEventListener("click", () => ga.demoMutation());
$("ga-demo-cx").addEventListener("click", () => ga.demoCrossover());

/* ---------------- ACO params ---------------- */
bindSlider("aco-ants", "aco-ants-val");
bindSlider("aco-alpha", "aco-alpha-val", (v) => (v / 10).toFixed(1));
bindSlider("aco-beta", "aco-beta-val", (v) => (v / 10).toFixed(1));
bindSlider("aco-rho", "aco-rho-val", (v) => (v / 100).toFixed(2));

$("aco-ants").addEventListener("input", (e) => aco.params.numAnts = Number(e.target.value));
$("aco-alpha").addEventListener("input", (e) => aco.params.alpha = Number(e.target.value) / 10);
$("aco-beta").addEventListener("input", (e) => aco.params.beta = Number(e.target.value) / 10);
$("aco-rho").addEventListener("input", (e) => aco.params.rho = Number(e.target.value) / 100);
$("aco-elitist").addEventListener("change", (e) => aco.params.elitist = e.target.checked);
$("aco-show-tau").addEventListener("change", (e) => renderer.showPheroLabels = e.target.checked);

/* ---------------- GA params ---------------- */
bindSlider("ga-pop", "ga-pop-val");
bindSlider("ga-elite", "ga-elite-val");
bindSlider("ga-mut", "ga-mut-val", (v) => (v / 100).toFixed(2));
bindSlider("ga-cx", "ga-cx-val", (v) => (v / 100).toFixed(2));
bindSlider("ga-fresh", "ga-fresh-val", (v) => (v / 100).toFixed(2));

$("ga-pop").addEventListener("input", (e) => ga.params.popSize = Number(e.target.value));
$("ga-elite").addEventListener("input", (e) => ga.params.eliteCount = Number(e.target.value));
$("ga-mut").addEventListener("input", (e) => ga.params.mutationRate = Number(e.target.value) / 100);
$("ga-cx").addEventListener("input", (e) => ga.params.crossoverRate = Number(e.target.value) / 100);
$("ga-fresh").addEventListener("input", (e) => ga.params.freshRate = Number(e.target.value) / 100);

$("ga-follow").addEventListener("change", (e) => ga.setFollowBest(e.target.checked));

/* ---------------- Exact tab ---------------- */
$("ex-play").addEventListener("click", () => {
  if (!renderer.graph) return;
  if (ex.playing) ex.pause();
  else ex.play();
});
$("ex-step").addEventListener("click", () => ex.step());
$("ex-reset").addEventListener("click", () => { ex.reset(); resyncTab(); });

function exMaxPathsFromSlider(v) {
  // log scale: 100 .. 10000
  return Math.round(Math.pow(10, 2 + v / 50) / 10) * 10;
}
$("ex-maxp").addEventListener("input", (e) => {
  ex.params.maxPaths = exMaxPathsFromSlider(Number(e.target.value));
  $("ex-maxp-val").textContent = ex.params.maxPaths.toLocaleString();
  exUI.stats(ex._stats());
});
$("ex-variant").addEventListener("change", (e) => {
  ex.params.variant = e.target.value;
  $("ex-maxp-wrap").classList.toggle("hidden", ex.params.variant !== "hlip");
  exUI.stats(ex._stats());
});

/* ---------------- GA population list ---------------- */
function renderPopulation() {
  if (!ga) return;
  renderReport();
  $("ga-follow").checked = ga.followBest;
  const list = $("ga-pop-list");
  list.innerHTML = "";
  if (!ga.pop.length) return;
  const maxLen = Math.max(1, ga.best ? ga.best.path.length : 1, ga.pop[0].path.length);
  const selected = ga.getSelected();

  ga.pop.forEach((ind, rank) => {
    const meta = ORIGIN_META[ind.origin] || ORIGIN_META.random;
    const row = document.createElement("div");
    row.className = "ind";
    if (selected && ind.id === selected.id) row.classList.add("selected");
    if (ga.best && ind.id === ga.best.id) row.classList.add("is-best");
    if (ind.born === ga.generation && ind.origin !== "elite" && ind.origin !== "survivor")
      row.classList.add("newborn");

    const star = ga.best && ind.id === ga.best.id ? "★" : "";
    const copies = ind.copies > 1 ? `×${ind.copies}` : "";
    row.innerHTML =
      `<span class="rank">${rank + 1}</span>` +
      `<span class="chip" style="background:${meta.color}">${meta.label}</span>` +
      `<span class="copies" title="its source was selected ${ind.copies} times">${copies}</span>` +
      `<span class="bar-wrap"><span class="bar" style="width:${(ind.path.length / maxLen) * 100}%"></span></span>` +
      `<span class="len">${ind.path.length}</span>` +
      `<span class="star">${star}</span>`;
    row.title = `#${ind.id} · ${meta.label} · ${ind.path.length} vertices\npath: ${ind.path.join(" → ")}`;
    row.addEventListener("click", () => {
      $("ga-follow").checked = false;
      ga.select(ind.id);
    });
    list.appendChild(row);
  });
}

/* ---------------- GA selection report ---------------- */
function renderReport() {
  const el = $("ga-report");
  const rep = ga ? ga.lastReport : null;
  if (!rep) { el.classList.add("hidden"); return; }
  el.classList.remove("hidden");
  const elim = rep.eliminated, dup = rep.duplicated;
  const fmtElim = elim.slice(0, 5).map((e) => e.len).join(", ")
    + (elim.length > 5 ? `, +${elim.length - 5} more` : "");
  const fmtDup = dup.slice(0, 4).map((d) => `${d.len} ×${d.count}`).join(", ")
    + (dup.length > 4 ? `, +${dup.length - 4} more` : "");
  el.innerHTML =
    `<div>☠ <b>${elim.length}</b> eliminated${elim.length ? ` (lengths ${fmtElim})` : ""}</div>` +
    `<div>⧉ <b>${dup.length}</b> selected 2+ times${dup.length ? ` (${fmtDup})` : ""}</div>`;
}

/* ---------------- GA chart ---------------- */
function drawChart() {
  if (!ga) return;
  const cv = $("ga-chart");
  const ctx = cv.getContext("2d");
  const dpr = window.devicePixelRatio || 1;
  const W = cv.clientWidth || 300, H = cv.clientHeight || 90;
  cv.width = W * dpr; cv.height = H * dpr;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, W, H);

  const hist = ga.history;
  if (hist.length < 2) return;

  let yMax = 1;
  for (const h of hist) yMax = Math.max(yMax, h.best);
  const planted = renderer.graph && renderer.graph.plantedPath
    ? renderer.graph.plantedPath.length : 0;
  yMax = Math.max(yMax, planted) * 1.1;

  const px = (i) => 6 + (i / (hist.length - 1)) * (W - 12);
  const py = (v) => H - 6 - (v / yMax) * (H - 12);

  if (planted > 0) {
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = "rgba(251,191,36,0.5)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(6, py(planted)); ctx.lineTo(W - 6, py(planted));
    ctx.stroke();
    ctx.setLineDash([]);
  }

  const line = (get, color, width) => {
    ctx.beginPath();
    hist.forEach((h, i) => {
      const x = px(i), y = py(get(h));
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    });
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineJoin = "round";
    ctx.stroke();
  };
  line((h) => h.avg, "#54a0ff", 1.5);
  line((h) => h.best, COLOR_BEST, 2);
}

/* ---------------- focus mode ---------------- */
function setFocusMode(on) {
  document.body.classList.toggle("focus", on);
  renderer.resize();
  if (on) renderer.fitToView();
}
$("btn-focus").addEventListener("click", () => setFocusMode(true));
$("btn-focus-exit").addEventListener("click", () => setFocusMode(false));
window.addEventListener("keydown", (e) => {
  const tag = e.target && e.target.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
  if (e.key === "f" || e.key === "F") {
    setFocusMode(!document.body.classList.contains("focus"));
  } else if (e.key === "Escape") {
    setFocusMode(false);
  }
});

/* ---------------- boot ---------------- */
renderer.resize();
setGraph(generatePlanted(40, 20, 5));
requestAnimationFrame(frame);
