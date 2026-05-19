package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.ilp.LongestInducedPathILP;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Runs a single GA config on yeast with 5 runs.
 * Pass config index (0-4) as first arg, or defaults to 0.
 * Appends results to shared CSV.
 */
public class GAYeastSingleConfig {

    static final int RUNS = 5;

    record GAConfig(String name, GeneticAlgorithmConfig.SelectionMode selection,
                    int popSize, int elitism, double mutRate, boolean useILP, int tournamentSize) {}

    static final GAConfig[] CONFIGS = {
        new GAConfig("ILP+Roulette_mut0.5", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.5, true, 2),
        new GAConfig("ILP+Roulette_mut0.3", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.3, true, 2),
        new GAConfig("ILP+T3_mut0.5", GeneticAlgorithmConfig.SelectionMode.TOURNAMENT, 1000, 50, 0.5, true, 3),
        new GAConfig("Roulette_mut0.5", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.5, false, 2),
        new GAConfig("T3_mut0.5", GeneticAlgorithmConfig.SelectionMode.TOURNAMENT, 1000, 50, 0.5, false, 3),
    };

    public static void main(String[] args) throws Exception {
        int configIdx = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        GAConfig gc = CONFIGS[configIdx];

        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/GAYeastAllConfigs.csv");
        Files.createDirectories(outputPath.getParent());

        Graph graph = ExperimentRunner.loadGraph("yeast");
        int V = graph.numVertices();
        long E = graph.numEdges();
        System.out.printf("yeast |V|=%d |E|=%d, Config: %s%n", V, E, gc.name);

        boolean[] seed = null;
        int ilpSeed = 0;
        double ilpTime = 0;

        if (gc.useILP) {
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
            ilpTime = (System.currentTimeMillis() - ilpStart) / 1000.0;
            ilpSeed = ilpPath.size();
            System.out.printf(" seed=%d in %.1fs%n", ilpSeed, ilpTime);

            seed = new boolean[V];
            for (int v : ilpPath) {
                int idx = graph.indexOf(v);
                if (idx >= 0) seed[idx] = true;
            }
        }

        boolean fileExists = Files.exists(outputPath) && Files.size(outputPath) > 0;
        try (BufferedWriter csv = Files.newBufferedWriter(outputPath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (!fileExists) {
                csv.write("Config,Instance,|V|,|E|,Run,ILPSeed,PopSize,Elitism,MutRate,Selection,PathLength,TotalTime(s),GATime(s)\n");
            }

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

                System.out.printf("  run %d: %d in %.1fs(GA)/%.1fs(total)%n", r + 1, len, gaElapsed, totalElapsed);
                csv.write(String.format("%s,yeast,%d,%d,%d,%d,%d,%d,%.1f,%s,%d,%.3f,%.3f%n",
                        gc.name, V, E, r + 1, ilpSeed, gc.popSize, gc.elitism,
                        gc.mutRate, gc.selection, len, totalElapsed, gaElapsed));
                csv.flush();
            }

            int best = lengths.stream().max(Integer::compare).orElse(0);
            int worst = lengths.stream().min(Integer::compare).orElse(0);
            double avg = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
            System.out.printf("  => Best=%d Avg=%.1f Worst=%d%n", best, avg, worst);
        }
    }
}
