package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp25: Tournament selection with size 3 instead of 2.
 * Higher selection pressure — stronger individuals survive more often.
 * Tests if this helps convergence or kills diversity.
 */
public class Exp25_TournamentSize3 {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        List<String> instances = List.of("usair", "494bus", "662bus", "yeast");

        ExperimentRunner.runExperiment(
                "Exp25_Tournament3",
                "Tournament size 3 + Exp10 improvements",
                instances, runs,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setTournamentSize(3);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );

        ExperimentRunner.appendToIndex("Exp25", "Tournament size 3 (5 runs each)",
                "Pending — check summary files");
    }
}
