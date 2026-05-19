package benchmark;

import core.algorithms.inducedpath.aco.LongestInducedPathACO;
import org.graph4j.Graph;

import java.util.List;

/**
 * Exp22: ACO baseline — 5 runs on key instances to establish reliable ACO numbers.
 */
public class Exp22_ACOBaseline {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        List<String> instances = List.of("usair", "494bus", "662bus", "yeast");

        ExperimentRunner.runExperiment(
                "Exp22_ACO",
                "ACO baseline: alpha=1.5, beta=3.0, evap=0.15, 1000 iter, max(20,V) ants",
                instances, runs,
                graph -> new LongestInducedPathACO(graph).getLongestInducedPath()
        );

        ExperimentRunner.appendToIndex("Exp22", "ACO baseline (5 runs each)",
                "Pending — check summary files");
    }
}
