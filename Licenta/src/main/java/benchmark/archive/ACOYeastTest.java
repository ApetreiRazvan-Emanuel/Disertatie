package benchmark;

import core.algorithms.inducedpath.aco.LongestInducedPathACO;
import core.algorithms.inducedpath.aco.LongestInducedPathACOv3;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ACO v1 vs v3 (fixed) on yeast, 2 runs each.
 * v2 is dropped as it consistently underperforms v1.
 */
public class ACOYeastTest {
    public static void main(String[] args) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        java.nio.file.Path outputPath = Paths.get("src/main/output/experiments/ACOYeast_" + timestamp + ".csv");
        Files.createDirectories(outputPath.getParent());

        Graph graph = ExperimentRunner.loadGraph("yeast");
        System.out.printf("yeast |V|=%d |E|=%d%n", graph.numVertices(), graph.numEdges());

        try (BufferedWriter csv = Files.newBufferedWriter(outputPath)) {
            csv.write("Version,Instance,|V|,|E|,Run,PathLength,Time(s)\n");

            for (String version : new String[]{"v1", "v3"}) {
                System.out.printf("%n--- ACO %s on yeast ---%n", version);
                for (int r = 0; r < 2; r++) {
                    long start = System.currentTimeMillis();
                    Path result;
                    if (version.equals("v1")) {
                        result = new LongestInducedPathACO(graph).getLongestInducedPath();
                    } else {
                        result = new LongestInducedPathACOv3(graph).getLongestInducedPath();
                    }
                    int len = result.size();
                    double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                    System.out.printf("  run %d: %d in %.1fs%n", r + 1, len, elapsed);
                    csv.write(String.format("%s,yeast,%d,%d,%d,%d,%.3f%n",
                            version, graph.numVertices(), graph.numEdges(), r + 1, len, elapsed));
                    csv.flush();
                }
            }
        }
        System.out.println("\nResults saved to: " + outputPath);
    }
}
