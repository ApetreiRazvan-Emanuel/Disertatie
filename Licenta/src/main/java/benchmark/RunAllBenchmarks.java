package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.InducedPathAlgorithm;
import core.algorithms.inducedpath.aco.LongestInducedPathACO;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.heuristics.LongestInducedPathHeuristic;
import core.algorithms.inducedpath.ilp.LongestInducedPathILP;
import core.io.GraphMLReader;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

public class RunAllBenchmarks {
    private static final String GRAPH_DIR = "src/main/resources/graph-instances/";
    private static final String OUTPUT_DIR = "src/main/output/benchmark-results/";
    private static final int TIMEOUT_MINUTES = 15;
    private static final int THREAD_COUNT = 8;

    private static final Map<String, Integer> PAPER_TARGETS = new LinkedHashMap<>();
    static {
        PAPER_TARGETS.put("high-tech", 13);
        PAPER_TARGETS.put("karate", 9);
        PAPER_TARGETS.put("mexican", 16);
        PAPER_TARGETS.put("sawmill", 18);
        PAPER_TARGETS.put("chesapeake", 16);
        PAPER_TARGETS.put("tailorS1", 13);
        PAPER_TARGETS.put("tailorS2", 15);
        PAPER_TARGETS.put("romeo and juliet", 9);
        PAPER_TARGETS.put("die hard", 10);
        PAPER_TARGETS.put("attiro", 31);
        PAPER_TARGETS.put("dolphins", 24);
        PAPER_TARGETS.put("krebs", 17);
        PAPER_TARGETS.put("prison", 36);
        PAPER_TARGETS.put("huck", 9);
        PAPER_TARGETS.put("sanjuansur", 38);
        PAPER_TARGETS.put("jean", 11);
        PAPER_TARGETS.put("david", 19);
        PAPER_TARGETS.put("ieeebus", 47);
        PAPER_TARGETS.put("sfi", 13);
        PAPER_TARGETS.put("anna", 20);
        PAPER_TARGETS.put("usair", 46);
        PAPER_TARGETS.put("494bus", 142);
        PAPER_TARGETS.put("662bus", -1);
        PAPER_TARGETS.put("yeast", -1);
    }

    private record GraphInstance(String name, Graph graph) {
        int numVertices() { return graph.numVertices(); }
        long numEdges() { return graph.numEdges(); }
    }

    private record AlgorithmEntry(String name, BiFunction<Graph, Path, InducedPathAlgorithm> factory) {}

    private static final List<AlgorithmEntry> ALGORITHMS = List.of(
            new AlgorithmEntry("Genetic", (graph, p) -> {
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                return new LongestInducedPathGenetic(config, p);
            }),
            new AlgorithmEntry("ACO", LongestInducedPathACO::new),
            new AlgorithmEntry("ILP", LongestInducedPathILP::new),
            new AlgorithmEntry("Heuristic", (g, p) -> new LongestInducedPathHeuristic(g))
    );

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get(OUTPUT_DIR));

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

        System.out.printf("=== Benchmark Run: %s ===%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.printf("Instances: %d | Algorithms: %d | Timeout: %d min | Threads: %d%n%n",
                instances.size(), ALGORITHMS.size(), TIMEOUT_MINUTES, THREAD_COUNT);

        System.out.printf("%-20s %5s %6s  %-15s %6s %6s %10s%n", "Instance", "|V|", "|E|", "Algorithm", "LIP", "Target", "Time(s)");
        System.out.println("-".repeat(85));

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

        System.out.println("\n" + "=".repeat(85));
        printSummary(results);
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
        long numE = instance.numEdges();
        Integer target = PAPER_TARGETS.getOrDefault(name, -1);
        String targetStr = target > 0 ? String.valueOf(target) : "?";
        String[] row;

        try {
            int pathLength = future.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            String status = target > 0 ? (pathLength >= target ? "OK" : "LOW") : "";
            row = new String[]{name, String.valueOf(numV), String.valueOf(numE), alg.name(),
                    String.valueOf(pathLength), targetStr, String.format("%.3f", elapsed), status};
            synchronized (PRINT_LOCK) {
                System.out.printf("%-20s %5d %6d  %-15s %6d %6s %10.3fs  %s%n",
                        name, numV, numE, alg.name(), pathLength, targetStr, elapsed, status);
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            int bestSoFar = sharedPath.size();
            String pathStr = bestSoFar > 0 ? bestSoFar + "*" : "TIMEOUT";
            String status = target > 0 && bestSoFar > 0 ? (bestSoFar >= target ? "OK" : "LOW") : "";
            row = new String[]{name, String.valueOf(numV), String.valueOf(numE), alg.name(),
                    pathStr, targetStr, String.format("%.3f", elapsed), status};
            synchronized (PRINT_LOCK) {
                System.out.printf("%-20s %5d %6d  %-15s %6s %6s %10.3fs  %s (timeout)%n",
                        name, numV, numE, alg.name(), pathStr, targetStr, elapsed, status);
            }
        } catch (Exception e) {
            future.cancel(true);
            row = new String[]{name, String.valueOf(numV), String.valueOf(numE), alg.name(),
                    "ERROR", targetStr, "0", ""};
            synchronized (PRINT_LOCK) {
                System.out.printf("%-20s %5d %6d  %-15s  ERROR: %s%n", name, numV, numE, alg.name(),
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            }
        } finally {
            executor.shutdownNow();
        }
        return row;
    }

    private static void printSummary(List<String[]> results) {
        System.out.println("\n=== SUMMARY ===");
        Map<String, int[]> algStats = new LinkedHashMap<>();
        for (AlgorithmEntry alg : ALGORITHMS) {
            algStats.put(alg.name(), new int[]{0, 0, 0});
        }

        for (String[] row : results) {
            String algName = row[3];
            String status = row.length > 7 ? row[7] : "";
            int[] stats = algStats.get(algName);
            if (stats == null) continue;
            stats[0]++;
            if ("OK".equals(status)) stats[1]++;
            if ("LOW".equals(status)) stats[2]++;
        }

        System.out.printf("%-15s %8s %8s %8s%n", "Algorithm", "Total", ">=Target", "<Target");
        for (var entry : algStats.entrySet()) {
            int[] s = entry.getValue();
            System.out.printf("%-15s %8d %8d %8d%n", entry.getKey(), s[0], s[1], s[2]);
        }
    }

    private static void writeCSV(List<String[]> results) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get(OUTPUT_DIR + "benchmark_" + timestamp + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("Instance,|V|,|E|,Algorithm,PathLength,Target,Time(s),Status\n");
            for (String[] row : results) {
                writer.write(String.join(",", row) + "\n");
            }
        }
        System.out.println("\nResults written to " + outputPath);
    }
}
