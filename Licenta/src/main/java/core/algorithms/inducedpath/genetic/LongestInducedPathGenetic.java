package core.algorithms.inducedpath.genetic;

import core.algorithms.GeneticAlgorithmConfig;
import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Edge;
import org.graph4j.Graph;
import org.graph4j.GraphBuilder;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A genetic algorithm designed to find the maximum induced path within a graph.
 *
 * @author Apetrei Razvan-Emanuel
 */

public class LongestInducedPathGenetic extends GraphAlgorithm implements InducedPathAlgorithm {
    private Path longestInducedPath;
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
    private double bestValue = 0;
    private final Random random = new Random();

    private final GeneticAlgorithmConfig config;

    /**
     * Initializes all the parameters of the genetic algorithm with default values
     * @param graph the graph
     */

    LongestInducedPathGenetic(Graph graph) {
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

    public LongestInducedPathGenetic(GeneticAlgorithmConfig geneticAlgorithmConfig) {
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
     * @param startVertex The starting vertex
     * @param currentVertex The current vertex
     * @param temp Holds the current induced path
     * @param tempSize Holds the length of the current induced path. Used for efficiency purposes
     * @param individual Contains the best induced path, the individual generated is returned through this variable
     * @param individualSize Contains the length of the best induced path so far
     * @param validVertices Array used to validate vertices before they are added to the path
     * @param numberOfPaths Current number of paths generated
     * @param maximumNumberOfPaths Limits the number of paths generated so this method ends quickly
     * @param maximumNumberOfPathsBackwards Limits the number of paths generated when the algorithm returns to the start
     * @param lastImprov Holds the last time we found an improvement
     * @param truncated If this parameter is set to true, then the execution of this method will end
     * @param backwards Used to identify the current direction of the search.
     */

    private void inducedPathsFromVertex(int startVertex, int currentVertex, boolean[] temp, int tempSize, boolean[] individual, int[] individualSize, int[] validVertices, int[] numberOfPaths, int maximumNumberOfPaths, int maximumNumberOfPathsBackwards, int[] lastImprov, boolean[] truncated, boolean backwards) {
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

            inducedPathsFromVertex(startVertex, neighbour, temp, tempSize, individual, individualSize, validVertices, numberOfPaths, maximumNumberOfPaths, maximumNumberOfPathsBackwards, lastImprov, truncated, backwards);
        }

        if(!foundNeighbour) {
            if(!backwards) {
                List<Integer> startingNodeNeighboursList = new ArrayList<>();
                for(int startingNodeNeighbour : graph.neighbors(startVertex)) {
                    if(validVertices[startingNodeNeighbour] == 1) {
                        startingNodeNeighboursList.add(startingNodeNeighbour);
                    }
                }

                while(!startingNodeNeighboursList.isEmpty()) {
                    int startingNodeNeighbourIndex = random.nextInt(startingNodeNeighboursList.size());
                    int startingNodeNeighbour = startingNodeNeighboursList.get(startingNodeNeighbourIndex);
                    startingNodeNeighboursList.remove(startingNodeNeighbourIndex);
                    int[] numberOfPathsBackwards = new int[1];
                    boolean[] truncatedBackwards = new boolean[1];
                    int[] lastImprovBackwards = new int[1];
                    inducedPathsFromVertex(startVertex, startingNodeNeighbour, temp, tempSize, individual, individualSize, validVertices, numberOfPathsBackwards, maximumNumberOfPathsBackwards, maximumNumberOfPathsBackwards, lastImprovBackwards, truncatedBackwards, true);
                }
            }

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
        inducedPathsFromVertex(startVertex, startVertex, new boolean[numVertices], 0, individual, new int[1], validVertices, new int[1], 100, 20, new int[1], new boolean[1], false);
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

    private double evaluateIndividual(boolean[] indv) {
        double fitness = 0;
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
                    int[] countNeighbours = new int[numVertices];
                    int countVertices = 0;
                    for (int vertex = 0; vertex < numVertices; vertex++) {
                        if (indv[vertex]) {
                            countVertices++;
                            for (int neighbour : graph.neighbors(vertex)) {
                                if (indv[neighbour]) {
                                    countNeighbours[vertex]++;
                                }
                            }
                            if (countNeighbours[vertex] == 1) {
                                endPoints.add(vertex);
                            }
                            if (countNeighbours[vertex] > 2) {
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
                while (secondIndex == firstIndex && !checkEqualityIndividuals(p.get(firstIndex), p.get(secondIndex))) {
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
            if(fitnessList.get(i) > bestValue) {
                bestValue = fitnessList.get(i);
                lastFoundImprovement = currentGeneration;
                bestIndividual = Arrays.copyOf(p.get(i), p.get(i).length);
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
     * Method used to convert an induced path stored as an individual to the representation as a vertex list.
     * @param indv the induced path represented as an individual within the population
     * @return the individual as a vertex list
     */

    public Path indvToPath(boolean[] indv) {
        List<Integer> endPoints = new ArrayList<>();
        int[] countNeighbours = new int[numVertices];
        int countVertices = 0;
        for(int vertex = 0; vertex < numVertices; vertex++) {
            if(indv[vertex]) {
                countVertices++;
                for (int neighbour : graph.neighbors(vertex)) {
                    if(indv[neighbour]) {
                        countNeighbours[vertex]++;
                    }
                }
                if(countNeighbours[vertex] == 1) {
                    endPoints.add(vertex);
                }
                if(countNeighbours[vertex] > 2) {
                    System.out.println("Genetic Algorithm error: The longest induced path has " +
                                       "a vertex with more than 2 neighbours!");
                }
            }
        }

        if(endPoints.size() != 2 && countVertices > 1) {
            System.out.println("Genetic Algorithm error: The longest induced path found doesn't have two endpoints! " +
                               "Number of endpoints: " + endPoints.size());
        }

        Path path = new Path(graph, countVertices);

        int current = endPoints.get(0);
        boolean found = true;
        boolean[] visited = new boolean[numVertices];
        while(found) {
            path.add(current);
            visited[current] = true;
            found = false;
            for(int neighbour : graph.neighbors(current)) {
                if(indv[neighbour] && !visited[neighbour]) {
                    current = neighbour;
                    found = true;
                }
            }
        }

        return path;
    }

    /**
     * The method that runs the genetic algorithm for the set amount of generations and calls the mutation operators.
     * Saves the path found in the longestInducedPath variable of the class.
     */

    private void runGeneticAlgorithm() {
        int localMaxGeneration = MAX_GENERATION;
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

        longestInducedPath = indvToPath(bestIndividual);
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
        longestInducedPath = new Path(entireGraph);
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
                Path componentLIPFound = new LongestInducedPathGenetic(config).getLongestInducedPath();
                if(longestInducedPath.size() < componentLIPFound.size()) {
                    longestInducedPath.clear();
                    for(int vertex : componentLIPFound) {
                        longestInducedPath.add(Integer.parseInt((String) graphComponent.getVertexLabel(vertex)));
                    }
                }
                longestInducedPath.isValid();
                longestInducedPath.isInduced();
            }
        }
        config.setGraph(entireGraph);
        config.setProcessEachComponentSeparately(true);
    }

    /**
     * When first called, it runs the genetic algorithm to compute the longest induced path and returns it. Subsequent
     * calls just returns the longest induced path already computed.
     * @return the longest induced path found by the genetic algorithm
     */

    @Override
    public Path getLongestInducedPath() {
        if(numVertices <= 2) {
            longestInducedPath = new Path(graph);
            longestInducedPath.addAll(graph.vertices());
            return longestInducedPath;
        }

        if(longestInducedPath == null) {
            if(config.isProcessEachComponentSeparately()) {
                runForEachConnectedComponent();
            } else {
                runGeneticAlgorithm();
            }
        }
        return longestInducedPath;
    }
}
