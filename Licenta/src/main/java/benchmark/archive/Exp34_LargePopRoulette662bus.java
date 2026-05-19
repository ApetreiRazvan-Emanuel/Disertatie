package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp34: Roulette pop=2000 on 662bus (no ILP).
 * Exp32 showed pop=2000 + roulette massively improved yeast (275/256.2).
 * Roulette at pop=1000 gives 662bus 297/281.4. Does pop=2000 push higher?
 */
public class Exp34_LargePopRoulette662bus {
    public static void main(String[] args) throws Exception {
        int runs = 5;

        ExperimentRunner.runExperiment(
                "Exp34_Roulette_Pop2000_662bus",
                "Roulette popSize=2000 on 662bus",
                List.of("662bus"), runs,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setSelectionMode(GeneticAlgorithmConfig.SelectionMode.ROULETTE);
                    config.setPopSize(2000);
                    config.setElitism(100);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );
    }
}
