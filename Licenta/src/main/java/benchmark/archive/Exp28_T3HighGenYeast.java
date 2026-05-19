package benchmark;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;

import java.util.List;

/**
 * Exp28: Tournament-3 with higher maxGeneration (1000) on yeast.
 * T3 converges fast (386s at maxGen=500). More generations may push peaks higher.
 * Also tests on 662bus for comparison.
 */
public class Exp28_T3HighGenYeast {
    public static void main(String[] args) throws Exception {
        int runs = 5;

        ExperimentRunner.runExperiment(
                "Exp28_T3_HighGen",
                "Tournament-3 + maxGen=1000 + maxGenIncrease=300",
                List.of("662bus", "yeast"), runs,
                graph -> {
                    GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(graph);
                    config.setTournamentSize(3);
                    config.setMaxGeneration(1000);
                    config.setMaxGenerationIncrease(300);
                    return new LongestInducedPathGenetic(config).getLongestInducedPath();
                }
        );
    }
}
