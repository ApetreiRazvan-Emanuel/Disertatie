/* ============================================================
 * graph.js — graph model, instance generators, parsers, layout
 * ============================================================ */
"use strict";

/** Simple undirected graph with adjacency sets and layout coordinates. */
class DemoGraph {
  constructor(n) {
    this.n = n;
    this.adj = Array.from({ length: n }, () => new Set());
    this.edges = [];               // [{a, b}] with a < b
    this.nodes = Array.from({ length: n }, (_, i) => ({
      id: i, x: 0, y: 0, vx: 0, vy: 0, fixed: false,
    }));
    this.plantedPath = null;       // int[] or null
  }

  addEdge(a, b) {
    if (a === b || this.adj[a].has(b)) return;
    this.adj[a].add(b);
    this.adj[b].add(a);
    this.edges.push(a < b ? { a, b } : { a: b, b: a });
  }

  hasEdge(a, b) { return this.adj[a].has(b); }
  degree(v) { return this.adj[v].size; }
  neighbors(v) { return [...this.adj[v]]; }

  static edgeKey(a, b) { return a < b ? a * 100000 + b : b * 100000 + a; }

  /** Verify path is a valid induced path. */
  isInducedPath(path) {
    for (let i = 0; i < path.length - 1; i++)
      if (!this.hasEdge(path[i], path[i + 1])) return false;
    for (let i = 0; i < path.length; i++)
      for (let j = i + 2; j < path.length; j++)
        if (this.hasEdge(path[i], path[j])) return false;
    return true;
  }
}

/* ------------------------------------------------------------
 * Random helpers
 * ------------------------------------------------------------ */
function randInt(n) { return Math.floor(Math.random() * n); }
function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = randInt(i + 1);
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}
function choice(arr) { return arr[randInt(arr.length)]; }

/* ------------------------------------------------------------
 * Induced-path walk primitives (shared by ACO ants and GA)
 * ------------------------------------------------------------ */

/**
 * Candidates to extend an induced path at `head`: neighbors of head that are
 * unvisited and adjacent to NO path vertex other than head itself.
 */
function inducedCandidates(g, path, inPath, head) {
  const out = [];
  for (const u of g.adj[head]) {
    if (inPath[u]) continue;
    let ok = true;
    for (const w of g.adj[u]) {
      if (inPath[w] && w !== head) { ok = false; break; }
    }
    if (ok) out.push(u);
  }
  return out;
}

/** Random induced walk from `start`, extending until stuck. Returns path array. */
function randomInducedWalk(g, start, pick) {
  const path = [start];
  const inPath = new Array(g.n).fill(false);
  inPath[start] = true;
  for (;;) {
    const cands = inducedCandidates(g, path, inPath, path[path.length - 1]);
    if (cands.length === 0) break;
    const u = pick ? pick(path[path.length - 1], cands) : choice(cands);
    path.push(u);
    inPath[u] = true;
  }
  return path;
}

/** Extend an existing induced path from both ends with random valid choices. */
function extendBothEnds(g, seed) {
  let path = [...seed];
  const inPath = new Array(g.n).fill(false);
  for (const v of path) inPath[v] = true;
  // forward
  for (;;) {
    const cands = inducedCandidates(g, path, inPath, path[path.length - 1]);
    if (cands.length === 0) break;
    const u = choice(cands);
    path.push(u); inPath[u] = true;
  }
  // backward (reverse, extend, reverse back)
  path.reverse();
  for (;;) {
    const cands = inducedCandidates(g, path, inPath, path[path.length - 1]);
    if (cands.length === 0) break;
    const u = choice(cands);
    path.push(u); inPath[u] = true;
  }
  path.reverse();
  return path;
}

/* ------------------------------------------------------------
 * Instance generators
 * ------------------------------------------------------------ */

/**
 * Simplified port of LIPInstanceGenerator: plant a path 0..L-1, add noise
 * vertices that each connect to `noiseDeg` spread-out path vertices, group
 * noise into small 3-cliques, sprinkle inter-clique edges.
 *
 * noiseDeg is the difficulty control: high (7+) makes traps obvious (picking
 * a noise vertex visibly poisons the path); low (3-4) makes them subtle; at
 * 2 the planted path may no longer be the true optimum.
 */
