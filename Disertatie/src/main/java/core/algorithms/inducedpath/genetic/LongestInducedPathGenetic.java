package core.algorithms.inducedpath.genetic;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.*;
import java.util.concurrent.*;

public class LongestInducedPathGenetic extends GraphAlgorithm implements InducedPathAlgorithm {

    private int popSize = 200;
    private int eliteCount = 10;
    private double mutationRate = 0.3;
    private double crossoverRate = 0.2;
    private double freshRate = 0.1;
    private long timeLimitMs = 120_000;
    private boolean useRoulette = false;
    private int numThreads = 0;
    private int dfsCount = 2;
    private int maxPaths = 10;
    private double crossoverRemoveRate = 0.5;
    private int maxTrimDivisor = 4;
    private int stagnationLimit = 500;
    private double restartRatio = 0.6;
    private int maxGenerations;
    private int improvementPatience;

    private final int n;
    private int[] globalBest = {};
    private final SplittableRandom rng = new SplittableRandom();
    private ExecutorService exec;

    public LongestInducedPathGenetic(Graph graph) {
        super(graph);
        this.n = graph.numVertices();
        this.maxGenerations = n * 2000;
        this.improvementPatience = n * 1000;
    }

    public LongestInducedPathGenetic(Graph graph, GeneticAlgorithmConfig cfg) {
        super(graph);
        this.n = graph.numVertices();
        this.popSize = cfg.popSize;
        this.eliteCount = Math.max(2, cfg.popSize / 20);
        this.mutationRate = cfg.mutationRate;
        this.crossoverRate = cfg.crossoverRate;
        this.freshRate = cfg.freshRate;
        this.useRoulette = cfg.useRoulette;
        this.dfsCount = cfg.dfsCount;
        this.maxPaths = cfg.maxPaths;
        this.crossoverRemoveRate = cfg.crossoverRemoveRate;
        this.maxTrimDivisor = cfg.maxTrimDivisor;
        this.stagnationLimit = cfg.stagnationLimit;
        this.restartRatio = cfg.restartRatio;
        this.maxGenerations = n * 2000;
        this.improvementPatience = n * 1000;
    }

    public void setTimeLimitMs(long ms) { this.timeLimitMs = ms; }
    public void setPopSize(int s) { this.popSize = s; this.eliteCount = Math.max(2, s / 20); }
    public void setMutationRate(double r) { this.mutationRate = r; }
    public void setCrossoverRate(double r) { this.crossoverRate = r; }
    public void setFreshRate(double r) { this.freshRate = r; }
    public void setUseRoulette(boolean b) { this.useRoulette = b; }
    public void setNumThreads(int t) { this.numThreads = t; }
    public void setDfsCount(int c) { this.dfsCount = c; }
    public void setMaxPaths(int m) { this.maxPaths = m; }
    public void setMaxGenerations(int g) { this.maxGenerations = g; }
    public void setConvergenceWindow(int w) { this.improvementPatience = w; }

    @Override
    public Path getLongestInducedPath() {
        int threads = numThreads > 0 ? numThreads : Runtime.getRuntime().availableProcessors();
        exec = Executors.newFixedThreadPool(threads);
        try {
            return run(threads);
        } finally {
            exec.shutdownNow();
        }
    }

    private Path run(int threads) {
        long t0 = System.nanoTime();

        List<int[]> pop = initPopulation();
        System.out.printf("[GA] n=%d m=%d pop=%d threads=%d init_best=%d%n",
                n, graph.numEdges(), popSize, threads, globalBest.length);

        int gen = 0, genSinceImprove = 0;
        int maxGen = maxGenerations;
        while ((timeLimitMs <= 0 || ms(t0) < timeLimitMs)
                && (maxGen <= 0 || gen < maxGen)) {
            gen++;
            genSinceImprove++;
            pop = select(pop);
            applyCrossover(pop);
            applyMutation(pop);
            injectFresh(pop);

            if (updateBest(pop)) {
                System.out.printf("[GA] Gen %d: best=%d (%.1fs)%n",
                        gen, globalBest.length, ms(t0) / 1000.0);
                genSinceImprove = 0;
                if (maxGen > 0) maxGen = Math.max(maxGen, gen + improvementPatience);
            }

            if (stagnationLimit > 0 && genSinceImprove >= stagnationLimit) {
                int keep = eliteCount;
                int replace = (int) ((popSize - keep) * restartRatio);
                List<Future<int[]>> futures = new ArrayList<>(replace);
                for (int i = 0; i < replace; i++) {
                    SplittableRandom r = rng.split();
                    futures.add(exec.submit(() -> biDFS(r)));
                }
                pop.sort((a, b) -> b.length - a.length);
                while (pop.size() > popSize - replace) pop.remove(pop.size() - 1);
                collect(futures, pop);
                genSinceImprove = 0;
                System.out.printf("[GA] Gen %d: RESTART injected %d fresh (%.1fs)%n",
                        gen, replace, ms(t0) / 1000.0);
            }
        }

        System.out.printf("[GA] Final: %d (%d gen, %.1fs)%n",
                globalBest.length, gen, ms(t0) / 1000.0);

        Path result = new Path(graph, globalBest.length);
        for (int v : globalBest) result.add(v);
        return result;
    }

