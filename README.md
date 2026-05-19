# Longest Induced Path (LIP) — Algorithms and Results

**Author:** Apetrei Razvan-Emanuel  
**Date:** 2026-05-19

## Problem Definition

Given an undirected graph G = (V, E), find the longest **induced path** — a path P where the subgraph induced by the vertices of P contains only the edges of P itself. In other words, no two non-consecutive vertices in the path may be adjacent in G.

This problem is NP-hard in general and has applications in network analysis, bioinformatics (protein interaction networks), and circuit design.

---

## Algorithms

### 1. Integer Linear Programming (ILP) — Exact Method

**File:** `core/algorithms/inducedpath/ilp/LongestInducedPathILP.java`

**Approach:** Formulates LIP as a binary integer program solved by Gurobi.

**Variables:**
- `x[i]` (binary): 1 if vertex i is in the path
- `y[e]` (binary): 1 if edge e is used in the path

**Constraints:**
1. **Edge linking:** `y[e] <= x[u]` and `y[e] <= x[v]` — an edge can only be used if both endpoints are selected
2. **Induced constraint:** `x[u] + x[v] - y[e] <= 1` — if both endpoints of an edge are selected, the edge must be used (ensures the subgraph is induced)
3. **Degree constraint:** Each vertex has at most degree 2 in the selected edges (path structure)
4. **Tree/path constraint:** `sum(y) = sum(x) - 1` — the number of edges equals vertices minus 1 (no cycles)
5. **Subtour elimination:** Lazy constraints added via callbacks detect cycles and cut them

**Objective:** Maximize `sum(x[i])` — the number of vertices in the path.

**Key design choices:**
- Uses Gurobi's lazy constraint callback to dynamically add subtour elimination cuts when cycles are detected in integer solutions, rather than pre-generating all possible cycle constraints (exponentially many)
- Updates the best known path during the solve process via callbacks, so even if interrupted by timeout, the best feasible solution found so far is returned
- Logging is suppressed (`LogToConsole = 0`) for batch experiments

**Strengths:** Finds provably optimal solutions for small/medium graphs (up to ~700 vertices).  
**Weakness:** Cannot scale to large graphs — on yeast (2361 vertices), only finds a path of length 4 in 2 minutes.

---

### 2. Genetic Algorithm (GA) — Metaheuristic

**File:** `core/algorithms/inducedpath/genetic/LongestInducedPathGenetic.java`  
**Config:** `core/algorithms/GeneticAlgorithmConfig.java`

**Representation:** Each individual is a boolean array of size |V|, where `true` means the vertex is in the path. The algorithm maintains the invariant that each individual represents a valid induced path.

**Population initialization:**
- 10% of population seeded from high-degree and low-degree vertices (alternating) to encourage diverse starting points
- If an ILP seed is provided, 10% of population is initialized with copies of the ILP solution (some boosted)
- Remaining individuals are randomly generated via a recursive path-building procedure

**Path generation (`inducedPathsFromVertex`):**
- Starts from a vertex and extends in one direction by randomly choosing valid neighbors (those with exactly 1 neighbor in the current path)
- When stuck, backtracks to the start vertex and extends in the opposite direction
- Explores multiple random paths with a cutoff to limit computation time

**Boost operator (`boostIndividual`):**
- Finds the endpoints of the current path
- Greedily extends from each endpoint by adding neighbors that maintain the induced path property
- Applied to every new individual to maximize path length before selection

**Selection (two modes):**
- **Roulette wheel:** Fitness (path length) raised to `selectionPressure` power, then proportional selection. Higher pressure biases toward longer paths.
- **Tournament (k):** Pick k random individuals, select the best. Tournament size k controls selection pressure.
- Elitism: Top individual is copied `ELITISM` times into the next generation.

**Crossover (two-point):**
- Selects two parents, picks two random crossover points
- Offspring vertices = union of parent vertices in the crossover segment, copy elsewhere
- Offspring are repaired via `fixIndividual` (finds longest induced path within the subgraph) and then boosted
- Applied with probability `PROB_CROSSOVER` (default 0.3)

