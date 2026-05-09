package core.algorithms.inducedcycle.genetic;

import core.algorithms.inducedcycle.InducedCycleAlgorithm;
import core.algorithms.GeneticAlgorithmConfig;
import org.graph4j.Edge;
import org.graph4j.Graph;
import org.graph4j.GraphBuilder;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Cycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A genetic algorithm designed to find the maximum induced cycle within a graph.
 *
 * @author Apetrei Razvan-Emanuel
 */

public class LongestInducedCycleGenetic extends GraphAlgorithm implements InducedCycleAlgorithm {
    private Cycle longestInducedCycle;
    private final int POP_SIZE;
    private final int MAX_GENERATION;
    private final boolean DYNAMIC_MAX_GENERATION;
    private final int MAX_GENERATION_INCREASE;
    private final int ELITISM;
    private final double MUTATION_RATE;
    private final int MUTATION_COUNT;
    private final double RANDOM_MUTATION_RATE;
    private final double PROB_CROSSOVER;
    private final double SELECTION_PRESSURE;
    private final int numVertices;
    private int currentGeneration = 0;
    private int lastFoundImprovement = 0;
    private boolean[] bestIndividual;
    private int bestValue = 0;
    private final Random random = new Random();
    private final GeneticAlgorithmConfig config;

    /**
     * Initializes all the parameters of the genetic algorithm with default values
     * @param graph the graph
     */

    LongestInducedCycleGenetic(Graph graph) {
        super(graph);
        GeneticAlgorithmConfig geneticAlgorithmConfig = new GeneticAlgorithmConfig(graph);
        config = geneticAlgorithmConfig;
        POP_SIZE = geneticAlgorithmConfig.getPopSize();
        MAX_GENERATION = geneticAlgorithmConfig.getMaxGeneration();
        DYNAMIC_MAX_GENERATION = geneticAlgorithmConfig.isDynamicMaxGeneration();
        MAX_GENERATION_INCREASE = geneticAlgorithmConfig.getMaxGenerationIncrease();
        ELITISM = geneticAlgorithmConfig.getElitism();
        MUTATION_RATE = geneticAlgorithmConfig.getMutationRate();
        MUTATION_COUNT = geneticAlgorithmConfig.getMutationCount();
        RANDOM_MUTATION_RATE = geneticAlgorithmConfig.getRandomMutationRate();
        PROB_CROSSOVER = geneticAlgorithmConfig.getProbabilityCrossover();
        SELECTION_PRESSURE = geneticAlgorithmConfig.getSelectionPressure();
        numVertices = graph.numVertices();
    }

    /**
     * Initializes all the parameters from the config to allow customization of all the parameters
     * @param geneticAlgorithmConfig the config containing the values of the parameters and the graph
     */

    public LongestInducedCycleGenetic(GeneticAlgorithmConfig geneticAlgorithmConfig) {
        super(geneticAlgorithmConfig.getGraph());
        config = geneticAlgorithmConfig;
        POP_SIZE = geneticAlgorithmConfig.getPopSize();
        MAX_GENERATION = geneticAlgorithmConfig.getMaxGeneration();
        DYNAMIC_MAX_GENERATION = geneticAlgorithmConfig.isDynamicMaxGeneration();
        MAX_GENERATION_INCREASE = geneticAlgorithmConfig.getMaxGenerationIncrease();
        ELITISM = geneticAlgorithmConfig.getElitism();
        MUTATION_RATE = geneticAlgorithmConfig.getMutationRate();
        MUTATION_COUNT = geneticAlgorithmConfig.getMutationCount();
        RANDOM_MUTATION_RATE = geneticAlgorithmConfig.getRandomMutationRate();
        PROB_CROSSOVER = geneticAlgorithmConfig.getProbabilityCrossover();
        SELECTION_PRESSURE = geneticAlgorithmConfig.getSelectionPressure();
        numVertices = graph.numVertices();
    }

