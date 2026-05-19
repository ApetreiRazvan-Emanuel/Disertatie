package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp24: Additional roulette runs on 662bus to validate the 297 finding.
 * 10 runs to get more statistical confidence.
 */
public class Exp24_RouletteValidation {
    public static void main(String[] args) throws Exception {
        int runs = 10;

        ExperimentRunner.runExperiment(
                "Exp24_Roulette_662bus_10runs",
                "Roulette+Exp10 on 662bus only, 10 runs for validation",
                List.of("662bus"), runs,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );

        ExperimentRunner.appendToIndex("Exp24", "Roulette 662bus validation (10 runs)",
                "Pending — check summary files");
    }
}
