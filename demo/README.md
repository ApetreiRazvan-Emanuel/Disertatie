# Longest Induced Path — Interactive Demo

Visual demo of the algorithms from the dissertation: **Ant Colony
Optimization** and the **Genetic Algorithm** (both intentionally simplified so
each step is easy to follow live) plus the **exact backtracking enumeration**
from the papers (faithful port).

## Run

Just open `index.html` in any browser — no server, no dependencies.

## What you can do

**Graph instance (left panel)**
- *Planted LIP* — simplified port of `LIPInstanceGenerator`: a hidden optimal
  path buried in noise cliques. "Reveal planted path" shows it in gold.
  The "Trap degree" slider replaces the old easy/medium/hard presets: it sets
  how many spread-out path vertices each noise vertex connects to. High =
  traps obviously poison the path (easy); low = subtle (hard); at 2 the
  planted path may no longer be the true optimum.
- *Random graph* — connected G(n, p) with a chosen average degree.
- *Hypercube Q_d* — snake-in-the-box territory.
- *Benchmark instances* — the whole `graph-instances/` set (except
  generated-hard-5000), embedded in `instances.js` so it works from `file://`.
  Regenerate that file with `build_instances.py` if the instance set changes.
  **usair renders on its real USA-map layout** (geographic coordinates from
  the SuiteSparse USAir97 dataset, `USAir97_coord.mtx`; the graphml's
  relabeling is undone via VF2++ isomorphism at build time, needs networkx).
  Fixed-layout instances freeze the force simulation; drag/zoom still work.
- *Paste your own* — any DIMACS `.txt` or GraphML text.

**🐜 Ant Colony tab** — watch each iteration in four phases: ants build
induced paths guided by pheromone → the longest ant wins → evaporation (blue
flash, edges fade) → deposit (green pulse on the best paths).

Color semantics: **green** = pheromone (brightness/thickness = amount; hover
an edge for the exact value, or tick "Show pheromone values on edges" to keep
them visible), **pink** = best path found, **cyan** = current path, **gold
dashed** = planted optimum. Pause freezes instantly, even mid-animation, and
Run resumes exactly where it stopped; ⏭ Step advances one small step at a
time — spawn, each move round, evaluate, evaporate, deposit (pressing Step
while frozen mid-step first finishes that step). Defaults are tuned for
presenting: 2 ants, α=1.5, β=0.5, speed 0.3×.

**🧬 Genetic tab** — the population is listed by fitness with the origin of
each individual (elite / selected / crossover / mutation / fresh). Click any
individual to see its path on the graph; the chart tracks best and average
fitness per generation (gold dashed line = planted optimum).

Operator demos: with an individual selected, **Mutate selected** animates the
trim-and-extend mutation (cut ends flash red, then the walk regrows), and
**Crossover selected** picks a partner, overlays it in purple, rings the
vertex union in white (dropped shared vertices in red), then rebuilds a child
by DFS inside the union and extends it. The child joins the population. After
each generation a selection report shows how many individuals were eliminated
and which were selected multiple times (×N badges in the list).

**🔍 Exact tab** — the paper algorithms, ported faithfully from
`ExactEnumOptimized.java` and `HLIPP10000.java`: backtracking enumeration of
all maximal induced paths from every start vertex. The current path is cyan,
every vertex adjacent to a non-head path vertex is **red** (permanently
forbidden — this is why the problem is hard), and green rings mark the
vertices that can extend the path next. Variant select: *Exact enumeration*
(complete — announces a **proven optimum** when it finishes) or *H-LIP*
(abandons a start vertex after N maximal paths without improvement; budget
slider 100–10000, applied live). Max speed engages turbo mode (batches of
steps per frame) since backtracking explodes combinatorially — full
enumeration only realistically finishes on small graphs (e.g. hypercube Q3).

**Canvas** — drag vertices, drag the background to pan, scroll to zoom.

## Simplifications vs. the Java implementations

- ACO: one shared undirected pheromone per edge, a handful of sequential
  ants, no restarts/sprints/deposit-tolerance schedule
  (see `LongestInducedPathACO.java` for the real thing).
- GA: tiny population, no threads, no stagnation restarts; operators keep the
  same ideas as `LongestInducedPathGenetic.java` (elitism + tournament,
  union-rebuild crossover, trim-and-extend mutation, fresh injections).
