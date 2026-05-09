package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.InducedPathAlgorithm;
import core.algorithms.inducedpath.exactalgorithm.LongestInducedPathExact;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.heuristics.LongestInducedPathHeuristic;
import core.algorithms.inducedpath.paperimplementation.ExactEnum;
import core.algorithms.inducedpath.paperimplementation.ExactEnumOptimized;
import core.algorithms.inducedpath.paperimplementation.HLIPP10000;
import core.io.GraphMLReader;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

/**
 * Runs all induced path algorithms on every graph instance and outputs results to console and CSV.
 * Configure which algorithms to run by commenting/uncommenting entries in the ALGORITHMS list.
 * Configure the timeout with TIMEOUT_MINUTES.
 * Algorithms that accept a shared Path (exact, paper implementations) will report the best
 * solution found so far even on timeout.
 *
 * @author Apetrei Razvan-Emanuel
 */
public class RunBenchmark {
    private static final String GRAPH_DIR = "src/main/resources/graph-instances/";
    private static final String OUTPUT_FILE = "src/main/output/results.csv";
    private static final int TIMEOUT_MINUTES = 10;
    private static final int THREAD_COUNT = 4;

    private record GraphInstance(String name, Graph graph) {
        int numVertices() { return graph.numVertices(); }
        int numEdges() { return graph.numEdges(); }
    }

    private record AlgorithmEntry(String name, BiFunction<Graph, Path, InducedPathAlgorithm> factory) {}

    private static final List<AlgorithmEntry> ALGORITHMS = List.of(
//            new AlgorithmEntry("LongestInducedPathExact", LongestInducedPathExact::new),
//            new AlgorithmEntry("ExactEnumOptimized", ExactEnumOptimized::new),
            // new AlgorithmEntry("ExactEnum", ExactEnum::new),
//            new AlgorithmEntry("HLIPP10000", HLIPP10000::new),
//            new AlgorithmEntry("LongestInducedPathHeuristic", (g, p) -> new LongestInducedPathHeuristic(g)),
            new AlgorithmEntry("LongestInducedPathGenetic", (graph, p) -> {
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                return new LongestInducedPathGenetic(config);
            })
    );

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get("src/main/output/"));

        List<GraphInstance> instances = new ArrayList<>();
        try (DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(Paths.get(GRAPH_DIR), "*.graphml")) {
            for (java.nio.file.Path file : stream) {
                String fileName = file.getFileName().toString().replace(".graphml", "");
                try {
                    Graph graph = new GraphMLReader().parseGraphMLFile(file).readGraph();
                    instances.add(new GraphInstance(fileName, graph));
                } catch (Exception e) {
                    System.out.println("Error reading " + fileName + ": " + e.getMessage());
                }
            }
        }
        instances.sort(Comparator.comparingInt(GraphInstance::numVertices));

        System.out.printf("Found %d graph files. Running %d algorithms with %d min timeout (%d threads).%n%n",
                instances.size(), ALGORITHMS.size(), TIMEOUT_MINUTES, THREAD_COUNT);

        System.out.printf("%-25s %5s %6s  %-30s %6s %10s%n", "Instance", "|V|", "|E|", "Algorithm", "LIP", "Time(s)");
        System.out.println("-".repeat(90));

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<String[]>> futures = new ArrayList<>();

        for (GraphInstance instance : instances) {
            for (AlgorithmEntry alg : ALGORITHMS) {
                futures.add(pool.submit(() -> runAlgorithm(instance, alg)));
            }
        }

        List<String[]> results = new ArrayList<>();
        for (Future<String[]> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();

        writeCSV(results);
    }

    private static final Object PRINT_LOCK = new Object();

    private static String[] runAlgorithm(GraphInstance instance, AlgorithmEntry alg) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Graph graph = instance.graph();
        Path sharedPath = new Path(graph);
        long start = System.currentTimeMillis();

        Future<Integer> future = executor.submit(() -> {
            InducedPathAlgorithm algorithm = alg.factory().apply(graph, sharedPath);
            Path path = algorithm.getLongestInducedPath();
            return path.size();
        });

        String name = instance.name();
        int numV = instance.numVertices();
        int numE = instance.numEdges();
        String[] row;

        try {
            int pathLength = future.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            row = new String[]{name, String.valueOf(numV), String.valueOf(numE), alg.name(), String.valueOf(pathLength), String.format("%.3f", elapsed)};
            synchronized (PRINT_LOCK) {
                System.out.printf("%-25s %5d %6d  %-30s %6d %10.3fs%n", name, numV, numE, alg.name(), pathLength, elapsed);
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            int bestSoFar = sharedPath.size();
            String pathStr = bestSoFar > 0 ? bestSoFar + "*" : "TIMEOUT";
            row = new String[]{name, String.valueOf(numV), String.valueOf(numE), alg.name(), pathStr, String.format("%.3f", elapsed)};
            synchronized (PRINT_LOCK) {
                System.out.printf("%-25s %5d %6d  %-30s %6s %10.3fs  (timeout, best so far)%n",
                        name, numV, numE, alg.name(), pathStr, elapsed);
            }
        } catch (Exception e) {
            future.cancel(true);
            row = new String[]{name, String.valueOf(numV), String.valueOf(numE), alg.name(), "ERROR", "0"};
            synchronized (PRINT_LOCK) {
                System.out.printf("%-25s %5d %6d  %-30s  ERROR: %s%n", name, numV, numE, alg.name(),
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            }
        } finally {
            executor.shutdownNow();
        }
        return row;
    }

    private static void writeCSV(List<String[]> results) throws IOException {
        java.nio.file.Path outputPath = Paths.get(OUTPUT_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("Instance,|V|,|E|,Algorithm,PathLength,Time(s)\n");
            for (String[] row : results) {
                writer.write(String.join(",", row) + "\n");
            }
        }
        System.out.println("Results written to " + OUTPUT_FILE);
    }
}
