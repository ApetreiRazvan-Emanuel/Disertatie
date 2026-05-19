package core.algorithms.inducedpath.aco;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.*;

/**
 * Improved ACO for Longest Induced Path using:
 * - Edge-based pheromone trails (transition quality, not just vertex quality)
 * - MAX-MIN Ant System with adaptive bounds
 * - Lookahead-based dead-end avoidance on sparse graphs
 * - Rank-based pheromone update (top-w ants)
 * - Enhanced local search: endpoint swap + segment replacement
 */
public class LongestInducedPathACOv2 extends GraphAlgorithm implements InducedPathAlgorithm {
    private final int numVertices;
    private Path longestInducedPath;

    private final int numAnts;
    private final int maxIterations;
    private final double alpha;
    private final double beta;
    private final double evaporationRate;
    private final double pheromoneDeposit;
    private double tauMin;
    private double tauMax;
    private final int noImprovementReinit;
    private final int rankW;

    private final Map<Long, Double> edgePheromone;
    private final double[] vertexHeuristic;
    private final double[] startWeight;
    private double startWeightSum;
    private final double avgDegree;
    private final boolean isSparse;
    private final Random random = new Random();

    public LongestInducedPathACOv2(Graph graph) {
        super(graph);
        this.numVertices = graph.numVertices();
        this.longestInducedPath = new Path(graph);

        this.numAnts = Math.min(Math.max(30, numVertices / 2), 500);
        this.maxIterations = 1500;
        this.alpha = 2.0;
        this.beta = 4.0;
        this.evaporationRate = 0.1;
        this.pheromoneDeposit = 1.0;
        this.tauMax = 10.0;
        this.tauMin = tauMax / (numVertices * 5.0);
        this.noImprovementReinit = 80;
        this.rankW = Math.max(3, numAnts / 10);

        this.edgePheromone = new HashMap<>();
        this.vertexHeuristic = new double[numVertices];

        this.startWeight = new double[numVertices];
        this.avgDegree = numVertices > 0 ? 2.0 * graph.numEdges() / numVertices : 0;
        this.isSparse = avgDegree < 4.0;

        int[] vertices = graph.vertices();
        for (int i = 0; i < numVertices; i++) {
            int deg = graph.degree(vertices[i]);
            vertexHeuristic[i] = 1.0 / (deg + 1.0);
        }

        initEdgePheromone();

        startWeightSum = 0;
        if (avgDegree < 6.0) {
            for (int i = 0; i < numVertices; i++) {
                int ecc = computeEccentricity(vertices[i]);
                startWeight[i] = ecc + 1.0;
                startWeightSum += startWeight[i];
            }
        }
    }

    public LongestInducedPathACOv2(Graph graph, Path longestInducedPath) {
        this(graph);
        this.longestInducedPath = longestInducedPath;
    }

    private void initEdgePheromone() {
        edgePheromone.clear();
        int[] vertices = graph.vertices();
        for (int i = 0; i < numVertices; i++) {
            for (int nb : graph.neighbors(vertices[i])) {
                int j = graph.indexOf(nb);
                if (i < j) {
                    edgePheromone.put(edgeKey(i, j), tauMax);
                }
            }
        }
    }

    private long edgeKey(int i, int j) {
        return i < j ? ((long) i << 32) | j : ((long) j << 32) | i;
    }

    private double getEdgePheromone(int i, int j) {
        Double val = edgePheromone.get(edgeKey(i, j));
        return val != null ? val : tauMin;
    }

    private void setEdgePheromone(int i, int j, double val) {
        edgePheromone.put(edgeKey(i, j), Math.max(tauMin, Math.min(tauMax, val)));
    }

    private int computeEccentricity(int vertex) {
        Queue<Integer> queue = new ArrayDeque<>();
        boolean[] visited = new boolean[numVertices];
        int startIdx = graph.indexOf(vertex);
        queue.add(vertex);
        visited[startIdx] = true;
        int maxDist = 0;
        int[] dist = new int[numVertices];
        while (!queue.isEmpty()) {
            int v = queue.poll();
            int vIdx = graph.indexOf(v);
            for (int nb : graph.neighbors(v)) {
                int nbIdx = graph.indexOf(nb);
                if (!visited[nbIdx]) {
                    visited[nbIdx] = true;
                    dist[nbIdx] = dist[vIdx] + 1;
                    if (dist[nbIdx] > maxDist) maxDist = dist[nbIdx];
                    queue.add(nb);
                }
            }
        }
        return maxDist;
    }

    private List<Integer> constructSolution() {
        int[] vertices = graph.vertices();
        int startIdx;
        if (random.nextDouble() < 0.6 && startWeightSum > 0) {
            double r = random.nextDouble() * startWeightSum;
            double cumul = 0;
            startIdx = numVertices - 1;
            for (int i = 0; i < numVertices; i++) {
                cumul += startWeight[i];
                if (r <= cumul) { startIdx = i; break; }
            }
        } else {
            startIdx = random.nextInt(numVertices);
        }

        List<Integer> path = new ArrayList<>();
        boolean[] inPath = new boolean[numVertices];
        int[] invalidCount = new int[numVertices];

        path.add(startIdx);
        inPath[startIdx] = true;
        invalidCount[startIdx]++;
        for (int nb : graph.neighbors(vertices[startIdx])) {
            invalidCount[graph.indexOf(nb)]++;
        }

        extendPath(path, inPath, invalidCount, vertices, true);
        extendPath(path, inPath, invalidCount, vertices, false);

        return path;
    }

