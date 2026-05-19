package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp29: Multi-restart Tournament-3 on yeast (10 runs).
 * Exp28 showed maxGen=1000 hurts — T3 converges fast and stagnates.
 * Better strategy: many short runs, take the best. 10 runs at maxGen=500.
 * Also runs 662bus for comparison.
 */
public class Exp29_T3MultiRestart {
    public static void main(String[] args) throws Exception {
        // 10 runs of T3 on yeast — looking for high peaks via restarts
        ExperimentRunner.runExperiment(
                "Exp29_T3_10runs",
                "Tournament-3 multi-restart (10 runs, maxGen=500)",
                List.of("yeast"), 10,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setTournamentSize(3);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );
    }
}
