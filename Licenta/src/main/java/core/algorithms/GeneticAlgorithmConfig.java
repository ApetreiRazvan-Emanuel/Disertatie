package core.algorithms;

import org.graph4j.Graph;

/**
 * Configuration class for the induced path genetic algorithm.
 * This class provides settings related to population size, generation limits,
 * mutation rates, and other parameters necessary for configuring the genetic algorithm.
 *
 * @author Apetrei Razvan-Emanuel
 */
public class GeneticAlgorithmConfig {
    public enum SelectionMode { TOURNAMENT, ROULETTE }

    private Graph graph;
    private int popSize;
    private int maxGeneration;
    private boolean dynamicMaxGeneration;
    private int maxGenerationIncrease;
    private int elitism;
    private double mutationRate;
    private int mutationCount;
    private double randomMutationRate;
    private double probabilityCrossover;
    private double selectionPressure;
    private boolean processEachComponentSeparately;
    private SelectionMode selectionMode;
    private int tournamentSize;
    private boolean[] initialSeed;
    private int[] preferredStartVertices;
    private boolean memeticLocalSearch;
    private int memeticTopK;

    /**
     * Constructs a GeneticAlgorithmConfig with settings based on the provided graph.
     * This constructor initializes configuration parameters with values that give good results.
     *
     * @param graph The graph for which the genetic algorithm will be configured
     */
    public GeneticAlgorithmConfig(Graph graph) {
        // Saving the graph
        this.graph = graph;

        // The population size is set to the number of vertices in the graph
        popSize = Math.min(graph.numVertices(), 1000);

        // Dynamic generation setting is enabled to make the algorithm continue searching for large instances.
        dynamicMaxGeneration = true;

        // Max generation increase is set high to allow for extensive searching on large instances.
        maxGenerationIncrease = 200;

        // Initial max generation is set to 300, a lower value so the algorithm doesn't take long on small instances.
        maxGeneration = 500;

        // Elitism is set to a fraction of the population size to preserve the best solutions
        elitism = popSize / 20;

        // Higher mutation rate to encourage diverse genetic variations
        mutationRate = 0.1;

        // Scale mutation count for large dense graphs; keep low for sparse graphs
        double avgDegree = 2.0 * graph.numEdges() / graph.numVertices();
        mutationCount = (graph.numVertices() > 100 && avgDegree >= 4.0)
                ? Math.min(15, 5 + graph.numVertices() / 100)
                : 5;

        // High random mutation rate to introduce additional individuals to prevent the algorithm from stagnating
        randomMutationRate = 0.5;

        // Lower probability of crossover since crossover is time expensive
        probabilityCrossover = 0.3;

        // An average selection pressure to encourage selecting the fittest individuals
        selectionPressure = 2;

        // Sets the algorithm to not run for each connected component inside the input graph
        processEachComponentSeparately = false;

        // Default to tournament selection
        selectionMode = SelectionMode.TOURNAMENT;
        tournamentSize = 2;

        // No ILP seed by default
        initialSeed = null;

        // No preferred start vertices by default
        preferredStartVertices = null;

        // Memetic local search disabled by default
        memeticLocalSearch = false;
        memeticTopK = 5;
    }

    /**
     * Gets the graph
     * @return the graph
     */

    public Graph getGraph() {
        return graph;
    }

    /**
     * Sets the graph
     * @param graph the graph
     */

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    /**
     * Gets the population size.
     * @return the population size
     */
    public int getPopSize() {
        return popSize;
    }

    /**
     * Sets the population size.
     * @param popSize the new population size
     */
    public void setPopSize(int popSize) {
        this.popSize = popSize;
    }

    /**
     * Returns true if the maximum generation count is dynamic. Dynamic means that the max generation will increase if
     * the genetic algorithm keeps finding improvements.
     * @return true if dynamic, false otherwise
     */
    public boolean isDynamicMaxGeneration() {
        return dynamicMaxGeneration;
    }

    /**
     * Sets whether the maximum generation count is dynamic. Dynamic means that the max generation will increase if
     * the genetic algorithm keeps finding improvements.
     * @param dynamicMaxGeneration true to make it dynamic, false otherwise
     */
    public void setDynamicMaxGeneration(boolean dynamicMaxGeneration) {
        this.dynamicMaxGeneration = dynamicMaxGeneration;
    }

