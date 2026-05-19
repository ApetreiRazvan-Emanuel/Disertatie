package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import org.graph4j.Graph;

import java.util.List;

/**
 * Exp19: Roulette wheel selection with all Exp10 improvements (boost, rate=0.5, etc.)
 * Tests the user's hypothesis that roulette gives weak individuals a chance
 * and boosters could make them competitive.
 */
public class Exp19_RouletteSelection {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        List<String> instances = List.of("usair", "494bus", "662bus", "yeast");

        ExperimentRunner.runExperiment(
                "Exp19_Roulette",
                "Roulette wheel selection (pressure=2) with all Exp10 improvements",
                instances, runs,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );

        ExperimentRunner.appendToIndex("Exp19", "Roulette selection + Exp10 improvements (5 runs)",
                "Pending — check summary files");
    }
}
