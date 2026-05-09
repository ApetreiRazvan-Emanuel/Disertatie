package info.uaic.licenta.demo.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import info.uaic.licenta.demo.utils.GraphUtils;
import org.graph4j.Graph;
import org.graph4j.GraphBuilder;

import java.util.List;
import java.util.Map;

public class GeneticAlgorithmConfig {
    @JsonIgnore
    private Graph graph;
    private Map<Integer, List<Integer>> adjacencyList;
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

    public GeneticAlgorithmConfig(Map<Integer, List<Integer>> adjacencyList,
                                  int popSize,
                                  int maxGeneration,
                                  boolean dynamicMaxGeneration,
                                  int maxGenerationIncrease,
                                  int elitism,
                                  double mutationRate,
                                  int mutationCount,
                                  double randomMutationRate,
                                  double probabilityCrossover,
                                  double selectionPressure,
                                  boolean processEachComponentSeparately) {
        this.adjacencyList = adjacencyList;
        this.graph = convertJsonToGraph(adjacencyList);
        this.popSize = popSize;
        this.maxGeneration = maxGeneration;
        this.dynamicMaxGeneration = dynamicMaxGeneration;
        this.maxGenerationIncrease = maxGenerationIncrease;
        this.elitism = elitism;
        this.mutationRate = mutationRate;
        this.mutationCount = mutationCount;
        this.randomMutationRate = randomMutationRate;
        this.probabilityCrossover = probabilityCrossover;
        this.selectionPressure = selectionPressure;
        this.processEachComponentSeparately = processEachComponentSeparately;
    }

    private Graph convertJsonToGraph(Map<Integer, List<Integer>> adjacencyList) {
        Graph graph = GraphBuilder.empty().buildGraph();

        for(int vertex : adjacencyList.keySet()) {
            graph.addVertex(vertex);
        }

        for(int vertex : adjacencyList.keySet()) {
            for(int neighbor : adjacencyList.get(vertex)) {
                graph.addEdge(vertex, neighbor);
            }
        }
        return graph;
    }


    public Graph getGraph() {
        if(graph == null) {
            graph = convertJsonToGraph(adjacencyList);
        }
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public int getPopSize() {
        return popSize;
    }

    public void setPopSize(int popSize) {
        this.popSize = popSize;
    }

    public boolean isDynamicMaxGeneration() {
        return dynamicMaxGeneration;
    }

    public void setDynamicMaxGeneration(boolean dynamicMaxGeneration) {
        this.dynamicMaxGeneration = dynamicMaxGeneration;
    }

    public int getMaxGenerationIncrease() {
        return maxGenerationIncrease;
    }

    public void setMaxGenerationIncrease(int maxGenerationIncrease) {
        this.maxGenerationIncrease = maxGenerationIncrease;
    }

    public int getMaxGeneration() {
        return maxGeneration;
    }

    public void setMaxGeneration(int maxGeneration) {
        this.maxGeneration = maxGeneration;
    }

    public int getElitism() {
        return elitism;
    }

    public void setElitism(int elitism) {
        this.elitism = elitism;
    }

    public double getMutationRate() {
        return mutationRate;
    }

    public void setMutationRate(double mutationRate) {
        this.mutationRate = mutationRate;
    }

    public int getMutationCount() {
        return mutationCount;
    }

    public void setMutationCount(int mutationCount) {
        this.mutationCount = mutationCount;
    }

    public double getRandomMutationRate() {
        return randomMutationRate;
    }

    public void setRandomMutationRate(double randomMutationRate) {
        this.randomMutationRate = randomMutationRate;
    }

    public double getProbabilityCrossover() {
        return probabilityCrossover;
    }

    public void setProbabilityCrossover(double probabilityCrossover) {
        this.probabilityCrossover = probabilityCrossover;
    }

    public double getSelectionPressure() {
        return selectionPressure;
    }

    public void setSelectionPressure(double selectionPressure) {
        this.selectionPressure = selectionPressure;
    }

    public boolean isProcessEachComponentSeparately() {
        return processEachComponentSeparately;
    }

    public void setProcessEachComponentSeparately(boolean processEachComponentSeparately) {
        this.processEachComponentSeparately = processEachComponentSeparately;
    }
}