    /**
     * Gets the increase in maximum generation when it's dynamic.
     * @return the maximum generation increase
     */
    public int getMaxGenerationIncrease() {
        return maxGenerationIncrease;
    }

    /**
     * Sets the increase in maximum generation when it's dynamic.
     * @param maxGenerationIncrease the maximum generation increase
     */
    public void setMaxGenerationIncrease(int maxGenerationIncrease) {
        this.maxGenerationIncrease = maxGenerationIncrease;
    }

    /**
     * Gets the maximum generation.
     * @return the maximum generation
     */
    public int getMaxGeneration() {
        return maxGeneration;
    }

    /**
     * Sets the maximum generation.
     * @param maxGeneration the new maximum generation
     */
    public void setMaxGeneration(int maxGeneration) {
        this.maxGeneration = maxGeneration;
    }

    /**
     * Gets the number of elite individuals preserved between generations.
     * @return the elitism count
     */
    public int getElitism() {
        return elitism;
    }

    /**
     * Sets the number of elite individuals to preserve between generations.
     * @param elitism the elitism count
     */
    public void setElitism(int elitism) {
        this.elitism = elitism;
    }

    /**
     * Gets the mutation rate.
     * @return the mutation rate
     */
    public double getMutationRate() {
        return mutationRate;
    }

    /**
     * Sets the mutation rate.
     * @param mutationRate the new mutation rate
     */
    public void setMutationRate(double mutationRate) {
        this.mutationRate = mutationRate;
    }

    /**
     * Gets the mutation count.
     * @return the mutation count
     */
    public int getMutationCount() {
        return mutationCount;
    }

    /**
     * Sets the mutation count.
     * @param mutationCount the new mutation count
     */
    public void setMutationCount(int mutationCount) {
        this.mutationCount = mutationCount;
    }

    /**
     * Gets the random mutation rate.
     * @return the random mutation rate
     */
    public double getRandomMutationRate() {
        return randomMutationRate;
    }

    /**
     * Sets the random mutation rate.
     * @param randomMutationRate the new random mutation rate
     */
    public void setRandomMutationRate(double randomMutationRate) {
        this.randomMutationRate = randomMutationRate;
    }

    /**
     * Gets the probability of crossover.
     * @return the probability of crossover
     */
    public double getProbabilityCrossover() {
        return probabilityCrossover;
    }

    /**
     * Sets the probability of crossover.
     * @param probabilityCrossover the new probability of crossover
     */
    public void setProbabilityCrossover(double probabilityCrossover) {
        this.probabilityCrossover = probabilityCrossover;
    }

    /**
     * Gets the selection pressure.
     * @return the selection pressure
     */
    public double getSelectionPressure() {
        return selectionPressure;
    }

    /**
     * Sets the selection pressure.
     * @param selectionPressure the new selection pressure
     */
    public void setSelectionPressure(double selectionPressure) {
        this.selectionPressure = selectionPressure;
    }

    /**
     * Returns true if the genetic algorithm is configured to run for each connected component individually.
     * @return true if each component will be processed separately, false otherwise
     */
    public boolean isProcessEachComponentSeparately() {
        return processEachComponentSeparately;
    }

    /**
     * Sets whether the algorithm should process each connected component separately.
     * @param processEachComponentSeparately true to make it process each connected component separately, false otherwise
     */
    public void setProcessEachComponentSeparately(boolean processEachComponentSeparately) {
        this.processEachComponentSeparately = processEachComponentSeparately;
    }

    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = selectionMode;
    }

    public int getTournamentSize() {
        return tournamentSize;
    }

    public void setTournamentSize(int tournamentSize) {
        this.tournamentSize = tournamentSize;
    }

    public boolean[] getInitialSeed() {
        return initialSeed;
    }

    public void setInitialSeed(boolean[] initialSeed) {
        this.initialSeed = initialSeed;
    }

    public int[] getPreferredStartVertices() {
        return preferredStartVertices;
    }

    public void setPreferredStartVertices(int[] preferredStartVertices) {
        this.preferredStartVertices = preferredStartVertices;
    }

    public boolean isMemeticLocalSearch() {
        return memeticLocalSearch;
    }

    public void setMemeticLocalSearch(boolean memeticLocalSearch) {
        this.memeticLocalSearch = memeticLocalSearch;
    }

    public int getMemeticTopK() {
        return memeticTopK;
    }

    public void setMemeticTopK(int memeticTopK) {
        this.memeticTopK = memeticTopK;
    }
}
