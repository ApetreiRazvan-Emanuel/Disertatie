/* ============================================================
 * render.js — canvas renderer: graph, pheromone, paths, ants
 * ============================================================ */
"use strict";

/* Color semantics (kept distinct so each hue means one thing):
 *  green  = pheromone (intensity = amount)   gold  = planted optimal path
 *  pink   = best path found so far           cyan  = current / selected path
 *  ants   = warm palette, no greens/golds                                  */
const COLOR_BEST = "#ff6bcb";
const COLOR_CURRENT = "#48dbfb";

const ANT_COLORS = [
  "#ff6b81", "#54a0ff", "#ff9f43", "#c56cf0", "#f8a5c2",
  "#ee5253", "#7d5fff", "#e15f41", "#786fa6", "#ff9ff3",
  "#546de5", "#ffa801", "#b33939", "#9980fa", "#ffb8b8",
  "#cd6133", "#40407a", "#ff5252", "#706fd3", "#d1ccc0",
];

class Renderer {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.graph = null;

    // view transform
    this.scale = 1;
    this.tx = 0;
    this.ty = 0;

    // overlays set by algorithm drivers
    this.pheromone = null;         // Map edgeKey -> tau (ACO)
    this.showPheroLabels = false;  // draw tau values on edges without hovering
    this.lineScale = 1;            // global stroke-width multiplier (photos/videos)
    this.tauMin = 0.1; this.tauMax = 30;
    this.ants = [];                // [{pos:{x,y}, color, path:[v...], stuck}]
    this.highlightPath = null;     // main highlighted path (best / selected)
    this.highlightColor = COLOR_CURRENT;
    this.secondaryPath = null;     // e.g. best-so-far shown dimmer
    this.overlayPaths = [];        // [{path, color, width?, glow?}] extra paths (GA demos)
    this.nodeMarks = null;         // Map(id -> color) ring markers (GA demos)
    this.blockedNodes = null;      // Set: forbidden vertices (exact tab, red)
    this.candNodes = null;         // Set: extendable vertices (exact tab, green ring)
    this.hotBlockedNodes = null;   // Set: head's forbidden neighbors (intense red)
    this.headEdges = null;         // {cand: [[a,b]], blocked: [[a,b]]} edges from the head
    this.showPlanted = false;
    this.flashEdges = null;        // Set of edgeKeys pulsing (deposit anim)
    this.flashT = 0;               // 0..1 pulse phase
    this.evapFlash = 0;            // 0..1 evaporation blue flash

    this.hoverNode = -1;
    this.hoverEdge = null;

    this.dragNode = -1;
    this.panning = false;
    this.lastMouse = null;