function generatePlanted(totalVertices, pathLength, noiseDeg) {
  const g = new DemoGraph(totalVertices);
  const L = pathLength;
  const planted = Array.from({ length: L }, (_, i) => i);
  for (let i = 0; i < L - 1; i++) g.addEdge(i, i + 1);

  const k = Math.max(2, Math.min(noiseDeg, L));
  const noise = [];
  for (let v = L; v < totalVertices; v++) noise.push(v);

  // each noise vertex gets exactly ~k connections, spread across path zones
  for (const nv of noise) {
    const zones = shuffle(Array.from({ length: k }, (_, z) => z));
    const zoneSize = L / k;
    let added = 0;
    for (const z of zones) {
      const zs = Math.floor(z * zoneSize);
      const ze = Math.max(zs + 1, Math.floor((z + 1) * zoneSize));
      const p = zs + randInt(ze - zs);
      if (!g.hasEdge(nv, p)) { g.addEdge(nv, p); added++; }
    }
    let attempts = 0;
    while (added < k && attempts++ < k * 4) {
      const p = randInt(L);
      if (!g.hasEdge(nv, p)) { g.addEdge(nv, p); added++; }
    }
  }

  // noise cliques (3-cliques cap noise-only induced paths at 2 vertices)
  const CLIQUE = 3, INTER_PROB = 0.10;
  const cliques = [];
  const rem = shuffle([...noise]);
  while (rem.length >= CLIQUE) {
    const cl = rem.splice(rem.length - CLIQUE, CLIQUE);
    cliques.push(cl);
    for (let i = 0; i < cl.length; i++)
      for (let j = i + 1; j < cl.length; j++) g.addEdge(cl[i], cl[j]);
  }
  for (const v of rem) {
    if (cliques.length) for (const c of choice(cliques)) g.addEdge(v, c);
  }

  // inter-clique edges
  for (let i = 0; i < cliques.length; i++)
    for (let j = i + 1; j < cliques.length; j++)
      if (Math.random() < INTER_PROB)
        g.addEdge(choice(cliques[i]), choice(cliques[j]));

  g.plantedPath = planted;
  if (!g.isInducedPath(planted)) console.warn("planted path broken?!");
  return g;
}

/** Random connected G(n, p) chosen from a target average degree. */
function generateRandom(n, avgDeg) {
  const g = new DemoGraph(n);
  const p = Math.min(1, avgDeg / Math.max(1, n - 1));
  for (let i = 0; i < n; i++)
    for (let j = i + 1; j < n; j++)
      if (Math.random() < p) g.addEdge(i, j);

  // connect components so algorithms can roam the whole graph
  const comp = new Array(n).fill(-1);
  let nc = 0;
  for (let s = 0; s < n; s++) {
    if (comp[s] !== -1) continue;
    const stack = [s]; comp[s] = nc;
    while (stack.length) {
      const v = stack.pop();
      for (const u of g.adj[v]) if (comp[u] === -1) { comp[u] = nc; stack.push(u); }
    }
    nc++;
  }
  if (nc > 1) {
    const reps = [];
    for (let c = 0; c < nc; c++) reps.push(comp.indexOf(c));
    for (let c = 1; c < nc; c++) g.addEdge(reps[c - 1], reps[c]);
  }
  return g;
}

/** Hypercube Q_d — pretty, symmetric, known LIP structure (snake-in-the-box). */
function generateCube(d) {
  const n = 1 << d;
  const g = new DemoGraph(n);
  for (let v = 0; v < n; v++)
    for (let b = 0; b < d; b++) {
      const u = v ^ (1 << b);
      if (u > v) g.addEdge(v, u);
    }
  return g;
}

/* ------------------------------------------------------------
 * Parsers: DIMACS ("e u v", 1-based) and GraphML
 * ------------------------------------------------------------ */
function parseInstance(text) {
  if (text.includes("<graphml") || text.includes("<node")) return parseGraphML(text);
  return parseDimacs(text);
}

function parseDimacs(text) {
  const edges = [];
  let maxV = 0;
  for (const line of text.split(/\r?\n/)) {
    const t = line.trim();
    if (t.startsWith("e ")) {
      const [, a, b] = t.split(/\s+/);
      const u = parseInt(a, 10) - 1, v = parseInt(b, 10) - 1;
      if (Number.isNaN(u) || Number.isNaN(v)) continue;
      edges.push([u, v]);
      maxV = Math.max(maxV, u, v);
    } else if (t.startsWith("p ")) {
      const parts = t.split(/\s+/);
      const n = parseInt(parts[2], 10);
      if (!Number.isNaN(n)) maxV = Math.max(maxV, n - 1);
    }
  }
  if (!edges.length) throw new Error("No 'e u v' lines found (DIMACS format)");
  const g = new DemoGraph(maxV + 1);
  for (const [u, v] of edges) g.addEdge(u, v);
  return g;
}

