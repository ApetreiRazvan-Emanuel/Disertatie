package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.ilp.LongestInducedPathILP;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Exp38: Iterated Local Search — GA + perturb-and-restart.
 * After GA converges, take best path, perturb (remove segment), seed new GA with it.
 * Repeat for multiple iterations. Tests if guided restarts beat single long runs.
 * Baseline: ILP(2min) + Roulette pop=2000 gives 275/256.2 on yeast.
 */
public class Exp38_IteratedLocalSearch {

    static boolean[] perturbSolution(boolean[] best, Graph graph, Random random, double perturbFraction) {
        int n = best.length;
        List<Integer> pathVertices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (best[i]) pathVertices.add(i);
        }
        if (pathVertices.size() <= 5) return Arrays.copyOf(best, n);

        // Find endpoints
        List<Integer> endpoints = new ArrayList<>();
        for (int v : pathVertices) {
            int deg = 0;
            for (int nb : graph.neighbors(v)) {
                if (best[nb]) deg++;
            }
            if (deg <= 1) endpoints.add(v);
        }
        if (endpoints.size() != 2) return Arrays.copyOf(best, n);

        // Walk the path from one endpoint to build ordered list
        List<Integer> orderedPath = new ArrayList<>();
        boolean[] visited = new boolean[n];
        int current = endpoints.get(0);
        while (current != -1) {
            orderedPath.add(current);
            visited[current] = true;
            int next = -1;
            for (int nb : graph.neighbors(current)) {
                if (best[nb] && !visited[nb]) {
                    next = nb;
                    break;
                }
            }
            current = next;
        }

        // Remove a segment from a random position (perturbFraction of the path)
        int removeCount = Math.max(3, (int) (orderedPath.size() * perturbFraction));
        int startIdx = random.nextInt(Math.max(1, orderedPath.size() - removeCount));

        boolean[] perturbed = Arrays.copyOf(best, n);
        for (int i = startIdx; i < Math.min(startIdx + removeCount, orderedPath.size()); i++) {
            perturbed[orderedPath.get(i)] = false;
        }

        return perturbed;
    }

    public static void main(String[] args) throws Exception {
        int runs = 5;
        int ilpTimeoutSeconds = 120;
        int popSize = 2000;
        int ilsIterations = 3;
        double perturbFraction = 0.3;

        String experimentId = "Exp38_ILS_Yeast";
        String description = "ILS: GA(pop=2000) + " + ilsIterations + " perturb-restart iterations on yeast";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = experimentId + "_" + timestamp;
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/" + fileName + ".csv");
        java.nio.file.Path summaryPath = Paths.get("src/main/output/experiments/" + fileName + "_summary.txt");
        Files.createDirectories(outputPath.getParent());

        Graph graph = ExperimentRunner.loadGraph("yeast");
        int V = graph.numVertices();
        long E = graph.numEdges();

        System.out.println("=== " + experimentId + " ===");
        System.out.println("Description: " + description);

        System.out.println("\n--- ILP phase for yeast ---");
        Path sharedPath = new Path(graph);
        ExecutorService ilpExecutor = Executors.newSingleThreadExecutor();
        Future<Path> ilpFuture = ilpExecutor.submit(() ->
                new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath());
        Path ilpPath;
        try {
            ilpPath = ilpFuture.get(ilpTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            ilpFuture.cancel(true);
            ilpPath = sharedPath;
        } catch (Exception e) {
            ilpFuture.cancel(true);
            ilpPath = sharedPath;
        } finally {
            ilpExecutor.shutdownNow();
        }
        int ilpSeed = ilpPath.size();
        System.out.printf("ILP seed: %d vertices%n", ilpSeed);

        boolean[] ilpSeedArray = new boolean[V];
        for (int v : ilpPath) {
            int idx = graph.indexOf(v);
            if (idx >= 0) ilpSeedArray[idx] = true;
        }

        Random random = new Random();

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath);
             BufferedWriter summary = Files.newBufferedWriter(summaryPath)) {

            csv.write("Instance,|V|,|E|,Run,ILPSeed,PopSize,ILSIters,PathLength,Time(s)\n");
            summary.write("Experiment: " + experimentId + "\n");
            summary.write("Description: " + description + "\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Runs: " + runs + "\n");
            summary.write("ILP timeout: " + ilpTimeoutSeconds + "s, PopSize: " + popSize + "\n");
            summary.write("ILS iterations: " + ilsIterations + ", Perturb fraction: " + perturbFraction + "\n\n");

            int best = 0, worst = Integer.MAX_VALUE, sum = 0;
            double totalTime = 0;
            List<Integer> lengths = new ArrayList<>();

            for (int r = 0; r < runs; r++) {
                long start = System.currentTimeMillis();

                // Initial GA run with ILP seed
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                config.setPopSize(popSize);
                config.setElitism(100);
                config.setInitialSeed(ilpSeedArray);

                LongestInducedPathGenetic ga = new LongestInducedPathGenetic(config);
                Path result = ga.getLongestInducedPath();
                int bestLen = result.size();

                // Convert path to boolean array
                boolean[] bestSolution = new boolean[V];
                for (int v : result) {
                    int idx = graph.indexOf(v);
                    if (idx >= 0) bestSolution[idx] = true;
                }

                System.out.printf("  run %d iter 0: %d (initial GA)%n", r + 1, bestLen);

                // ILS iterations: perturb best, re-run shorter GA
                for (int iter = 0; iter < ilsIterations; iter++) {
                    boolean[] perturbed = perturbSolution(bestSolution, graph, random, perturbFraction);

                    GeneticAlgorithmConfig ilsConfig = new GeneticAlgorithmConfig(graph);
                    ilsConfig.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                    ilsConfig.setPopSize(popSize);
                    ilsConfig.setElitism(100);
                    ilsConfig.setInitialSeed(perturbed);
                    ilsConfig.setMaxGeneration(200);
                    ilsConfig.setMaxGenerationIncrease(100);

                    LongestInducedPathGenetic ilsGA = new LongestInducedPathGenetic(ilsConfig);
                    Path ilsResult = ilsGA.getLongestInducedPath();
                    int ilsLen = ilsResult.size();

                    System.out.printf("  run %d iter %d: %d (perturbed restart)%n", r + 1, iter + 1, ilsLen);

                    if (ilsLen > bestLen) {
                        bestLen = ilsLen;
                        bestSolution = new boolean[V];
                        for (int v : ilsResult) {
                            int idx = graph.indexOf(v);
                            if (idx >= 0) bestSolution[idx] = true;
                        }
                    }
                }

                double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                best = Math.max(best, bestLen);
                worst = Math.min(worst, bestLen);
                sum += bestLen;
                lengths.add(bestLen);
                totalTime += elapsed;

                System.out.printf("  yeast run %d FINAL: %d in %.1fs%n", r + 1, bestLen, elapsed);
                csv.write(String.format("yeast,%d,%d,%d,%d,%d,%d,%d,%.3f%n",
                        V, E, r + 1, ilpSeed, popSize, ilsIterations, bestLen, elapsed));
                csv.flush();
            }

            double avg = (double) sum / lengths.size();
            double avgTime = totalTime / lengths.size();
            System.out.printf("yeast Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs%n", best, avg, worst, avgTime);
            summary.write(String.format("yeast |V|=%d |E|=%d  Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs ILPSeed=%d PopSize=%d ILSIters=%d Runs=%s%n",
                    V, E, best, avg, worst, avgTime, ilpSeed, popSize, ilsIterations, lengths));
        }

        System.out.println("\nResults saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
    }
}
