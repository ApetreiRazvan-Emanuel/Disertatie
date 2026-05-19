package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Exp33b: Continue Exp33 — T3 + pop=2000 on yeast, runs 3-5.
 * Runs 1-2 done: [250, 247].
 */
public class Exp33b_LargePopT3Continue {
    public static void main(String[] args) throws Exception {
        int runs = 3;

        String experimentId = "Exp33b_T3_Pop2000_Yeast";
        String description = "Tournament-3 popSize=2000 on yeast (continuation runs 3-5)";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = experimentId + "_" + timestamp;
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/" + fileName + ".csv");
        java.nio.file.Path summaryPath = Paths.get("src/main/output/experiments/" + fileName + "_summary.txt");
        Files.createDirectories(outputPath.getParent());

        Graph graph = ExperimentRunner.loadGraph("yeast");
        int V = graph.numVertices();
        long E = graph.numEdges();

        System.out.println("=== " + experimentId + " ===");
        System.out.println("Description: " + description);

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath);
             BufferedWriter summary = Files.newBufferedWriter(summaryPath)) {

            csv.write("Instance,|V|,|E|,Run,PopSize,PathLength,Time(s)\n");
            summary.write("Experiment: " + experimentId + "\n");
            summary.write("Description: " + description + "\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Runs: " + runs + " (continuation from Exp33 runs 1-2: [250, 247])\n");
            summary.write("PopSize: 2000, Tournament size: 3\n\n");

            int best = 0, worst = Integer.MAX_VALUE, sum = 0;
            double totalTime = 0;
            List<Integer> lengths = new ArrayList<>();

            for (int r = 0; r < runs; r++) {
                long start = System.currentTimeMillis();
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                config.setTournamentSize(3);
                config.setPopSize(2000);
                config.setElitism(100);

                Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                int len = result.size();
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                best = Math.max(best, len);
                worst = Math.min(worst, len);
                sum += len;
                lengths.add(len);
                totalTime += elapsed;

                System.out.printf("  yeast run %d: %d in %.1fs (T3, pop=2000)%n", r + 3, len, elapsed);
                csv.write(String.format("yeast,%d,%d,%d,2000,%d,%.3f%n", V, E, r + 3, len, elapsed));
                csv.flush();
            }

            double avg = (double) sum / lengths.size();
            double avgTime = totalTime / lengths.size();
            System.out.printf("yeast (runs 3-5) Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs%n", best, avg, worst, avgTime);
            summary.write(String.format("yeast (runs 3-5) Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs Runs=%s%n",
                    best, avg, worst, avgTime, lengths));
            summary.write(String.format("Combined (runs 1-5): [250, 247] + %s%n", lengths));
        }

        System.out.println("\nResults saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
    }
}
