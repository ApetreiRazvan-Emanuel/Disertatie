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
 * Exp43: ILP+T3_mut0.5 with popSize=2000 on yeast.
 * Combines best config (ILP+T3) with best pop size (2000).
 * Previous records: pop=2000+Roulette=275, pop=1000+ILP+T3=271.
 */
public class Exp43_ILP_T3_Pop2000 {
    static final int RUNS = 5;

    public static void main(String[] args) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/Exp43_ILP_T3_Pop2000_" + timestamp + ".csv");
        Files.createDirectories(outputPath.getParent());

        Graph graph = ExperimentRunner.loadGraph("yeast");
        int V = graph.numVertices();
        long E = graph.numEdges();
        System.out.printf("yeast |V|=%d |E|=%d%n", V, E);

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
            csv.write("Config,Instance,|V|,|E|,Run,ILPSeed,PopSize,Elitism,MutRate,Selection,TournSize,PathLength,GATime(s),TotalTime(s)\n");

            List<Integer> lengths = new ArrayList<>();
            for (int r = 0; r < RUNS; r++) {
                long start = System.currentTimeMillis();
                GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.TOURNAMENT);
                config.setTournamentSize(3);
                config.setPopSize(2000);
                config.setElitism(100);
                config.setRandomMutationRate(0.5);
                config.setInitialSeed(seed);

                Path result = new LongestInducedPathGenetic(config).getLongestInducedPath();
                int len = result.size();
                double gaElapsed = (System.currentTimeMillis() - start) / 1000.0;
                double totalElapsed = gaElapsed + ilpTime;
                lengths.add(len);

                System.out.printf("  run %d: %d in %.1fs(GA)/%.1fs(total)%n", r + 1, len, gaElapsed, totalElapsed);
                csv.write(String.format("ILP+T3_pop2000,yeast,%d,%d,%d,%d,2000,100,0.5,TOURNAMENT,3,%d,%.3f,%.3f%n",
                        V, E, r + 1, ilpSeed, len, gaElapsed, totalElapsed));
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
