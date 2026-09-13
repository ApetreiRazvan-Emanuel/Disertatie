# Handoff: dissertation presentation (Romanian)

Context document for the Claude Code session that will write the defense
presentation (PowerPoint / Google Slides) and any supporting doc, **in
Romanian**. Written by the session that built the interactive demo (2026-07-08).

## The task

Presentation structure requested by Răzvan:
1. **Introducere** — problem statement (Longest Induced Path), motivation and
   applications (worth researching something genuinely interesting here).
2. **Stadiul actual (state of the art)** — branch & bound / ILP formulations
   explained high-level and understandably; how they work.
3. **Algoritmul exact** — backtracking enumeration (ExactEnum / H-LIP).
4. **ACO** — the ant colony approach.
5. **Algoritmul genetic** — the GA.
6. **Rezultate** — benchmark tables.
7. **Concluzii.**

## Authoritative sources (read these first)

- `Disertatie.pdf` (repo root) — the dissertation itself. **The single source
  of truth for claims, definitions, notation and related work.** Read it
  before writing anything; the presentation must match it.
- `Disertatie/src/main/output/benchmark_final_table.txt` — final benchmark
  results (3 formatted tables: deterministic, stochastic, best-per-instance).
  This is the results data for the slides.
- `demo/README.md` + `demo/index.html` — interactive browser demo (see below).

⚠️ Older experiment numbers floating around memory/notes (ILP+GA hybrid runs,
MemeticGA yeast=333, ACOv4/v6 and GAv2 variants) are from intermediate
campaigns before the codebase was consolidated. **Do not use them unless the
PDF itself mentions them.** Use `benchmark_final_table.txt` + the PDF.

## The problem (1-slide version)

Longest Induced Path (LIP): given an undirected graph G, find the longest
path P such that the subgraph *induced* by P's vertices contains **only** the
path's edges — no chords, no edges between non-consecutive path vertices
(Romanian: *drum indus de lungime maximă*; a path with no "scurtături").
NP-hard. The induced condition is what makes it brutal: picking a vertex
permanently forbids all its other neighbors.

Motivation angles (verify against the PDF, then pick the coolest):
- **Snake-in-the-box** (hypercubes): longest induced paths in Q_d are exactly
  the "snakes" used in error-correcting Gray codes, coding theory — a famous
  open problem; cube-7/8/9 are benchmark instances here.
- Network analysis: the benchmark set is real networks (social: karate,
  dolphins; infrastructure: 494bus/662bus/ieeebus power grids, usair flight
  network; biology: yeast protein interactions).
- Theoretical interest: harder than Longest Path (which is already NP-hard);
  related to graph searching / treewidth literature.

## The six algorithms in the final benchmark

All under `Disertatie/src/main/java/core/algorithms/inducedpath/`:

| Name in tables | File | What it is |
|---|---|---|
| CEC | `ilp/CEC.java` | ILP formulation, cycle-elimination-style constraints (solver-based, proves optimality) |
| CUT | `ilp/CUT.java` | ILP formulation, cut-based constraints (also exact/proving) |
| Exact | `paperimplementation/ExactEnumOptimized.java` | Backtracking enumeration of all maximal induced paths from every start vertex (paper algorithm) |
| HLIPP | `paperimplementation/HLIPP10000.java` | Same search but abandons a start vertex after 10000 maximal paths without improvement (paper heuristic) |
| GA | `genetic/LongestInducedPathGenetic.java` | Own genetic algorithm: elitism + tournament, union-rebuild crossover, trim-and-extend mutation, fresh injections, stagnation restarts, parallel |
| ACO | `aco/LongestInducedPathACO.java` | Port of Prof. Frăsinaru's ACO: parallel pheromone-guided DFS, adaptive evaporation, sprint restarts, deposit tolerance |

For the ILP slide keep it high-level: binary variables select vertices/edges,
degree constraints force a path shape, and connectivity/cycle-elimination
constraints (added lazily as cuts) forbid disconnected pieces — read the PDF
chapter and the two Java files for the exact formulation before writing it.

