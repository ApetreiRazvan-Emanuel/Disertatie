package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp30b: Continue Exp30 — Roulette pressure=3 on yeast only.
 * 662bus already done: best=280, avg=277.6 (worse than p=2's 281.4).
 */
public class Exp30b_HighPressureRouletteYeast {
    public static void main(String[] args) throws Exception {
        int runs = 5;

        ExperimentRunner.runExperiment(
                "Exp30b_Roulette_P3_Yeast",
                "Roulette selection pressure=3 on yeast (continuation)",
                List.of("yeast"), runs,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                    config.setSelectionPressure(3);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );
    }
}
