package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.aco.LongestInducedPathACO;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
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
import java.util.function.Function;

public class ExperimentRunner {
    private static final String GRAPH_DIR = "src/main/resources/graph-instances/";
    private static final String EXPERIMENT_DIR = "src/main/output/experiments/";
    private static final int TIMEOUT_MINUTES = 15;

    private static final List<String> KEY_INSTANCES = List.of(
            "usair", "494bus", "662bus", "yeast");

    private static final List<String> SMALL_INSTANCES = List.of(
            "tailorS2", "attiro", "sanjuansur", "ieeebus", "sfi");

    public record RunResult(String instance, int vertices, long edges,
                            int pathLength, double timeSeconds) {}

    public static Graph loadGraph(String name) throws Exception {
        java.nio.file.Path file = Paths.get(GRAPH_DIR + name + ".graphml");
        return new GraphMLReader().parseGraphMLFile(file).readGraph();
    }

    public static List<RunResult> runExperiment(
            String experimentId,
            String description,
            List<String> instances,
            int runs,
            Function<Graph, Path> algorithmFactory) throws Exception {

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = experimentId + "_" + timestamp;
        java.nio.file.Path outputPath = Paths.get(EXPERIMENT_DIR + fileName + ".csv");
        java.nio.file.Path summaryPath = Paths.get(EXPERIMENT_DIR + fileName + "_summary.txt");
        Files.createDirectories(outputPath.getParent());

        List<RunResult> allResults = new ArrayList<>();

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath);
             BufferedWriter summary = Files.newBufferedWriter(summaryPath)) {

            csv.write("Instance,|V|,|E|,Run,PathLength,Time(s)\n");

            summary.write("Experiment: " + experimentId + "\n");
            summary.write("Description: " + description + "\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Runs per instance: " + runs + "\n");
            summary.write("Timeout: " + TIMEOUT_MINUTES + " min\n\n");

            System.out.println("=== " + experimentId + " ===");
            System.out.println("Description: " + description);
            System.out.println("Runs per instance: " + runs);
            System.out.printf("%-15s %5s %6s  %6s %6s %6s %10s%n",
                    "Instance", "|V|", "|E|", "Best", "Avg", "Worst", "AvgTime");
            System.out.println("-".repeat(75));

            for (String name : instances) {
                Graph graph = loadGraph(name);
                int V = graph.numVertices();
                long E = graph.numEdges();

                int best = 0, worst = Integer.MAX_VALUE, sum = 0;
                double totalTime = 0;
                List<Integer> lengths = new ArrayList<>();

                for (int r = 0; r < runs; r++) {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    long start = System.currentTimeMillis();

                    Future<Integer> future = executor.submit(() -> {
                        Path path = algorithmFactory.apply(graph);
                        return path.size();
                    });

                    int len;
                    double elapsed;
                    try {
                        len = future.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
                        elapsed = (System.currentTimeMillis() - start) / 1000.0;
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        elapsed = (System.currentTimeMillis() - start) / 1000.0;
                        len = -1;
                        System.out.printf("  %s run %d: TIMEOUT (%.1fs)%n", name, r + 1, elapsed);
                    } finally {
                        executor.shutdownNow();
                    }

                    if (len > 0) {
                        best = Math.max(best, len);
                        worst = Math.min(worst, len);
                        sum += len;
                        lengths.add(len);
                        totalTime += elapsed;
                        System.out.printf("  %s run %d: %d in %.1fs%n", name, r + 1, len, elapsed);
                    }

                    csv.write(String.format("%s,%d,%d,%d,%s,%.3f%n",
                            name, V, E, r + 1, len > 0 ? String.valueOf(len) : "TIMEOUT", elapsed));
                    csv.flush();

                    allResults.add(new RunResult(name, V, E, len, elapsed));
                }

                if (!lengths.isEmpty()) {
                    double avg = (double) sum / lengths.size();
                    double avgTime = totalTime / lengths.size();
                    System.out.printf("%-15s %5d %6d  %6d %6.1f %6d %10.1fs%n",
                            name, V, E, best, avg, worst, avgTime);

                    summary.write(String.format("%-15s |V|=%d |E|=%d  Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs Runs=%s%n",
                            name, V, E, best, avg, worst, avgTime, lengths));
                } else {
                    System.out.printf("%-15s %5d %6d  ALL TIMEOUT%n", name, V, E);
                    summary.write(String.format("%-15s |V|=%d |E|=%d  ALL TIMEOUT%n", name, V, E));
                }
                summary.flush();
                System.out.println();
            }

            summary.write("\nFiles: " + outputPath.getFileName() + "\n");
        }

        System.out.println("Results saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
        return allResults;
    }

    public static void appendToIndex(String experimentId, String description, String resultSummary) throws IOException {
        java.nio.file.Path indexPath = Paths.get(EXPERIMENT_DIR + "EXPERIMENT_INDEX.md");
        boolean exists = Files.exists(indexPath);
        try (BufferedWriter writer = Files.newBufferedWriter(indexPath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (!exists) {
                writer.write("# Experiment Index\n\n");
                writer.write("| # | Date | ID | Description | Key Results |\n");
                writer.write("|---|------|-----|-------------|-------------|\n");
            }
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            writer.write(String.format("| - | %s | %s | %s | %s |%n", date, experimentId, description, resultSummary));
        }
    }
}
