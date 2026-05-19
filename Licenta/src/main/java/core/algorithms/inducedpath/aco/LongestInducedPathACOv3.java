package core.algorithms.inducedpath.aco;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.*;

/**
 * ACO v3 for Longest Induced Path with:
 * - Edge-based pheromone (MMAS with adaptive bounds)
 * - Rank-based pheromone update + elitist global best
 * - Dynamic candidate scoring: residual connectivity heuristic
 * - Ruin-and-recreate on MIDDLE segments (not just endpoints)
 * - Internal vertex replacement local search
 * - Eccentricity-weighted start vertex selection
 * - Adaptive exploration rate based on stagnation
 */
public class LongestInducedPathACOv3 extends GraphAlgorithm implements InducedPathAlgorithm {
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

    private final int[][] adjacencyIdx;
    private final boolean[][] adjMatrix;

    public LongestInducedPathACOv3(Graph graph) {
        super(graph);
        this.numVertices = graph.numVertices();
        this.longestInducedPath = new Path(graph);

        this.avgDegree = numVertices > 0 ? 2.0 * graph.numEdges() / numVertices : 0;
        this.isSparse = avgDegree < 4.0;

        this.numAnts = Math.min(Math.max(40, numVertices / 2), 400);
        this.maxIterations = 2000;
        this.alpha = 2.0;
        this.beta = 3.5;
        this.evaporationRate = 0.08;
        this.pheromoneDeposit = 1.0;
        this.tauMax = 10.0;
        this.tauMin = tauMax / (numVertices * 5.0);
        this.noImprovementReinit = 100;
        this.rankW = Math.max(3, numAnts / 8);

        this.edgePheromone = new HashMap<>();
        this.vertexHeuristic = new double[numVertices];
        this.startWeight = new double[numVertices];

        int[] vertices = graph.vertices();

        this.adjacencyIdx = new int[numVertices][];
        for (int i = 0; i < numVertices; i++) {
            int[] nbs = graph.neighbors(vertices[i]);
            adjacencyIdx[i] = new int[nbs.length];
            for (int j = 0; j < nbs.length; j++) {
                adjacencyIdx[i][j] = graph.indexOf(nbs[j]);
            }
        }

        this.adjMatrix = numVertices <= 3000 ? new boolean[numVertices][numVertices] : null;
        if (adjMatrix != null) {
            for (int i = 0; i < numVertices; i++) {
                for (int nbIdx : adjacencyIdx[i]) {
                    adjMatrix[i][nbIdx] = true;
                }
            }
        }

        for (int i = 0; i < numVertices; i++) {
            int deg = adjacencyIdx[i].length;
            vertexHeuristic[i] = 1.0 / (deg + 1.0);
        }

        initEdgePheromone();

        startWeightSum = 0;
        if (avgDegree < 8.0) {
            for (int i = 0; i < numVertices; i++) {
                int ecc = computeEccentricity(i);
                startWeight[i] = ecc + 1.0;
                startWeightSum += startWeight[i];
            }
        }
    }

    public LongestInducedPathACOv3(Graph graph, Path longestInducedPath) {
        this(graph);
        this.longestInducedPath = longestInducedPath;
    }

