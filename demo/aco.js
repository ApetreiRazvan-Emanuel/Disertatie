/* ============================================================
 * aco.js — simplified Ant Colony Optimization demo driver
 *
 * Intentionally simplified vs. LongestInducedPathACO.java:
 *  - a handful of ants, one shared undirected pheromone value per edge
 *  - each iteration: ants walk -> evaluate -> evaporation -> deposit
 *  - no threads, restarts, sprints or tolerance schedules
 * The essence (pheromone-guided induced walks + evaporate/deposit) is intact.
 *
 * The driver is a stage machine: each _runStage() call executes ONE small
 * stage (spawn / one round of ant moves / evaluate / evaporate / deposit),
 * so Pause takes effect between stages and Step advances stage by stage.
 * ============================================================ */
"use strict";

class ACODriver {
  constructor(renderer, ui) {
    this.r = renderer;
    this.ui = ui;             // {phase(txt,idx), stats(obj), log(txt)}
    this.speed = 1;
    this.playing = false;     // user intent: auto-advance stages
    this.paused = false;      // freezes the animation clock immediately
    this._loopActive = false; // a play loop is alive (possibly frozen mid-stage)
    this._stagePromise = null;
    this.runId = 0;
    this.params = { numAnts: 2, alpha: 1.5, beta: 0.5, rho: 0.15, elitist: true };
    this.reset();
  }

  get graph() { return this.r.graph; }

  reset() {
    this.runId++;
    this.playing = false;
    this.paused = false;
    this.iteration = 0;
    this.bestPath = [];
    this.bestIter = 0;
    this.stage = "spawn";
    this.ants = [];
    this.walkRound = 0;
    this._iterBest = null;
    this._improved = false;
    this.tau = new Map();
    this.tauMin = 1.0;
    this.tauMax = this.graph ? Math.max(20, this.graph.n * 0.6) : 30;
    if (this.graph) {
      for (const e of this.graph.edges)
        this.tau.set(DemoGraph.edgeKey(e.a, e.b), this.tauMin);
      this.r.pheromone = this.tau;
      this.r.tauMin = this.tauMin;
      this.r.tauMax = this.tauMax; // full scale: only heavily reinforced edges reach bright green
    } else {
      this.r.pheromone = null;
    }
    this.r.ants = [];
    this.r.highlightPath = null;
    this.r.secondaryPath = null;
    this.r.flashEdges = null;
    this.ui.phase("ready — press ▶ Run or ⏭ Step", -1);
    this.ui.stats(this._stats());
    if (this.ui.clearLog) this.ui.clearLog();
    this.ui.playState?.(false);
  }

  _stats() {
    return {
      iteration: this.iteration,
      best: Math.max(0, this.bestPath.length),
      bestIter: this.bestIter,
    };
  }