## Headline results (from benchmark_final_table.txt, 15-min timeout)

- **Small instances (V ≤ ~140):** everything finds the optimum. ACO is
  fastest (<1s typically; GA 10–60s; exact backtracking fine until ~120
  vertices, then explodes — e.g. sanjuansur V=75 takes 110s).
- **usair (V=332):** optimum 46 proven by CEC/CUT (~2 min). ACO hits 46 in
  ~16s. Exact times out; HLIPP degrades to 38.
- **494bus (V=494):** optimum 142 proven (CEC 17s). ACO 142 in 2.5s (!).
  HLIPP only 109.
- **662bus (V=662):** optimum 305 proven (CEC 127s). ACO max 302, GA max 283.
- **yeast (V=2361):** ILPs partial (142/184), exact/HLIPP timeout. **ACO
  dominates: 384–395.** GA 279–328.
- **generated-hard-5000 (V=5000, dense):** ILPs get 51. **GA wins: 375–395**,
  ACO 334–339.
- Story arc: exact methods prove optimality but die beyond ~600 vertices;
  ACO excels on large sparse real-world graphs; GA takes over on huge dense
  instances. ACO is 10–200× faster than GA on medium instances.

## The interactive demo (`demo/` — screenshots & live demo material)

Open `demo/index.html` in any browser (no server, no deps). Built for the
defense; algorithms deliberately simplified for explainability (say so if
asked — `demo/README.md` documents exactly what was cut).

- **Instance panel:** planted-path generator (hidden optimum shown in gold,
  revealable), random graphs, hypercubes, all 27 benchmark instances embedded,
  paste-import DIMACS/GraphML.
- **🐜 ACO tab:** watch ants build induced paths step by step; 4 phases per
  iteration (explore → evaluate → evaporate → deposit). Green edges =
  pheromone (intensity = amount, optional numeric labels). Pink = best found,
  cyan = current, gold dashed = planted optimum. Defaults tuned for
  presenting: 2 ants, α=1.5, β=0.5, speed 0.25×.
- **🧬 Genetic tab:** live population list with origin chips
  (elite/selected/crossover/mutation/fresh) and ×N duplication badges,
  per-generation selection report (eliminated/duplicated), best/avg fitness
  chart, and **animated operator demos**: "Mutate selected" (trim ends red,
  re-extend) and "Crossover selected" (parent B purple, vertex union green
  rings, dropped shared red, child rebuilt live).
- **🔍 Exact tab:** faithful port of ExactEnumOptimized + HLIPP (variant
  select + live budget slider). The teaching visual: cyan path grows; green
  edges/rings = valid extensions, red vertices/edges = forbidden (the head's
  blocked neighbors burn brighter); banner narrates "4 valid in green, 1
  forbidden in red — pick a green one". On hypercube Q3 the exact variant
  finishes and announces a proven optimum (5). Turbo mode at max speed.
- All tabs: Run/Pause (instant freeze, resumes in place)/Step
  (phase-by-phase)/Reset, speed 0.1×–10×.

Slide ideas from the demo: screenshot the pheromone-covered graph, the
crossover union moment, and the exact tab's red/green decision step — these
three images explain the algorithms better than pseudocode. Suggest a live
demo moment: run ACO on the planted instance, then toggle "Reveal planted
path" to show how close it got. Playwright + system Chrome works headless for
capturing screenshots (`pip install playwright`, `channel="chrome"`; no
Node.js on this machine).

## Romanian terminology used so far / suggestions

- drum indus / cel mai lung drum indus (LIP)
- graf neorientat, subgraf indus, coardă (chord)
- formulare de programare liniară întreagă (ILP), restricții/tăieturi (cuts)
- enumerare cu backtracking, optim demonstrat
- algoritm genetic: populație, selecție prin turneu, elitism, încrucișare
  (crossover), mutație, fitness
- ACO: furnici, feromon, evaporare, depunere, influența feromonului (α),
  euristica gradului (β)
