package core.algorithms.inducedpath.genetic;

public class GeneticAlgorithmConfig {
    public int popSize = 200;
    public double mutationRate = 0.3;
    public double crossoverRate = 0.2;
    public double freshRate = 0.1;
    public boolean useRoulette = false;
    public int dfsCount = 2;
    public int maxPaths = 10;
    public double crossoverRemoveRate = 0.5;
    public int maxTrimDivisor = 4;
    public int stagnationLimit = 0;
    public double restartRatio = 0.6;
    public String label = "";

    public GeneticAlgorithmConfig() {}

    public GeneticAlgorithmConfig(String label) { this.label = label; }

    @Override
    public String toString() {
        return String.format("pop=%d mut=%.2f cx=%.2f fresh=%.2f roul=%b dfs=%d maxP=%d cxRem=%.1f trim=1/%d stag=%d",
                popSize, mutationRate, crossoverRate, freshRate, useRoulette, dfsCount, maxPaths, crossoverRemoveRate, maxTrimDivisor, stagnationLimit);
    }
}
