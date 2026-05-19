package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.ilp.LongestInducedPathILP;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.util.List;
import java.util.concurrent.*;

/**
 * Exp27: Longer ILP timeout (5 min) + Roulette GA on yeast only.
 * The 2-minute ILP only finds 4 vertices on yeast — trying 5 minutes to get a better seed.
 * Also tests 662bus with 5-min ILP to see if it can beat 301.
 */
public class Exp27_LongerILPYeast {
    private static final int ILP_TIMEOUT_SECONDS = 300;

    private static boolean[] runILPWithTimeout(Graph graph) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Path sharedPath = new Path(graph);

        Future<Path> future = executor.submit(() -> {
            LongestInducedPathILP ilp = new LongestInducedPathILP(graph, sharedPath);
            return ilp.getLongestInducedPath();
        });

        Path ilpPath;
        try {
            ilpPath = future.get(ILP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            ilpPath = sharedPath;
        } catch (Exception e) {
            future.cancel(true);
            ilpPath = sharedPath;
        } finally {
            executor.shutdownNow();
        }

        if (ilpPath == null || ilpPath.isEmpty()) return null;

        boolean[] seed = new boolean[graph.numVertices()];
        for (int v : ilpPath) {
            int idx = graph.indexOf(v);
            if (idx >= 0) seed[idx] = true;
        }

        int count = 0;
        for (boolean b : seed) if (b) count++;
        System.out.printf("  ILP seed: %d vertices in %ds%n", count, ILP_TIMEOUT_SECONDS);

        return seed;
    }

    public static void main(String[] args) throws Exception {
        int runs = 5;

        System.out.println("=== Exp27: Longer ILP(5min) + Roulette GA ===");
        System.out.println("ILP timeout: " + ILP_TIMEOUT_SECONDS + "s\n");

        // 662bus with 5-min ILP — can it beat 301?
        {
            String name = "662bus";
            Graph graph = ExperimentRunner.loadGraph(name);
            System.out.println("Computing ILP seed for " + name + " (5 min)...");
            boolean[] seed = runILPWithTimeout(graph);

            ExperimentRunner.runExperiment(
                    "Exp27_ILP5min_Roulette_" + name,
                    "ILP(" + ILP_TIMEOUT_SECONDS + "s) + Roulette GA on " + name,
                    List.of(name), runs,
                    g -> {
                        GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(g);
                        config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                        if (seed != null) {
                            config.setInitialSeed(seed);
                        }
                        return new LongestInducedPathGenetic(config).getLongestInducedPath();
                    }
            );
        }

        // yeast with 5-min ILP
        {
            String name = "yeast";
            Graph graph = ExperimentRunner.loadGraph(name);
            System.out.println("Computing ILP seed for " + name + " (5 min)...");
            boolean[] seed = runILPWithTimeout(graph);

            ExperimentRunner.runExperiment(
                    "Exp27_ILP5min_Roulette_" + name,
                    "ILP(" + ILP_TIMEOUT_SECONDS + "s) + Roulette GA on " + name,
                    List.of(name), runs,
                    g -> {
                        GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(g);
                        config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                        if (seed != null) {
                            config.setInitialSeed(seed);
                        }
                        return new LongestInducedPathGenetic(config).getLongestInducedPath();
                    }
            );
        }
    }
}
