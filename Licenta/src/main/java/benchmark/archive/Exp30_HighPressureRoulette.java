package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp30: Roulette with higher selection pressure (3 instead of 2) on yeast and 662bus.
 * ILP+Roulette(pressure=2) gives best yeast consistency (avg=253.6).
 * Higher pressure may push averages higher while keeping roulette diversity.
 */
public class Exp30_HighPressureRoulette {
    public static void main(String[] args) throws Exception {
        int runs = 5;

        ExperimentRunner.runExperiment(
                "Exp30_Roulette_P3",
                "Roulette selection pressure=3 + Exp10 improvements",
                List.of("662bus", "yeast"), runs,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                    config.setSelectionPressure(3);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );
    }
}
