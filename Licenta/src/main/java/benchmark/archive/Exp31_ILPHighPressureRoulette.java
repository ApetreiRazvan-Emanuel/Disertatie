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
 * Exp31: ILP(2min) + Roulette pressure=3 on yeast and 662bus.
 * Tests if higher roulette pressure improves ILP-seeded GA.
 * Baseline: ILP+Roulette(p=2) gives yeast 258/253.6, 662bus 301/301.
 */
public class Exp31_ILPHighPressureRoulette {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        int ilpTimeoutSeconds = 120;
        List<String> instances = List.of("662bus", "yeast");

        String experimentId = "Exp31_ILP_Roulette_P3";
        String description = "ILP(2min) + Roulette pressure=3";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = experimentId + "_" + timestamp;
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/" + fileName + ".csv");
        java.nio.file.Path summaryPath = Paths.get("src/main/output/experiments/" + fileName + "_summary.txt");
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath);
             BufferedWriter summary = Files.newBufferedWriter(summaryPath)) {

            csv.write("Instance,|V|,|E|,Run,ILPSeed,PathLength,Time(s)\n");
            summary.write("Experiment: " + experimentId + "\n");
            summary.write("Description: " + description + "\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Runs per instance: " + runs + "\n");
            summary.write("ILP timeout: " + ilpTimeoutSeconds + "s\n\n");

            System.out.println("=== " + experimentId + " ===");
            System.out.println("Description: " + description);

            for (String name : instances) {
                Graph graph = ExperimentRunner.loadGraph(name);
                int V = graph.numVertices();
                long E = graph.numEdges();

                System.out.println("\n--- ILP phase for " + name + " ---");
                long ilpStart = System.currentTimeMillis();
                Path sharedPath = new Path(graph);
                ExecutorService ilpExecutor = Executors.newSingleThreadExecutor();
                Future<Path> ilpFuture = ilpExecutor.submit(() -> {
                    return new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath();
                });
                Path ilpPath;
                try {
                    ilpPath = ilpFuture.get(ilpTimeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    ilpFuture.cancel(true);
                    ilpPath = sharedPath;
                } catch (Exception e) {
                    ilpFuture.cancel(true);
                    ilpPath = sharedPath;
                } finally {
                    ilpExecutor.shutdownNow();
                }
                int ilpSeed = ilpPath.size();
                double ilpTime = (System.currentTimeMillis() - ilpStart) / 1000.0;
                System.out.printf("ILP seed for %s: %d vertices in %.1fs%n", name, ilpSeed, ilpTime);

                boolean[] seed = new boolean[graph.numVertices()];
                for (int v : ilpPath) {
                    int idx = graph.indexOf(v);
                    if (idx >= 0) seed[idx] = true;
                }

                int best = 0, worst = Integer.MAX_VALUE, sum = 0;
                double totalTime = 0;
                List<Integer> lengths = new ArrayList<>();

                for (int r = 0; r < runs; r++) {
                    long start = System.currentTimeMillis();
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                    config.setSelectionPressure(3);
                    config.setInitialSeed(seed);

                    Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                    int len = result.size();
                    double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                    best = Math.max(best, len);
                    worst = Math.min(worst, len);
                    sum += len;
                    lengths.add(len);
                    totalTime += elapsed;

                    System.out.printf("  %s run %d: %d in %.1fs (ILP seed=%d)%n", name, r + 1, len, elapsed, ilpSeed);
                    csv.write(String.format("%s,%d,%d,%d,%d,%d,%.3f%n", name, V, E, r + 1, ilpSeed, len, elapsed));
                    csv.flush();
                }

                double avg = (double) sum / lengths.size();
                double avgTime = totalTime / lengths.size();
                System.out.printf("%-15s Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs Seed=%d%n",
                        name, best, avg, worst, avgTime, ilpSeed);
                summary.write(String.format("%-15s |V|=%d |E|=%d  Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs ILPSeed=%d Runs=%s%n",
                        name, V, E, best, avg, worst, avgTime, ilpSeed, lengths));
                summary.flush();
            }
        }

        System.out.println("\nResults saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
    }
}
