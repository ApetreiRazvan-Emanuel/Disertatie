/* ============================================================
 * ga.js — simplified Genetic Algorithm demo driver
 *
 * Simplified vs. LongestInducedPathGenetic.java:
 *  - tiny population, no threads / restarts / stagnation logic
 *  - same operator ideas: elitism + tournament selection,
 *    union-rebuild crossover, trim-and-extend mutation, fresh blood
 * ============================================================ */
"use strict";

let GA_NEXT_ID = 1;

const ORIGIN_META = {
  random:    { label: "random",    color: "#8f9bb8" },
  elite:     { label: "elite",     color: "#fbbf24" },
  survivor:  { label: "selected",  color: "#54a0ff" },
  crossover: { label: "crossover", color: "#c56cf0" },
  mutation:  { label: "mutation",  color: "#4ecdc4" },
  fresh:     { label: "fresh",     color: "#10ac84" },
};

class GADriver {
  constructor(renderer, ui) {
    this.r = renderer;
    this.ui = ui;   // {stats(obj), phase(txt), renderPop(), chart()}
    this.speed = 1;
    this.playing = false;
    this._loopActive = false;
    this.runId = 0;
    this.params = { popSize: 24, eliteCount: 3, mutationRate: 0.4, crossoverRate: 0.5, freshRate: 0.1 };
    this.reset();
  }

  get graph() { return this.r.graph; }

  reset() {
    this.runId++;
    this.playing = false;
    this.generation = 0;
    this.pop = [];
    this.best = null;          // individual
    this.bestGen = 0;
    this.history = [];         // [{gen, best, avg}]
    this.selectedId = null;
    this.followBest = true;
    this.lastReport = null;
    this._demoCancel = true; // aborts any running operator demo
    this.r.ants = [];
    this.r.highlightPath = null;
    this.r.secondaryPath = null;
    this.r.overlayPaths = [];
    this.r.nodeMarks = null;
    this.r.pheromone = null;
    if (this.graph) this._initPopulation();
    this.ui.phase(this.graph
      ? "population initialized with random induced walks — press ▶ Run or ⏭ Step"
      : "generate a graph first");
    this.ui.playState?.(false);
    this._afterChange();
  }

  _newIndividual(path, origin, parents) {
    return { id: GA_NEXT_ID++, path, origin, parents: parents || null, born: this.generation };
  }

  _initPopulation() {
    const g = this.graph;
    this.pop = [];
    for (let i = 0; i < this.params.popSize; i++) {
      const start = randInt(g.n);
      const p = extendBothEnds(g, [start]);
      this.pop.push(this._newIndividual(p, "random"));
    }
    this._sort();
    this._updateBest();
    this.history.push(this._snapshot());
  }

  _sort() { this.pop.sort((a, b) => b.path.length - a.path.length); }

  _updateBest() {
    let improved = false;
    for (const ind of this.pop) {
      if (!this.best || ind.path.length > this.best.path.length) {
        this.best = ind;
        this.bestGen = this.generation;
        improved = true;
      }
    }
    return improved;
  }

  _snapshot() {
    const lens = this.pop.map((i) => i.path.length);
    const avg = lens.reduce((s, x) => s + x, 0) / Math.max(1, lens.length);
    return { gen: this.generation, best: this.best ? this.best.path.length : 0, avg };
  }

  _afterChange() {
    this.ui.stats({
      generation: this.generation,
      best: this.best ? this.best.path.length : 0,
      bestGen: this.bestGen,
      popSize: this.pop.length,
    });
    this.ui.renderPop();
    this.ui.chart();
    this._syncHighlight();
  }

  _syncHighlight() {
    const sel = this.getSelected();
    this.r.highlightPath = sel ? sel.path : null;
    this.r.highlightColor = sel && this.best && sel.id === this.best.id ? COLOR_BEST : COLOR_CURRENT;
    this.r.secondaryPath =
      sel && this.best && sel.id !== this.best.id ? this.best.path : null;
  }

  getSelected() {
    if (this.followBest || this.selectedId === null) return this.best;
    return this.pop.find((i) => i.id === this.selectedId)
        || (this.best && this.best.id === this.selectedId ? this.best : null)
        || this.best;
  }

