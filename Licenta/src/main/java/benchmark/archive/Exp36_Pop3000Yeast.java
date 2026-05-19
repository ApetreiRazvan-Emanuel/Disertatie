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
 * Exp36: ILP(2min) + Roulette pop=3000 on yeast.
 * Pop=2000 gave 275/256.2. Test if pop=3000 pushes further.
 */
public class Exp36_Pop3000Yeast {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        int ilpTimeoutSeconds = 120;
        int popSize = 3000;

        String experimentId = "Exp36_Pop3000_Yeast";
        String description = "ILP(2min) + Roulette popSize=3000 on yeast";
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

        System.out.println("\n--- ILP phase for yeast ---");
        Path sharedPath = new Path(graph);
        ExecutorService ilpExecutor = Executors.newSingleThreadExecutor();
        Future<Path> ilpFuture = ilpExecutor.submit(() ->
                new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath());
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
        System.out.printf("ILP seed: %d vertices%n", ilpSeed);

        boolean[] seed = new boolean[graph.numVertices()];
        for (int v : ilpPath) {
            int idx = graph.indexOf(v);
            if (idx >= 0) seed[idx] = true;
        }

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath);
             BufferedWriter summary = Files.newBufferedWriter(summaryPath)) {

            csv.write("Instance,|V|,|E|,Run,ILPSeed,PopSize,PathLength,Time(s)\n");
            summary.write("Experiment: " + experimentId + "\n");
            summary.write("Description: " + description + "\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Runs: " + runs + "\n");
            summary.write("ILP timeout: " + ilpTimeoutSeconds + "s, PopSize: " + popSize + "\n\n");

            int best = 0, worst = Integer.MAX_VALUE, sum = 0;
            double totalTime = 0;
            List<Integer> lengths = new ArrayList<>();

            for (int r = 0; r < runs; r++) {
                long start = System.currentTimeMillis();
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                config.setPopSize(popSize);
                config.setElitism(150);
                config.setInitialSeed(seed);

                Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                int len = result.size();
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                best = Math.max(best, len);
                worst = Math.min(worst, len);
                sum += len;
                lengths.add(len);
                totalTime += elapsed;

                System.out.printf("  yeast run %d: %d in %.1fs (pop=%d, seed=%d)%n", r + 1, len, elapsed, popSize, ilpSeed);
                csv.write(String.format("yeast,%d,%d,%d,%d,%d,%d,%.3f%n", V, E, r + 1, ilpSeed, popSize, len, elapsed));
                csv.flush();
            }

            double avg = (double) sum / lengths.size();
            double avgTime = totalTime / lengths.size();
            System.out.printf("yeast Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs%n", best, avg, worst, avgTime);
            summary.write(String.format("yeast |V|=%d |E|=%d  Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs ILPSeed=%d PopSize=%d Runs=%s%n",
                    V, E, best, avg, worst, avgTime, ilpSeed, popSize, lengths));
        }

        System.out.println("\nResults saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
    }
}
