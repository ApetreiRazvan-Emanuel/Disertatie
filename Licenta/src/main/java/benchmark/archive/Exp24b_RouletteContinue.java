package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp24b: Continuation of Exp24 — 6 more roulette runs on 662bus.
 */
public class Exp24b_RouletteContinue {
    public static void main(String[] args) throws Exception {
        ExperimentRunner.runExperiment(
                "Exp24b_Roulette_662bus",
                "Roulette+Exp10 on 662bus, continuation runs 5-10",
                List.of("662bus"), 6,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );
    }
}
