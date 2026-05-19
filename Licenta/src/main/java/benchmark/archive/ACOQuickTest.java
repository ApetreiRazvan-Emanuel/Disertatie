package benchmark;

import core.algorithms.inducedpath.aco.LongestInducedPathACO;
import core.algorithms.inducedpath.aco.LongestInducedPathACOv2;
import core.algorithms.inducedpath.aco.LongestInducedPathACOv3;
import org.graph4j.Graph;
import org.graph4j.util.Path;

/**
 * Quick test of ACO v1/v2/v3 on usair (smallest dense instance).
 */
public class ACOQuickTest {
    public static void main(String[] args) throws Exception {
        String inst = "usair";
        Graph graph = ExperimentRunner.loadGraph(inst);
        System.out.printf("Instance: %s |V|=%d |E|=%d%n", inst, graph.numVertices(), graph.numEdges());

        for (String version : new String[]{"v1", "v2", "v3"}) {
            System.out.printf("%n--- ACO %s ---%n", version);
            for (int r = 0; r < 2; r++) {
                long start = System.currentTimeMillis();
                Path result;
                if (version.equals("v1")) {
                    result = new LongestInducedPathACO(graph).getLongestInducedPath();
                } else if (version.equals("v2")) {
                    result = new LongestInducedPathACOv2(graph).getLongestInducedPath();
                } else {
                    result = new LongestInducedPathACOv3(graph).getLongestInducedPath();
                }
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                System.out.printf("  run %d: path=%d time=%.1fs%n", r + 1, result.size(), elapsed);
            }
        }
    }
}
