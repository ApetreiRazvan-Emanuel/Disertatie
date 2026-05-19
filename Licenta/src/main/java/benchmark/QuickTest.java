package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.io.GraphMLReader;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuickTest {
    private static final String GRAPH_DIR = "src/main/resources/graph-instances/";

    private static final List<String> TEST_GRAPHS = List.of(
            "tailorS2", "attiro", "sanjuansur", "ieeebus", "sfi",
            "usair", "494bus", "662bus", "yeast");

    private static final Map<String, Integer> TARGETS = new LinkedHashMap<>();
    static {
        TARGETS.put("tailorS2", 15);
        TARGETS.put("attiro", 31);
        TARGETS.put("sanjuansur", 38);
        TARGETS.put("ieeebus", 47);
        TARGETS.put("sfi", 13);
        TARGETS.put("usair", 46);
        TARGETS.put("494bus", 142);
        TARGETS.put("662bus", -1);
        TARGETS.put("yeast", -1);
    }

    private static final Map<String, Integer> OLD_BASELINE = new LinkedHashMap<>();
    static {
        OLD_BASELINE.put("usair", 42);
        OLD_BASELINE.put("494bus", 138);
        OLD_BASELINE.put("662bus", 276);
        OLD_BASELINE.put("yeast", 245);
    }

    public static void main(String[] args) throws Exception {
        int runs = 3;
        if (args.length > 0) {
            try { runs = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        System.out.println("=== GA Single-Threaded Fair Comparison ===");
        System.out.println("Runs per instance: " + runs);
        System.out.printf("%-15s %5s %6s  %6s %6s %6s %10s  %s%n",
                "Instance", "|V|", "|E|", "Best", "Avg", "Target", "AvgTime", "vs Old");
        System.out.println("-".repeat(80));

        for (String name : TEST_GRAPHS) {
            java.nio.file.Path file = Paths.get(GRAPH_DIR + name + ".graphml");
            if (!Files.exists(file)) {
                System.out.println("File not found: " + file);
                continue;
            }
            Graph graph = new GraphMLReader().parseGraphMLFile(file).readGraph();

            int best = 0;
            int sum = 0;
            double totalTime = 0;
            for (int r = 0; r < runs; r++) {
                long start = System.currentTimeMillis();
                Path path = new LongestInducedPathGenetic(new GeneticAlgorithmConfig(graph)).getLongestInducedPath();
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                int len = path.size();
                best = Math.max(best, len);
                sum += len;
                totalTime += elapsed;
                System.out.printf("  run %d: %d in %.1fs%n", r + 1, len, elapsed);
            }
            double avg = (double) sum / runs;
            double avgTime = totalTime / runs;
            int target = TARGETS.getOrDefault(name, -1);
            String targetStr = target > 0 ? String.valueOf(target) : "?";
            Integer oldVal = OLD_BASELINE.get(name);
            String vsOld = oldVal != null ? String.format("%.1f vs %d (%+.1f)", avg, oldVal, avg - oldVal) : "";

            System.out.printf("%-15s %5d %6d  %6d %6.1f %6s %10.1fs  %s%n",
                    name, graph.numVertices(), graph.numEdges(), best, avg, targetStr, avgTime, vsOld);
            System.out.println();
        }
    }
}
