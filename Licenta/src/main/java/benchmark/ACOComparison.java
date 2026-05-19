package benchmark;

import core.algorithms.inducedpath.aco.LongestInducedPathACO;
import core.algorithms.inducedpath.aco.LongestInducedPathACOv2;
import core.algorithms.inducedpath.aco.LongestInducedPathACOv3;
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
 * ACO v1 vs v2 vs v3 comparison on all instances, 3 runs each.
 * Start with smaller instances first so we get partial results if killed.
 */
public class ACOComparison {
    static final String[] INSTANCES = {"usair", "494bus", "662bus", "yeast"};
    static final int RUNS = 3;

    public static void main(String[] args) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/ACOComparison_" + timestamp + ".csv");
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath)) {
            csv.write("Version,Instance,|V|,|E|,Run,PathLength,Time(s)\n");

            for (String instanceName : INSTANCES) {
                Graph graph = ExperimentRunner.loadGraph(instanceName);
                int V = graph.numVertices();
                long E = graph.numEdges();

                for (String version : new String[]{"v1", "v2", "v3"}) {
                    System.out.printf("%n=== %s on %s (|V|=%d, |E|=%d) ===%n", version, instanceName, V, E);

                    for (int r = 0; r < RUNS; r++) {
                        long start = System.currentTimeMillis();
                        Path result;
                        if (version.equals("v1")) {
                            result = new LongestInducedPathACO(graph).getLongestInducedPath();
                        } else if (version.equals("v2")) {
                            result = new LongestInducedPathACOv2(graph).getLongestInducedPath();
                        } else {
                            result = new LongestInducedPathACOv3(graph).getLongestInducedPath();
                        }
                        int len = result.size();
                        double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                        System.out.printf("  run %d: %d in %.1fs%n", r + 1, len, elapsed);
                        csv.write(String.format("%s,%s,%d,%d,%d,%d,%.3f%n", version, instanceName, V, E, r + 1, len, elapsed));
                        csv.flush();
                    }
                }
            }
        }

        System.out.println("\nResults saved to: " + outputPath);
    }
}
