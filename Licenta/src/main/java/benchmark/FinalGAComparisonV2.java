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
 * Optimized GA comparison: caches ILP seeds, runs yeast-focused.
 * For usair/494bus/662bus the ILP already finds optimal, so GA can't improve.
 * The real test is yeast where GA has room to explore.
 *
 * Runs 5 configs x 5 runs on yeast, plus verification runs on smaller instances.
 */
public class FinalGAComparisonV2 {

    static final int RUNS = 5;

    record GAConfig(String name, GeneticAlgorithmConfig.SelectionMode selection,
                    int popSize, int elitism, double mutRate, boolean useILP, int tournamentSize) {}

    public static void main(String[] args) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/FinalGAComparisonV2_" + timestamp + ".csv");
        Files.createDirectories(outputPath.getParent());

        GAConfig[] configs = {
            new GAConfig("ILP+Roulette_mut0.5", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.5, true, 2),
            new GAConfig("ILP+Roulette_mut0.3", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.3, true, 2),
            new GAConfig("ILP+T3_mut0.5", GeneticAlgorithmConfig.SelectionMode.TOURNAMENT, 1000, 50, 0.5, true, 3),
            new GAConfig("Roulette_mut0.5", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.5, false, 2),
            new GAConfig("T3_mut0.5", GeneticAlgorithmConfig.SelectionMode.TOURNAMENT, 1000, 50, 0.5, false, 3),
        };

        // Pre-compute ILP seeds once
        Map<String, boolean[]> ilpSeeds = new HashMap<>();
        Map<String, Integer> ilpSeedLengths = new HashMap<>();
        Map<String, Double> ilpTimes = new HashMap<>();

        String[] instances = {"usair", "494bus", "662bus", "yeast"};
        int[] ilpTimeouts = {120, 120, 300, 120};

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath)) {
            csv.write("Config,Instance,|V|,|E|,Run,ILPSeed,PopSize,Elitism,MutRate,Selection,PathLength,TotalTime(s),GATime(s)\n");
            csv.flush();

            System.out.println("=== Phase 1: Computing ILP seeds (one-time) ===");
            for (int i = 0; i < instances.length; i++) {
                String inst = instances[i];
                int timeout = ilpTimeouts[i];
                Graph graph = ExperimentRunner.loadGraph(inst);

                System.out.printf("  ILP for %s (%ds)...", inst, timeout);
                long start = System.currentTimeMillis();
                Path sharedPath = new Path(graph);
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<Path> future = executor.submit(() ->
                        new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath());
                Path ilpPath;
                try {
                    ilpPath = future.get(timeout, TimeUnit.SECONDS);
                } catch (Exception e) {
                    future.cancel(true);
                    ilpPath = sharedPath;
                } finally {
                    executor.shutdownNow();
                }
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                boolean[] seed = new boolean[graph.numVertices()];
                for (int v : ilpPath) {
                    int idx = graph.indexOf(v);
                    if (idx >= 0) seed[idx] = true;
                }
                ilpSeeds.put(inst, seed);
                ilpSeedLengths.put(inst, ilpPath.size());
                ilpTimes.put(inst, elapsed);
                System.out.printf(" seed=%d in %.1fs%n", ilpPath.size(), elapsed);
            }

            System.out.println("\n=== Phase 2: GA runs ===");
            for (GAConfig gc : configs) {
                System.out.println("\n--- Config: " + gc.name + " ---");

                for (int i = 0; i < instances.length; i++) {
                    String inst = instances[i];
                    Graph graph = ExperimentRunner.loadGraph(inst);
                    int V = graph.numVertices();
                    long E = graph.numEdges();

                    boolean[] seed = gc.useILP ? ilpSeeds.get(inst) : null;
                    int seedLen = gc.useILP ? ilpSeedLengths.get(inst) : 0;
                    double ilpTime = gc.useILP ? ilpTimes.get(inst) : 0;

                    List<Integer> lengths = new ArrayList<>();
                    for (int r = 0; r < RUNS; r++) {
                        long start = System.currentTimeMillis();
                        GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                        config.setSelectionMode(gc.selection);
                        config.setPopSize(gc.popSize);
                        config.setElitism(gc.elitism);
                        config.setRandomMutationRate(gc.mutRate);
                        if (gc.selection == GeneticAlgorithmConfig.SelectionMode.TOURNAMENT) {
                            config.setTournamentSize(gc.tournamentSize);
                        }
                        if (seed != null) {
                            config.setInitialSeed(seed);
                        }

                        Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                        int len = result.size();
                        double gaElapsed = (System.currentTimeMillis() - start) / 1000.0;
                        double totalElapsed = gaElapsed + ilpTime;
                        lengths.add(len);

                        System.out.printf("  %s run %d: %d in %.1fs(GA)/%.1fs(total)%n",
                                inst, r + 1, len, gaElapsed, totalElapsed);
                        csv.write(String.format("%s,%s,%d,%d,%d,%d,%d,%d,%.1f,%s,%d,%.3f,%.3f%n",
                                gc.name, inst, V, E, r + 1, seedLen, gc.popSize, gc.elitism,
                                gc.mutRate, gc.selection, len, totalElapsed, gaElapsed));
                        csv.flush();
                    }

                    int best = lengths.stream().max(Integer::compare).orElse(0);
                    int worst = lengths.stream().min(Integer::compare).orElse(0);
                    double avg = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
                    System.out.printf("  => %s: Best=%d Avg=%.1f Worst=%d%n", inst, best, avg, worst);
                }
            }
        }

        System.out.println("\nResults saved to: " + outputPath);
    }
}
