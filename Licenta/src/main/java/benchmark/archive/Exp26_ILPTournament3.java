package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.ilp.LongestInducedPathILP;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.util.List;
import java.util.concurrent.*;

/**
 * Exp26: ILP-seeded GA with tournament size 3.
 * Tournament-3 achieved best pure GA yeast=266. ILP+Roulette achieved best hybrid yeast=258.
 * This tests if ILP + higher selection pressure (T3) can beat both.
 */
public class Exp26_ILPTournament3 {
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

        System.out.println("=== Exp26: ILP-Seeded Tournament-3 GA ===");
        System.out.println("ILP timeout: " + ILP_TIMEOUT_SECONDS + "s per instance\n");

        for (String name : instances) {
            Graph graph = ExperimentRunner.loadGraph(name);
            System.out.println("Computing ILP seed for " + name + "...");
            boolean[] seed = runILPWithTimeout(graph);

            ExperimentRunner.runExperiment(
                    "Exp26_ILP_T3_" + name,
                    "ILP(" + ILP_TIMEOUT_SECONDS + "s) + Tournament-3 GA on " + name,
                    List.of(name), runs,
                    g -> {
                        GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(g);
                        config.setTournamentSize(3);
                        if (seed != null) {
                            config.setInitialSeed(seed);
                        }
                        return new LongestInducedPathGenetic(config).getLongestInducedPath();
                    }
            );
        }
    }
}