    private void initEdgePheromone() {
        edgePheromone.clear();
        for (int i = 0; i < numVertices; i++) {
            for (int j : adjacencyIdx[i]) {
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

    private boolean isAdj(int i, int j) {
        if (adjMatrix != null) return adjMatrix[i][j];
        for (int nb : adjacencyIdx[i]) {
            if (nb == j) return true;
        }
        return false;
    }

    private int computeEccentricity(int startIdx) {
        Queue<Integer> queue = new ArrayDeque<>();
        boolean[] visited = new boolean[numVertices];
        queue.add(startIdx);
        visited[startIdx] = true;
        int maxDist = 0;
        int[] dist = new int[numVertices];
        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (int nb : adjacencyIdx[v]) {
                if (!visited[nb]) {
                    visited[nb] = true;
                    dist[nb] = dist[v] + 1;
                    if (dist[nb] > maxDist) maxDist = dist[nb];
                    queue.add(nb);
                }
            }
        }
        return maxDist;
    }

    private int countResidualNeighbors(int candidateIdx, boolean[] inPath, int[] invalidCount, int currentEndpoint) {
        int count = 0;
        for (int nb : adjacencyIdx[candidateIdx]) {
            if (nb == currentEndpoint) continue;
            if (!inPath[nb] && invalidCount[nb] <= 1) {
                count++;
            }
        }
        return count;
    }

    private List<Integer> constructSolution(double explorationRate) {
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
        for (int nb : adjacencyIdx[startIdx]) {
            invalidCount[nb]++;
        }

        extendPath(path, inPath, invalidCount, true, explorationRate);
        extendPath(path, inPath, invalidCount, false, explorationRate);

        return path;
    }

    private void extendPath(List<Integer> path, boolean[] inPath, int[] invalidCount,
                            boolean forward, double explorationRate) {
        while (true) {
            if (Thread.currentThread().isInterrupted()) return;

            int endpoint = forward ? path.get(path.size() - 1) : path.get(0);
            List<Integer> candidates = new ArrayList<>();
            double[] weights = new double[numVertices];
            double totalWeight = 0;

            for (int nbIdx : adjacencyIdx[endpoint]) {
                if (invalidCount[nbIdx] == 1 && !inPath[nbIdx]) {
                    if (isSparse) {
                        int residual = countResidualNeighbors(nbIdx, inPath, invalidCount, endpoint);
                        if (residual == 0 && adjacencyIdx[nbIdx].length > 1) continue;
                    }
                    candidates.add(nbIdx);
                    double phero = getEdgePheromone(endpoint, nbIdx);
                    double heur = vertexHeuristic[nbIdx];
                    int residual = countResidualNeighbors(nbIdx, inPath, invalidCount, endpoint);
                    double dynamicHeur = heur * (1.0 + 0.3 * residual);
                    double w = Math.pow(phero, alpha) * Math.pow(dynamicHeur, beta);
                    weights[nbIdx] = w;
                    totalWeight += w;
                }
            }

            if (candidates.isEmpty()) {
                if (isSparse) {
                    for (int nbIdx : adjacencyIdx[endpoint]) {
                        if (invalidCount[nbIdx] == 1 && !inPath[nbIdx]) {
                            candidates.add(nbIdx);
                            double phero = getEdgePheromone(endpoint, nbIdx);
                            double w = Math.pow(phero, alpha) * Math.pow(vertexHeuristic[nbIdx], beta);
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
            } else if (random.nextDouble() < explorationRate) {
                chosen = candidates.get(random.nextInt(candidates.size()));
            } else if (random.nextDouble() < 0.08) {
                double bestW = -1;
                chosen = candidates.get(0);
                for (int c : candidates) {
                    if (weights[c] > bestW) { bestW = weights[c]; chosen = c; }
                }
            } else {
                double r = random.nextDouble() * totalWeight;
                double cumulative = 0;
                chosen = candidates.get(candidates.size() - 1);
                for (int c : candidates) {
                    cumulative += weights[c];
                    if (r <= cumulative) { chosen = c; break; }
                }
            }

            if (forward) path.add(chosen);
            else path.add(0, chosen);
            inPath[chosen] = true;
            invalidCount[chosen]++;
            for (int nb : adjacencyIdx[chosen]) {
                invalidCount[nb]++;
            }
        }
    }

    private void rebuildState(List<Integer> path, boolean[] inPath, int[] invalidCount) {
        Arrays.fill(inPath, false);
        Arrays.fill(invalidCount, 0);
        for (int idx : path) {
            inPath[idx] = true;
            invalidCount[idx]++;
            for (int nb : adjacencyIdx[idx]) {
                invalidCount[nb]++;
            }
        }
    }

    private void localSearch(List<Integer> path, boolean[] inPath, int[] invalidCount) {
        endpointPerturbation(path, inPath, invalidCount);
        middleRuinAndRecreate(path, inPath, invalidCount);
        internalVertexReplacement(path, inPath, invalidCount);
        endpointPerturbation(path, inPath, invalidCount);
    }

    private void endpointPerturbation(List<Integer> path, boolean[] inPath, int[] invalidCount) {
        for (int attempt = 0; attempt < 10; attempt++) {
            boolean improved = false;
            for (int side = 0; side < 2; side++) {
                if (path.size() <= 2) break;
                int removeIdx = (side == 0) ? 0 : path.size() - 1;
                int removed = path.get(removeIdx);

                path.remove(removeIdx);
                inPath[removed] = false;
                invalidCount[removed]--;
                for (int nb : adjacencyIdx[removed]) invalidCount[nb]--;

                int sizeBefore = path.size();
                extendPath(path, inPath, invalidCount, side != 0, 0.02);

                if (path.size() <= sizeBefore) {
                    if (side == 0) path.add(0, removed);
                    else path.add(removed);
                    inPath[removed] = true;
                    invalidCount[removed]++;
                    for (int nb : adjacencyIdx[removed]) invalidCount[nb]++;
                } else {
                    improved = true;
                }
            }
            if (!improved) break;
        }
    }

    private void middleRuinAndRecreate(List<Integer> path, boolean[] inPath, int[] invalidCount) {
        if (path.size() <= 8) return;

        for (int trial = 0; trial < 8; trial++) {
            int pathLen = path.size();
            int ruinSize = 3 + random.nextInt(Math.min(8, pathLen / 5));
            int margin = Math.max(2, pathLen / 10);
            int startPos = margin + random.nextInt(Math.max(1, pathLen - 2 * margin - ruinSize));
            int endPos = Math.min(startPos + ruinSize, pathLen - margin);
            if (endPos <= startPos || endPos >= pathLen) continue;

            List<Integer> savedPath = new ArrayList<>(path);
            boolean[] savedInPath = Arrays.copyOf(inPath, inPath.length);
            int[] savedInvalid = Arrays.copyOf(invalidCount, invalidCount.length);

            int leftEnd = path.get(startPos - 1);
            int rightEnd = path.get(endPos);

            for (int i = endPos - 1; i >= startPos; i--) {
                int removed = path.remove(i);
                inPath[removed] = false;
                invalidCount[removed]--;
                for (int nb : adjacencyIdx[removed]) invalidCount[nb]--;
            }

            List<Integer> bridge = findBridge(leftEnd, rightEnd, inPath, invalidCount);

            if (bridge != null && bridge.size() >= (endPos - startPos)) {
                for (int i = 0; i < bridge.size(); i++) {
                    path.add(startPos + i, bridge.get(i));
                    inPath[bridge.get(i)] = true;
                    invalidCount[bridge.get(i)]++;
                    for (int nb : adjacencyIdx[bridge.get(i)]) invalidCount[nb]++;
                }

                extendPath(path, inPath, invalidCount, true, 0.02);
                extendPath(path, inPath, invalidCount, false, 0.02);

                if (path.size() > savedPath.size()) {
                    continue;
                }
            }

            path.clear();
            path.addAll(savedPath);
            System.arraycopy(savedInPath, 0, inPath, 0, numVertices);
            System.arraycopy(savedInvalid, 0, invalidCount, 0, numVertices);
        }
    }

    private List<Integer> findBridge(int leftEnd, int rightEnd, boolean[] inPath, int[] invalidCount) {
        Queue<int[]> queue = new ArrayDeque<>();
        Map<Integer, Integer> parent = new HashMap<>();
        queue.add(new int[]{leftEnd, 0});
        parent.put(leftEnd, -1);
        int maxDepth = 15;

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int node = curr[0];
            int depth = curr[1];

            if (depth >= maxDepth) continue;

            for (int nb : adjacencyIdx[node]) {
                if (parent.containsKey(nb)) continue;
                if (inPath[nb]) continue;

                if (nb == rightEnd) {
                    List<Integer> bridge = new ArrayList<>();
                    int backtrack = node;
                    while (backtrack != leftEnd) {
                        bridge.add(0, backtrack);
                        backtrack = parent.get(backtrack);
                    }
                    boolean valid = true;
                    for (int bv : bridge) {
                        if (invalidCount[bv] > 1) { valid = false; break; }
                    }
                    if (valid) return bridge;
                    continue;
                }

                if (invalidCount[nb] <= 1) {
                    parent.put(nb, node);
                    queue.add(new int[]{nb, depth + 1});
                }
            }
        }
        return null;
    }

    private void internalVertexReplacement(List<Integer> path, boolean[] inPath, int[] invalidCount) {
        if (path.size() <= 4) return;

        int maxTrials = Math.min(20, path.size() / 3);
        for (int trial = 0; trial < maxTrials; trial++) {
            int pos = 1 + random.nextInt(path.size() - 2);
            int current = path.get(pos);
            int prev = path.get(pos - 1);
            int next = path.get(pos + 1);

            List<Integer> savedPath = new ArrayList<>(path);
            boolean[] savedInPath = Arrays.copyOf(inPath, inPath.length);
            int[] savedInvalid = Arrays.copyOf(invalidCount, invalidCount.length);

            path.remove(pos);
            inPath[current] = false;
            invalidCount[current]--;
            for (int nb : adjacencyIdx[current]) invalidCount[nb]--;

            boolean reconnected = false;
            for (int candidate : adjacencyIdx[prev]) {
                if (candidate == current || inPath[candidate]) continue;
                if (invalidCount[candidate] > 1) continue;
                if (!isAdj(candidate, next)) continue;

                path.add(pos, candidate);
                inPath[candidate] = true;
                invalidCount[candidate]++;
                for (int nb : adjacencyIdx[candidate]) invalidCount[nb]++;

                extendPath(path, inPath, invalidCount, true, 0.02);
                extendPath(path, inPath, invalidCount, false, 0.02);

                if (path.size() > savedPath.size()) {
                    reconnected = true;
                    break;
                } else {
                    path.clear();
                    path.addAll(savedPath);
                    System.arraycopy(savedInPath, 0, inPath, 0, numVertices);
                    System.arraycopy(savedInvalid, 0, invalidCount, 0, numVertices);
                    break;
                }
            }

            if (!reconnected) {
                path.clear();
                path.addAll(savedPath);
                System.arraycopy(savedInPath, 0, inPath, 0, numVertices);
                System.arraycopy(savedInvalid, 0, invalidCount, 0, numVertices);
            }
        }
    }

    private void segmentPerturbation(List<Integer> path, boolean[] inPath, int[] invalidCount) {
        if (path.size() <= 6) return;

        for (int trial = 0; trial < 6; trial++) {
            int removeCount = 2 + random.nextInt(Math.min(6, path.size() / 4));
            int side = random.nextInt(2);

            List<Integer> savedPath = new ArrayList<>(path);
            boolean[] savedInPath = Arrays.copyOf(inPath, inPath.length);
            int[] savedInvalid = Arrays.copyOf(invalidCount, invalidCount.length);

            for (int i = 0; i < removeCount && path.size() > 2; i++) {
                int removeIdx = (side == 0) ? 0 : path.size() - 1;
                int removed = path.remove(removeIdx);
                inPath[removed] = false;
                invalidCount[removed]--;
                for (int nb : adjacencyIdx[removed]) invalidCount[nb]--;
            }

            extendPath(path, inPath, invalidCount, side != 0, 0.02);
            extendPath(path, inPath, invalidCount, side == 0, 0.02);

            if (path.size() > savedPath.size()) return;

            path.clear();
            path.addAll(savedPath);
            System.arraycopy(savedInPath, 0, inPath, 0, numVertices);
            System.arraycopy(savedInvalid, 0, invalidCount, 0, numVertices);
        }
    }

    private synchronized void updateBestPath(List<Integer> path) {
        if (path.size() > longestInducedPath.size()) {
            int[] vertices = graph.vertices();
            longestInducedPath.clear();
            for (int idx : path) {
                longestInducedPath.add(vertices[idx]);
            }
        }
    }

    private void solve() {
        int bestLength = longestInducedPath.size();
        int noImprovementCount = 0;

        for (int iter = 0; iter < maxIterations; iter++) {
            if (Thread.currentThread().isInterrupted()) break;

            double explorationRate = 0.03 + 0.07 * ((double) noImprovementCount / noImprovementReinit);

            List<List<Integer>> antPaths = new ArrayList<>();
            int[] pathLengths = new int[numAnts];

            for (int ant = 0; ant < numAnts; ant++) {
                if (Thread.currentThread().isInterrupted()) break;

                List<Integer> path = constructSolution(explorationRate);

                boolean[] inPath = new boolean[numVertices];
                int[] invalidCount = new int[numVertices];
                rebuildState(path, inPath, invalidCount);

                localSearch(path, inPath, invalidCount);
                segmentPerturbation(path, inPath, invalidCount);

                antPaths.add(path);
                pathLengths[ant] = path.size();
                updateBestPath(path);
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

            for (Map.Entry<Long, Double> entry : edgePheromone.entrySet()) {
                double val = entry.getValue() * (1.0 - evaporationRate);
                entry.setValue(Math.max(tauMin, val));
            }

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

            if (longestInducedPath.size() > 0) {
                double eliteDeposit = pheromoneDeposit * 2.0 * longestInducedPath.size();
                int prev = -1;
                for (int v : longestInducedPath) {
                    int idx = graph.indexOf(v);
                    if (prev >= 0) {
                        setEdgePheromone(prev, idx, getEdgePheromone(prev, idx) + eliteDeposit);
                    }
                    prev = idx;
                }
            }

            tauMax = pheromoneDeposit * bestLength / evaporationRate;
            tauMin = tauMax / (numVertices * 3.0);

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
