package core.algorithms.inducedpath.aco;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * Port of Prof. Cristian Frăsinaru's ACO implementation for the Longest Induced Path Problem.
 *
 * Original: ro.uaic.info.lipp.LippAlgorithm (graph4j-based, multi-threaded DFS with pheromone).
 *
 * Key design: parallel pheromone-guided DFS from multiple starting vertices, with adaptive
 * evaporation, sprint-based restart mechanism, tolerance-filtered proportional pheromone
 * deposit, and DFS pruning bounds. Synced with upstream commit aab568b (2026-06-27), which
 * replaced the tabu-path mechanism with per-restart "sprint" bests and deposit tolerance.
 */
public class LongestInducedPathACO extends GraphAlgorithm implements InducedPathAlgorithm {

    // --- Configuration ---
    private double minPheromoneLevel = 1.0;
    private double maxPheromoneLevel;
    private double evaporationRate = 0.1;
    private final double minEvaporationRate = 0.1;
    private final double maxEvaporationRate = 1.0;
    private final double evaporationRateStep = 0.1;
    private double pheromoneInfluence = 1;
    private final double minPheromoneInfluence = 0;
    private final double pheromoneInfluenceStep = 0.1;
    private double heuristicInfluence = 0;
    private int dfsCount = 2;
    private int depositPathsLimit;
    private final int maxDepositPathsLimit = 5;
    private double depositTolerance = 0.05;
    private final double maxDepositTolerance = 0.25;
    private final double depositToleranceStep = 0.001;
    private final double optSprintDepositMultiplier = 2;
    private int maxRestarts = 100;
    private int restartInterval = 100;
    private int stagnationThreshold = 10;
    private long timeLimitMs = 15 * 60 * 1000;
    private int numThreads = 0;
    private boolean outputEnabled = true;

    // --- State ---
    private boolean[][] adjMatrix;
    private int[][] adjList;
    private int[] degrees;
    private double[][] pheromoneMatrix;
    volatile Path optGlobalPath;
    private int optIteration;
    private long optTime;
    private int iterations;
    private int restarts;
    private volatile long deadlineNanos;

    public LongestInducedPathACO(Graph graph) {
        super(graph);
        this.maxPheromoneLevel = optSprintDepositMultiplier * graph.numVertices();
        createAdjList();
        createAdjMatrix();
        this.degrees = graph.degrees();
        double regularity = computeRegularity();
        this.depositPathsLimit = Math.min(
                1 + (int) ((1.0 - regularity) * (graph.numVertices() / 100)),
                maxDepositPathsLimit);
    }

    public void setTimeLimitMs(long ms) {
        this.timeLimitMs = ms;
    }

    public void setOutputEnabled(boolean enabled) {
        this.outputEnabled = enabled;
    }

    @Override
    public Path getLongestInducedPath() {
        return find();
    }

