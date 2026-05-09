package core.algorithms.inducedpath.exactalgorithm;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

/**
 * Computes the longest induced path within a graph using backtracking. Used mostly for smaller graphs.
 * @author Apetrei Razvan-Emanuel
 */

public class LongestInducedPathExact extends GraphAlgorithm implements InducedPathAlgorithm {
    private int numVertices;
    private Path longestInducedPath;

    public LongestInducedPathExact(Graph graph) {
        super(graph);
        longestInducedPath = new Path(graph);
        numVertices = graph.numVertices();
    }

    /**
     * Constructor used in a multithreading environment to get the longest induced path found even if the algorithm
     * gets interrupted.
     * @param graph The graph.
     * @param longestInducedPath A Path updated during the execution of the algorithm.
     */

    public LongestInducedPathExact(Graph graph, Path longestInducedPath) {
        super(graph);
        this.longestInducedPath = longestInducedPath;
        numVertices = graph.numVertices();
    }

    /**
     * The method used to compute all induced paths starting from a vertex using backtracking. The main idea is to
     * increment in an array all the neighbours of the current vertex preventing them from being added to the path
     * in the future. If another neighbour of a vertex would be added in the future, then we would obtain a cycle.
     * @param current The current vertex.
     * @param invalidVertices An array used to validate each vertex if it can be added to the current path.
     * @param inducedPath The current induced path.
     */

    private void findLongestInducedPath(int current, int[] invalidVertices, Path inducedPath) {
        if(Thread.currentThread().isInterrupted())
            return;

        inducedPath.add(current);
        int[] neighbours = graph.neighbors(current);
        for(int neighbour : neighbours) {
            invalidVertices[neighbour]++;
        }

        for(int neighbour : neighbours) {
            if(invalidVertices[neighbour] == 1) {
                findLongestInducedPath(neighbour, invalidVertices, inducedPath);
            }
        }

        for(int neighbour : neighbours) {
            invalidVertices[neighbour]--;
        }

        if(inducedPath.size() > longestInducedPath.size()) {
            longestInducedPath.clear();
            longestInducedPath.addAll(inducedPath.vertices());
        }

        inducedPath.removeFromPos(inducedPath.size() - 1);
    }

    /**
     * Calls the method to compute generate all induced paths starting from each vertex. We increment invalidVertices
     * for the starting node to prevent it from being added twice.
     */

    private void compute() {
        int[] invalidVertices = new int[numVertices];
        for(int i = 0; i < numVertices; i++) {
            Path inducedPath = new Path(graph);
            invalidVertices[i] = 1;
            findLongestInducedPath(i, invalidVertices, inducedPath);
            invalidVertices[i] = 0;
        }
    }

    /**
     *
     * @return the longest induced path within the graph.
     */
    @Override
    public Path getLongestInducedPath() {
        if(longestInducedPath.isEmpty()) {
            compute();
        }
        return longestInducedPath;
    }
}