    private void extendPath(List<Integer> path, boolean[] inPath, int[] invalidCount, int[] vertices, boolean forward) {
        while (true) {
            if (Thread.currentThread().isInterrupted()) return;

            int endpoint = forward ? path.get(path.size() - 1) : path.get(0);
            List<Integer> candidates = new ArrayList<>();
            double[] weights = new double[numVertices];
            double totalWeight = 0;

            for (int nb : graph.neighbors(vertices[endpoint])) {
                int nbIdx = graph.indexOf(nb);
                if (invalidCount[nbIdx] == 1 && !inPath[nbIdx]) {
                    if (isSparse && !hasLookahead(nbIdx, inPath, invalidCount, vertices, endpoint)) {
                        continue;
                    }
                    candidates.add(nbIdx);
                    double phero = getEdgePheromone(endpoint, nbIdx);
                    double heur = vertexHeuristic[nbIdx];
                    double w = Math.pow(phero, alpha) * Math.pow(heur, beta);
                    weights[nbIdx] = w;
                    totalWeight += w;
                }
            }

            if (candidates.isEmpty()) {
                // Fallback: try without lookahead if sparse filtering removed all candidates
                if (isSparse) {
                    for (int nb : graph.neighbors(vertices[endpoint])) {
                        int nbIdx = graph.indexOf(nb);
                        if (invalidCount[nbIdx] == 1 && !inPath[nbIdx]) {
                            candidates.add(nbIdx);
                            double phero = getEdgePheromone(endpoint, nbIdx);
                            double heur = vertexHeuristic[nbIdx];
                            double w = Math.pow(phero, alpha) * Math.pow(heur, beta);
                            weights[nbIdx] = w;
                            totalWeight += w;
                        }
                    }
                }
                if (candidates.isEmpty()) break;
            }

            int chosen;
            if (totalWeight <= 0) {
                chosen = candidates.get(random.nextInt(candidates.size()));
            } else if (random.nextDouble() < 0.05) {
                // Pseudo-random proportional rule: with small prob, pick best directly
                double bestW = -1;
                chosen = candidates.get(0);
                for (int c : candidates) {
                    if (weights[c] > bestW) {
                        bestW = weights[c];
                        chosen = c;
                    }
                }
            } else {
                double r = random.nextDouble() * totalWeight;
                double cumulative = 0;
                chosen = candidates.get(candidates.size() - 1);
                for (int c : candidates) {
                    cumulative += weights[c];
                    if (r <= cumulative) {
                        chosen = c;
                        break;
                    }
                }
            }

            if (forward) {
                path.add(chosen);
            } else {
                path.add(0, chosen);
            }
            inPath[chosen] = true;
            invalidCount[chosen]++;
            for (int nb : graph.neighbors(vertices[chosen])) {
                invalidCount[graph.indexOf(nb)]++;
            }
        }
    }

    private boolean hasLookahead(int candidateIdx, boolean[] inPath, int[] invalidCount, int[] vertices, int currentEndpoint) {
        // Check if this candidate has at least one valid neighbor beyond the current endpoint
        int candidateVertex = vertices[candidateIdx];
        for (int nb : graph.neighbors(candidateVertex)) {
            int nbIdx = graph.indexOf(nb);
            if (nbIdx == currentEndpoint) continue;
            if (!inPath[nbIdx] && invalidCount[nbIdx] <= 1) {
                return true;
            }
        }
        return false;
    }

    private void localSearch(List<Integer> path, boolean[] inPath, int[] invalidCount, int[] vertices) {
        // Phase 1: Endpoint extension attempts (remove endpoint, try re-extending)
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean improved = false;

            for (int side = 0; side < 2; side++) {
                if (path.size() <= 2) break;
                int removeIdx = (side == 0) ? 0 : path.size() - 1;
                int removed = path.get(removeIdx);

                path.remove(removeIdx);
                inPath[removed] = false;
                invalidCount[removed]--;
                for (int nb : graph.neighbors(vertices[removed])) {
                    invalidCount[graph.indexOf(nb)]--;
                }

                int sizeBefore = path.size();
                extendPath(path, inPath, invalidCount, vertices, side != 0);

                if (path.size() <= sizeBefore) {
                    if (side == 0) path.add(0, removed);
                    else path.add(removed);
                    inPath[removed] = true;
                    invalidCount[removed]++;
                    for (int nb : graph.neighbors(vertices[removed])) {
                        invalidCount[graph.indexOf(nb)]++;
                    }
                } else {
                    improved = true;
                }
            }

            if (!improved) break;
        }

