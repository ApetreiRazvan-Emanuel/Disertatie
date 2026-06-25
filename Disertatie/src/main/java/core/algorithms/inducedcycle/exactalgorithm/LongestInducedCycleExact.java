package core.algorithms.inducedcycle.exactalgorithm;

import core.algorithms.inducedcycle.InducedCycleAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Cycle;
import org.graph4j.util.Path;

/**
 * Computes the maximum induced path using backtracking. Used for smaller graphs.
 *
 * @author Apetrei Razvan-Emanuel
 */

public class LongestInducedCycleExact extends GraphAlgorithm implements InducedCycleAlgorithm {
    int numVertices;
    Cycle longestInducedCycle;

    public LongestInducedCycleExact(Graph graph) {
        super(graph);
        longestInducedCycle = new Cycle(graph);
        numVertices = graph.numVertices();
    }

    /**
     * Constructor used in a multithreading environment to get the longest induced cycle found even if the algorithm
     * gets interrupted.
     * @param graph The graph.
     * @param longestInducedCycle A Cycle updated during the execution of the algorithm.
     */

    public LongestInducedCycleExact(Graph graph, Cycle longestInducedCycle) {
        super(graph);
        this.longestInducedCycle = longestInducedCycle;
        numVertices = graph.numVertices();
    }

    /**
     * Checks if the second vertex given as a parameter is a neighbour of the first vertex given as a parameter.
     * Used to check if the last node added is a neighbour of the start node to confirm that the induced path can be
     * extended into a cycle.
     * @param vertex1 First Vertex.
     * @param vertex2 Second Vertex.
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
     * Utilized to update the longest induced cycle found so far since the algorithm computes an induced path and
     * attempts to transform it in an induced cycle.
     * @param inducedPath The current induced path saved as a path.
     * @param endVertex the last vertex added to the induced path
     */

    private void updateLongestInducedCyclePath(Path inducedPath, int endVertex) {
        longestInducedCycle.clear();

        for(int vertex : inducedPath) {
            longestInducedCycle.add(vertex);
        }
        longestInducedCycle.add(endVertex);
    }

    /**
     * Finds the longest induced cycle using an algorithm that generates all induced paths from a start vertex.
     * Using these induced paths, we can save the maximum one that is also a cycle.
     * Saves the longest induced cycle found in the longestInducedCycle variable within the class.
     * InvalidVertices holds the number of neighbours each vertex has within the current induced path. If this number is
     * more than 1 (has another neighbour except the current node) that means by adding it we would obtain a cycle.
     * @param current The current vertex
     * @param start The start vertex of the induced path
     * @param invalidVertices An array that is used to verify if a vertex can be added to the graph
     * @param inducedPath A Path used to hold the induced path.
     */
    private void findLongestInducedCycle(int current, int start, int[] invalidVertices, Path inducedPath) {
        if(Thread.currentThread().isInterrupted())
            return;

        inducedPath.add(current);
        int[] neighbours = graph.neighbors(current);
        for(int neighbour : neighbours) {
            invalidVertices[neighbour]++;
        }

        for(int neighbour : neighbours) {
            if(invalidVertices[neighbour] == 1) {
                findLongestInducedCycle(neighbour, start, invalidVertices, inducedPath);
            }
            // Check if we can use this node to obtain a cycle. This node must be adjacent with the start vertex.
            // We compare invalidVertices to be 2, one from the current vertex and one from the start vertex.
            if(invalidVertices[neighbour] == 2 && checkAdjacency(neighbour, start) && inducedPath.size() >= 2) {
                // + 1 to also take into account the neighbour, we also make sure that the neighbor is a new node and
                // not from the path already, to make sure that we form a cycle
                if(inducedPath.size() + 1 > longestInducedCycle.size() && !inducedPath.contains(neighbour)) {
                    updateLongestInducedCyclePath(inducedPath, neighbour);
                }
            }
        }

        for(int neighbour : neighbours) {
            invalidVertices[neighbour]--;
        }

        inducedPath.removeFromPos(inducedPath.size() - 1);
    }

    /**
     * Calls the method to search for the longest induced cycle for each vertex in the graph.
     */

    private void compute() {
        int[] invalidVertices = new int[numVertices];
        for(int i = 0; i < numVertices; i++) {
            Path inducedPath = new Path(graph);
            invalidVertices[i] = 1;
            findLongestInducedCycle(i, i, invalidVertices, inducedPath);
            invalidVertices[i] = 0;
        }
    }

    /**
     * Method call to compute and return the longest induced cycle. Subsequent calls only returns the cycle found.
     * @return Returns the longest induced cycle in the graph.
     */
    @Override
    public Cycle getLongestInducedCycle() {
        if(longestInducedCycle.isEmpty()) {
            compute();
        }
        return longestInducedCycle;
    }
}