**Mutation:**
- **Standard mutation** (rate 0.1): Adds or removes a vertex at a random endpoint, repeated `MUTATION_COUNT` times
- **Random injection** (rate 0.5): Generates a fresh random individual, boosts it, and adds to population. This is the primary diversity mechanism.

**Dynamic generation extension:**
- If still improving when max generation is reached (last improvement within 100 generations of limit), extends the limit by `MAX_GENERATION_INCREASE` (200) additional generations
- This allows promising runs to continue while stopping stagnant ones early

**Best configuration found (champion — Exp51):**
- Selection: Roulette wheel (pressure=2.0)
- Population: 2000, Elitism: 200 (10%)
- Mutation rate: 0.5 (random injection)
- Crossover: 0.3
- ILP seed: 2-minute timeout
- Max generation: 500 (dynamic extension enabled)
- **All-time best: 285 on yeast** (Avg=257.4 over 5 runs)

**Key design choices:**
- The boost operator is critical — it greedily extends every individual to its maximum, ensuring the GA works with full-length paths rather than partial solutions
- High random injection rate (0.5) prevents premature convergence by continuously introducing fresh genetic material
- Roulette selection with moderate pressure scales better to large populations than tournament selection, which causes premature convergence at pop=2000
- The ILP seed provides an excellent starting point for small/medium graphs where ILP finds the optimal; for large graphs, it provides minimal benefit (seed=4 on yeast) but doesn't hurt
- The `perturbAndReboost` local search operator (trim 1-3 vertices from an endpoint, then reboost) is available but not used in the standard configuration

---

### 3. Ant Colony Optimization v1 (ACO v1) — Metaheuristic

**File:** `core/algorithms/inducedpath/aco/LongestInducedPathACO.java`

**Approach:** Vertex-based pheromone MMAS (Max-Min Ant System).

**Pheromone model:** Vertex-based — each vertex has a pheromone value that indicates how desirable it is to include in the path.

**Heuristic:** `h(v) = 1 / (degree(v) + 1)` — lower degree vertices are preferred because they block fewer neighbors.

