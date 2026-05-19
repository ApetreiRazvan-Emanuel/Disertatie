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
 * Exp42: Lower randomMutationRate (0.3 vs 0.5) with pop=2000 on yeast.
 * Baseline: pop=2000 rate=0.5 gives avg=251.6 (15 runs).
 * Test if less random mutation allows better refinement with roulette diversity.
 */
public class Exp42_LowMutationPop2000 {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        int ilpTimeoutSeconds = 120;
        int popSize = 2000;
        int elitism = 100;
        double randomMutationRate = 0.3;

        String experimentId = "Exp42_LowMutation_Pop2000_Yeast";
        String description = String.format("ILP(2min) + Roulette pop=%d elitism=%d randomMutationRate=%.1f on yeast", popSize, elitism, randomMutationRate);
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

            csv.write("Instance,|V|,|E|,Run,ILPSeed,PopSize,Elitism,MutRate,PathLength,Time(s)\n");
            summary.write("Experiment: " + experimentId + "\n");
            summary.write("Description: " + description + "\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Runs: " + runs + "\n");
            summary.write(String.format("ILP timeout: %ds, PopSize: %d, Elitism: %d, RandomMutationRate: %.1f%n%n",
                    ilpTimeoutSeconds, popSize, elitism, randomMutationRate));

            int best = 0, worst = Integer.MAX_VALUE, sum = 0;
            double totalTime = 0;
            List<Integer> lengths = new ArrayList<>();

            for (int r = 0; r < runs; r++) {
                long start = System.currentTimeMillis();
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                config.setPopSize(popSize);
                config.setElitism(elitism);
                config.setRandomMutationRate(randomMutationRate);
                config.setInitialSeed(seed);

                Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                int len = result.size();
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                best = Math.max(best, len);
                worst = Math.min(worst, len);
                sum += len;
                lengths.add(len);
                totalTime += elapsed;

                System.out.printf("  yeast run %d: %d in %.1fs (pop=%d, mutRate=%.1f)%n", r + 1, len, elapsed, popSize, randomMutationRate);
                csv.write(String.format("yeast,%d,%d,%d,%d,%d,%d,%.1f,%d,%.3f%n", V, E, r + 1, ilpSeed, popSize, elitism, randomMutationRate, len, elapsed));
                csv.flush();
            }

            double avg = (double) sum / lengths.size();
            double avgTime = totalTime / lengths.size();
            System.out.printf("yeast Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs%n", best, avg, worst, avgTime);
            summary.write(String.format("yeast |V|=%d |E|=%d  Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs ILPSeed=%d PopSize=%d Elitism=%d MutRate=%.1f Runs=%s%n",
                    V, E, best, avg, worst, avgTime, ilpSeed, popSize, elitism, randomMutationRate, lengths));
        }

        System.out.println("\nResults saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
    }
}