        // Phase 2: Segment removal and re-extension (remove 2-4 from one end, try extending)
        segmentPerturbation(path, inPath, invalidCount, vertices);
    }

    private void segmentPerturbation(List<Integer> path, boolean[] inPath, int[] invalidCount, int[] vertices) {
        if (path.size() <= 6) return;

        for (int trial = 0; trial < 5; trial++) {
            int removeCount = 2 + random.nextInt(Math.min(5, path.size() / 4));
            int side = random.nextInt(2);

            List<Integer> savedPath = new ArrayList<>(path);
            boolean[] savedInPath = Arrays.copyOf(inPath, inPath.length);
            int[] savedInvalid = Arrays.copyOf(invalidCount, invalidCount.length);

            for (int i = 0; i < removeCount && path.size() > 2; i++) {
                int removeIdx = (side == 0) ? 0 : path.size() - 1;
                int removed = path.remove(removeIdx);
                inPath[removed] = false;
                invalidCount[removed]--;
                for (int nb : graph.neighbors(vertices[removed])) {
                    invalidCount[graph.indexOf(nb)]--;
                }
            }

            extendPath(path, inPath, invalidCount, vertices, side != 0);
            extendPath(path, inPath, invalidCount, vertices, side == 0);

            if (path.size() > savedPath.size()) {
                return;
            }

            path.clear();
            path.addAll(savedPath);
            System.arraycopy(savedInPath, 0, inPath, 0, numVertices);
            System.arraycopy(savedInvalid, 0, invalidCount, 0, numVertices);
        }
    }

    private synchronized void updateBestPath(List<Integer> path, int[] vertices) {
        if (path.size() > longestInducedPath.size()) {
            longestInducedPath.clear();
            for (int idx : path) {
                longestInducedPath.add(vertices[idx]);
            }
        }
    }

    private void solve() {
        int[] vertices = graph.vertices();
        int bestLength = 0;
        int noImprovementCount = 0;

        for (int iter = 0; iter < maxIterations; iter++) {
            if (Thread.currentThread().isInterrupted()) break;

            List<List<Integer>> antPaths = new ArrayList<>();
            int[] pathLengths = new int[numAnts];

            for (int ant = 0; ant < numAnts; ant++) {
                if (Thread.currentThread().isInterrupted()) break;

                List<Integer> path = constructSolution();

                boolean[] inPath = new boolean[numVertices];
                int[] invalidCount = new int[numVertices];
                for (int idx : path) {
                    inPath[idx] = true;
                    invalidCount[idx]++;
                    for (int nb : graph.neighbors(vertices[idx])) {
                        invalidCount[graph.indexOf(nb)]++;
                    }
                }
                localSearch(path, inPath, invalidCount, vertices);

                antPaths.add(path);
                pathLengths[ant] = path.size();
                updateBestPath(path, vertices);
            }

            int iterBestLength = 0;
            for (int len : pathLengths) {
                if (len > iterBestLength) iterBestLength = len;
            }

            if (iterBestLength > bestLength) {
                bestLength = iterBestLength;
                noImprovementCount = 0;
            } else {
                noImprovementCount++;
            }

            // Evaporate all edges
            for (Map.Entry<Long, Double> entry : edgePheromone.entrySet()) {
                double val = entry.getValue() * (1.0 - evaporationRate);
                entry.setValue(Math.max(tauMin, val));
            }

            // Rank-based update: sort ants by path length, top-w deposit
            Integer[] indices = new Integer[numAnts];
            for (int i = 0; i < numAnts; i++) indices[i] = i;
            Arrays.sort(indices, (a, b) -> pathLengths[b] - pathLengths[a]);

            for (int rank = 0; rank < Math.min(rankW, numAnts); rank++) {
                List<Integer> path = antPaths.get(indices[rank]);
                double weight = (double)(rankW - rank) / rankW;
                double deposit = pheromoneDeposit * weight * path.size();
                for (int k = 0; k < path.size() - 1; k++) {
                    setEdgePheromone(path.get(k), path.get(k + 1),
                            getEdgePheromone(path.get(k), path.get(k + 1)) + deposit);
                }
            }

            // Global best also deposits (elitist strategy)
            if (longestInducedPath.size() > 0) {
                double eliteDeposit = pheromoneDeposit * 2.0 * longestInducedPath.size();
                int prev = -1;
                for (int v : longestInducedPath) {
                    int idx = graph.indexOf(v);
                    if (prev >= 0) {
                        setEdgePheromone(prev, idx,
                                getEdgePheromone(prev, idx) + eliteDeposit);
                    }
                    prev = idx;
                }
            }

            // Adaptive MMAS bounds
            tauMax = pheromoneDeposit * bestLength / evaporationRate;
            tauMin = tauMax / (numVertices * 3.0);

            // Stagnation restart
            if (noImprovementCount >= noImprovementReinit) {
                initEdgePheromone();
                noImprovementCount = 0;
            }
        }
    }

    @Override
    public Path getLongestInducedPath() {
        if (longestInducedPath.isEmpty()) {
            solve();
        }
        return longestInducedPath;
    }
}