- instanțe de test / benchmark, limită de timp, metaeuristici
- Check the PDF for the exact Romanian phrasing already used — stay consistent
  with it.

## Timing plan (agreed with Răzvan)

20 minutes total including questions → ~16-17 minutes of speaking. Rough
budget: introducere 2, ILP/stadiul actual 2, exact 2, ACO 2.5, GA 2.5,
rezultate 2, concluzii 1, plus a **2-minute live demo slot placed right
BEFORE the conclusions** (never after — close on the contributions slide).
The demo slide must be droppable by design: if he's past ~minute 14 when he
reaches it, he skips it and the demo lives in Q&A instead. The rehearsed live
scenario: planted instance (40 vertices / path 20) already generated → run
ACO at ~1× a few iterations narrating pheromone build-up → pause → toggle
"Reveal planted path" (gold overlay vs found path is the payoff moment).

## Media (screenshots & videos)

- Visual strategy: screenshots go INSIDE the algorithm slides as figures
  (exact tab's red/green decision step; pheromone-covered graph; crossover
  union). Only ACO gets a video on its slide — motion carries that
  explanation; GA and exact work as stills.
- Pre-recorded clips are in `presentation-assets/` (repo root), each as
  .mp4 (H.264, for PowerPoint) and .webm (for Drive/Google Slides), 1080p:
  - `aco-slide-clip.mp4/.webm` (~30s) — 2 ants exploring at 1.3×, pheromone
    building over ~3 iterations; for the ACO slide.
  - `demo-backup.mp4/.webm` (~87s) — the full rehearsed live-demo scenario:
    generate planted instance → ACO runs ~10 iterations at 1× → pause →
    "Reveal planted path" gold overlay (from ~78s). Fallback if the live
    demo can't run on defense day.
  - `aco-focus-slowmo.mp4/.webm` (~64s) - FOCUS MODE (graph only, full
    frame, no UI): 3 ants at 0.15x slow motion, iteration 1 plus the start
    of iteration 2 where ants visibly follow the pheromone. For the ACO
    slide.
  - `exact-focus.mp4/.webm` (~96s) - FOCUS MODE with narration banner:
    backtracking at 0.3x, red forbidden vertices, green candidates, dead
    ends and backtracks. For the backtracking slide.
  - `exact-slide-photo.png` (3200x2400) - high-DPI STILL for the
    backtracking slide: sparse 22-vertex instance, thick strokes, cyan
    path with head vertex firing six red edges at forbidden neighbors and
    one green edge to the only valid extension. Candidates were auto-scored
    to avoid near-collinear consecutive path edges. Replaces the older,
    busier exact_redgreen.png.
  - `aco-slide-photo.png` (3200x2400) - high-DPI STILL for the ACO slide,
    graph only (no UI): 3 ants frozen mid-walk with colored trails on top
    of green pheromone highways of visibly different intensities. Replaces
    aco_pheromone.png (which was a full-UI screenshot) if desired.
  - The demo has a Focus button (or press F) that hides all panels and fits
    the graph to the screen; that is how these were recorded.
  - Regenerate screenshots or new clips headless with Playwright + system
    Chrome; ffmpeg (winget, Gyan.FFmpeg) is installed for mp4 conversion.
    Gotcha: page.evaluate("aco.play()") deadlocks (evaluate awaits the
    returned play-loop promise) - use page.evaluate("void aco.play()").
- Google Slides needs videos uploaded to Drive (or YouTube) first;
  PowerPoint embeds mp4 directly.

## Presentation format tip

Python 3.14 is available; `python-pptx` can generate a real .pptx that
imports cleanly into Google Slides. Alternative: Marp/reveal.js HTML. Ask the
user which they prefer before generating. LaTeX chapter sources exist at repo
root (`chapter3.tex`, `rewritten_section.tex`) — check if they match the PDF.

One style note from the user (saved in memory): in LaTeX/text, write line
ranges as "X to Y", never "X--Y".
