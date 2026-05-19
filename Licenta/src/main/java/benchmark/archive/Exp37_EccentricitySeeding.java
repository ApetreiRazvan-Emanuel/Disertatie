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
import java.util.*;
import java.util.concurrent.*;

/**
 * Exp37: Eccentricity-based seeding + ILP(2min) + Roulette pop=2000 on yeast.
 * Marzo & Ribeiro (2021) found high-eccentricity vertices are good path endpoints.
 * Seed initial population with paths starting from top eccentricity vertices.
 */
public class Exp37_EccentricitySeeding {

    static int[] computeTopEccentricityVertices(Graph graph, int topK) {
        int n = graph.numVertices();
        int[] eccentricity = new int[n];

        for (int src = 0; src < n; src++) {
            int[] dist = new int[n];
            Arrays.fill(dist, -1);
            dist[src] = 0;
            Queue<Integer> queue = new LinkedList<>();
            queue.add(src);
            int maxDist = 0;
            while (!queue.isEmpty()) {
                int u = queue.poll();
                for (int nb : graph.neighbors(u)) {
                    if (dist[nb] == -1) {
                        dist[nb] = dist[u] + 1;
                        maxDist = Math.max(maxDist, dist[nb]);
                        queue.add(nb);
                    }
                }
            }
            eccentricity[src] = maxDist;
        }

        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> eccentricity[b] - eccentricity[a]);

        int[] result = new int[Math.min(topK, n)];
        for (int i = 0; i < result.length; i++) {
            result[i] = indices[i];
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        int runs = 5;
        int ilpTimeoutSeconds = 120;
        int popSize = 2000;
        int topK = 200;

        String experimentId = "Exp37_EccentricitySeeding_Yeast";
        String description = "Eccentricity seeding + ILP(2min) + Roulette pop=2000 on yeast (top " + topK + " vertices)";
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

        System.out.println("\n--- Computing eccentricities for yeast ---");
        long eccStart = System.currentTimeMillis();
        int[] topVertices = computeTopEccentricityVertices(graph, topK);
        double eccTime = (System.currentTimeMillis() - eccStart) / 1000.0;
        System.out.printf("Eccentricity computation: %.1fs, top vertex eccentricity range used%n", eccTime);

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

            csv.write("Instance,|V|,|E|,Run,ILPSeed,PopSize,TopK,PathLength,Time(s)\n");
            summary.write("Experiment: " + experimentId + "\n");
            summary.write("Description: " + description + "\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Runs: " + runs + "\n");
            summary.write("ILP timeout: " + ilpTimeoutSeconds + "s, PopSize: " + popSize + ", TopK: " + topK + "\n");
            summary.write("Eccentricity computation time: " + eccTime + "s\n\n");

            int best = 0, worst = Integer.MAX_VALUE, sum = 0;
            double totalTime = 0;
            List<Integer> lengths = new ArrayList<>();

            for (int r = 0; r < runs; r++) {
                long start = System.currentTimeMillis();
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                config.setPopSize(popSize);
                config.setElitism(100);
                config.setInitialSeed(seed);
                config.setPreferredStartVertices(topVertices);

                Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                int len = result.size();
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                best = Math.max(best, len);
                worst = Math.min(worst, len);
                sum += len;
                lengths.add(len);
                totalTime += elapsed;

                System.out.printf("  yeast run %d: %d in %.1fs (pop=%d, seed=%d, topK=%d)%n",
                        r + 1, len, elapsed, popSize, ilpSeed, topK);
                csv.write(String.format("yeast,%d,%d,%d,%d,%d,%d,%d,%.3f%n",
                        V, E, r + 1, ilpSeed, popSize, topK, len, elapsed));
                csv.flush();
            }

            double avg = (double) sum / lengths.size();
            double avgTime = totalTime / lengths.size();
            System.out.printf("yeast Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs%n", best, avg, worst, avgTime);
            summary.write(String.format("yeast |V|=%d |E|=%d  Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs ILPSeed=%d PopSize=%d TopK=%d Runs=%s%n",
                    V, E, best, avg, worst, avgTime, ilpSeed, popSize, topK, lengths));
        }

        System.out.println("\nResults saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
    }
}