function parseGraphML(text) {
  const doc = new DOMParser().parseFromString(text, "text/xml");
  const nodeEls = [...doc.getElementsByTagName("node")];
  const edgeEls = [...doc.getElementsByTagName("edge")];
  if (!nodeEls.length) throw new Error("No <node> elements found (GraphML)");
  const idMap = new Map();
  nodeEls.forEach((el, i) => idMap.set(el.getAttribute("id"), i));
  const g = new DemoGraph(nodeEls.length);
  for (const el of edgeEls) {
    const s = idMap.get(el.getAttribute("source"));
    const t = idMap.get(el.getAttribute("target"));
    if (s !== undefined && t !== undefined) g.addEdge(s, t);
  }
  return g;
}

/* ------------------------------------------------------------
 * Force-directed layout (async ticks, cheap O(n^2) fine for demo sizes)
 * ------------------------------------------------------------ */
class ForceLayout {
  constructor(graph, width, height, spacing = 1) {
    this.g = graph;
    this.width = width;
    this.height = height;
    this.spacing = spacing;   // multiplier on the ideal edge length
    this.alpha = 1.0;
    this.frozen = false;      // fixed positions (e.g. geographic) — never tick
    this.running = false;
    this.seed();
  }

  seed() {
    const g = this.g;
    const cx = this.width / 2, cy = this.height / 2;
    const R = Math.min(this.width, this.height) * 0.38;
    // Planted path seeded on a serpentine arc so it untangles nicely
    if (g.plantedPath) {
      const L = g.plantedPath.length;
      g.plantedPath.forEach((v, i) => {
        const t = i / Math.max(1, L - 1);
        const ang = -Math.PI * 0.9 + t * Math.PI * 1.8;
        g.nodes[v].x = cx + Math.cos(ang) * R * 0.8;
        g.nodes[v].y = cy + Math.sin(ang) * R * 0.8;
      });
      for (const nd of g.nodes) {
        if (g.plantedPath.includes(nd.id)) continue;
        const a = Math.random() * Math.PI * 2, r = Math.random() * R * 0.5;
        nd.x = cx + Math.cos(a) * r;
        nd.y = cy + Math.sin(a) * r;
      }
    } else {
      for (const nd of g.nodes) {
        const a = Math.random() * Math.PI * 2;
        const r = R * (0.2 + 0.8 * Math.sqrt(Math.random()));
        nd.x = cx + Math.cos(a) * r;
        nd.y = cy + Math.sin(a) * r;
      }
    }
  }

  tick() {
    const g = this.g;
    const n = g.n;
    if (n === 0 || this.frozen) return;
    const area = this.width * this.height;
    const k = Math.sqrt(area / n) * 0.7 * this.spacing; // ideal edge length
    const a = this.alpha;

    // repulsion
    for (let i = 0; i < n; i++) {
      const ni = g.nodes[i];
      for (let j = i + 1; j < n; j++) {
        const nj = g.nodes[j];
        let dx = ni.x - nj.x, dy = ni.y - nj.y;
        let d2 = dx * dx + dy * dy;
        if (d2 < 0.01) { dx = Math.random() - 0.5; dy = Math.random() - 0.5; d2 = 0.25; }
        const d = Math.sqrt(d2);
        const f = (k * k) / d2 * a * 2.2;
        const fx = dx / d * f * k * 0.08, fy = dy / d * f * k * 0.08;
        ni.vx += fx; ni.vy += fy;
        nj.vx -= fx; nj.vy -= fy;
      }
    }
    // springs
    for (const { a: u, b: v } of g.edges) {
      const nu = g.nodes[u], nv = g.nodes[v];
      const dx = nv.x - nu.x, dy = nv.y - nu.y;
      const d = Math.max(0.1, Math.hypot(dx, dy));
      const f = (d - k) / d * 0.06 * a * 4;
      nu.vx += dx * f; nu.vy += dy * f;
      nv.vx -= dx * f; nv.vy -= dy * f;
    }
    // gravity toward center + integrate
    const cx = this.width / 2, cy = this.height / 2;
    for (const nd of g.nodes) {
      nd.vx += (cx - nd.x) * 0.005 * a;
      nd.vy += (cy - nd.y) * 0.005 * a;
      if (!nd.fixed) {
        nd.x += Math.max(-15, Math.min(15, nd.vx));
        nd.y += Math.max(-15, Math.min(15, nd.vy));
      }
      nd.vx *= 0.55; nd.vy *= 0.55;
    }
    this.alpha = Math.max(0.02, this.alpha * 0.995);
  }
}
