package info.uaic.licenta.demo.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.graph4j.Graph;
import org.graph4j.GraphBuilder;

import java.util.List;
import java.util.Map;

public class HeuristicConfig {
    @JsonIgnore
    private Graph graph;
    private Map<Integer, List<Integer>> adjacencyList;
    private int maxPaths;

    @JsonCreator
    public HeuristicConfig(@JsonProperty("adjacencyList") Map<Integer, List<Integer>> adjacencyList, @JsonProperty("maxPaths") int maxPaths) {
        this.adjacencyList = adjacencyList;
        this.graph = convertJsonToGraph(adjacencyList);
        this.maxPaths = maxPaths;
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

    public Map<Integer, List<Integer>> getAdjacencyList() {
        return adjacencyList;
    }

    public void setAdjacencyList(Map<Integer, List<Integer>> adjacencyList) {
        this.adjacencyList = adjacencyList;
    }

    @JsonIgnore
    public Graph getGraph() {
        if(graph == null) {
            graph = convertJsonToGraph(adjacencyList);
        }
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public int getMaxPaths() {
        return maxPaths;
    }

    public void setMaxPaths(int maxPaths) {
        this.maxPaths = maxPaths;
    }
}