  /**
   * rAF clock that only advances while not paused, so Pause freezes any
   * animation mid-flight and Run/Step resumes it. Aborts on reset (runId).
   */
  _animTime(dur, onProgress) {
    dur = Math.max(1, dur);
    const id = this.runId;
    return new Promise((res) => {
      let elapsed = 0;
      let last = performance.now();
      const tick = (now) => {
        if (this.runId !== id) return res();
        if (!this.paused) elapsed += now - last;
        last = now;
        const t = Math.min(1, elapsed / dur);
        if (onProgress) onProgress(t);
        if (t >= 1) return res();
        requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
  }

  _delay(ms) {
    const t = ms / this.speed;
    if (t < 4) return Promise.resolve();
    return this._animTime(t, null);
  }

  /** weight for moving from v to u */
  _weight(v, u) {
    const tau = this.tau.get(DemoGraph.edgeKey(v, u)) ?? this.tauMin;
    let w = Math.pow(tau, this.params.alpha);
    if (this.params.beta > 0) {
      w *= Math.pow(1 + 1 / this.graph.degree(u), this.params.beta * 4);
    }
    return w;
  }

  _pickWeighted(v, cands) {
    const ws = cands.map((u) => this._weight(v, u));
    const total = ws.reduce((s, x) => s + x, 0);
    let r = Math.random() * total;
    for (let i = 0; i < cands.length; i++) {
      r -= ws[i];
      if (r <= 0) return cands[i];
    }
    return cands[cands.length - 1];
  }

  async play() {
    if (!this.graph) return;
    this.playing = true;
    this.paused = false;            // resumes a frozen animation instantly
    this.ui.playState?.(true);
    if (this._loopActive) return;   // the existing (possibly frozen) loop continues
    this._loopActive = true;
    const id = this.runId;
    try {
      while (this.playing && this.runId === id) {
        await this._runStage();
      }
    } finally {
      this._loopActive = false;
      this.ui.playState?.(false);
    }
  }

  /** Freezes immediately, even mid-animation. Run or Step resumes. */
  pause() {
    this.playing = false;
    this.paused = true;
    this.ui.playState?.(false);
  }

  /** One micro-step; if paused mid-stage, finishes the current stage instead. */
  async step() {
    if (!this.graph || this.playing) return;
    this.paused = false;
    if (this._stagePromise) return; // frozen mid-stage: let it play out to the boundary
    await this._runStage();
  }

  /* ---------------- stage machine ---------------- */

  /** Runs one stage; concurrent callers share the in-flight promise (no spin). */
  _runStage() {
    if (!this._stagePromise) {
      this._stagePromise = this._execStage()
        .finally(() => { this._stagePromise = null; });
    }
    return this._stagePromise;
  }

  async _execStage() {
    switch (this.stage) {
      case "spawn":     await this._stageSpawn(); break;
      case "walk":      await this._stageWalk(); break;
      case "evaluate":  await this._stageEvaluate(); break;
      case "evaporate": await this._stageEvaporate(); break;
      case "deposit":   await this._stageDeposit(); break;
    }
  }

  async _stageSpawn() {
    const g = this.graph;
    const id = this.runId;
    this.iteration++;
    this.walkRound = 0;
    this.ui.phase(`iteration ${this.iteration} — ants spawn and start exploring`, 0);
    const starts = this._pickStarts(this.params.numAnts);
    this.ants = starts.map((s, i) => ({
      path: [s],
      inPath: (() => { const a = new Array(g.n).fill(false); a[s] = true; return a; })(),
      color: ANT_COLORS[i % ANT_COLORS.length],
      pos: { x: g.nodes[s].x, y: g.nodes[s].y },
      stuck: false,
    }));
    this.r.ants = this.ants;
    this.r.flashEdges = null;
    this.r.highlightPath = null;
    this.r.secondaryPath = this.bestPath.length > 1 ? this.bestPath : null;
    await this._delay(350);
    if (this.runId !== id) return;
    this.stage = "walk";
  }

  /** ONE simultaneous round of moves; stays in "walk" until every ant is stuck. */
  async _stageWalk() {
    const g = this.graph;
    const id = this.runId;
    const moves = [];
    for (const ant of this.ants) {
      if (ant.stuck) continue;
      const head = ant.path[ant.path.length - 1];
      const cands = inducedCandidates(g, ant.path, ant.inPath, head);
      if (cands.length === 0) { ant.stuck = true; continue; }
      const u = this._pickWeighted(head, cands);
      moves.push({ ant, from: head, to: u });
      ant.path.push(u);
      ant.inPath[u] = true;
    }
    if (moves.length === 0) {
      this.stage = "evaluate";
      return;
    }
    this.walkRound++;
    this.ui.phase(`iteration ${this.iteration} — ants exploring (move ${this.walkRound})`, 0);
    await this._animateMoves(moves, 260);
    if (this.runId !== id) return;
    // stay in "walk"
  }

  async _stageEvaluate() {
    const id = this.runId;
    const sorted = [...this.ants].sort((a, b) => b.path.length - a.path.length);
    const iterBest = sorted[0];
    this._iterBest = iterBest;
    this.ui.phase(
      `iteration ${this.iteration} — best ant found ${iterBest.path.length} vertices`, 1);
    this.r.highlightPath = iterBest.path;
    this.r.highlightColor = COLOR_CURRENT;
    this._improved = false;
    if (iterBest.path.length > this.bestPath.length) {
      this.bestPath = [...iterBest.path];
      this.bestIter = this.iteration;
      this._improved = true;
      this.r.highlightColor = COLOR_BEST;
      this.ui.log(`iter ${this.iteration}: new best = ${this.bestPath.length} vertices`);
    }
    this.ui.stats(this._stats());
    await this._delay(this._improved ? 800 : 450);
    if (this.runId !== id) return;
    this.stage = "evaporate";
  }

  async _stageEvaporate() {
    const id = this.runId;
    const P = this.params;
    this.ui.phase(`iteration ${this.iteration} — pheromone evaporates (×${(1 - P.rho).toFixed(2)})`, 2);
    for (const [k, v] of this.tau)
      this.tau.set(k, Math.max(this.tauMin, v * (1 - P.rho)));
    this.r.evapFlash = 1;
    await this._fade((t) => { this.r.evapFlash = 1 - t; }, 350 / this.speed);
    this.r.evapFlash = 0;
    if (this.runId !== id) return;
    this.stage = "deposit";
  }

  async _stageDeposit() {
    const id = this.runId;
    const P = this.params;
    this.ui.phase(`iteration ${this.iteration} — best ants deposit pheromone`, 3);
    const depositors = [this._iterBest.path];
    if (P.elitist && this.bestPath.length > 1 && !this._improved)
      depositors.push(this.bestPath);
    const flash = new Set();
    for (const path of depositors) {
      const gain = path.length * 0.9;
      for (let i = 0; i < path.length - 1; i++) {
        const k = DemoGraph.edgeKey(path[i], path[i + 1]);
        this.tau.set(k, Math.min(this.tauMax, (this.tau.get(k) ?? this.tauMin) + gain));
        flash.add(k);
      }
    }
    this.r.flashEdges = flash;
    await this._fade((t) => { this.r.flashT = t; }, 550 / this.speed);
    this.r.flashEdges = null;
    if (this.runId !== id) return;

    // clear ants between iterations, keep pheromone glow
    this.r.ants = [];
    this.ants = [];
    this.ui.stats(this._stats());
    await this._delay(200);
    if (this.runId !== id) return;
    this.stage = "spawn";
  }

  _pickStarts(k) {
    const g = this.graph;
    const prefer = [];
    for (let v = 0; v < g.n; v++) if (g.degree(v) >= 1) prefer.push(v);
    shuffle(prefer);
    return prefer.slice(0, Math.min(k, prefer.length));
  }

  /** Animate simultaneous ant moves by interpolating positions. */
  _animateMoves(moves, ms) {
    const g = this.graph;
    const dur = Math.max(16, ms / this.speed);
    if (dur <= 20) {
      for (const m of moves) {
        const nd = g.nodes[m.to];
        m.ant.pos = { x: nd.x, y: nd.y };
      }
      return Promise.resolve();
    }
    const startPos = moves.map((m) => ({ x: g.nodes[m.from].x, y: g.nodes[m.from].y }));
    return this._animTime(dur, (t) => {
      const e = t * t * (3 - 2 * t); // smoothstep
      for (let i = 0; i < moves.length; i++) {
        const m = moves[i], a = startPos[i], b = g.nodes[m.to];
        m.ant.pos = { x: a.x + (b.x - a.x) * e, y: a.y + (b.y - a.y) * e };
      }
    });
  }

  _fade(fn, dur) { return this._animTime(Math.max(16, dur), fn); }
}
