package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.ilp.LongestInducedPathILP;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.util.List;
import java.util.concurrent.*;

/**
 * Exp23b: Continuation of Exp23 — 662bus runs 3-5 and yeast 5 runs.
 */
public class Exp23b_ILPRouletteContinue {
    private static final int ILP_TIMEOUT_SECONDS = 120;

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
        System.out.println("=== Exp23b: ILP+Roulette continuation (662bus r3-5, yeast) ===\n");

        // 662bus: 3 more runs
        {
            String name = "662bus";
            Graph graph = ExperimentRunner.loadGraph(name);
            System.out.println("Computing ILP seed for " + name + "...");
            boolean[] seed = runILPWithTimeout(graph);

            ExperimentRunner.runExperiment(
                    "Exp23b_ILP_Roulette_" + name,
                    "ILP(" + ILP_TIMEOUT_SECONDS + "s) + Roulette GA on " + name + " (continuation runs 3-5)",
                    List.of(name), 3,
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

        // yeast: 5 runs
        {
            String name = "yeast";
            Graph graph = ExperimentRunner.loadGraph(name);
            System.out.println("Computing ILP seed for " + name + "...");
            boolean[] seed = runILPWithTimeout(graph);

            ExperimentRunner.runExperiment(
                    "Exp23b_ILP_Roulette_" + name,
                    "ILP(" + ILP_TIMEOUT_SECONDS + "s) + Roulette GA on " + name,
                    List.of(name), 5,
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
