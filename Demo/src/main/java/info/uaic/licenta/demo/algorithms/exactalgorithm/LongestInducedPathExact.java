package info.uaic.licenta.demo.algorithms.exactalgorithm;

import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;


public class LongestInducedPathExact extends GraphAlgorithm {
    private int numVertices;
    private Path longestInducedPath;

    public LongestInducedPathExact(Graph graph) {
        super(graph);
        longestInducedPath = new Path(graph);
        numVertices = graph.numVertices();
    }

    public LongestInducedPathExact(Graph graph, Path longestInducedPath) {
        super(graph);
        this.longestInducedPath = longestInducedPath;
        numVertices = graph.numVertices();
    }

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

    private void compute() {
        int[] invalidVertices = new int[numVertices];
        for(int i = 0; i < numVertices; i++) {
            Path inducedPath = new Path(graph);
            invalidVertices[i] = 1;
            findLongestInducedPath(i, invalidVertices, inducedPath);
            invalidVertices[i] = 0;
        }
    }

    public Path getLongestInducedPath() {
        if(longestInducedPath.isEmpty()) {
            compute();
        }
        return longestInducedPath;
    }
}