    public Path find() {
        int[] vertices = graph.vertices();
        int n = graph.numVertices();

        long absoluteDeadline = timeLimitMs > 0
                ? System.nanoTime() + 1_000_000L * timeLimitMs
                : Long.MAX_VALUE;

        boolean[] visited = new boolean[n];
        List<List<Integer>> components = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                List<Integer> comp = new ArrayList<>();
                Queue<Integer> queue = new LinkedList<>();
                queue.add(i);
                visited[i] = true;
                while (!queue.isEmpty()) {
                    int vi = queue.poll();
                    comp.add(vi);
                    int v = vertices[vi];
                    for (var it = graph.neighborIterator(v); it.hasNext(); ) {
                        int u = it.next();
                        int ui = graph.indexOf(u);
                        if (!visited[ui]) {
                            visited[ui] = true;
                            queue.add(ui);
                        }
                    }
                }
                components.add(comp);
            }
        }

        if (components.size() == 1) {
            return findConnected();
        }

        Path bestPath = new Path(graph);
        for (List<Integer> comp : components) {
            if (comp.size() <= 1) continue;
            long remainingMs = (absoluteDeadline - System.nanoTime()) / 1_000_000L;
            if (remainingMs <= 0) break;
            int[] compVertices = comp.stream().mapToInt(i -> vertices[i]).toArray();
            Graph subgraph = graph.subgraph(compVertices);
            LongestInducedPathACO subAlg = new LongestInducedPathACO(subgraph);
            subAlg.timeLimitMs = remainingMs;
            subAlg.outputEnabled = this.outputEnabled;
            Path subPath = subAlg.findConnected();
            if (subPath.size() > bestPath.size()) {
                bestPath = new Path(graph, subPath.vertices());
            }
        }
        return bestPath;
    }

    private Path findConnected() {
        int n = graph.numVertices();
        pheromoneMatrix = new double[n][n];
        resetPheromone();

        optGlobalPath = new Path(graph);

        List<DFSTask> tasks = new ArrayList<>(n);
        IntStream.of(graph.vertices())
                .filter(v -> graph.degree(v) > 2)
                .forEach(v -> tasks.add(new DFSTask(v)));
        if (tasks.isEmpty()) {
            tasks.add(new DFSTask(graph.vertexAt(0)));
        }

        int threads = numThreads > 0 ? numThreads : Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        if (outputEnabled) {
            System.out.println("[ProfACO] Tasks: " + tasks.size() + ", Threads: " + threads
                    + ", n=" + n + ", m=" + graph.numEdges()
                    + ", depositPathsLimit=" + depositPathsLimit);
        }

        try {
            long startTime = System.nanoTime();
            deadlineNanos = timeLimitMs > 0 ? startTime + 1_000_000L * timeLimitMs : Long.MAX_VALUE;
            iterations = 0;
            restarts = 0;
            int stagnationIts = 0;
            Set<PathKey> iterPathKeys = new HashSet<>();
            List<Path> iterPaths = new ArrayList<>();
            Path optSprintPath = new Path(graph, 0);

            while (timeLimitMs == 0
                    || (System.nanoTime() - startTime) < 1_000_000L * timeLimitMs) {
                iterations++;

                List<Future<Path>> futures = new ArrayList<>(tasks.size());
                for (DFSTask task : tasks) {
                    if (!optGlobalPath.contains(task.startVertex)) {
                        futures.add(executor.submit(task));
                    }
                }

                boolean improvedSprint = false;
                boolean improvedGlobal = false;
                iterPaths.clear();
                iterPathKeys.clear();
                Path optLocalPath = new Path(graph, 0);

                for (Future<Path> f : futures) {
                    long waitMs = Math.max(1, (deadlineNanos - System.nanoTime()) / 1_000_000L + 5000);
                    Path path;
                    try {
                        path = f.get(waitMs, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException te) {
                        f.cancel(true);
                        continue;
                    }
                    if (path.size() > optLocalPath.size()) {
                        optLocalPath = path;
                    }
                    PathKey pk = new PathKey(path);
                    if (iterPathKeys.add(pk)) {
                        iterPaths.add(path);
                    }
                }

                final int optSprintPathSize = optSprintPath.size();
                if (optLocalPath.size() > optSprintPathSize) {
                    optSprintPath = optLocalPath;
                    improvedSprint = true;

                    if (optLocalPath.size() > optGlobalPath.size()) {
                        optGlobalPath = optLocalPath;
                        optIteration = iterations;
                        optTime = System.nanoTime() - startTime;
                        improvedGlobal = true;
                    }
                }

                if (improvedSprint) {
                    stagnationIts = 0;
                    evaporationRate = minEvaporationRate;

                    if (improvedGlobal) {
                        restarts = 0;
                        if (outputEnabled) {
                            System.out.println("[ProfACO] New best: " + optGlobalPath.size()
                                    + ", iter=" + iterations
                                    + ", evap=" + String.format("%.2f", evaporationRate)
                                    + ", time=" + optTime / 1_000_000 + "ms");
                        }
                    }
                } else {
                    stagnationIts++;
                    if (stagnationIts % restartInterval == 0) {
                        restarts++;
                        if (maxRestarts > 0 && restarts > maxRestarts) break;
                        optSprintPath = new Path(graph, 0);
                        resetPheromone();
                        evaporationRate = minEvaporationRate;
                        dfsCount = 2 + (int) (Math.log(restarts) / Math.log(2.0));
                        if (restarts % 20 == 0) {
                            depositPathsLimit++;
                        }
                        if (outputEnabled) {
                            System.out.println("[ProfACO] Restart #" + restarts
                                    + ", iter=" + iterations
                                    + ", dfsCount=" + dfsCount
                                    + ", depositTolerance=" + String.format("%.3f", depositTolerance)
                                    + ", depositLimit=" + depositPathsLimit
                                    + ", opt=" + optGlobalPath.size());
                        }
                        continue;
                    } else if (stagnationIts % stagnationThreshold == 0) {
                        evaporationRate = Math.min(evaporationRate + evaporationRateStep, maxEvaporationRate);
                    }
                }

                evaporatePheromone();

                if (improvedSprint) {
                    depositPheromone(optSprintPath, optSprintDepositMultiplier);
                } else {
                    List<Path> eligibleToDeposit = new ArrayList<>();
                    iterPaths.stream()
                            .filter(p -> p.size() >= (1 - depositTolerance) * optSprintPathSize)
                            .sorted(Comparator.comparingInt(Path::size).reversed())
                            .limit(depositPathsLimit)
                            .forEach(eligibleToDeposit::add);

                    if (eligibleToDeposit.isEmpty()) {
                        if (depositTolerance < maxDepositTolerance) {
                            depositTolerance += depositToleranceStep;
                        }
                        eligibleToDeposit.add(optGlobalPath);
                    }
                    for (Path p : eligibleToDeposit) {
                        double factor = (double) p.size() / optGlobalPath.size();
                        depositPheromone(p, factor);
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Task failed", ex.getCause());
        } finally {
            executor.shutdownNow();
        }

        if (outputEnabled) {
            System.out.println("[ProfACO] Final: " + optGlobalPath.size()
                    + ", foundAtIter=" + optIteration
                    + ", foundAtTime=" + optTime / 1_000_000 + "ms");
        }
        return optGlobalPath;
    }

    // --- PathKey: hashable wrapper for Path using vertex arrays ---
    private static class PathKey {
        private final int[] vertices;
        private final int hash;

        PathKey(Path path) {
            this.vertices = path.vertices().clone();
            this.hash = Arrays.hashCode(vertices);
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object o) {
            return o instanceof PathKey pk && Arrays.equals(vertices, pk.vertices);
        }
    }

    // avg degree / standard deviation: 1 = regular graph, 0 = highly irregular graph
    private double computeRegularity() {
        int n = graph.numVertices();
        if (n == 0) {
            return 1.0;
        }
        double avgDegree = 2.0 * graph.numEdges() / n;
        if (avgDegree == 0) {
            return 1.0;
        }
        double variance = 0.0;
        for (int v : graph.vertices()) {
            double diff = graph.degree(v) - avgDegree;
            variance += diff * diff;
        }
        variance /= n;
        double stdDev = Math.sqrt(variance);
        return Math.max(0.0, 1.0 - stdDev / avgDegree);
    }

    // --- Adjacency structures ---
    private void createAdjList() {
        int n = graph.numVertices();
        adjList = new int[n][];
        for (int v : graph.vertices()) {
            adjList[graph.indexOf(v)] = graph.neighbors(v);
        }
    }

    private void createAdjMatrix() {
        int n = graph.numVertices();
        long maxMemory = Runtime.getRuntime().maxMemory();
        double neededBytes = ((double) n * n) / 8.0;
        if (neededBytes * 3.0 > maxMemory) {
            adjMatrix = null;
            return;
        }
        adjMatrix = new boolean[n][n];
        for (int v : graph.vertices()) {
            int vi = graph.indexOf(v);
            for (var it = graph.neighborIterator(v); it.hasNext(); ) {
                int ui = graph.indexOf(it.next());
                adjMatrix[vi][ui] = true;
            }
        }
    }

    boolean containsEdge(int v, int u) {
        if (adjMatrix != null) {
            return adjMatrix[graph.indexOf(v)][graph.indexOf(u)];
        }
        return graph.containsEdge(v, u);
    }

    // --- Pheromone operations ---
    private void resetPheromone() {
        for (int v : graph.vertices()) {
            for (var it = graph.neighborIterator(v); it.hasNext(); ) {
                int u = it.next();
                setPheromone(v, u, minPheromoneLevel);
            }
        }
    }

    private double getPheromone(int v, int u) {
        return pheromoneMatrix[graph.indexOf(v)][graph.indexOf(u)];
    }

    private void setPheromone(int v, int u, double level) {
        if (level > maxPheromoneLevel) level = maxPheromoneLevel;
        else if (level < minPheromoneLevel) level = minPheromoneLevel;
        pheromoneMatrix[graph.indexOf(v)][graph.indexOf(u)] = level;
    }

    private void evaporatePheromone() {
        for (int v : graph.vertices()) {
            for (var it = graph.neighborIterator(v); it.hasNext(); ) {
                int u = it.next();
                setPheromone(v, u, (1 - evaporationRate) * getPheromone(v, u));
            }
        }
    }

    private void depositPheromone(Path path, double factor) {
        int[] vertices = path.vertices();
        int k = vertices.length;
        for (int i = 0; i < k - 1; i++) {
            int v = vertices[i], u = vertices[i + 1];
            setPheromone(v, u, getPheromone(v, u) + factor * (k - i - 1));
        }
        for (int i = k - 1; i > 0; i--) {
            int v = vertices[i], u = vertices[i - 1];
            setPheromone(v, u, getPheromone(v, u) + factor * (k - i - 1));
        }
    }

    // --- DFS Path Finder (inner class — runs as a Callable task) ---
    // Uses invalidCount array for O(1) feasibility checks instead of O(path_length) scan.
    private class DFSTask implements Callable<Path> {
        final int startVertex;
        private final SplittableRandom rng = new SplittableRandom();
        private final boolean[] visited;
        private final int[] invalidCount;
        private final int[] candidateBuf;
        private double localPheromoneInfluence;

        DFSTask(int startVertex) {
            this.startVertex = startVertex;
            int n = graph.numVertices();
            this.visited = new boolean[n];
            this.invalidCount = new int[n];
            this.candidateBuf = new int[n];
        }

        @Override
        public Path call() {
            localPheromoneInfluence = pheromoneInfluence;
            Path bestPath = dfs(startVertex);
            for (int i = 1; i < dfsCount; i++) {
                if (System.nanoTime() > deadlineNanos) break;
                Path path2 = dfs(bestPath.get(bestPath.size() - 1));
                if (path2.size() > bestPath.size()) {
                    bestPath = path2;
                } else {
                    localPheromoneInfluence = Math.max(minPheromoneInfluence,
                            localPheromoneInfluence - pheromoneInfluenceStep);
                }
            }
            return bestPath;
        }

        private Path dfs(int start) {
            int n = graph.numVertices();
            Path workPath = new Path(graph);
            Path bestPath = new Path(graph, 0);

            Arrays.fill(visited, false);
            Arrays.fill(invalidCount, 0);

            int si = graph.indexOf(start);
            visited[si] = true;
            workPath.add(start);
            updateInvalidCount(start, +1);
            int visitedCount = 1;

            boolean forward = true;
            while (workPath.size() > 0) {
                if (System.nanoTime() > deadlineNanos) break;

                int workPathSize = workPath.size();
                int maxPathSize = workPathSize + (n - visitedCount);
                if (maxPathSize < bestPath.size()
                        || maxPathSize < (1 - depositTolerance) * optGlobalPath.size()) {
                    break;
                }

                int v = workPath.get(workPathSize - 1);
                int vi = graph.indexOf(v);

                int cnt = 0;
                for (int u : adjList[vi]) {
                    int ui = graph.indexOf(u);
                    if (!visited[ui] && invalidCount[ui] == 1) {
                        candidateBuf[cnt++] = u;
                    }
                }

                if (cnt > 0) {
                    int u = selectCandidate(v, cnt);
                    int ui = graph.indexOf(u);
                    visited[ui] = true;
                    workPath.add(u);
                    updateInvalidCount(u, +1);
                    visitedCount++;
                    forward = true;
                } else {
                    if (forward) {
                        forward = false;
                        if (bestPath.size() < workPathSize) {
                            bestPath = copyPath(workPath);
                        }
                    }
                    int removed = workPath.get(workPathSize - 1);
                    workPath.removeFromPos(workPathSize - 1);
                    updateInvalidCount(removed, -1);
                }
            }
            return bestPath;
        }

        private void updateInvalidCount(int v, int delta) {
            int vi = graph.indexOf(v);
            invalidCount[vi] += delta;
            for (int u : adjList[vi]) {
                invalidCount[graph.indexOf(u)] += delta;
            }
        }

        private int selectCandidate(int v, int cnt) {
            if (cnt == 1) return candidateBuf[0];

            double[] weight = new double[cnt];
            double totalWeight = 0.0;

            for (int i = 0; i < cnt; i++) {
                int u = candidateBuf[i];
                weight[i] = Math.pow(getPheromone(v, u), localPheromoneInfluence);
                if (heuristicInfluence > 0) {
                    double heuristicValue = 1 + 1.0 / degrees[graph.indexOf(u)];
                    weight[i] *= Math.pow(heuristicValue, heuristicInfluence);
                }
                totalWeight += weight[i];
            }

            double prob = rng.nextDouble();
            double partial = 0.0;
            for (int i = 0; i < cnt; i++) {
                partial += weight[i];
                if (prob <= partial / totalWeight) {
                    return candidateBuf[i];
                }
            }
            return candidateBuf[cnt - 1];
        }

        private Path copyPath(Path src) {
            Path dst = new Path(graph);
            for (int i = 0; i < src.size(); i++) {
                dst.add(src.get(i));
            }
            return dst;
        }
    }
}
