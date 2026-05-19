package benchmark;

import core.algorithms.GeneticAlgorithmConfig;

import java.util.List;

/**
 * Exp20b: Old GA (original config) — 5 runs on key instances.
 */
public class Exp20_OldGA {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        List<String> instances = List.of("usair", "494bus", "662bus", "yeast");

        ExperimentRunner.runExperiment(
                "Exp20b_OldGA",
                "Old GA: roulette, rate=0.85, no boost, popSize=V uncapped, maxGen=300",
                instances, runs,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setPopSize(graph.numVertices());
                    config.setMaxGeneration(300);
                    config.setMaxGenerationIncrease(100);
                    config.setRandomMutationRate(0.85);
                    config.setMutationCount(5);
                    config.setDynamicMaxGeneration(false);
                    return new old.geneticOldVariants.LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );

        ExperimentRunner.appendToIndex("Exp20b", "Old GA original config (5 runs)",
                "Pending — check summary files");
    }
}
