package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.aco.LongestInducedPathACO;
import core.algorithms.inducedpath.aco.LongestInducedPathACOv3;
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

public class FullVerification {
    static final List<String> ALL_GRAPHS = List.of(
            "karate", "sawmill", "high-tech", "mexican", "attiro",
            "romeo and juliet", "prison", "sanjuansur", "dolphins",
            "chesapeake", "krebs", "sfi", "tailorS1", "tailorS2",
            "die hard", "ieeebus", "jean", "huck", "david", "anna",
            "usair", "494bus", "662bus", "yeast");

    static final int GA_RUNS = 3;
    static final int ACO_RUNS = 3;
    static final int ILP_TIMEOUT_SEC = 120;

    public static void main(String[] args) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get("src/main/output/FullVerification_" + timestamp + ".csv");
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath)) {
            csv.write("Instance,|V|,|E|,Algorithm,Run,PathLength,Time(s)\n");

            for (String name : ALL_GRAPHS) {
                Graph graph;
                try {
                    graph = ExperimentRunner.loadGraph(name);
                } catch (Exception e) {
                    System.out.printf("SKIP %s: %s%n", name, e.getMessage());
                    continue;
                }
                int V = graph.numVertices();
                long E = graph.numEdges();
                double avgDeg = 2.0 * E / V;
                System.out.printf("%n=== %s |V|=%d |E|=%d avgDeg=%.1f ===%n", name, V, E, avgDeg);

                // ILP (1 run, 2-min timeout)
                runILP(graph, name, V, E, csv);

                // GA best config: ILP+Roulette, pop scaled by graph size
                runGA(graph, name, V, E, csv);

                // ACO v1 + v3 (1 run each for speed, 3 runs on key instances)
                int acoRuns = (V > 300) ? 1 : ACO_RUNS;
                runACO(graph, name, V, E, acoRuns, csv);

                csv.flush();
            }
        }
        System.out.println("\nAll results saved to: " + outputPath);
    }

    static void runILP(Graph graph, String name, int V, long E, BufferedWriter csv) throws Exception {
        System.out.print("  ILP...");
        Path sharedPath = new Path(graph);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        long start = System.currentTimeMillis();
        Future<Path> future = executor.submit(() ->
                new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath());
        Path ilpPath;
        try {
            ilpPath = future.get(ILP_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            ilpPath = sharedPath;
        } finally {
            executor.shutdownNow();
        }
        double elapsed = (System.currentTimeMillis() - start) / 1000.0;
        int len = ilpPath.size();
        System.out.printf(" %d in %.1fs%n", len, elapsed);
        csv.write(String.format("%s,%d,%d,ILP,1,%d,%.3f%n", name, V, E, len, elapsed));
    }

    static void runGA(Graph graph, String name, int V, long E, BufferedWriter csv) throws Exception {
        int popSize = V <= 100 ? Math.min(V, 500) : (V <= 500 ? 1000 : 2000);
        int elitism = popSize / 20;
        if (popSize >= 2000) elitism = 100;

        // Get ILP seed for GA
        Path sharedPath = new Path(graph);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Path> future = executor.submit(() ->
                new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath());
        Path ilpPath;
        try {
            ilpPath = future.get(ILP_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            ilpPath = sharedPath;
        } finally {
            executor.shutdownNow();
        }

        boolean[] seed = null;
        if (ilpPath.size() > 1) {
            seed = new boolean[V];
            for (int v : ilpPath) {
                int idx = graph.indexOf(v);
                if (idx >= 0) seed[idx] = true;
            }
        }

        for (int r = 0; r < GA_RUNS; r++) {
            System.out.printf("  GA run %d...", r + 1);
            long start = System.currentTimeMillis();

            GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
            config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
            config.setPopSize(popSize);
            config.setElitism(elitism);
            config.setRandomMutationRate(0.5);
            if (seed != null) config.setInitialSeed(seed);

            ExecutorService gaExec = Executors.newSingleThreadExecutor();
            Future<Path> gaFuture = gaExec.submit(() ->
                    new LongestInducedPathGenetic(config).getLongestInducedPath());
            int len;
            try {
                Path result = gaFuture.get(15, TimeUnit.MINUTES);
                len = result.size();
            } catch (Exception e) {
                gaFuture.cancel(true);
                len = -1;
            } finally {
                gaExec.shutdownNow();
            }
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            System.out.printf(" %d in %.1fs%n", len, elapsed);
            csv.write(String.format("%s,%d,%d,GA,%d,%d,%.3f%n", name, V, E, r + 1, len, elapsed));
            csv.flush();
        }
    }

    static void runACO(Graph graph, String name, int V, long E, int runs, BufferedWriter csv) throws Exception {
        // ACO v1
        for (int r = 0; r < runs; r++) {
            System.out.printf("  ACOv1 run %d...", r + 1);
            long start = System.currentTimeMillis();
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<Path> future = exec.submit(() ->
                    new LongestInducedPathACO(graph).getLongestInducedPath());
            int len;
            try {
                Path result = future.get(15, TimeUnit.MINUTES);
                len = result.size();
            } catch (Exception e) {
                future.cancel(true);
                len = -1;
            } finally {
                exec.shutdownNow();
            }
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            System.out.printf(" %d in %.1fs%n", len, elapsed);
            csv.write(String.format("%s,%d,%d,ACOv1,%d,%d,%.3f%n", name, V, E, r + 1, len, elapsed));
            csv.flush();
        }

        // ACO v3
        for (int r = 0; r < runs; r++) {
            System.out.printf("  ACOv3 run %d...", r + 1);
            long start = System.currentTimeMillis();
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<Path> future = exec.submit(() ->
                    new LongestInducedPathACOv3(graph).getLongestInducedPath());
            int len;
            try {
                Path result = future.get(15, TimeUnit.MINUTES);
                len = result.size();
            } catch (Exception e) {
                future.cancel(true);
                len = -1;
            } finally {
                exec.shutdownNow();
            }
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            System.out.printf(" %d in %.1fs%n", len, elapsed);
            csv.write(String.format("%s,%d,%d,ACOv3,%d,%d,%.3f%n", name, V, E, r + 1, len, elapsed));
            csv.flush();
        }
    }
}
