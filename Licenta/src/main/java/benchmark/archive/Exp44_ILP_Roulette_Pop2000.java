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
 * Exp44: ILP+Roulette with popSize=2000, testing mut0.3 vs mut0.5.
 * Previous best: ILP+Roulette+pop2000+mut0.5 = 275 (all-time record).
 * Testing if mut0.3 can do better at pop=2000.
 */
public class Exp44_ILP_Roulette_Pop2000 {
    static final int RUNS = 5;

    public static void main(String[] args) throws Exception {
        double mutRate = args.length > 0 ? Double.parseDouble(args[0]) : 0.3;
        String configName = String.format("ILP+Roulette_pop2000_mut%.1f", mutRate);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/Exp44_ILP_Roulette_Pop2000_" + timestamp + ".csv");
        Files.createDirectories(outputPath.getParent());

        Graph graph = ExperimentRunner.loadGraph("yeast");
        int V = graph.numVertices();
        long E = graph.numEdges();
        System.out.printf("yeast |V|=%d |E|=%d, Config: %s%n", V, E, configName);

        System.out.print("ILP phase (120s)...");
        Path sharedPath = new Path(graph);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Path> future = executor.submit(() ->
                new LongestInducedPathILP(graph, sharedPath).getLongestInducedPath());
        Path ilpPath;
        long ilpStart = System.currentTimeMillis();
        try {
            ilpPath = future.get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            ilpPath = sharedPath;
        } finally {
            executor.shutdownNow();
        }
        double ilpTime = (System.currentTimeMillis() - ilpStart) / 1000.0;
        int ilpSeed = ilpPath.size();
        System.out.printf(" seed=%d in %.1fs%n", ilpSeed, ilpTime);

        boolean[] seed = new boolean[V];
        for (int v : ilpPath) {
            int idx = graph.indexOf(v);
            if (idx >= 0) seed[idx] = true;
        }

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath)) {
            csv.write("Config,Instance,|V|,|E|,Run,ILPSeed,PopSize,Elitism,MutRate,Selection,PathLength,GATime(s),TotalTime(s)\n");

            List<Integer> lengths = new ArrayList<>();
            for (int r = 0; r < RUNS; r++) {
                long start = System.currentTimeMillis();
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                config.setPopSize(2000);
                config.setElitism(100);
                config.setRandomMutationRate(mutRate);
                config.setInitialSeed(seed);

                Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                int len = result.size();
                double gaElapsed = (System.currentTimeMillis() - start) / 1000.0;
                double totalElapsed = gaElapsed + ilpTime;
                lengths.add(len);

                System.out.printf("  run %d: %d in %.1fs(GA)/%.1fs(total)%n", r + 1, len, gaElapsed, totalElapsed);
                csv.write(String.format("%s,yeast,%d,%d,%d,%d,2000,100,%.1f,ROULETTE,%d,%.3f,%.3f%n",
                        configName, V, E, r + 1, ilpSeed, mutRate, len, gaElapsed, totalElapsed));
                csv.flush();
            }

            int best = lengths.stream().max(Integer::compare).orElse(0);
            int worst = lengths.stream().min(Integer::compare).orElse(0);
            double avg = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
            System.out.printf("  => Best=%d Avg=%.1f Worst=%d%n", best, avg, worst);
        }
        System.out.println("Results saved to: " + outputPath);
    }
}