    this._bindEvents();
  }

  setGraph(g) {
    this.graph = g;
    this.pheromone = null;
    this.ants = [];
    this.highlightPath = null;
    this.secondaryPath = null;
    this.overlayPaths = [];
    this.nodeMarks = null;
    this.blockedNodes = null;
    this.candNodes = null;
    this.hotBlockedNodes = null;
    this.headEdges = null;
    this.flashEdges = null;
    this.scale = 1; this.tx = 0; this.ty = 0;
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    const r = this.canvas.getBoundingClientRect();
    this.canvas.width = r.width * dpr;
    this.canvas.height = r.height * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.w = r.width;
    this.h = r.height;
  }

  toWorld(px, py) {
    return { x: (px - this.tx) / this.scale, y: (py - this.ty) / this.scale };
  }

  /** Zoom/pan so the whole graph fills the canvas (used by focus mode). */
  fitToView(pad = 70) {
    const g = this.graph;
    if (!g || !g.n) return;
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    for (const nd of g.nodes) {
      if (nd.x < minX) minX = nd.x;
      if (nd.x > maxX) maxX = nd.x;
      if (nd.y < minY) minY = nd.y;
      if (nd.y > maxY) maxY = nd.y;
    }
    const bw = Math.max(1, maxX - minX), bh = Math.max(1, maxY - minY);
    const s = Math.max(0.15, Math.min(6,
      Math.min((this.w - 2 * pad) / bw, (this.h - 2 * pad) / bh)));
    this.scale = s;
    this.tx = (this.w - s * (minX + maxX)) / 2;
    this.ty = (this.h - s * (minY + maxY)) / 2;
  }

  _bindEvents() {
    const c = this.canvas;
    c.addEventListener("mousedown", (e) => {
      const m = this._mouse(e);
      const v = this._nodeAt(m.x, m.y);
      if (v >= 0) {
        this.dragNode = v;
        this.graph.nodes[v].fixed = true;
      } else {
        this.panning = true;
      }
      this.lastMouse = m;
    });
    window.addEventListener("mousemove", (e) => {
      const m = this._mouse(e);
      if (this.dragNode >= 0 && this.graph) {
        const w = this.toWorld(m.x, m.y);
        const nd = this.graph.nodes[this.dragNode];
        nd.x = w.x; nd.y = w.y; nd.vx = 0; nd.vy = 0;
        if (window.layout) window.layout.alpha = Math.max(window.layout.alpha, 0.25);
      } else if (this.panning) {
        this.tx += m.x - this.lastMouse.x;
        this.ty += m.y - this.lastMouse.y;
      } else if (this.graph) {
        this.hoverNode = this._nodeAt(m.x, m.y);
        this.hoverEdge = this.hoverNode >= 0 ? null : this._edgeAt(m.x, m.y);
      }
      this.lastMouse = m;
      this.mousePos = m;
    });
    window.addEventListener("mouseup", () => {
      if (this.dragNode >= 0 && this.graph) this.graph.nodes[this.dragNode].fixed = false;
      this.dragNode = -1;
      this.panning = false;
    });
    c.addEventListener("wheel", (e) => {
      e.preventDefault();
      const m = this._mouse(e);
      const f = e.deltaY < 0 ? 1.12 : 1 / 1.12;
      const w = this.toWorld(m.x, m.y);
      this.scale = Math.max(0.15, Math.min(6, this.scale * f));
      this.tx = m.x - w.x * this.scale;
      this.ty = m.y - w.y * this.scale;
    }, { passive: false });
  }

  _mouse(e) {
    const r = this.canvas.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  _nodeAt(px, py) {
    if (!this.graph) return -1;
    const w = this.toWorld(px, py);
    const rr = (this._nodeRadius() + 4) / 1;
    let best = -1, bd = rr * rr;
    for (const nd of this.graph.nodes) {
      const dx = nd.x - w.x, dy = nd.y - w.y;
      const d2 = dx * dx + dy * dy;
      if (d2 < bd) { bd = d2; best = nd.id; }
    }
    return best;
  }

  _edgeAt(px, py) {
    if (!this.graph) return null;
    const w = this.toWorld(px, py);
    const tol = 6 / this.scale;
    for (const e of this.graph.edges) {
      const a = this.graph.nodes[e.a], b = this.graph.nodes[e.b];
      const d = pointSegDist(w.x, w.y, a.x, a.y, b.x, b.y);
      if (d < tol) return e;
    }
    return null;
  }

  _nodeRadius() {
    if (!this.graph) return 8;
    const n = this.graph.n;
    return n <= 40 ? 11 : n <= 80 ? 9 : n <= 150 ? 7 : 5;
  }

  /* ---------------- drawing ---------------- */

  draw() {
    const ctx = this.ctx;
    ctx.save();
    ctx.clearRect(0, 0, this.w, this.h);
    if (!this.graph) { ctx.restore(); return; }
    ctx.translate(this.tx, this.ty);
    ctx.scale(this.scale, this.scale);

    const g = this.graph;
    const R = this._nodeRadius();

    // ---- edges ----
    const pathEdgeSet = this._edgeSet(this.highlightPath);
    const secEdgeSet = this._edgeSet(this.secondaryPath);
    const plantedSet = this.showPlanted ? this._edgeSet(g.plantedPath) : null;

    for (const e of g.edges) {
      const a = g.nodes[e.a], b = g.nodes[e.b];
      const key = DemoGraph.edgeKey(e.a, e.b);

      let width = 1, color = "rgba(120,135,160,0.22)", glow = 0;

      if (this.pheromone) {
        const tau = this.pheromone.get(key) ?? this.tauMin;
        const t = Math.min(1, Math.max(0, (tau - this.tauMin) / (this.tauMax - this.tauMin)));
        const tt = Math.pow(t, 0.6); // perceptual boost for low values
        if (tt > 0.02) {
          width = 1 + tt * 6;
          color = pheroColor(tt, 0.25 + tt * 0.75);
          glow = tt;
        }
      }
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.lineWidth = width * this.lineScale;
      ctx.strokeStyle = color;
      if (glow > 0.35) {
        ctx.shadowColor = pheroColor(glow, 0.9);
        ctx.shadowBlur = 10 * glow;
      }
      ctx.stroke();
      ctx.shadowBlur = 0;

      // deposit pulse (green — pheromone being added)
      if (this.flashEdges && this.flashEdges.has(key)) {
        const p = Math.sin(this.flashT * Math.PI);
        ctx.beginPath();
        ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y);
        ctx.lineWidth = 2 + p * 7;
        ctx.strokeStyle = `rgba(80,255,150,${0.25 + 0.6 * p})`;
        ctx.shadowColor = "rgba(80,255,150,0.9)";
        ctx.shadowBlur = 16 * p;
        ctx.stroke();
        ctx.shadowBlur = 0;
      }
    }

    // evaporation flash: cool blue wash over all edges
    if (this.evapFlash > 0 && this.pheromone) {
      ctx.strokeStyle = `rgba(90,150,255,${0.28 * this.evapFlash})`;
      ctx.lineWidth = 2.5;
      ctx.beginPath();
      for (const e of g.edges) {
        const a = g.nodes[e.a], b = g.nodes[e.b];
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
      }
      ctx.stroke();
    }

    // planted path (gold dashed underlay)
    if (plantedSet) {
      ctx.setLineDash([7, 5]);
      for (const e of g.edges) {
        if (!plantedSet.has(DemoGraph.edgeKey(e.a, e.b))) continue;
        const a = g.nodes[e.a], b = g.nodes[e.b];
        ctx.beginPath();
        ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y);
        ctx.lineWidth = 3;
        ctx.strokeStyle = "rgba(251,191,36,0.85)";
        ctx.shadowColor = "rgba(251,191,36,0.7)";
        ctx.shadowBlur = 8;
        ctx.stroke();
      }
      ctx.setLineDash([]);
      ctx.shadowBlur = 0;
    }

    // secondary path (dim)
    if (this.secondaryPath && this.secondaryPath.length > 1) {
      this._strokePath(this.secondaryPath, "rgba(150,160,190,0.4)", 4, 0);
    }

    // overlay paths (operator demos: parent B, trimmed segments, ...)
    for (const op of this.overlayPaths) {
      if (op.path && op.path.length > 1) {
        this._strokePath(op.path, op.color, op.width || 4, op.glow ?? 7, op.color);
      }
    }

    // ant trails
    for (const ant of this.ants) {
      if (ant.path.length > 1) {
        this._strokePath(ant.path, hexToRgba(ant.color, ant.stuck ? 0.25 : 0.75), 3.5, ant.stuck ? 0 : 6, ant.color);
      }
    }

    // highlighted path on top
    if (this.highlightPath && this.highlightPath.length > 1) {
      this._strokePath(this.highlightPath, this.highlightColor, 5, 12, this.highlightColor);
    }

    // head edges (exact tab): red = forbidden neighbor, green = valid extension
    if (this.headEdges) {
      for (const [a, b] of this.headEdges.blocked) {
        const na = g.nodes[a], nb = g.nodes[b];
        ctx.beginPath();
        ctx.moveTo(na.x, na.y);
        ctx.lineTo(nb.x, nb.y);
        ctx.lineWidth = 3.8 * this.lineScale;
        ctx.strokeStyle = "rgba(255,82,82,0.92)";
        ctx.shadowColor = "rgba(255,82,82,0.9)";
        ctx.shadowBlur = 10;
        ctx.stroke();
      }
      for (const [a, b] of this.headEdges.cand) {
        const na = g.nodes[a], nb = g.nodes[b];
        ctx.beginPath();
        ctx.moveTo(na.x, na.y);
        ctx.lineTo(nb.x, nb.y);
        ctx.lineWidth = 4.2 * this.lineScale;
        ctx.strokeStyle = "rgba(61,255,158,0.95)";
        ctx.shadowColor = "rgba(61,255,158,0.9)";
        ctx.shadowBlur = 12;
        ctx.stroke();
      }
      ctx.shadowBlur = 0;
    }

    // pheromone value labels at edge midpoints (only edges above baseline)
    if (this.pheromone && this.showPheroLabels) {
      ctx.font = '9px "Segoe UI", sans-serif';
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      for (const e of g.edges) {
        const tau = this.pheromone.get(DemoGraph.edgeKey(e.a, e.b)) ?? this.tauMin;
        if (tau <= this.tauMin * 1.02) continue;
        const a = g.nodes[e.a], b = g.nodes[e.b];
        const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
        const label = tau >= 9.95 ? String(Math.round(tau)) : tau.toFixed(1);
        const w = ctx.measureText(label).width + 7;
        ctx.fillStyle = "rgba(8,14,20,0.82)";
        ctx.beginPath();
        ctx.roundRect(mx - w / 2, my - 7, w, 14, 5);
        ctx.fill();
        ctx.fillStyle = "#8dffb8";
        ctx.fillText(label, mx, my + 0.5);
      }
    }

    // ---- nodes ----
    const hlSet = new Set(this.highlightPath || []);
    const planted = this.showPlanted && g.plantedPath ? new Set(g.plantedPath) : null;
    ctx.font = `${Math.max(8, R - 1)}px "Segoe UI", sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    for (const nd of g.nodes) {
      const inHl = hlSet.has(nd.id);
      const inPlanted = planted && planted.has(nd.id);
      const isBlocked = this.blockedNodes && this.blockedNodes.has(nd.id);
      const isHot = this.hotBlockedNodes && this.hotBlockedNodes.has(nd.id);
      let fill = "#2a3550", stroke = "#47557a";
      if (isBlocked) { fill = "#5c1522"; stroke = "rgba(255,90,90,0.9)"; }
      if (isHot) { fill = "#8a1626"; stroke = "#ff5252"; }
      if (inPlanted) { fill = "#4a3a10"; stroke = "#fbbf24"; }
      if (inHl) { fill = "#0d3a4f"; stroke = this.highlightColor; }
      if (nd.id === this.hoverNode) { stroke = "#ffffff"; }

      ctx.beginPath();
      ctx.arc(nd.x, nd.y, R, 0, Math.PI * 2);
      ctx.fillStyle = fill;
      ctx.fill();
      ctx.lineWidth = inHl || inPlanted || isHot ? 2.5 : 1.4;
      ctx.strokeStyle = stroke;
      if (isHot) { ctx.shadowColor = "#ff5252"; ctx.shadowBlur = 10; }
      ctx.stroke();
      ctx.shadowBlur = 0;

      if (R >= 7) {
        ctx.fillStyle = inHl ? "#c9fff8"
          : isBlocked ? "rgba(255,160,160,0.6)"
          : "rgba(200,210,235,0.85)";
        ctx.fillText(String(nd.id), nd.x, nd.y + 0.5);
      }
    }

    // extendable candidates (exact tab): green glowing rings
    if (this.candNodes) {
      for (const id of this.candNodes) {
        const nd = g.nodes[id];
        ctx.beginPath();
        ctx.arc(nd.x, nd.y, R + 3.5, 0, Math.PI * 2);
        ctx.lineWidth = 2.4;
        ctx.strokeStyle = "rgba(61,255,158,0.95)";
        ctx.shadowColor = "rgba(61,255,158,0.9)";
        ctx.shadowBlur = 9;
        ctx.stroke();
        ctx.shadowBlur = 0;
      }
    }

    // node ring markers (operator demos: union / removed vertices)
    if (this.nodeMarks) {
      for (const [id, color] of this.nodeMarks) {
        const nd = g.nodes[id];
        ctx.beginPath();
        ctx.arc(nd.x, nd.y, R + 3.5, 0, Math.PI * 2);
        ctx.lineWidth = 2.6;
        ctx.strokeStyle = color;
        ctx.shadowColor = color;
        ctx.shadowBlur = 9;
        ctx.stroke();
        ctx.shadowBlur = 0;
      }
    }

    // path endpoints markers
    if (this.highlightPath && this.highlightPath.length > 1) {
      for (const endId of [this.highlightPath[0], this.highlightPath[this.highlightPath.length - 1]]) {
        const nd = g.nodes[endId];
        ctx.beginPath();
        ctx.arc(nd.x, nd.y, R + 3.5, 0, Math.PI * 2);
        ctx.lineWidth = 2;
        ctx.strokeStyle = this.highlightColor;
        ctx.stroke();
      }
    }

    // ---- ants (drawn as glowing dots) ----
    for (const ant of this.ants) {
      const p = ant.pos;
      if (!p) continue;
      ctx.beginPath();
      ctx.arc(p.x, p.y, R * 0.62, 0, Math.PI * 2);
      ctx.fillStyle = ant.stuck ? hexToRgba(ant.color, 0.35) : ant.color;
      if (!ant.stuck) {
        ctx.shadowColor = ant.color;
        ctx.shadowBlur = 14;
      }
      ctx.fill();
      ctx.shadowBlur = 0;
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = "rgba(10,14,25,0.9)";
      ctx.stroke();
    }

    ctx.restore();
    this._drawTooltip();
  }

  _strokePath(path, color, width, blur, glowColor) {
    const ctx = this.ctx, g = this.graph;
    ctx.beginPath();
    const p0 = g.nodes[path[0]];
    ctx.moveTo(p0.x, p0.y);
    for (let i = 1; i < path.length; i++) {
      const p = g.nodes[path[i]];
      ctx.lineTo(p.x, p.y);
    }
    ctx.lineWidth = width * this.lineScale;
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.strokeStyle = color;
    if (blur > 0) { ctx.shadowColor = glowColor || color; ctx.shadowBlur = blur; }
    ctx.stroke();
    ctx.shadowBlur = 0;
  }

  _edgeSet(path) {
    if (!path || path.length < 2) return null;
    const s = new Set();
    for (let i = 0; i < path.length - 1; i++)
      s.add(DemoGraph.edgeKey(path[i], path[i + 1]));
    return s;
  }

  _drawTooltip() {
    if (!this.mousePos || !this.graph) return;
    const ctx = this.ctx;
    let text = null;
    if (this.hoverNode >= 0) {
      text = `vertex ${this.hoverNode} · degree ${this.graph.degree(this.hoverNode)}`;
    } else if (this.hoverEdge && this.pheromone) {
      const key = DemoGraph.edgeKey(this.hoverEdge.a, this.hoverEdge.b);
      const tau = this.pheromone.get(key) ?? this.tauMin;
      text = `edge ${this.hoverEdge.a}–${this.hoverEdge.b} · pheromone ${tau.toFixed(2)}`;
    }
    if (!text) return;
    ctx.save();
    ctx.font = '12px "Segoe UI", sans-serif';
    const w = ctx.measureText(text).width + 16;
    let x = this.mousePos.x + 14, y = this.mousePos.y - 10;
    if (x + w > this.w) x = this.w - w - 4;
    ctx.fillStyle = "rgba(15,20,35,0.92)";
    ctx.strokeStyle = "rgba(100,120,160,0.5)";
    ctx.beginPath();
    ctx.roundRect(x, y - 11, w, 22, 6);
    ctx.fill(); ctx.stroke();
    ctx.fillStyle = "#dbe4ff";
    ctx.textBaseline = "middle";
    ctx.fillText(text, x + 8, y);
    ctx.restore();
  }
}

/* ---------------- color helpers ---------------- */

/** pheromone: green, intensity = amount (dark faint green -> bright mint) */
function pheroColor(t, alpha) {
  const r = Math.round(25 + 65 * t);
  const g = Math.round(150 + 105 * t);
  const b = Math.round(70 + 85 * t);
  return `rgba(${r},${g},${b},${alpha})`;
}

function hexToRgba(hex, a) {
  const v = parseInt(hex.slice(1), 16);
  return `rgba(${(v >> 16) & 255},${(v >> 8) & 255},${v & 255},${a})`;
}

function pointSegDist(px, py, ax, ay, bx, by) {
  const dx = bx - ax, dy = by - ay;
  const l2 = dx * dx + dy * dy;
  if (l2 === 0) return Math.hypot(px - ax, py - ay);
  let t = ((px - ax) * dx + (py - ay) * dy) / l2;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}