  select(id) {
    this.selectedId = id;
    this.followBest = false;
    this.ui.renderPop();
    this._syncHighlight();
  }

  setFollowBest(v) {
    this.followBest = v;
    if (v) this.selectedId = null;
    this.ui.renderPop();
    this._syncHighlight();
  }

  async play() {
    if (!this.graph) return;
    this._demoCancel = true; // a running operator demo yields to the real run
    this.playing = true;
    this.ui.playState?.(true);
    if (this._loopActive) return; // never start a second loop
    this._loopActive = true;
    const id = this.runId;
    try {
      while (this.playing && this.runId === id) {
        this.stepGeneration();
        await this._pausableDelay(id);
      }
    } finally {
      this._loopActive = false;
      this.ui.playState?.(false);
    }
  }

  pause() {
    this.playing = false;
    this.ui.playState?.(false);
  }

  /** Inter-generation delay that ends early on pause or reset. */
  _pausableDelay(id) {
    const dur = Math.max(30, 900 / this.speed);
    const t0 = performance.now();
    return new Promise((res) => {
      const tick = () => {
        if (!this.playing || this.runId !== id || performance.now() - t0 >= dur) return res();
        setTimeout(tick, 40);
      };
      setTimeout(tick, 40);
    });
  }

  /** One GA generation (synchronous — the drama is in the population panel). */
  stepGeneration() {
    const g = this.graph;
    if (!g) return;
    const P = this.params;
    this.generation++;

    /* selection: elites + tournament (chosen counts feed the selection report) */
    this._sort();
    const chosen = new Map(); // old id -> times selected into next gen
    const next = [];
    for (let i = 0; i < Math.min(P.eliteCount, this.pop.length); i++) {
      const e = this.pop[i];
      chosen.set(e.id, (chosen.get(e.id) || 0) + 1);
      next.push(this._newIndividual(e.path, "elite", [e.id]));
    }
    while (next.length < P.popSize) {
      const a = this.pop[randInt(this.pop.length)];
      const b = this.pop[randInt(this.pop.length)];
      const w = a.path.length >= b.path.length ? a : b;
      chosen.set(w.id, (chosen.get(w.id) || 0) + 1);
      next.push(this._newIndividual(w.path, "survivor", [w.id]));
    }
    for (const ind of next) ind.copies = chosen.get(ind.parents[0]) || 1;
    this.lastReport = {
      gen: this.generation,
      eliminated: this.pop.filter((i) => !chosen.has(i.id))
        .map((i) => ({ id: i.id, len: i.path.length })),
      duplicated: this.pop.filter((i) => (chosen.get(i.id) || 0) > 1)
        .map((i) => ({ id: i.id, len: i.path.length, count: chosen.get(i.id) })),
    };

    /* crossover: consecutive pairs */
    const children = [];
    for (let i = 0; i + 1 < next.length; i += 2) {
      if (Math.random() < P.crossoverRate) {
        const c = this._crossover(next[i].path, next[i + 1].path);
        children.push(this._newIndividual(c, "crossover", [next[i].id, next[i + 1].id]));
      }
    }

    /* mutation */
    const mutants = [];
    for (const ind of next) {
      if (Math.random() < P.mutationRate) {
        const m = this._mutate(ind.path);
        mutants.push(this._newIndividual(m, "mutation", [ind.id]));
      }
    }

    /* fresh blood */
    const fresh = [];
    for (let i = 0; i < P.popSize; i++) {
      if (Math.random() < P.freshRate) {
        fresh.push(this._newIndividual(extendBothEnds(g, [randInt(g.n)]), "fresh"));
      }
    }

    this.pop = [...next, ...children, ...mutants, ...fresh];
    this._sort();
    // trim back to popSize + extras so the panel stays readable
    if (this.pop.length > P.popSize + 12) this.pop.length = P.popSize + 12;

    const improved = this._updateBest();
    this.history.push(this._snapshot());
    if (this.history.length > 400) this.history.shift();

    this.ui.phase(
      `generation ${this.generation}: ${children.length} offspring, ${mutants.length} mutants, ` +
      `${fresh.length} fresh` + (improved ? ` — NEW BEST ${this.best.path.length}!` : ""));
    this._afterChange();
  }