    // ===== GA OPERATORS =====

    private List<int[]> initPopulation() {
        List<int[]> pop = new ArrayList<>(popSize);
        List<Future<int[]>> futures = new ArrayList<>(popSize);
        for (int i = 0; i < popSize; i++) {
            SplittableRandom r = rng.split();
            futures.add(exec.submit(() -> biDFS(r)));
        }
        collect(futures, pop);
        updateBest(pop);
        return pop;
    }

    private void applyCrossover(List<int[]> pop) {
        int sz = pop.size();
        for (int i = 0; i < sz - 1; i += 2) {
            if (rng.nextDouble() < crossoverRate) {
                SplittableRandom r = rng.split();
                int[] p1 = pop.get(i), p2 = pop.get(i + 1);
                pop.add(crossover(p1, p2, r));
            }
        }
    }

    private void applyMutation(List<int[]> pop) {
        List<Future<int[]>> futures = new ArrayList<>();
        for (int i = 0; i < pop.size(); i++) {
            if (rng.nextDouble() < mutationRate) {
                int[] path = pop.get(i);
                SplittableRandom r = rng.split();
                futures.add(exec.submit(() -> mutate(path, r)));
            }
        }
        collect(futures, pop);
    }

    private void injectFresh(List<int[]> pop) {
        int count = 0;
        for (int i = 0; i < popSize; i++)
            if (rng.nextDouble() < freshRate) count++;

        List<Future<int[]>> futures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SplittableRandom r = rng.split();
            futures.add(exec.submit(() -> biDFS(r)));
        }
        collect(futures, pop);
    }

    private List<int[]> select(List<int[]> pop) {
        pop.sort((a, b) -> b.length - a.length);
        List<int[]> next = new ArrayList<>(popSize);
        for (int i = 0; i < Math.min(eliteCount, pop.size()); i++)
            next.add(pop.get(i));

        if (useRoulette) {
            double total = 0;
            double[] fit = new double[pop.size()];
            for (int i = 0; i < pop.size(); i++) {
                fit[i] = pop.get(i).length;
                total += fit[i];
            }
            while (next.size() < popSize) {
                double r = rng.nextDouble() * total, cum = 0;
                for (int i = 0; i < pop.size(); i++) {
                    cum += fit[i];
                    if (r <= cum) { next.add(pop.get(i)); break; }
                }
            }
        } else {
            while (next.size() < popSize) {
                int a = rng.nextInt(pop.size()), b = rng.nextInt(pop.size());
                next.add(pop.get(a).length >= pop.get(b).length ? pop.get(a) : pop.get(b));
            }
        }
        return next;
    }

    // ===== CORE DFS =====

    private int[] coreDFS(int start, boolean[] visited, int[] adjCount, boolean[] filter, SplittableRandom r) {
        int[][] cands = new int[n][];
        int[] candN = new int[n];
        int[] work = new int[n];
        int wLen = 0;
        work[wLen++] = start;
        int[] best = {start};
        int paths = 0, lastImprov = 0;
        boolean fwd = true;

        while (wLen > 0 && paths - lastImprov <= maxPaths) {
            int v = work[wLen - 1];
            if (fwd) {
                int[] nbs = graph.neighbors(v);
                int[] buf = new int[nbs.length];
                int cnt = 0;
                for (int u : nbs)
                    if (!visited[u] && adjCount[u] == 1 && (filter == null || filter[u]))
                        buf[cnt++] = u;
                cands[wLen - 1] = buf;
                candN[wLen - 1] = cnt;
            }
            if (candN[wLen - 1] > 0) {
                int ci = candN[wLen - 1] == 1 ? 0 : r.nextInt(candN[wLen - 1]);
                int u = cands[wLen - 1][ci];
                cands[wLen - 1][ci] = cands[wLen - 1][--candN[wLen - 1]];
                work[wLen++] = u;
                visited[u] = true;
                for (int nb : graph.neighbors(u)) adjCount[nb]++;
                fwd = true;
            } else {
                if (fwd) {
                    paths++;
                    if (wLen > best.length) {
                        best = Arrays.copyOf(work, wLen);
                        lastImprov = paths;
                    }
                }
                int popped = work[--wLen];
                visited[popped] = false;
                for (int nb : graph.neighbors(popped)) adjCount[nb]--;
                fwd = false;
            }
        }
        return best;
    }

    private int[] freshDFS(int start, SplittableRandom r) {
        boolean[] vis = new boolean[n];
        int[] inv = new int[n];
        vis[start] = true;
        for (int nb : graph.neighbors(start)) inv[nb]++;
        return coreDFS(start, vis, inv, null, r);
    }

    private int[] biDFS(SplittableRandom r) {
        return biDFS(r.nextInt(n), r);
    }

    private int[] biDFS(int start, SplittableRandom r) {
        int[] p = freshDFS(start, r);
        for (int i = 1; i < dfsCount && p.length > 1; i++) {
            int[] p2 = freshDFS(p[p.length - 1], r);
            if (p2.length > p.length) p = p2;
        }
        return p;
    }

    // ===== EXTEND =====

    private int[] extend(int[] seed, SplittableRandom r) {
        if (seed.length == 0) return biDFS(r);

        boolean[] inPath = new boolean[n];
        int[] adjCount = new int[n];
        for (int v : seed) {
            inPath[v] = true;
            for (int nb : graph.neighbors(v)) adjCount[nb]++;
        }

        int[] fwdPath = coreDFS(seed[seed.length - 1], inPath.clone(), adjCount.clone(), null, r);
        int fwdLen = fwdPath.length - 1;

        for (int i = 1; i <= fwdLen; i++) {
            int v = fwdPath[i];
            inPath[v] = true;
            for (int nb : graph.neighbors(v)) adjCount[nb]++;
        }

        int[] bwdPath = coreDFS(seed[0], inPath, adjCount, null, r);
        int bwdLen = bwdPath.length - 1;

        int[] result = new int[bwdLen + seed.length + fwdLen];
        for (int i = 0; i < bwdLen; i++) result[bwdLen - 1 - i] = bwdPath[i + 1];
        System.arraycopy(seed, 0, result, bwdLen, seed.length);
        System.arraycopy(fwdPath, 1, result, bwdLen + seed.length, fwdLen);
        return result;
    }

    // ===== MUTATION =====

    private int[] mutate(int[] path, SplittableRandom r) {
        if (path.length <= 3) return biDFS(r);
        int maxTrim = Math.max(2, path.length / maxTrimDivisor);
        int trimL = r.nextInt(maxTrim);
        int trimR = r.nextInt(maxTrim);
        if (trimL + trimR >= path.length - 1) return biDFS(r);
        int[] middle = Arrays.copyOfRange(path, trimL, path.length - trimR);
        return extend(middle, r);
    }

    // ===== CROSSOVER =====

    private int[] crossover(int[] p1, int[] p2, SplittableRandom r) {
        boolean[] in1 = new boolean[n], in2 = new boolean[n];
        for (int v : p1) in1[v] = true;
        for (int v : p2) in2[v] = true;

        boolean[] union = new boolean[n];
        for (int v : p1) union[v] = true;
        for (int v : p2) union[v] = true;

        for (int v = 0; v < n; v++)
            if (in1[v] && in2[v] && r.nextDouble() < crossoverRemoveRate) union[v] = false;

        int start = union[p1[0]] ? p1[0] : (union[p2[0]] ? p2[0] : r.nextInt(n));
        boolean[] vis = new boolean[n];
        int[] inv = new int[n];
        vis[start] = true;
        for (int nb : graph.neighbors(start)) inv[nb]++;

        int[] subPath = coreDFS(start, vis, inv, union, r);
        return extend(subPath, r);
    }

    // ===== HELPERS =====

    private boolean updateBest(List<int[]> pop) {
        boolean improved = false;
        for (int[] p : pop)
            if (p.length > globalBest.length) { globalBest = p.clone(); improved = true; }
        return improved;
    }

    private void collect(List<Future<int[]>> futures, List<int[]> dest) {
        for (Future<int[]> f : futures) {
            try { dest.add(f.get()); } catch (Exception ignored) {}
        }
    }

    private long ms(long t0) { return (System.nanoTime() - t0) / 1_000_000; }
}
