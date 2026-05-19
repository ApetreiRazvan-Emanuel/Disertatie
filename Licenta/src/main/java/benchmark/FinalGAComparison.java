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
 * Final GA comparison: 5 configs x 4 instances x 5 runs each.
 * All runs must complete in <10 min (600s) per run.
 */
public class FinalGAComparison {

    static final String[] INSTANCES = {"usair", "494bus", "662bus", "yeast"};
    static final int RUNS = 5;

    record GAConfig(String name, GeneticAlgorithmConfig.SelectionMode selection,
                    int popSize, int elitism, double mutRate, int ilpTimeout, int tournamentSize) {}

    public static void main(String[] args) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/FinalGAComparison_" + timestamp + ".csv");
        java.nio.file.Path summaryPath = Paths.get("src/main/output/experiments/FinalGAComparison_" + timestamp + "_summary.txt");
        Files.createDirectories(outputPath.getParent());

        GAConfig[] configs = {
            new GAConfig("ILP+Roulette_mut0.5", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.5, 120, 2),
            new GAConfig("ILP+Roulette_mut0.3", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.3, 120, 2),
            new GAConfig("ILP+T3_mut0.5", GeneticAlgorithmConfig.SelectionMode.TOURNAMENT, 1000, 50, 0.5, 120, 3),
            new GAConfig("Roulette_mut0.5", GeneticAlgorithmConfig.SelectionMode.ROULETTE, 1000, 50, 0.5, 0, 2),
            new GAConfig("T3_mut0.5", GeneticAlgorithmConfig.SelectionMode.TOURNAMENT, 1000, 50, 0.5, 0, 3),
        };

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath);
             BufferedWriter summary = Files.newBufferedWriter(summaryPath)) {

            csv.write("Config,Instance,|V|,|E|,Run,ILPSeed,PopSize,Elitism,MutRate,Selection,PathLength,TotalTime(s),GATime(s)\n");
            summary.write("Final GA Comparison\n");
            summary.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            summary.write("Configs: " + configs.length + ", Instances: " + INSTANCES.length + ", Runs: " + RUNS + "\n\n");

            for (GAConfig gc : configs) {
                System.out.println("\n========== Config: " + gc.name + " ==========");
                summary.write("=== " + gc.name + " ===\n");

                for (String instanceName : INSTANCES) {
                    Graph graph = ExperimentRunner.loadGraph(instanceName);
                    int V = graph.numVertices();
                    long E = graph.numEdges();

                    // ILP phase
                    boolean[] seed = null;
                    int ilpSeed = 0;
                    int ilpTime = gc.ilpTimeout;

                    // For 662bus, use 5-min ILP to get optimal 304 seed
                    if (instanceName.equals("662bus") && gc.ilpTimeout > 0) {
                        ilpTime = 300;
                    }

                    if (ilpTime > 0) {
                        System.out.printf("  ILP phase for %s (%ds)...", instanceName, ilpTime);
                        Path sharedPath = new Path(graph);
                        ExecutorService ilpExecutor = Executors.newSingleThreadExecutor();
                        Future<Path> ilpFuture = ilpExecutor.submit(() ->
                                new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath());
                        Path ilpPath;
                        try {
                            ilpPath = ilpFuture.get(ilpTime, TimeUnit.SECONDS);
                        } catch (TimeoutException e) {
                            ilpFuture.cancel(true);
                            ilpPath = sharedPath;
                        } catch (Exception e) {
                            ilpFuture.cancel(true);
                            ilpPath = sharedPath;
                        } finally {
                            ilpExecutor.shutdownNow();
                        }
                        ilpSeed = ilpPath.size();
                        System.out.printf(" seed=%d%n", ilpSeed);

                        seed = new boolean[graph.numVertices()];
                        for (int v : ilpPath) {
                            int idx = graph.indexOf(v);
                            if (idx >= 0) seed[idx] = true;
                        }
                    } else {
                        System.out.printf("  No ILP for %s%n", instanceName);
                    }

                    int best = 0, worst = Integer.MAX_VALUE, sum = 0;
                    double totalGATime = 0;
                    List<Integer> lengths = new ArrayList<>();

                    for (int r = 0; r < RUNS; r++) {
                        long start = System.currentTimeMillis();
                        GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                        config.setSelectionMode(gc.selection);
                        config.setPopSize(gc.popSize);
                        config.setElitism(gc.elitism);
                        config.setRandomMutationRate(gc.mutRate);
                        if (gc.selection == GeneticAlgorithmConfig.SelectionMode.TOURNAMENT) {
                            config.setTournamentSize(gc.tournamentSize);
                        }
                        if (seed != null) {
                            config.setInitialSeed(seed);
                        }

                        Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                        int len = result.size();
                        double gaElapsed = (System.currentTimeMillis() - start) / 1000.0;
                        double totalElapsed = gaElapsed + ilpTime;

                        best = Math.max(best, len);
                        worst = Math.min(worst, len);
                        sum += len;
                        lengths.add(len);
                        totalGATime += gaElapsed;

                        System.out.printf("    %s run %d: %d in %.1fs (GA) / %.1fs (total)%n",
                                instanceName, r + 1, len, gaElapsed, totalElapsed);
                        csv.write(String.format("%s,%s,%d,%d,%d,%d,%d,%d,%.1f,%s,%d,%.3f,%.3f%n",
                                gc.name, instanceName, V, E, r + 1, ilpSeed, gc.popSize, gc.elitism,
                                gc.mutRate, gc.selection, len, totalElapsed, gaElapsed));
                        csv.flush();
                    }

                    double avg = (double) sum / lengths.size();
                    double avgGATime = totalGATime / lengths.size();
                    double avgTotalTime = avgGATime + ilpTime;
                    System.out.printf("  %s: Best=%d Avg=%.1f Worst=%d AvgTime=%.1fs(GA)/%.1fs(total)%n",
                            instanceName, best, avg, worst, avgGATime, avgTotalTime);
                    summary.write(String.format("  %s |V|=%d |E|=%d: Best=%d Avg=%.1f Worst=%d AvgGATime=%.1fs AvgTotalTime=%.1fs ILPSeed=%d Runs=%s%n",
                            instanceName, V, E, best, avg, worst, avgGATime, avgTotalTime, ilpSeed, lengths));
                }
                summary.write("\n");
                summary.flush();
            }
        }

        System.out.println("\nResults saved to: " + outputPath);
        System.out.println("Summary saved to: " + summaryPath);
    }
}