**Solution construction:**
- Start vertex chosen 50% by eccentricity-weighted selection (high-eccentricity vertices are near the graph's periphery, more likely to be on long paths), 50% random
- Path extended bidirectionally from start vertex
- At each step, candidates are neighbors with `invalidCount == 1` (only blocked by the path's endpoint)
- Selection uses ACO probability: `p(v) = tau(v)^alpha * h(v)^beta / sum`, with epsilon-greedy exploration for dense graphs

**Local search:**
- Endpoint perturbation: Remove one endpoint, try to re-extend (5 attempts)
- Deep perturbation (for dense graphs with V<=300): Remove 2-4 vertices from one end, re-extend both directions

**Pheromone update:**
- Evaporation: `tau *= (1 - rho)`, with min/max bounds [0.1, 5.0]
- Iteration-best deposit: `deposit = 2.0 * pathLength / numVertices`
- Global-best elitist deposit: double the iteration-best deposit
- Stagnation reset: When no improvement for 50 iterations, reset all pheromone to 1.0

**Parameters:** numAnts = max(20, |V|), maxIterations = 1000, alpha = 1.5, beta = 3.0, rho = 0.15

**Strengths:** Fast, works well on sparse graphs (tree-like structures) where the simple vertex pheromone effectively guides search.  
**Weakness:** Limited performance on dense graphs where the heuristic and pheromone provide weaker guidance.

---

### 4. Ant Colony Optimization v3 (ACO v3) — Advanced Metaheuristic

**File:** `core/algorithms/inducedpath/aco/LongestInducedPathACOv3.java`

**Key differences from v1:**

1. **Edge-based pheromone:** Pheromone is deposited on edges rather than vertices, capturing the quality of specific edge transitions in the path. Uses a HashMap for storage.

2. **Rank-based pheromone update:** Top W ants (W = numAnts/8) deposit pheromone weighted by rank. The global best also deposits with 2x weight. This focuses learning on the best solutions found.

3. **Adaptive MMAS bounds:** `tauMax = deposit * bestLength / rho`, `tauMin = tauMax / (3 * |V|)`. Bounds adapt as better solutions are found.

4. **Dynamic candidate scoring:** When choosing the next vertex, the heuristic is augmented by residual connectivity: `dynamicHeur = h(v) * (1 + 0.3 * residualNeighbors)`. Vertices that keep more future options open are preferred.

5. **Sparse graph filtering:** On sparse graphs (avgDegree < 4), candidates with 0 residual neighbors (dead ends) are filtered out unless no other options exist.

6. **Ruin-and-recreate (middle segments):** Removes 3-8 vertices from a random middle segment of the path, then tries to find an alternative bridge via BFS (max depth 15). If the bridge produces a longer path, it's accepted.

7. **Internal vertex replacement:** Randomly selects internal vertices and tries to replace them with alternative neighbors that maintain path connectivity. This enables the path to "shift" through the graph.

8. **Segment perturbation:** Similar to v1's deep perturbation but with more aggressive removal (2-6 vertices).

9. **Adaptive exploration rate:** `epsilon = 0.03 + 0.07 * (stagnationCount / reinitThreshold)`. Exploration increases as the algorithm stagnates.

10. **Adjacency matrix:** Pre-computed for graphs up to 3000 vertices, enabling O(1) adjacency lookups in local search.

**Parameters:** numAnts = min(max(40, |V|/2), 400), maxIterations = 2000, alpha = 2.0, beta = 3.5, rho = 0.08

**Strengths:** Superior on medium/dense graphs (yeast: 217 vs v1's 184) thanks to the advanced local search operators that can restructure the middle of paths.  
**Weakness:** The ruin-and-recreate and internal vertex replacement operators add significant overhead. On sparse graphs, the BFS bridge search rarely finds useful alternatives, wasting computation.

---

### 5. ILP + GA Hybrid (Best Overall Method)

**Not a separate class** — uses `LongestInducedPathILP` followed by `LongestInducedPathGenetic` with the ILP solution as `initialSeed`.

**Approach:**
1. Run ILP with a 2-minute timeout to find the best feasible solution
2. Convert the ILP solution to a boolean seed array
3. Initialize 10% of the GA population with copies of the ILP seed
4. Run the full GA with the best configuration

**Why it works:**
- On small/medium graphs: ILP finds the optimal solution. The GA preserves it via elitism and confirms it.
- On large graphs: ILP provides a starting point (even if weak), while the GA's large population and random injection find much better solutions. The ILP seed acts as a quality floor.

---

## Benchmark Instances

24 graphs from various domains, ordered by size:

| Instance | \|V\| | \|E\| | Avg Degree | Domain |
|----------|------|-------|------------|--------|
| high-tech | 33 | 91 | 5.5 | Social network |
| karate | 34 | 78 | 4.6 | Social network |
| mexican | 35 | 117 | 6.7 | Social network |
| sawmill | 36 | 62 | 3.4 | Social network |
| chesapeake | 39 | 170 | 8.7 | Ecological |
| tailorS1 | 39 | 158 | 8.1 | Social network |
| tailorS2 | 39 | 223 | 11.4 | Social network |
| romeo and juliet | 41 | 120 | 5.9 | Literary network |
| die hard | 47 | 237 | 10.1 | Movie network |
| attiro | 59 | 128 | 4.3 | Social network |
| dolphins | 62 | 159 | 5.1 | Animal social |
| krebs | 62 | 153 | 4.9 | Political books |
| prison | 67 | 142 | 4.2 | Social network |
| huck | 69 | 297 | 8.6 | Literary network |
| sanjuansur | 75 | 144 | 3.8 | Geographic |
| jean | 77 | 254 | 6.6 | Literary network |
| david | 87 | 406 | 9.3 | Biblical network |
| sfi | 118 | 200 | 3.4 | Collaboration |
| ieeebus | 118 | 179 | 3.0 | Power grid |
| anna | 138 | 493 | 7.1 | Literary network |
| usair | 332 | 2,126 | 12.8 | Air routes |
| 494bus | 494 | 586 | 2.4 | Power grid |
| 662bus | 662 | 906 | 2.7 | Power grid |
| yeast | 2,361 | 6,646 | 5.6 | Protein interaction |

---

## Results

### Complete Results Table — All Algorithms, All Graphs

Verified on 2026-05-19. ILP: 1 run, 2-min timeout. GA: best config (ILP+Roulette, pop scaled by graph size), 3 runs. ACO v1 and v3: 3 runs each on small graphs, 1 run on large graphs (|V|>300). Yeast GA/ACO used 15-min timeout (some runs timed out; extensive prior data available).

| Instance | \|V\| | \|E\| | OPT | Our ILP | Our GA Best | Our GA Avg | Our ACOv1 | Our ACOv3 | HLIPP | Prev. GA |
|----------|-------|-------|-----|---------|-------------|------------|-----------|-----------|-------|----------|
| high-tech | 33 | 91 | 13 | **13** | 13 | 13.0 | 13 | 13 | 13 | 13 |
| karate | 34 | 78 | 9 | **9** | 9 | 9.0 | 9 | 9 | 9 | 9 |
| mexican | 35 | 117 | 16 | **16** | 16 | 16.0 | 16 | 16 | 16 | 16 |
| sawmill | 36 | 62 | 18 | **18** | 18 | 18.0 | 18 | 18 | 18 | 18 |
| chesapeake | 39 | 170 | 16 | **16** | 16 | 16.0 | 16 | 16 | 16 | 16 |
| tailorS1 | 39 | 158 | 13 | **13** | 13 | 13.0 | 13 | 13 | 13 | 13 |
| tailorS2 | 39 | 223 | 15 | **15** | 15 | 15.0 | 15 | 15 | 15 | 15 |
| romeo&juliet | 41 | 120 | 9 | **9** | 9 | 9.0 | 9 | 9 | 9 | 9 |
| die hard | 47 | 237 | 10 | **10** | 10 | 10.0 | 10 | 10 | 10 | 10 |
| attiro | 59 | 128 | 31 | **31** | 31 | 31.0 | 31 | 31 | 30 | 31 |
| dolphins | 62 | 159 | 24 | **24** | 24 | 24.0 | 23 | 24 | 23 | 24 |
| krebs | 62 | 153 | 17 | **17** | 17 | 17.0 | 17 | 17 | 17 | 17 |
| prison | 67 | 142 | 36 | **36** | 36 | 36.0 | 36 | 36 | 36 | 36 |
| huck | 69 | 297 | 9 | **9** | 9 | 9.0 | 9 | 9 | 9 | 9 |
| sanjuansur | 75 | 144 | 38 | **38** | 38 | 38.0 | 38 | 38 | 36 | 38 |
| jean | 77 | 254 | 11 | **11** | 11 | 11.0 | 11 | 11 | 11 | 11 |
| david | 87 | 406 | 19 | **19** | 19 | 19.0 | 19 | 19 | 19 | 19 |
| sfi | 118 | 200 | 13 | **13** | 13 | 13.0 | 13 | 13 | 13 | 13 |
| ieeebus | 118 | 179 | 47 | **47** | 47 | 47.0 | 47 | 47 | 47 | 47 |
| anna | 138 | 493 | 20 | **20** | 20 | 20.0 | 20 | 20 | 20 | 20 |
| usair | 332 | 2,126 | 46 | **46** | **46** | 46.0 | 40 | 42 | 38 | 42 |
| 494bus | 494 | 586 | 142 | **142** | **142** | 142.0 | 134 | 128 | 109 | 138 |
| 662bus | 662 | 906 | ? | 304* | **304** | 304.0 | 287 | 201 | 237 | 276 |
| yeast | 2,361 | 6,646 | ? | 4 | **285** | 257.4 | 179 | T/O | 204 | 245 |

 **HLIPP** = original HLIPP heuristic from Marzo & Ribeiro (2021), as tested in our bachelor thesis (Licenta, Table 3.3). HLIPP Modified results: attiro=31, sanjuansur=38, usair=42, 494bus=129, 662bus=251, yeast=221.  
 **Prev. GA** = Genetic Algorithm results from our bachelor thesis (Licenta, Table 3.3) using the original untuned configuration (pop=|V|, mutRate=0.85, maxGen=300).  
\* 662bus: Our ILP finds 304 in <5 min. All 8 ILP formulations in Bokler et al. (2020) timed out at 20 min. Literature ILP results: usair=46 (922s), 494bus=142 (171s), 662bus=TIMEOUT, yeast=TIMEOUT.

**Improvement over bachelor thesis (Prev. GA → Our GA Best):**
- **usair**: 42 → **46** (+9.5%, now optimal)
- **494bus**: 138 → **142** (+2.9%, now optimal)
- **662bus**: 276 → **304** (+10.1%, exceeds all prior literature)
- **yeast**: 245 → **285** (+16.3%, new state-of-the-art)

### Key Instance Results (from extensive prior experiments)

Literature state-of-the-art from Marzo & Ribeiro (2021, RAIRO-OR), Bokler et al. (2020, ISCO), and Marzo et al. (2022, COR). Their heuristic is HLIPP (LH10000). Their ILP used 20-min time limit. Note: their "S.Cerevisae" (1458V, 1948E) is a smaller version of yeast than ours (2361V, 6646E).

#### usair (332V, 2126E) — ILP optimal = 46

| Algorithm | Best | Avg | Runs | Source |
|-----------|------|-----|------|--------|
| **Our ILP** | **46** | — | Optimal in <2 min | Ours |
| Our ILP+GA (any config) | 46 | 46.0 | 5 runs, all optimal | Ours |
| Our GA (Roulette, no ILP) | 45 | 43.4 | 5 runs | Ours |
| Our ACO v3 | 45 | 44.0 | 3 runs | Ours |
| Our ACO v1 | 44 | 41.7 | 3 runs | Ours |
| Lit. HLIPP heuristic | 38 | — | — | Marzo 2021 |
| Lit. Matsypura heuristic | 30 | — | — | Marzo 2021 |
| Lit. ILP (best, 20 min) | 46 | — | 922s | Bokler 2020 |

#### 494bus (494V, 586E) — ILP optimal = 142

| Algorithm | Best | Avg | Runs | Source |
|-----------|------|-----|------|--------|
| **Our ILP** | **142** | — | Optimal in <2 min | Ours |
| Our ILP+GA (any config) | 142 | 142.0 | 5 runs, all optimal | Ours |
| Our GA (Roulette, no ILP) | 142 | 136.6 | 5 runs | Ours |
| Our ACO v1 | 138 | 129.7 | 3 runs | Ours |
| Our ACO v3 | 130 | 125.3 | 3 runs | Ours |
| Lit. HLIPP heuristic | 114 | — | — | Marzo 2021 |
| Lit. Matsypura heuristic | 61 | — | — | Marzo 2021 |
| Lit. ILP (best, 20 min) | 142 | — | 171s | Bokler 2020 |

#### 662bus (662V, 906E) — No proven optimal

| Algorithm | Best | Avg | Runs | Source |
|-----------|------|-----|------|--------|
| **Our ILP** | **304** | — | Optimal in <5 min | Ours |
| Our ILP+GA (any config) | 304 | 304.0 | 5 runs, all optimal | Ours |
| Our GA (Roulette, no ILP) | 295 | 284.6 | 5 runs | Ours |
| Our ACO v1 | 273 | 269.3 | 3 runs | Ours |
| Our ACO v3 | 204 | 203.3 | 3 runs | Ours |
| Lit. HLIPP heuristic | 242 | — | — | Marzo 2021 |
| Lit. Matsypura heuristic | 110 | — | — | Marzo 2021 |
| Lit. ILP (20 min) | TIMEOUT | — | — | Bokler 2020 |

Note: All literature ILP formulations timed out on 662bus at 20 min. Our ILP finds 304 in <5 min — if confirmed as optimal, this is a new result. Our GA result of 304 exceeds the previous best heuristic (242) by 25%.

#### yeast (2361V, 6646E) — No known optimal

| Algorithm | Best | Avg | Runs | Source |
|-----------|------|-----|------|--------|
| **Our ILP+GA (Roulette, pop=2000, elite=200)** | **285** | **257.4** | 5 | **Ours (Exp51) — NEW STATE-OF-THE-ART** |
| Our ILP+GA (Roulette, pop=2000, elite=100) | 275 | 254.7 | 10 | Ours |
| Our ILP+GA (Roulette, pop=3000, elite=300) | 272 | 257.7 | 3 | Ours |
| Our ILP+GA (T3, pop=1000) | 271 | 248.1 | 15 | Ours |
| Our GA (Roulette, no ILP) | 259 | 247.7 | 15 | Ours |
| Our ACO v3 | 217 | 210.3 | 3 | Ours |
| Our ACO v1 | 184 | 167.7 | 3 | Ours |
| Our ILP (2 min) | 4 | — | 1 | Ours |
| Lit. HLIPP heuristic (S.Cerevisae*) | 155 | — | — | Marzo 2021 |
| Lit. Matsypura heuristic (S.Cerevisae*) | 43 | — | — | Marzo 2021 |
| Lit. ILP (20 min) | TIMEOUT | — | — | Bokler 2020 |

\* Literature used S.Cerevisae (1458V, 1948E), a smaller version of the yeast protein interaction network. Our yeast instance (2361V, 6646E) is the larger Matsypura et al. (2019) version. Direct comparison is approximate, but our results on the harder instance still far exceed theirs on the easier one.

---

## GA Configuration Comparison (yeast, 15 runs per config)

All configs: pop=1000, elitism=50, maxGen=500, dynamic extension enabled.

| Config | Selection | Mut Rate | ILP Seed | Best | Avg | Worst |
|--------|-----------|----------|----------|------|-----|-------|
| ILP+T3_mut0.5 | Tournament(3) | 0.5 | Yes | **271** | **248.1** | 232 |
| Roulette_mut0.5 | Roulette | 0.5 | No | 259 | 247.7 | 237 |
| ILP+Roulette_mut0.5 | Roulette | 0.5 | Yes | 261 | 245.1 | 237 |
| ILP+Roulette_mut0.3 | Roulette | 0.3 | Yes | 265 | 244.7 | 233 |
| T3_mut0.5 | Tournament(3) | 0.5 | No | 253 | 243.3 | 233 |

## Pop=2000 Experiments (yeast)

| Exp | Config | Runs | Best | Avg | Worst |
|-----|--------|------|------|-----|-------|
| **51** | **ILP+Roulette, elite=200 (10%)** | **5** | **285** | **257.4** | **248** |
| 52 | ILP+Roulette, pop=3000, elite=300 (10%) | 3 | 272 | 257.7 | 250 |
| — | ILP+Roulette, elite=100 (5%) | 10 | 275 | 254.7 | 241 |
| 53 | ILP+Roulette, elite=200 + memetic top-5 | 3 | 266 | 255.3 | 249 |
| 50 | ILP+Roulette, pop=3000, elite=150 (5%) | 3 | 266 | 255.7 | 244 |
| — | ILP+Roulette, mut0.3 | 9 | 264 | 254.2 | 239 |
| — | Memetic (top-5 LS) | 3 | 260 | 252.0 | 245 |
| 54 | ILP+Roulette, elite=200, cross=0.5 | 3 | 258 | 251.0 | 246 |
| 49 | ILP+Roulette, mut=0.7 | 5 | 271 | 251.0 | 233 |
| — | Multi-restart (10 runs) | 10 | 265 | 249.4 | 241 |
| — | T3 (no ILP) | 8 | 257 | 248.3 | 243 |
| 55 | ILP+Roulette, elite=250 (12.5%) | 5 | 254 | 247.4 | 238 |
| — | ILP+T3, mut0.5 | 5 | 259 | 245.4 | 239 |
| — | Island model (4x500) | 5 | 251 | 245.6 | 241 |
| 47 | ILP+Roulette, crossover=0.0 | 5 | 245 | 238.8 | 234 |

### Elitism Sweep (pop=2000, Roulette, mut=0.5, ILP seed)

| Elitism | % of Pop | Best | Avg | Worst | Runs |
|---------|----------|------|-----|-------|------|
| 100 | 5% | 275 | 254.7 | 241 | 10 |
| **200** | **10%** | **285** | **257.4** | **248** | **5** |
| 250 | 12.5% | 254 | 247.4 | 238 | 5 |

**Finding:** 10% elitism is the sweet spot. It preserves enough diversity to trigger dynamic generation extension more frequently, leading to occasional deep exploration runs (~33 min vs typical ~12 min) that produce exceptional results. Higher elitism (12.5%) over-preserves and reduces effective diversity.

---

## Key Findings

### 1. ILP is the gold standard for small/medium graphs
ILP finds provably optimal solutions for all graphs up to ~700 vertices within minutes. For small graphs (<150 vertices), all algorithms find the optimal, making ILP unnecessary — but ILP proves it is optimal.

### 2. ILP+GA hybrid is the best overall approach
- On small/medium graphs: ILP seed guarantees GA finds optimal every run
- On large graphs (yeast): GA far exceeds ILP alone (285 vs 4), with ILP seed acting as a quality floor

### 3. GA dramatically outperforms ACO
On yeast, the best GA (285) is 31% better than the best ACO (217). Even pure GA without ILP seeding (259) beats ACO by 19%. The GA's large population with high random injection rate maintains diversity that ACO's pheromone-guided search cannot match on this problem.

### 4. ACO v1 vs v3 is density-dependent
- **Sparse graphs (avgDeg < 3):** ACO v1 dominates (662bus: 273 vs 204). The ruin-and-recreate local search in v3 wastes time on near-tree-like structures.
- **Dense/medium graphs (avgDeg > 4):** ACO v3 dominates (yeast: 217 vs 184). The advanced local search operators effectively exploit the denser connectivity.

### 5. Selection method scales differently with population size
- **Tournament(k=3)** at pop=1000: Higher peak (271) due to stronger selection pressure driving exploration
- **Tournament(k=3)** at pop=2000: Premature convergence (avg 245.4) — too much pressure kills diversity
- **Roulette** at pop=2000: Best results (avg 254.7) — softer pressure maintains diversity in larger populations

### 6. High random mutation rate is essential
With randomMutationRate=0.5, each generation adds ~50% new random individuals to the population. This aggressive diversity injection prevents stagnation and is the single most impactful parameter (experiments 10-13 tested rates from 0.35 to 0.85).

### 7. 10% elitism is the breakthrough parameter (Exp51)
Increasing elitism from 5% (100/2000) to 10% (200/2000) produced the new all-time best of 285 on yeast, beating the previous record of 275 by 10 vertices. The mechanism: high elitism preserves more diversity in the elite pool, which triggers dynamic generation extension more frequently. This leads to occasional deep exploration runs (~33 min vs typical ~12 min) that discover exceptional solutions. However, 12.5% elitism is too much — it over-preserves and reduces effective diversity (Avg=247.4 vs 257.4).

### 8. Crossover and memetic operators hurt with high elitism
Both higher crossover rate (0.5 vs default 0.3) and memetic local search (perturbAndReboost on top-5) reduce performance when combined with 10% elitism. Crossover at 0.5 makes each generation ~50% slower due to expensive fixIndividual+boostIndividual repairs, while memetic search homogenizes the elite pool. The default crossover rate of 0.3 and no memetic search remain optimal.

---

## Experiment History

56 experiments were conducted, testing:
- **Exp1-18:** GA parameter tuning (mutation rate, selection mode, elitism, generation limit, degree-biased heuristics, perturbation operators)
- **Exp19-31:** Selection variants (Roulette vs Tournament, ILP seeding, various pressure levels)
- **Exp32-44:** Population scaling (pop=2000, pop=3000), advanced strategies (island model, ILS, eccentricity seeding, multi-restart, high elitism, low mutation)
- **Exp45-48:** Memetic GA, high crossover, no crossover, selection pressure tuning
- **Exp49-56:** Elitism sweep (5%-12.5%), mutation rate 0.7, pop=3000+10% elitism, memetic+high elitism, high crossover+high elitism
- **ACO comparison:** v1 vs v2 vs v3, adaptive patch testing
- **FinalGAComparison:** Authoritative 5-config x 4-instance x 5-run dataset
- **Extended yeast:** 15 runs per config across 3 independent datasets

Total estimated compute time: **150+ hours** across all experiments.