  /** trim both ends randomly, re-extend — same idea as Java mutate(). */
  _mutate(path) {
    const g = this.graph;
    if (path.length <= 3) return extendBothEnds(g, [randInt(g.n)]);
    const maxTrim = Math.max(2, Math.floor(path.length / 3));
    const trimL = randInt(maxTrim), trimR = randInt(maxTrim);
    if (trimL + trimR >= path.length - 1) return extendBothEnds(g, [randInt(g.n)]);
    const middle = path.slice(trimL, path.length - trimR);
    return extendBothEnds(g, middle);
  }

  /* ---------------- animated operator demos ----------------
   * Run the real operator logic on the selected individual, but step by
   * step on the canvas. The resulting child joins the population. */

  _demoAborted(id) { return this.runId !== id || this._demoCancel; }
  cancelDemo() { this._demoCancel = true; }

  _demoDelay(ms) {
    return new Promise((res) => setTimeout(res, ms / this.speed));
  }

  /** extendBothEnds, animated: highlight grows one vertex at a time. */
  async _animatedExtend(seed, id, allowed) {
    const g = this.graph;
    let path = [...seed];
    const inPath = new Array(g.n).fill(false);
    for (const v of path) inPath[v] = true;
    for (let dir = 0; dir < 2; dir++) {
      for (;;) {
        if (this._demoAborted(id)) return path;
        let cands = inducedCandidates(g, path, inPath, path[path.length - 1]);
        if (allowed) cands = cands.filter((u) => allowed[u]);
        if (!cands.length) break;
        const u = choice(cands);
        path.push(u);
        inPath[u] = true;
        this.r.highlightPath = [...path];
        await this._demoDelay(230);
      }
      path.reverse();
    }
    return path;
  }

  async demoMutation() {
    const g = this.graph;
    if (!g || this.playing || this._demoBusy) return;
    const parent = this.getSelected();
    if (!parent) return;
    this._demoBusy = true;
    this._demoCancel = false;
    const id = this.runId;
    const r = this.r;
    try {
      const P = parent.path;
      this.ui.phase(`mutation demo — parent has ${P.length} vertices`);
      r.highlightPath = [...P];
      r.highlightColor = COLOR_CURRENT;
      r.secondaryPath = null;
      r.overlayPaths = [];
      await this._demoDelay(1100);
      if (this._demoAborted(id)) return;

      // trim (same idea as _mutate; clamped so a visible middle always remains)
      const maxTrim = Math.max(2, Math.floor(P.length / 3));
      let trimL = randInt(maxTrim), trimR = randInt(maxTrim);
      if (P.length <= 3) { trimL = 0; trimR = 0; }
      else if (trimL + trimR >= P.length - 1) { trimR = Math.max(0, P.length - 2 - trimL); }
      const middle = P.slice(trimL, P.length - trimR);
      this.ui.phase(`trim the ends: cut ${trimL} from one side, ${trimR} from the other`);
      r.overlayPaths = [];
      if (trimL > 0) r.overlayPaths.push({ path: P.slice(0, trimL + 1), color: "rgba(255,82,82,0.85)" });
      if (trimR > 0) r.overlayPaths.push({ path: P.slice(P.length - trimR - 1), color: "rgba(255,82,82,0.85)" });
      r.highlightPath = [...middle];
      await this._demoDelay(1600);
      if (this._demoAborted(id)) return;
      r.overlayPaths = [];

      this.ui.phase("re-extend the kept middle with a random induced walk…");
      const grown = await this._animatedExtend(middle, id);
      if (this._demoAborted(id)) return;

      const child = this._newIndividual(grown, "mutation", [parent.id]);
      this.pop.push(child);
      this._sort();
      const improved = this._updateBest();
      const diff = grown.length - P.length;
      this.ui.phase(`mutant: ${grown.length} vertices (${diff >= 0 ? "+" : ""}${diff} vs parent)`
        + (improved ? " — NEW BEST!" : ""));
      this.selectedId = child.id;
      this.followBest = false;
      this._afterChange();
    } finally {
      this._demoBusy = false;
      this.r.overlayPaths = [];
      this.r.nodeMarks = null;
    }
  }

