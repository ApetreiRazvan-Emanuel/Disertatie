/* ============================================================
 * exact.js — exact backtracking enumeration demo driver
 *
 * Faithful port of the paper implementations:
 *  - ExactEnumOptimized.java: from every start vertex, enumerate ALL
 *    maximal induced paths by backtracking (complete search).
 *  - HLIPP10000.java (H-LIP): identical search, but a start vertex is
 *    abandoned after `maxPaths` maximal paths without improvement.
 *
 * Teaching visual: as the path grows, every vertex adjacent to a
 * non-head path vertex is permanently forbidden (red) — choosing a
 * vertex "kills" its whole neighborhood. Green rings = extendable.
 * ============================================================ */
"use strict";

class ExactDriver {
  constructor(renderer, ui) {
    this.r = renderer;
    this.ui = ui;   // {phase, stats, log, clearLog, playState}
    this.speed = 1;
    this.playing = false;
    this._loopActive = false;
    this.runId = 0;
    this.params = { variant: "hlip", maxPaths: 1000 };
    this.reset();
  }

  get graph() { return this.r.graph; }

  reset() {
    this.runId++;
    this.playing = false;
    this.done = false;
    this.best = [];
    this.deadEnds = 0;
    this.startIdx = 0;
    this.pathsThisStart = 0;
    this.lastImprov = 0;
    this.path = [];
    this.inPath = null;
    this.adjCount = null;
    this.gen = this.graph ? this._search() : null;
    this.r.blockedNodes = null;
    this.r.candNodes = null;
    this.r.hotBlockedNodes = null;
    this.r.headEdges = null;
    this.r.highlightPath = null;
    this.r.secondaryPath = null;
    this.ui.phase("ready — press ▶ Run or ⏭ Step");
    this.ui.clearLog?.();
    this.ui.stats(this._stats());
    this.ui.playState?.(false);
  }

  _maxPaths() {
    return this.params.variant === "hlip" ? this.params.maxPaths : Infinity;
  }

  _stats() {
    const n = this.graph ? this.graph.n : 0;
    return {
      start: this.graph ? `${Math.min(this.startIdx + 1, n)}/${n}` : "–",
      deadEnds: this.deadEnds,
      best: this.best.length,
      since: Math.max(0, this.pathsThisStart - this.lastImprov),
      budget: this._maxPaths(),
      variant: this.params.variant,
      done: this.done,
    };
  }

  /* ---------------- the search as a generator of events ---------------- */

  *_search() {
    const g = this.graph;
    for (let s = 0; s < g.n; s++) {
      this.startIdx = s;
      this.pathsThisStart = 0;
      this.lastImprov = 0;
      yield { type: "start", s };

      let truncated = false;
      this._push(s);
      const stack = [this._makeFrame(s)];
      yield { type: "add", v: s };
      if (stack[0].cands.length === 0) {
        truncated = yield* this._leafEvents();
      }

      while (stack.length) {
        const f = stack[stack.length - 1];
        if (!truncated && f.i < f.cands.length) {
          const t = f.cands[f.i++];
          this._push(t);
          const nf = this._makeFrame(t);
          stack.push(nf);
          yield { type: "add", v: t };
          if (nf.cands.length === 0) {
            truncated = yield* this._leafEvents();
          }
        } else {
          const v = this.path[this.path.length - 1];
          this._pop();
          stack.pop();
          yield { type: "backtrack", v };
        }
      }
      if (truncated) yield { type: "truncate", s };
    }
    this.done = true;
    yield { type: "done" };
  }

  /** candidates to extend at head v: unvisited, adjacent ONLY to v */
  _makeFrame(v) {
    const g = this.graph;
    const cands = [];
    for (const u of g.adj[v]) {
      if (!this.inPath[u] && this.adjCount[u] === 1) cands.push(u);
    }
    return { cands, i: 0 };
  }

  _push(v) {
    if (!this.inPath) {
      const n = this.graph.n;
      this.inPath = new Array(n).fill(false);
      this.adjCount = new Array(n).fill(0);
    }
    this.path.push(v);
    this.inPath[v] = true;
    for (const nb of this.graph.adj[v]) this.adjCount[nb]++;
  }

  _pop() {
    const v = this.path.pop();
    this.inPath[v] = false;
    for (const nb of this.graph.adj[v]) this.adjCount[nb]--;
  }

  /** a maximal induced path was reached (dead end): count + compare */
  *_leafEvents() {
    this.deadEnds++;
    this.pathsThisStart++;
    if (this.path.length > this.best.length) {
      this.best = [...this.path];
      this.lastImprov = this.pathsThisStart;
      yield { type: "improve", len: this.best.length };
    } else {
      yield { type: "deadend", len: this.path.length };
    }
    // H-LIP cutoff (read live so the slider applies without reset)
    return this.pathsThisStart - this.lastImprov > this._maxPaths();
  }

  /* ---------------- driver ---------------- */