    /**
     * Method used to search to generate an individual randomly by searching for the best induced path starting from a
     * vertex.
     * @param currentVertex The current vertex
     * @param temp Holds the current induced path
     * @param tempSize Holds the length of the current induced path. Used for efficiency purposes
     * @param individual Contains the best induced path, the individual generated is returned through this variable
     * @param individualSize Contains the length of the best induced path so far
     * @param validVertices Array used to validate vertices before they are added to the path
     * @param numberOfPaths Current number of paths generated
     * @param maximumNumberOfPaths Limits the number of paths generated so this method ends quickly
     * @param lastImprov Holds the last time we found an improvement
     * @param truncated If this parameter is set to true, then the execution of this method will end
     */

    private void inducedPathsFromVertex(int currentVertex, boolean[] temp, int tempSize, boolean[] individual, int[] individualSize, int[] validVertices, int[] numberOfPaths, int maximumNumberOfPaths, int[] lastImprov, boolean[] truncated) {
        if(truncated[0])
            return;

        validVertices[currentVertex]++;
        temp[currentVertex] = true;
        tempSize++;
        int[] neighbours = graph.neighbors(currentVertex);

        for (int neighbor : neighbours) {
            validVertices[neighbor]++;
        }

        List<Integer> neighboursList = new ArrayList<>();
        boolean foundNeighbour = false;
        for(int neighbour : neighbours) {
            if(validVertices[neighbour] == 1) {
                foundNeighbour = true;
                neighboursList.add(neighbour);
            }
        }

        while(!neighboursList.isEmpty()) {
            int neighbourIndex = random.nextInt(neighboursList.size());
            int neighbour = neighboursList.get(neighbourIndex);
            neighboursList.remove(neighbourIndex);

            inducedPathsFromVertex(neighbour, temp, tempSize, individual, individualSize, validVertices, numberOfPaths, maximumNumberOfPaths, lastImprov, truncated);
        }

        if(!foundNeighbour) {
            if(tempSize > individualSize[0]) {
                individualSize[0] = tempSize;
                for(int i = 0; i < numVertices; i++) {
                    individual[i] = temp[i];
                }
                lastImprov[0] = numberOfPaths[0];
            }

            numberOfPaths[0]++;

            if(numberOfPaths[0] - lastImprov[0] > maximumNumberOfPaths) {
                truncated[0] = true;
            }
        }

        temp[currentVertex] = false;

        for (int neighbor : neighbours) {
            validVertices[neighbor]--;
        }
    }

    /**
     * Generates a random individual by picking a random vertex and calling the inducedPathsFromVertex method.
     *
     * @return the individual generated
     */

    boolean[] generateRandomIndividual() {
        boolean[] individual = new boolean[numVertices];
        int[] validVertices = new int[numVertices];
        int startVertex = random.nextInt(numVertices);
        validVertices[startVertex] = 1;
        inducedPathsFromVertex(startVertex, new boolean[numVertices], 0, individual, new int[1], validVertices, new int[1], 100, new int[1], new boolean[1]);
        return individual;
    }

    /**
     * Generates the starting population by calling the generateRandomIndividual method POP_SIZE times.
     * @return the starting population generated
     */

    List<boolean[]> generateStartingPopulation() {
        List<boolean[]> population = new ArrayList<>();

        for(int i = 0; i < POP_SIZE; i++) {
            population.add(generateRandomIndividual());
        }
        return population;
    }

    /**
     * This is the fitness function, used to evaluate the fitness of an individual. In our case, an induced path is
     * better than another induced path if it has more length.
     * @param indv the individual
     * @return the fitness of the individual
     */

    private int evaluateIndividual(boolean[] indv) {
        int fitness = 0;
        for(int i = 0; i < numVertices; i++) {
            if(indv[i]) {
                fitness++;
            }
        }
        return fitness;
    }

    /**
     * Used in the mutation when we extend the path to check if we can add the target vertex. We can add a vertex if
     * the single neighbour it has within the path is the start or end vertex for the path.
     * @param indv the individual representing an induced path
     * @param vertex the vertex that will be checked if it can be added to the induced path
     * @return true if it can be vertex can be added, false otherwise
     */

    boolean checkVertexToAdd(boolean[] indv, int vertex) {
        int countNeighbours = 0;
        for(int neighbour : graph.neighbors(vertex)) {
            if(indv[neighbour]) {
                countNeighbours++;
            }
        }
        return countNeighbours == 1;
    }

