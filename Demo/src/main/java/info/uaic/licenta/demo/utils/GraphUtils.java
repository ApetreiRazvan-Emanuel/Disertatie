package info.uaic.licenta.demo.utils;

import org.graph4j.Graph;

import java.util.*;

public class GraphUtils {

    static public Map<Integer, List<Integer>> returnAdjacencyList(Graph<String, String> graph) {
        Map<Integer, List<Integer>> adjacencyList = new HashMap<>();
        for (int vertex : graph.vertices()) {
            List<Integer> neighbors = Arrays.stream(graph.neighbors(vertex)).boxed().toList();
            adjacencyList.put(vertex, neighbors);
        }
        return adjacencyList;
    }
}