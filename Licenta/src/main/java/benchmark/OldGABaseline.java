package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import old.geneticOldVariants.LongestInducedPathGenetic;
import core.io.GraphMLReader;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.nio.file.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the OLD GA (roulette wheel, no boostIndividual, original params)
 * with original config values to verify baseline results.
 */
public class OldGABaseline {
    private static final String GRAPH_DIR = "src/main/resources/graph-instances/";

    private static final List<String> TEST_GRAPHS = List.of(
            "tailorS2", "attiro", "sanjuansur", "ieeebus", "sfi",
            "usair", "494bus", "662bus", "yeast");

    private static final Map<String, Integer> CLAIMED_BASELINE = new LinkedHashMap<>();
    static {
        CLAIMED_BASELINE.put("usair", 42);
        CLAIMED_BASELINE.put("494bus", 138);
        CLAIMED_BASELINE.put("662bus", 276);
        CLAIMED_BASELINE.put("yeast", 245);
    }

    public static void main(String[] args) throws Exception {
        int runs = 3;
        if (args.length > 0) {
            try { runs = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        System.out.println("=== OLD GA Baseline Verification ===");
        System.out.println("Config: popSize=V (uncapped), maxGen=300, maxGenIncrease=100, rate=0.85, mutCount=5, roulette selection, NO boost");
        System.out.println("Runs per instance: " + runs);
        System.out.printf("%-15s %5s %6s  %6s %6s %10s  %s%n",
                "Instance", "|V|", "|E|", "Best", "Avg", "AvgTime", "vs Claimed");
        System.out.println("-".repeat(80));

        for (String name : TEST_GRAPHS) {
            java.nio.file.Path file = Paths.get(GRAPH_DIR + name + ".graphml");
            if (!Files.exists(file)) {
                System.out.println("File not found: " + file);
                continue;
            }
            Graph graph = new GraphMLReader().parseGraphMLFile(file).readGraph();

            GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
            config.setPopSize(graph.numVertices());
            config.setMaxGeneration(300);
            config.setMaxGenerationIncrease(100);
            config.setRandomMutationRate(0.85);
            config.setMutationCount(5);

            int best = 0;
            int sum = 0;
            double totalTime = 0;
            for (int r = 0; r < runs; r++) {
                long start = System.currentTimeMillis();
                Path path = new LongestInducedPathGenetic(new GeneticAlgorithmConfig(graph) {{
                    setPopSize(graph.numVertices());
                    setMaxGeneration(300);
                    setMaxGenerationIncrease(100);
                    setRandomMutationRate(0.85);
                    setMutationCount(5);
                }}).getLongestInducedPath();
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                int len = path.size();
                best = Math.max(best, len);
                sum += len;
                totalTime += elapsed;
                System.out.printf("  run %d: %d in %.1fs%n", r + 1, len, elapsed);
            }
            double avg = (double) sum / runs;
            double avgTime = totalTime / runs;
            Integer claimed = CLAIMED_BASELINE.get(name);
            String vsClaimed = claimed != null ? String.format("%.1f vs %d (%+.1f)", avg, claimed, avg - claimed) : "";

            System.out.printf("%-15s %5d %6d  %6d %6.1f %10.1fs  %s%n",
                    name, graph.numVertices(), graph.numEdges(), best, avg, avgTime, vsClaimed);
            System.out.println();
        }
    }
}
