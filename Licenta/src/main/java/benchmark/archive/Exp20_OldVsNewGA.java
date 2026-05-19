package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.io.GraphMLReader;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.util.List;

/**
 * Exp20: Old GA (roulette, no boost, original params) vs New GA (Exp10 config) — 5 runs each.
 */
public class Exp20_OldVsNewGA {
    public static void main(String[] args) throws Exception {
        int runs = 5;
        List<String> instances = List.of("usair", "494bus", "662bus", "yeast");

        System.out.println("\n========== PART 1: NEW GA (Exp10 config) ==========\n");
        ExperimentRunner.runExperiment(
                "Exp20_NewGA",
                "New GA with Exp10 config: tournament, rate=0.5, boost all, popCap=1000, dynamic gen",
                instances, runs,
                graph -> new LongestInducedPathGenetic(new GeneticAlgorithmConfig(graph)).getLongestInducedPath()
        );

        System.out.println("\n========== PART 2: OLD GA (original config) ==========\n");
        ExperimentRunner.runExperiment(
                "Exp20_OldGA",
                "Old GA with original config: roulette, rate=0.85, no boost, popSize=V uncapped, maxGen=300",
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

        ExperimentRunner.appendToIndex("Exp20", "Old GA vs New GA (5 runs each)",
                "Pending — check summary files");
    }
}
