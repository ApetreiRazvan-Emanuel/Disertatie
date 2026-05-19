package core.algorithms.inducedpath.aco;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.*;

public class LongestInducedPathACO extends GraphAlgorithm implements InducedPathAlgorithm {
    private final int numVertices;
    private Path longestInducedPath;

    private final int numAnts;
    private final int maxIterations;
    private final double alpha;
    private final double beta;
    private final double evaporationRate;
    private final double pheromoneDeposit;
    private final double tauMin;
    private final double tauMax;
    private final int noImprovementReinit;

    private final double[] pheromone;
    private final double[] heuristic;
    private final double[] startWeight;
    private double startWeightSum;
    private final double avgDegree;
    private final double epsilon;
    private final Random random = new Random();

    public LongestInducedPathACO(Graph graph) {
        super(graph);
        this.numVertices = graph.numVertices();
        this.longestInducedPath = new Path(graph);

        this.numAnts = Math.max(20, numVertices);
        this.maxIterations = 1000;
        this.alpha = 1.5;
        this.beta = 3.0;
        this.evaporationRate = 0.15;
        this.pheromoneDeposit = 2.0;
        this.tauMin = 0.1;
        this.tauMax = 5.0;
        this.noImprovementReinit = 50;

        this.pheromone = new double[numVertices];
        this.heuristic = new double[numVertices];

        this.startWeight = new double[numVertices];

        int[] vertices = graph.vertices();
        for (int i = 0; i < numVertices; i++) {
            pheromone[i] = 1.0;
            int deg = graph.degree(vertices[i]);
            heuristic[i] = 1.0 / (deg + 1.0);
        }

        startWeightSum = 0;
        this.avgDegree = numVertices > 0 ? 2.0 * graph.numEdges() / numVertices : 0;
        this.epsilon = avgDegree >= 3.0 ? 0.1 * Math.min(1.0, 200.0 / numVertices) : 0.0;
        if (avgDegree < 6.0) {
            for (int i = 0; i < numVertices; i++) {
                int ecc = computeEccentricity(vertices[i]);
                startWeight[i] = ecc + 1.0;
                startWeightSum += startWeight[i];
            }
        }
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

    public LongestInducedPathACO(Graph graph, Path longestInducedPath) {
        this(graph);
        this.longestInducedPath = longestInducedPath;
    }

    private List<Integer> constructSolution() {
        int[] vertices = graph.vertices();
        int startIdx;
        if (random.nextDouble() < 0.5 && startWeightSum > 0) {
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
        int[] invalidVertices = new int[numVertices];

        path.add(startIdx);
        inPath[startIdx] = true;
        invalidVertices[startIdx]++;
        for (int nb : graph.neighbors(vertices[startIdx])) {
            invalidVertices[graph.indexOf(nb)]++;
        }

        extendPath(path, inPath, invalidVertices, vertices, true);
        extendPath(path, inPath, invalidVertices, vertices, false);

        return path;
    }

    private void extendPath(List<Integer> path, boolean[] inPath, int[] invalidVertices, int[] vertices, boolean forward) {
        while (true) {
            if (Thread.currentThread().isInterrupted()) return;

            int endpoint = forward ? path.get(path.size() - 1) : path.get(0);
            List<Integer> candidates = new ArrayList<>();
            double[] weights = new double[numVertices];
            double totalWeight = 0;

            for (int nb : graph.neighbors(vertices[endpoint])) {
                int nbIdx = graph.indexOf(nb);
                if (invalidVertices[nbIdx] == 1 && !inPath[nbIdx]) {
                    candidates.add(nbIdx);
                    double w = Math.pow(pheromone[nbIdx], alpha) * Math.pow(heuristic[nbIdx], beta);
                    weights[nbIdx] = w;
                    totalWeight += w;
                }
            }

            if (candidates.isEmpty()) break;

            int chosen;
            if (totalWeight <= 0 || (epsilon > 0 && random.nextDouble() < epsilon)) {
                chosen = candidates.get(random.nextInt(candidates.size()));
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
            invalidVertices[chosen]++;
            for (int nb : graph.neighbors(vertices[chosen])) {
                invalidVertices[graph.indexOf(nb)]++;
            }
        }
    }

    private void localSearch(List<Integer> path, boolean[] inPath, int[] invalidVertices, int[] vertices) {
        for (int attempt = 0; attempt < 5; attempt++) {
            boolean improved = false;

            for (int side = 0; side < 2; side++) {
                if (path.size() <= 2) break;
                int removeIdx = (side == 0) ? 0 : path.size() - 1;
                int removed = path.get(removeIdx);

                path.remove(removeIdx);
                inPath[removed] = false;
                invalidVertices[removed]--;
                for (int nb : graph.neighbors(vertices[removed])) {
                    invalidVertices[graph.indexOf(nb)]--;
                }

                int sizeBefore = path.size();
                extendPath(path, inPath, invalidVertices, vertices, side != 0);

                if (path.size() <= sizeBefore) {
                    if (side == 0) {
                        path.add(0, removed);
                    } else {
                        path.add(removed);
                    }
                    inPath[removed] = true;
                    invalidVertices[removed]++;
                    for (int nb : graph.neighbors(vertices[removed])) {
                        invalidVertices[graph.indexOf(nb)]++;
                    }
                } else {
                    improved = true;
                }
            }

            if (!improved) break;
        }

        if (avgDegree >= 3.0 && numVertices <= 300) {
            deepPerturbation(path, inPath, invalidVertices, vertices);
        }
    }

    private void deepPerturbation(List<Integer> path, boolean[] inPath, int[] invalidVertices, int[] vertices) {
        if (path.size() <= 4) return;

        for (int trial = 0; trial < 3; trial++) {
            int removeCount = 2 + random.nextInt(Math.min(4, path.size() / 3));
            int side = random.nextInt(2);

            List<Integer> savedPath = new ArrayList<>(path);
            boolean[] savedInPath = Arrays.copyOf(inPath, inPath.length);
            int[] savedInvalid = Arrays.copyOf(invalidVertices, invalidVertices.length);

            for (int i = 0; i < removeCount && path.size() > 2; i++) {
                int removeIdx = (side == 0) ? 0 : path.size() - 1;
                int removed = path.remove(removeIdx);
                inPath[removed] = false;
                invalidVertices[removed]--;
                for (int nb : graph.neighbors(vertices[removed])) {
                    invalidVertices[graph.indexOf(nb)]--;
                }
            }

            extendPath(path, inPath, invalidVertices, vertices, side != 0);
            extendPath(path, inPath, invalidVertices, vertices, side == 0);

            if (path.size() > savedPath.size()) {
                return;
            }

            path.clear();
            path.addAll(savedPath);
            System.arraycopy(savedInPath, 0, inPath, 0, numVertices);
            System.arraycopy(savedInvalid, 0, invalidVertices, 0, numVertices);
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
            int iterBestLength = 0;
            int iterBestIdx = -1;

            for (int ant = 0; ant < numAnts; ant++) {
                if (Thread.currentThread().isInterrupted()) break;

                List<Integer> path = constructSolution();

                boolean[] inPath = new boolean[numVertices];
                int[] invalidVertices = new int[numVertices];
                for (int idx : path) {
                    inPath[idx] = true;
                    invalidVertices[idx]++;
                    for (int nb : graph.neighbors(vertices[idx])) {
                        invalidVertices[graph.indexOf(nb)]++;
                    }
                }
                localSearch(path, inPath, invalidVertices, vertices);

                antPaths.add(path);
                if (path.size() > iterBestLength) {
                    iterBestLength = path.size();
                    iterBestIdx = ant;
                }
                updateBestPath(path, vertices);
            }

            if (iterBestLength > bestLength) {
                bestLength = iterBestLength;
                noImprovementCount = 0;
            } else {
                noImprovementCount++;
            }

            for (int i = 0; i < numVertices; i++) {
                pheromone[i] *= (1.0 - evaporationRate);
                if (pheromone[i] < tauMin) pheromone[i] = tauMin;
            }

            if (iterBestIdx >= 0) {
                List<Integer> bestPath = antPaths.get(iterBestIdx);
                double deposit = pheromoneDeposit * bestPath.size() / numVertices;
                for (int idx : bestPath) {
                    pheromone[idx] += deposit;
                    if (pheromone[idx] > tauMax) pheromone[idx] = tauMax;
                }
            }

            if (longestInducedPath.size() > 0) {
                double eliteDeposit = pheromoneDeposit * 2.0 * longestInducedPath.size() / numVertices;
                for (int v : longestInducedPath) {
                    int idx = graph.indexOf(v);
                    pheromone[idx] += eliteDeposit;
                    if (pheromone[idx] > tauMax) pheromone[idx] = tauMax;
                }
            }

            if (noImprovementCount >= noImprovementReinit) {
                for (int i = 0; i < numVertices; i++) {
                    pheromone[i] = 1.0;
                }
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