    /**
     * Checks if the second vertex given as a parameter is a neighbour of the first vertex given as a parameter.
     * Used to check if the last node added is a neighbour of the start node to confirm that the induced path can be
     * extended into a cycle.
     * @param vertex1 the first Vertex.
     * @param vertex2 the second Vertex.
     * @return true if the vertices are adjacent and false otherwise.
     */

    private boolean checkAdjacency(int vertex1, int vertex2) {
        for(int neighbour : graph.neighbors(vertex1)) {
            if(vertex2 == neighbour) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the individual represented as an induced path can be extended into an induced cycle. If that is the
     * case then we check if this induced cycle has greater length than the longest one found so far and, if this is
     * the case, we update the longest found cycle.
     * @param indv the induced path represented as an individual in the population
     * @param indvSize the number of vertices in the induced path
     * @param countNeighbors an array containing the amount of neighbors each vertex has within the induced path
     * @param startVertex the starting vertex of the induced path (or the end one, it doesn't matter)
     * @param endVertex the end vertex of the induced path (or the start one, it doesn't matter)
     */

    void checkIndvToCycle(boolean[] indv, int indvSize, int[] countNeighbors, int startVertex, int endVertex) {
        if(indvSize <= 2) {
            return;
        }
        for(int neighbor : graph.neighbors(startVertex)) {
            if(!indv[neighbor] && countNeighbors[neighbor] == 2 && checkAdjacency(neighbor, endVertex)) {
                if(indvSize + 1 > bestValue) {
                    for(int i = 0; i < numVertices; i++) {
                        bestIndividual[i] = indv[i];
                    }
                    bestIndividual[neighbor] = true;
                    bestValue = indvSize + 1;
                }
            }
        }
    }

    /**
     * The mutation operator, takes the current population and mutates each individual within the population with a
     * chance stored in the MUTATION_RATE member of this class. The mutation removes or adds a vertex to the induced
     * path MUTATION_COUNT times. It also adds randomly generated individuals, based on the RANDOM_MUTATION_RATE member.
     * @param p the current population
     */

    private void mutate(List<boolean[]> p) {
        int size = p.size();
        for(int i = 0; i < size; i++) {
            if(random.nextDouble() < RANDOM_MUTATION_RATE) {
                p.add(generateRandomIndividual());
            }
            if(random.nextDouble() < MUTATION_RATE) {
                boolean[] indv = p.get(i);
                for(int j = 0; j < MUTATION_COUNT; j++) {
                    List<Integer> endPoints = new ArrayList<>();
                    int[] countNeighbors = new int[numVertices];
                    int countVertices = 0;
                    for (int vertex = 0; vertex < numVertices; vertex++) {
                        for (int neighbour : graph.neighbors(vertex)) {
                            if (indv[neighbour]) {
                                countNeighbors[vertex]++;
                            }
                        }
                        if (indv[vertex]) {
                            countVertices++;
                            if (countNeighbors[vertex] == 1) {
                                endPoints.add(vertex);
                            }
                            if (countNeighbors[vertex] > 2) {
                                System.out.println("Genetic Algorithm Induced Path: Mutation Error, count > 2");
                            }
                        }
                    }
                    if(countVertices <= 1) {
                        continue;
                    }
                    if (endPoints.size() != 2) {
                        System.out.println("Genetic Algorithm Induced Path: Mutation Error, endpoints != 2, value: " + endPoints.size());
                        System.out.println(Arrays.toString(indv));
                    }

                    checkIndvToCycle(indv, countVertices, countNeighbors, endPoints.get(0), endPoints.get(1));

                    int endVertex = endPoints.get(random.nextInt(2));
                    boolean add = random.nextBoolean();
                    if (add) {
                        List<Integer> validNeighbours = new ArrayList<>();
                        for (int neighbour : graph.neighbors(endVertex)) {
                            if (checkVertexToAdd(indv, neighbour)) {
                                validNeighbours.add(neighbour);
                            }
                        }
                        if (!validNeighbours.isEmpty()) {
                            indv[validNeighbours.get(random.nextInt(validNeighbours.size()))] = true;
                        }
                    } else {
                        indv[endVertex] = false;
                    }
                }
            }
        }
    }

    /**
     * Method used to search for a maximum induced path within an individual. This is used to repair individuals that
     * could possibly be not an induced path. This differs slightly from the method used to generate a random individual
     * removing the possibility of going both directions from the start vertex as it is computationally expensive and
     * not needed in this scenario since the graph created by an individual is generally smaller and less dense.
     * @param current The current vertex
     * @param indv Contains the individual (the subgraph)
     * @param validVertices Array used to validate vertices before they are added to the path
     * @param temp Holds the current induced path
     * @param tempSize Holds the length of the current induced path. Used for efficiency purposes
     * @param longestInducedPath Holds the longest induced path within the individual
     * @param longestInducedPathSize Holds the length of the longest induced path within the individual.
     * @param numberOfPaths Current number of paths generated
     * @param maxPaths Limits the number of paths generated so this method ends quickly
     * @param lastImprov Holds the last time we found an improvement
     * @param truncated If this parameter is set to true, then the execution of this method will end
     */

    void findLongestInducedPath(int current, boolean[] indv, int[] validVertices, boolean[] temp, int tempSize, boolean[] longestInducedPath, int[] longestInducedPathSize, int[] numberOfPaths, int maxPaths, int[] lastImprov, boolean[] truncated) {
        if (truncated[0])
            return;

        temp[current] = true;
        tempSize++;
        int[] neighbours = graph.neighbors(current);
        for (int neighbour : neighbours) {
            if(indv[neighbour]) {
                validVertices[neighbour]++;
            }
        }

        for (int neighbour : neighbours) {
            if (indv[neighbour] && validVertices[neighbour] == 1) {
                findLongestInducedPath(neighbour, indv, validVertices, temp, tempSize, longestInducedPath, longestInducedPathSize, numberOfPaths, maxPaths, lastImprov, truncated);
            }
        }

        for (int neighbour : neighbours) {
            if(indv[neighbour]) {
                validVertices[neighbour]--;
            }
        }

        numberOfPaths[0]++;
        if (tempSize > longestInducedPathSize[0]) {
            for(int i = 0; i < numVertices; i++) {
                longestInducedPath[i] = temp[i];
            }
            longestInducedPathSize[0] = tempSize;
            lastImprov[0] = numberOfPaths[0];
        }

        temp[current] = false;
        if (numberOfPaths[0] - lastImprov[0] > maxPaths) {
            truncated[0] = true;
        }
    }
    /**
     * Function used to repair an individual that is possibly no longer an induced path anymore. Repairing means finding
     * a maximum induced path within the individual.
     * @param indv the individual to be repaired
     * @return the individual repaired
     */

    boolean[] fixIndividual(boolean[] indv) {
        int[] validVertices = new int[numVertices];
        boolean[] longestInducedPathFound = new boolean[numVertices];
        int[] longestInducedPathSize = new int[1];
        for(int i = 0; i < numVertices; i++) {
            if(indv[i]) {
                validVertices[i] = 1;
                findLongestInducedPath(i, indv, validVertices, new boolean[numVertices], 0, longestInducedPathFound, longestInducedPathSize, new int[1], 100, new int[1], new boolean[1]);
                validVertices[i] = 0;
            }
        }
        return longestInducedPathFound;
    }

    /**
     * Function used to check if two individuals are equal.
     * @param indv1 the first individual
     * @param indv2 the second individual
     * @return true if the first individual is equal to the second one, false otherwise
     */

    private boolean checkEqualityIndividuals(boolean[] indv1, boolean[] indv2) {
        for(int i = 0; i < numVertices; i++) {
            if(indv1[i] != indv2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies the two point crossover operator for a percentage of the population set by the PROB_CROSSOVER member of
     * the class. Individuals created by this operator will need to be repaired to make sure that they are still
     * induced paths.
     * @param p the population
     */

    private void crossOver(List<boolean[]> p) {
        int populationSize = p.size();
        for(int i = 0; i < POP_SIZE; i += 2) {
            if (random.nextDouble() <= PROB_CROSSOVER) {
                int firstIndex = random.nextInt(populationSize);
                int secondIndex = random.nextInt(populationSize);
                while (secondIndex == firstIndex || checkEqualityIndividuals(p.get(firstIndex), p.get(secondIndex))) {
                    secondIndex = random.nextInt(populationSize);
                }

                boolean[] firstParent = p.get(firstIndex);
                boolean[] secondParent = p.get(secondIndex);

                int firstSplittingPoint = random.nextInt(numVertices - 1);
                int secondSplittingPoint = random.nextInt(firstSplittingPoint + 1, numVertices);

                boolean[] firstOffspring = new boolean[numVertices];
                boolean[] secondOffspring = new boolean[numVertices];

                for (int j = 0; j < numVertices; j++) {
                    if (j >= firstSplittingPoint && j <= secondSplittingPoint) {
                        firstOffspring[j] = firstParent[j] || secondParent[j];
                        secondOffspring[j] = firstParent[j] || secondParent[j];
                    } else {
                        firstOffspring[j] = firstParent[j];
                        secondOffspring[j] = secondParent[j];
                    }
                }

                p.add(fixIndividual(firstOffspring));
                p.add(fixIndividual(secondOffspring));
            }
        }
    }

    /**
     * Roulette wheel selection operator, creates a new population from the given population, where each individual
     * has a chance to be passed into the next population based on his fitness. The fitness of each individual is
     * exponentiated by the SELECTION_PRESSURE member of this class. Also saves the best individual a multiple amount
     * of times based on the value of the ELITISM member of this class.
     * @param p the current population
     * @return the new population after the selection
     */

    private List<boolean[]> selection(List<boolean[]> p) {
        int size = p.size();
        List<Double> fitnessList = new ArrayList<>(size);

        double sum = 0;
        double maxim = 0;
        int pmaxim = -1;
        for (int i = 0; i < size; ++i)
        {
            fitnessList.add(Math.pow(evaluateIndividual(p.get(i)), SELECTION_PRESSURE));
            if (maxim < fitnessList.get(i))
            {
                maxim = fitnessList.get(i);
                pmaxim = i;
            }
            sum += fitnessList.get(i);
        }

        List<Double> probabilityList = new ArrayList<>(size);
        for (int i = 0; i < size; ++i)
        {
            probabilityList.add(fitnessList.get(i) / sum);
        }

        List<Double> summedProb = new ArrayList<>(size);
        summedProb.add(probabilityList.getFirst());
        for (int i = 1; i < size; ++i)
        {
            summedProb.add(summedProb.get(i - 1) + probabilityList.get(i));
        }

        List<boolean[]> newPop = new ArrayList<>(POP_SIZE);

        for(int i = 0; i < ELITISM; i++) {
            boolean[] copyOfIndividual = Arrays.copyOf(p.get(pmaxim), p.get(pmaxim).length);
            newPop.add(copyOfIndividual);
        }

        for(int i = ELITISM; i < POP_SIZE; i++) {
            double r = random.nextDouble();
            for(int j = 0; j < size; j++) {
                if(r < summedProb.get(j)) {
                    boolean[] copyOfIndividual = Arrays.copyOf(p.get(j), p.get(j).length);
                    newPop.add(copyOfIndividual);
                    break;
                }
            }
        }
        return newPop;
    }

    /**
     * Method used to convert an induced cycle stored as an individual to the representation as a cycle.
     * @param indv the induced cycle represented as an individual within the population
     * @return the individual as a cycle
     */

    public Cycle indvToCycle(boolean[] indv) {
        Cycle cycle = new Cycle(graph);
        boolean[] visited = new boolean[numVertices];

        int currentVertex = -1;
        for(int vertex = 0; vertex < numVertices; vertex++) {
            if(indv[vertex]) {
                currentVertex = vertex;
                break;
            }
        }

        if(currentVertex == -1) {
            return cycle;
        }

        boolean found = true;
        while(found) {
            found = false;
            cycle.add(currentVertex);
            visited[currentVertex] = true;
            for(int neighbor : graph.neighbors(currentVertex)) {
                if(indv[neighbor] && !visited[neighbor]) {
                    found = true;
                    currentVertex = neighbor;
                    break;
                }
            }
        }

        if(!cycle.isInduced()) {
            System.out.println("The cycle found by the genetic algorithm is not induced!");
        }

        if(!cycle.isValid()) {
            System.out.println("The cycle found by the genetic algorithm is not valid!");
        }

        return cycle;
    }

    /**
     * The method that runs the genetic algorithm for the set amount of generations and calls the mutation operators.
     * Saves the path found in the longestInducedPath variable of the class.
     */

    private void runGeneticAlgorithm() {
        int localMaxGeneration = MAX_GENERATION;
        bestIndividual = new boolean[numVertices];
        List<boolean[]> p = generateStartingPopulation();
        while(currentGeneration < localMaxGeneration) {
            p = selection(p);
            mutate(p);
            crossOver(p);

            currentGeneration++;
            if(DYNAMIC_MAX_GENERATION &&  currentGeneration == localMaxGeneration && lastFoundImprovement + 100 >= localMaxGeneration) {
                localMaxGeneration += MAX_GENERATION_INCREASE;
            }
        }

        longestInducedCycle = indvToCycle(bestIndividual);
    }

    /**
     * Creates a graph containing a connected component
     * @param currentVertex the start (current) vertex
     * @param visited used to hold the vertices already visited
     * @param entireGraph the entire graph
     * @param connectedComponent the graph that will contain the connected component
     */

    private void createConnectedComponentUsingDFS(int currentVertex, boolean[] visited, Graph entireGraph, Graph connectedComponent) {
        connectedComponent.addVertex(connectedComponent.numVertices(), String.valueOf(currentVertex));
        visited[currentVertex] = true;

        for(int neighbor : entireGraph.neighbors(currentVertex)) {
            if(!visited[neighbor]) {
                createConnectedComponentUsingDFS(neighbor, visited, entireGraph, connectedComponent);
            }
        }
    }

    /**
     * Method use to call genetic algorithm for each connected component within the graph. The genetic algorithm
     * performance is negatively affected if the graph contains multiple connected components. It is best to create a
     * separate graph for each connected component and run the genetic algorithm for each. This is what this method does.
     */

    private void runForEachConnectedComponent() {
        Graph entireGraph = graph;
        int totalNumVertices = numVertices;
        boolean[] visited = new boolean[totalNumVertices];
        longestInducedCycle = new Cycle(entireGraph);
        for(int i = 0; i < totalNumVertices; i++) {
            if(!visited[i]) {
                Graph graphComponent = GraphBuilder.empty().buildGraph();

                createConnectedComponentUsingDFS(i, visited, entireGraph, graphComponent);

                for (Edge edge : entireGraph.edges()) {
                    int source = -1;
                    int target = -1;
                    for(int vertex : graphComponent.vertices()) {
                        if(Integer.parseInt((String)graphComponent.getVertexLabel(vertex)) == edge.source()) {
                            source = vertex;
                        }
                        if(Integer.parseInt((String)graphComponent.getVertexLabel(vertex)) == edge.target()) {
                            target = vertex;
                        }
                    }
                    if(source != -1 && target != -1) {
                        graphComponent.addEdge(source, target);
                    }
                }

                config.setGraph(graphComponent);
                config.setProcessEachComponentSeparately(false);
                Cycle componentLICFound = new LongestInducedCycleGenetic(config).getLongestInducedCycle();
                if(longestInducedCycle.size() < componentLICFound.size()) {
                    longestInducedCycle.clear();
                    for(int vertex : componentLICFound) {
                        longestInducedCycle.add(Integer.parseInt((String) graphComponent.getVertexLabel(vertex)));
                    }
                }
                longestInducedCycle.isValid();
                longestInducedCycle.isInduced();
            }
        }
        config.setGraph(entireGraph);
        config.setProcessEachComponentSeparately(true);
    }

    /**
     * When first called, it runs the genetic algorithm to compute the longest induced cycle and returns it. Subsequent
     * calls just returns the longest induced cycle already computed.
     * @return the longest induced cycle found by the genetic algorithm
     */

    @Override
    public Cycle getLongestInducedCycle() {
        if(numVertices <= 2) {
            longestInducedCycle = new Cycle(graph);
            return longestInducedCycle;
        }

        if(longestInducedCycle == null) {
            if(config.isProcessEachComponentSeparately()) {
                runForEachConnectedComponent();
            } else {
                runGeneticAlgorithm();
            }
        }
        return longestInducedCycle;
    }
}
