package old.geneticOldVariants;

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

    boolean[] generateRandomIndividual() {
        boolean[] individual = new boolean[numVertices];
        int[] validVertices = new int[numVertices];
        int startVertex = random.nextInt(numVertices);
        validVertices[startVertex] = 1;
        inducedPathsFromVertex(startVertex, startVertex, new boolean[numVertices], 0, individual, new int[1], validVertices, new int[1], 100, 20, new int[1], new boolean[1], false);
        return individual;
    }

    List<boolean[]> generateStartingPopulation() {
        List<boolean[]> population = new ArrayList<>();
        for(int i = 0; i < POP_SIZE; i++) {
            population.add(generateRandomIndividual());
        }
        return population;
    }

    private double evaluateIndividual(boolean[] indv) {
        double fitness = 0;
        for(int i = 0; i < numVertices; i++) {
            if(indv[i]) {
                fitness++;
            }
        }
        return fitness;
    }

    boolean checkVertexToAdd(boolean[] indv, int vertex) {
        int countNeighbours = 0;
        for(int neighbour : graph.neighbors(vertex)) {
            if(indv[neighbour]) {
                countNeighbours++;
            }
        }
        return countNeighbours == 1;
    }

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
                        }
                    }
                    if(countVertices <= 1) {
                        continue;
                    }
                    if (endPoints.size() != 2) {
                        continue;
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

    private boolean checkEqualityIndividuals(boolean[] indv1, boolean[] indv2) {
        for(int i = 0; i < numVertices; i++) {
            if(indv1[i] != indv2[i]) {
                return false;
            }
        }
        return true;
    }

    private void crossOver(List<boolean[]> p) {
        int populationSize = p.size();
        for(int i = 0; i < POP_SIZE; i += 2) {
            if (random.nextDouble() <= PROB_CROSSOVER) {
                int firstIndex = random.nextInt(populationSize);
                int secondIndex = random.nextInt(populationSize);
                int attempts = 0;
                while (secondIndex == firstIndex && attempts < 10) {
                    secondIndex = random.nextInt(populationSize);
                    attempts++;
                }
                if (secondIndex == firstIndex) continue;

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

    private List<boolean[]> selection(List<boolean[]> p) {
        int size = p.size();
        List<Double> fitnessList = new ArrayList<>(size);

        double sum = 0;
        double maxim = 0;
        int pmaxim = -1;
        for (int i = 0; i < size; ++i) {
            fitnessList.add(Math.pow(evaluateIndividual(p.get(i)), SELECTION_PRESSURE));
            if (maxim < fitnessList.get(i)) {
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
        for (int i = 0; i < size; ++i) {
            probabilityList.add(fitnessList.get(i) / sum);
        }

        List<Double> summedProb = new ArrayList<>(size);
        summedProb.add(probabilityList.getFirst());
        for (int i = 1; i < size; ++i) {
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
            }
        }

        if(endPoints.size() != 2 && countVertices > 1) {
            return new Path(graph);
        }
        if(countVertices <= 1) {
            Path path = new Path(graph);
            for(int i = 0; i < numVertices; i++) {
                if(indv[i]) path.add(i);
            }
            return path;
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

    private void runGeneticAlgorithm() {
        longestInducedPath = new Path(graph);
        int localMaxGeneration = MAX_GENERATION;
        List<boolean[]> p = generateStartingPopulation();
        while(currentGeneration < localMaxGeneration) {
            p = selection(p);
            mutate(p);
            crossOver(p);

            currentGeneration++;
            if(DYNAMIC_MAX_GENERATION && currentGeneration == localMaxGeneration && lastFoundImprovement + 100 >= localMaxGeneration) {
                localMaxGeneration += MAX_GENERATION_INCREASE;
            }
        }

        if (bestIndividual != null) {
            longestInducedPath = indvToPath(bestIndividual);
        }
    }

    @Override
    public Path getLongestInducedPath() {
        if(numVertices <= 2) {
            longestInducedPath = new Path(graph);
            longestInducedPath.addAll(graph.vertices());
            return longestInducedPath;
        }

        if(longestInducedPath == null) {
            runGeneticAlgorithm();
        }
        return longestInducedPath;
    }
}
