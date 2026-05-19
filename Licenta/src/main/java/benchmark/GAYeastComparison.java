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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Focused GA comparison on yeast only — the critical differentiator.
 * Tests all 5 configs × 5 runs each with shared ILP seed.
 */
public class GAYeastComparison {

    static final int RUNS = 5;

    record GAConfig(String name, GeneticAlgorithmConfig.SelectionMode selection,
                    int popSize, int elitism, double mutRate, boolean useILP, int tournamentSize) {}

    public static void main(String[] args) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/GAYeastComparison_" + timestamp + ".csv");
        Files.createDirectories(outputPath.getParent());

        Graph graph = ExperimentRunner.loadGraph("yeast");
        int V = graph.numVertices();
        long E = graph.numEdges();
        System.out.printf("yeast |V|=%d |E|=%d%n", V, E);

        // ILP phase once (2 min)
        System.out.print("ILP phase (120s)...");
        Path sharedPath = new Path(graph);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Path> future = executor.submit(() ->
                new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath());
        Path ilpPath;
        long ilpStart = System.currentTimeMillis();
        try {
            ilpPath = future.get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            ilpPath = sharedPath;
        } finally {
            executor.shutdownNow();
        }
        double ilpTime = (System.currentTimeMillis() - ilpStart) / 1000.0;
        int ilpSeed = ilpPath.size();
        System.out.printf(" seed=%d in %.1fs%n", ilpSeed, ilpTime);

        boolean[] seed = new boolean[V];
        for (int v : ilpPath) {
            int idx = graph.indexOf(v);
            if (idx >= 0) seed[idx] = true;
        }

        GAConfig[] configs = {
            new GAConfig("ILP+Roulette_mut0.5", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.5, true, 2),
            new GAConfig("ILP+Roulette_mut0.3", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.3, true, 2),
            new GAConfig("ILP+T3_mut0.5", GeneticAlgorithmConfig.SelectionMode.TOURNAMENT, 1000, 50, 0.5, true, 3),
            new GAConfig("Roulette_mut0.5", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.5, false, 2),
            new GAConfig("T3_mut0.5", GeneticAlgorithmConfig.SelectionMode.TOURNAMENT, 1000, 50, 0.5, false, 3),
        };

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath)) {
            csv.write("Config,Instance,|V|,|E|,Run,ILPSeed,PopSize,Elitism,MutRate,Selection,PathLength,TotalTime(s),GATime(s)\n");

            for (GAConfig gc : configs) {
                System.out.printf("%n--- %s ---%n", gc.name);
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
                    if (gc.useILP) {
                        config.setInitialSeed(seed);
                    }

                    Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                    int len = result.size();
                    double gaElapsed = (System.currentTimeMillis() - start) / 1000.0;
                    double totalElapsed = gaElapsed + (gc.useILP ? ilpTime : 0);
                    lengths.add(len);

                    System.out.printf("  run %d: %d in %.1fs(GA)/%.1fs(total)%n", r + 1, len, gaElapsed, totalElapsed);
                    csv.write(String.format("%s,yeast,%d,%d,%d,%d,%d,%d,%.1f,%s,%d,%.3f,%.3f%n",
                            gc.name, V, E, r + 1, gc.useILP ? ilpSeed : 0, gc.popSize, gc.elitism,
                            gc.mutRate, gc.selection, len, totalElapsed, gaElapsed));
                    csv.flush();
                }

                int best = lengths.stream().max(Integer::compare).orElse(0);
                int worst = lengths.stream().min(Integer::compare).orElse(0);
                double avg = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
                System.out.printf("  => Best=%d Avg=%.1f Worst=%d%n", best, avg, worst);
            }
        }
        System.out.println("\nResults saved to: " + outputPath);
    }
}
