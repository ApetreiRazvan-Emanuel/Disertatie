package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.ilp.LongestInducedPathILP;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.util.List;
import java.util.concurrent.*;

/**
 * Exp21: ILP-seeded GA hybrid. Run ILP for 2 minutes to find a good initial solution,
 * then seed the GA population with it and let the GA evolve from there.
 */
public class Exp21_ILPSeededGA {
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
        int runs = 5;
        List<String> instances = List.of("usair", "494bus", "662bus", "yeast");

        System.out.println("=== Exp21: ILP-Seeded GA ===");
        System.out.println("ILP timeout: " + ILP_TIMEOUT_SECONDS + "s per instance");
        System.out.println("Then GA with Exp10 config seeded with ILP solution\n");

        for (String name : instances) {
            Graph graph = ExperimentRunner.loadGraph(name);
            System.out.println("Computing ILP seed for " + name + "...");
            boolean[] seed = runILPWithTimeout(graph);

            ExperimentRunner.runExperiment(
                    "Exp21_ILP_GA_" + name,
                    "ILP(" + ILP_TIMEOUT_SECONDS + "s) + GA hybrid on " + name,
                    List.of(name), runs,
                    g -> {
                        GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(g);
                        if (seed != null) {
                            config.setInitialSeed(seed);
                        }
                        return new LongestInducedPathGenetic(config).getLongestInducedPath();
                    }
            );
        }

        ExperimentRunner.appendToIndex("Exp21", "ILP(2min)+GA hybrid (5 runs each)",
                "Pending — check summary files");
    }
}
