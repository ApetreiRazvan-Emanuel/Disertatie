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
 * Exp41: Island Model GA on yeast.
 * 4 islands x pop=500 (total 2000), each runs 100 gen, then migrate top 5 between islands.
 * 5 migration cycles = 500 total generations. Compare with single pop=2000 (avg=251.6).
 */
public class Exp41_IslandModel {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        int ilpTimeoutSeconds = 120;
        int numIslands = 4;
        int islandPopSize = 500;
        int islandElitism = 25;
        int migrationSize = 5;
        int genPerCycle = 100;
        int migrationCycles = 5;

        String experimentId = "Exp41_IslandModel_Yeast";
        String description = String.format("ILP(2min) + Island Model: %d islands x pop=%d, migrate top %d every %d gen, %d cycles on yeast",
                numIslands, islandPopSize, migrationSize, genPerCycle, migrationCycles);
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

            csv.write("Instance,|V|,|E|,Run,ILPSeed,Islands,IslandPop,MigrationCycles,PathLength,Time(s)\n");
            summary.write("Experiment: " + experimentId + "\n");
            summary.write("Description: " + description + "\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Runs: " + runs + "\n");
            summary.write(String.format("ILP timeout: %ds, Islands: %d, IslandPop: %d, Elitism: %d, MigrationSize: %d, GenPerCycle: %d, Cycles: %d%n%n",
                    ilpTimeoutSeconds, numIslands, islandPopSize, islandElitism, migrationSize, genPerCycle, migrationCycles));

            int best = 0, worst = Integer.MAX_VALUE, sum = 0;
            double totalTime = 0;
            List<Integer> lengths = new ArrayList<>();

            for (int r = 0; r < runs; r++) {
                long start = System.currentTimeMillis();
                int result = runIslandModel(graph, seed, numIslands, islandPopSize, islandElitism,
                        migrationSize, genPerCycle, migrationCycles);
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                best = Math.max(best, result);
                worst = Math.min(worst, result);
                sum += result;
                lengths.add(result);
                totalTime += elapsed;

                System.out.printf("  yeast run %d: %d in %.1fs (islands=%d, pop=%d)%n",
                        r + 1, result, elapsed, numIslands, islandPopSize);
                csv.write(String.format("yeast,%d,%d,%d,%d,%d,%d,%d,%d,%.3f%n",
                        V, E, r + 1, ilpSeed, numIslands, islandPopSize, migrationCycles, result, elapsed));
                csv.flush();
            }

            double avg = (double) sum / lengths.size();
            double avgTime = totalTime / lengths.size();
            System.out.printf("yeast Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs%n", best, avg, worst, avgTime);
            summary.write(String.format("yeast |V|=%d |E|=%d  Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs ILPSeed=%d Islands=%d IslandPop=%d Runs=%s%n",
                    V, E, best, avg, worst, avgTime, ilpSeed, numIslands, islandPopSize, lengths));
        }

        System.out.println("\nResults saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
    }

    private static int runIslandModel(Graph graph, boolean[] seed, int numIslands,
                                       int islandPopSize, int islandElitism, int migrationSize,
                                       int genPerCycle, int migrationCycles) {
        int numVertices = graph.numVertices();
        int globalBest = 0;

        // Each island runs independently with limited generations per cycle
        // After each cycle, top migrants are injected as seeds into other islands
        boolean[][] islandSeeds = new boolean[numIslands][];
        for (int i = 0; i < numIslands; i++) {
            islandSeeds[i] = seed.clone();
        }

        for (int cycle = 0; cycle < migrationCycles; cycle++) {
            int[][] islandResults = new int[numIslands][];
            int[] islandBestLengths = new int[numIslands];

            // Run each island
            for (int i = 0; i < numIslands; i++) {
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                config.setPopSize(islandPopSize);
                config.setElitism(islandElitism);
                config.setInitialSeed(islandSeeds[i]);
                config.setMaxGeneration(genPerCycle);
                config.setDynamicMaxGeneration(false);

                LongestInducedPathGenetic ga = new LongestInducedPathGenetic(config);
                Path path = ga.getLongestInducedPath();
                int len = path.size();
                islandBestLengths[i] = len;
                globalBest = Math.max(globalBest, len);

                // Extract best individual as boolean array for migration
                boolean[] bestIndividual = new boolean[numVertices];
                for (int v : path) {
                    int idx = graph.indexOf(v);
                    if (idx >= 0) bestIndividual[idx] = true;
                }
                islandResults[i] = new int[numVertices];
                for (int j = 0; j < numVertices; j++) {
                    islandResults[i][j] = bestIndividual[j] ? 1 : 0;
                }
            }

            // Migration: each island receives the best solution from the next island (ring topology)
            if (cycle < migrationCycles - 1) {
                for (int i = 0; i < numIslands; i++) {
                    int donor = (i + 1) % numIslands;
                    boolean[] migrantSeed = new boolean[numVertices];
                    for (int j = 0; j < numVertices; j++) {
                        migrantSeed[j] = islandResults[donor][j] == 1;
                    }
                    islandSeeds[i] = migrantSeed;
                }
            }

            System.out.printf("    Cycle %d: islands=[%d,%d,%d,%d] globalBest=%d%n",
                    cycle + 1, islandBestLengths[0], islandBestLengths[1],
                    islandBestLengths[2], islandBestLengths[3], globalBest);
        }

        return globalBest;
    }
}