  async demoCrossover() {
    const g = this.graph;
    if (!g || this.playing || this._demoBusy) return;
    const A = this.getSelected();
    if (!A || this.pop.length < 2) return;
    const others = this.pop.filter((i) => i.id !== A.id);
    if (!others.length) return;
    // partner from the better half of the population (pop is sorted)
    const B = others[randInt(Math.max(1, Math.ceil(others.length / 2)))];
    this._demoBusy = true;
    this._demoCancel = false;
    const id = this.runId;
    const r = this.r;
    try {
      this.ui.phase(`crossover demo — parent A (${A.path.length}, cyan) × parent B (${B.path.length}, purple)`);
      r.highlightPath = [...A.path];
      r.highlightColor = COLOR_CURRENT;
      r.secondaryPath = null;
      r.overlayPaths = [{ path: [...B.path], color: "rgba(197,108,240,0.9)", width: 4.5 }];
      await this._demoDelay(1800);
      if (this._demoAborted(id)) return;

      // union of both parents' vertices, drop some shared ones at random
      const in1 = new Set(A.path), in2 = new Set(B.path);
      const allowed = new Array(g.n).fill(false);
      for (const v of A.path) allowed[v] = true;
      for (const v of B.path) allowed[v] = true;
      const removed = [];
      let shared = 0;
      for (const v of in1) {
        if (in2.has(v)) {
          shared++;
          if (Math.random() < 0.5) { allowed[v] = false; removed.push(v); }
        }
      }
      const marks = new Map();
      for (let v = 0; v < g.n; v++) if (allowed[v]) marks.set(v, "rgba(61,255,158,0.95)");
      for (const v of removed) marks.set(v, "rgba(255,82,82,0.95)");
      r.nodeMarks = marks;
      this.ui.phase(`union of both parents (green rings) — ${shared} shared vertices, `
        + `${removed.length} dropped at random (red)`);
      await this._demoDelay(2000);
      if (this._demoAborted(id)) return;

      // rebuild: random induced walk restricted to the kept union
      let start = allowed[A.path[0]] ? A.path[0]
        : allowed[B.path[0]] ? B.path[0]
        : marks.keys().next().value;
      r.overlayPaths = [];
      r.highlightPath = [start];
      this.ui.phase("rebuild: random induced walk restricted to the kept vertices…");
      const sub = await this._animatedExtend([start], id, allowed);
      if (this._demoAborted(id)) return;

      this.ui.phase("…then extend freely outside the union where possible");
      const grown = await this._animatedExtend(sub, id);
      if (this._demoAborted(id)) return;
      r.nodeMarks = null;

      const child = this._newIndividual(grown, "crossover", [A.id, B.id]);
      this.pop.push(child);
      this._sort();
      const improved = this._updateBest();
      this.ui.phase(`child: ${grown.length} vertices (parents had ${A.path.length} and ${B.path.length})`
        + (improved ? " — NEW BEST!" : ""));
      this.selectedId = child.id;
      this.followBest = false;
      this._afterChange();
    } finally {
      this._demoBusy = false;
      this.r.overlayPaths = [];
      this.r.nodeMarks = null;
    }
  }

  /** union of parents' vertices minus random shared removals, rebuild + extend. */
  _crossover(p1, p2) {
    const g = this.graph;
    const in1 = new Set(p1), in2 = new Set(p2);
    const allowed = new Array(g.n).fill(false);
    for (const v of p1) allowed[v] = true;
    for (const v of p2) allowed[v] = true;
    for (let v = 0; v < g.n; v++)
      if (in1.has(v) && in2.has(v) && Math.random() < 0.5) allowed[v] = false;

    let start = allowed[p1[0]] ? p1[0] : allowed[p2[0]] ? p2[0] : randInt(g.n);

    // greedy induced walk restricted to `allowed`
    const path = [start];
    const inPath = new Array(g.n).fill(false);
    inPath[start] = true;
    for (;;) {
      const cands = inducedCandidates(g, path, inPath, path[path.length - 1])
        .filter((u) => allowed[u]);
      if (!cands.length) break;
      const u = choice(cands);
      path.push(u); inPath[u] = true;
    }
    return extendBothEnds(g, path);
  }
}
