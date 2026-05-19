package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp20a: New GA (Exp10 config) — 5 runs on key instances.
 */
public class Exp20_NewGA {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        List<String> instances = List.of("usair", "494bus", "662bus", "yeast");

        ExperimentRunner.runExperiment(
                "Exp20a_NewGA",
                "New GA with Exp10 config: tournament, rate=0.5, boost all, popCap=1000, dynamic gen",
                instances, runs,
                graph -> new LongestInducedPathGenetic(new GeneticAlgorithmConfig(graph)).getLongestInducedPath()
        );

        ExperimentRunner.appendToIndex("Exp20a", "New GA Exp10 config (5 runs)",
                "Pending — check summary files");
    }
}