  async play() {
    if (!this.graph || this.done) return;
    this.playing = true;
    this.ui.playState?.(true);
    if (this._loopActive) return;
    this._loopActive = true;
    const id = this.runId;
    try {
      while (this.playing && this.runId === id && !this.done) {
        await this._advance(id, false);
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

  async step() {
    if (!this.graph || this.playing || this.done || this._busy) return;
    this._busy = true;
    try {
      await this._advance(this.runId, true);
    } finally {
      this._busy = false;
    }
  }

  async _advance(id, single) {
    if (!this.gen) return;
    // turbo: near-max speed batches many events per frame (backtracking
    // explodes combinatorially — nobody wants to watch every backtrack)
    if (!single && this.speed >= 9) {
      let ev = null;
      for (let i = 0; i < 600; i++) {
        ev = this.gen.next().value;
        if (!ev || ev.type === "improve" || ev.type === "done") break;
      }
      if (ev) this._applyEvent(ev);
      await new Promise((r) => setTimeout(r, 16));
      return;
    }
    const ev = this.gen.next().value;
    if (!ev) return;
    this._applyEvent(ev);
    const delays = { add: 240, backtrack: 150, deadend: 480, improve: 1100, start: 650, truncate: 1100, done: 10 };
    await this._delay(delays[ev.type] ?? 200);
    void id;
  }

  _applyEvent(ev) {
    const g = this.graph;
    switch (ev.type) {
      case "start":
        this.ui.phase(`start vertex ${ev.s} (${ev.s + 1} of ${g.n}) — enumerate all induced paths from here`);
        break;
      case "add": {
        let nc = 0, nb = 0;
        for (const u of g.adj[ev.v]) {
          if (this.inPath[u]) continue;
          this.adjCount[u] === 1 ? nc++ : nb++;
        }
        this.ui.phase(nc > 0
          ? `at vertex ${ev.v} (path ${this.path.length}): ${nc} valid ${nc === 1 ? "neighbor" : "neighbors"} in green, `
            + `${nb} forbidden in red — pick a green one`
          : `at vertex ${ev.v} (path ${this.path.length}): no valid neighbors left`
            + (nb > 0 ? ` (all ${nb} are red)` : "") + ` — maximal path`);
        break;
      }
      case "backtrack":
        this.ui.phase(`backtrack from vertex ${ev.v} — options exhausted (path ${this.path.length})`);
        break;
      case "deadend":
        this.ui.phase(`maximal path of ${ev.len} — not better, dead end #${this.deadEnds.toLocaleString()}`);
        break;
      case "improve":
        this.ui.phase(`NEW BEST: ${ev.len} vertices! (dead end #${this.deadEnds.toLocaleString()})`);
        this.ui.log(`dead end #${this.deadEnds.toLocaleString()}: new best = ${ev.len} vertices (start ${this.startIdx})`);
        break;
      case "truncate":
        this.ui.phase(`H-LIP cutoff: ${this._maxPaths().toLocaleString()} paths without improvement — give up on start ${ev.s}`);
        break;
      case "done": {
        this.playing = false;
        const exact = this.params.variant === "exact";
        this.ui.phase(exact
          ? `search complete — ${this.best.length} vertices is the PROVEN optimum ✓`
          : `H-LIP finished — best found: ${this.best.length} vertices (heuristic, not a proof)`);
        this.ui.log(exact
          ? `complete: optimum = ${this.best.length} (${this.deadEnds.toLocaleString()} maximal paths enumerated)`
          : `finished: best = ${this.best.length} (${this.deadEnds.toLocaleString()} maximal paths tried)`);
        break;
      }
    }
    this._syncVisuals();
    this.ui.stats(this._stats());
  }

  _syncVisuals() {
    const g = this.graph;
    if (!g) return;
    this.r.highlightPath = this.path.length > 0 ? [...this.path] : null;
    this.r.highlightColor = COLOR_CURRENT;
    this.r.secondaryPath = this.best.length > 1 ? this.best : null;

    const blocked = new Set(), cand = new Set(), hot = new Set();
    const candE = [], blockE = [];
    if (this.path.length > 0 && this.inPath) {
      const head = this.path[this.path.length - 1];
      for (let v = 0; v < g.n; v++) {
        if (this.inPath[v]) continue;
        const adjHead = g.hasEdge(head, v) ? 1 : 0;
        if (this.adjCount[v] - adjHead >= 1) blocked.add(v);
        else if (adjHead && this.adjCount[v] === 1) cand.add(v);
      }
      // the head's own neighborhood: green edges to valid picks,
      // red edges to neighbors killed by the rest of the path
      for (const u of g.adj[head]) {
        if (this.inPath[u]) continue;
        if (this.adjCount[u] === 1) candE.push([head, u]);
        else { blockE.push([head, u]); hot.add(u); }
      }
    }
    this.r.blockedNodes = blocked;
    this.r.candNodes = cand;
    this.r.hotBlockedNodes = hot;
    this.r.headEdges = (candE.length || blockE.length) ? { cand: candE, blocked: blockE } : null;
  }

  /** pause-responsive delay (ends early when paused or reset) */
  _delay(ms) {
    const dur = ms / this.speed;
    if (dur < 8) return new Promise((r) => setTimeout(r, dur));
    const t0 = performance.now();
    const id = this.runId;
    return new Promise((res) => {
      const tick = () => {
        if (this.runId !== id || performance.now() - t0 >= dur
            || (!this.playing && !this._busy)) return res();
        setTimeout(tick, 30);
      };
      setTimeout(tick, 30);
    });
  }
}
