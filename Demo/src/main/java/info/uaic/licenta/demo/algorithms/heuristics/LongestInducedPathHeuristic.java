package info.uaic.licenta.demo.algorithms.heuristics;

import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;
import org.graph4j.util.VertexSet;

import java.util.ArrayDeque;
import java.util.Queue;

public class LongestInducedPathHeuristic extends GraphAlgorithm {
    private int numVertices;
    private int maxPaths = 400000; // 200000, 10000
    Path longestInducedPath;

    public LongestInducedPathHeuristic(Graph graph) {
        super(graph);
        longestInducedPath = new Path(graph);
        numVertices = graph.numVertices();
    }

    public LongestInducedPathHeuristic(Graph graph, int maxPaths) {
        super(graph);
        longestInducedPath = new Path(graph);
        numVertices = graph.numVertices();
        this.maxPaths = maxPaths;
    }

    public LongestInducedPathHeuristic(Graph graph, Path longestInducedPath) {
        super(graph);
        this.longestInducedPath = longestInducedPath;
        numVertices = graph.numVertices();
    }
    private int getEccentricityBFS(int startNode) {
        Queue<Integer> queue = new ArrayDeque<>();
        boolean[] visited = new boolean[numVertices];
        int[] distance = new int[numVertices];

        queue.offer(startNode);
        visited[startNode] = true;
        distance[startNode] = 0;

        int maxDistance = 0;

        while (!queue.isEmpty()) {
            int currentNode = queue.poll();
            for (int neighbor : graph.neighbors(currentNode)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    distance[neighbor] = distance[currentNode] + 1;
                    maxDistance = Math.max(maxDistance, distance[neighbor]);
                    queue.offer(neighbor);
                }
            }
        }

        return maxDistance;
    }

    public int[] getEccentricitiesPairArray() {
        int[] eccentricities = new int[numVertices];

        for (int startNode = 0; startNode < numVertices; startNode++) {
            eccentricities[startNode] = getEccentricityBFS(startNode);
        }

        return eccentricities;
    }

    private void findLongestInducedPathHeuristic(int startVertex, int current, int[] invalidVertices, Path inducedPath, int[] numberOfPaths, int[] lastImprov, boolean[] truncated, boolean backwards) {
        if(Thread.currentThread().isInterrupted() || truncated[0])
            return;

        inducedPath.add(current);
        int[] neighbours = graph.neighbors(current);
        for(int neighbour : neighbours) {
            invalidVertices[neighbour]++;
        }

        boolean foundNeighbour = false;
        for(int neighbour : neighbours) {
            if(invalidVertices[neighbour] == 1) {
                foundNeighbour = true;
                findLongestInducedPathHeuristic(startVertex, neighbour, invalidVertices, inducedPath, numberOfPaths, lastImprov, truncated, backwards);
            }
        }

        if(!foundNeighbour) {
            if(!backwards) {
                for(int startingNodeNeighbour : graph.neighbors(startVertex)) {
                    if(invalidVertices[startingNodeNeighbour] == 1) {
                        int[] numberOfPathsBackwards = new int[1];
                        numberOfPathsBackwards[0] = maxPaths - 50;
                        boolean[] truncatedBackwards = new boolean[1];
                        int[] lastImprovBackwards = new int[1];
                        findLongestInducedPathHeuristic(startVertex, startingNodeNeighbour, invalidVertices, inducedPath, numberOfPathsBackwards, lastImprovBackwards, truncatedBackwards, true);
                    }
                }
            }

            numberOfPaths[0]++;

            if(inducedPath.size() > longestInducedPath.size()) {
                longestInducedPath.clear();
                longestInducedPath.addAll(inducedPath.vertices());
                lastImprov[0] = numberOfPaths[0];
            }

            if(numberOfPaths[0] - lastImprov[0] > maxPaths) {
                truncated[0] = true;
            }
        }

        for(int neighbour : neighbours) {
            invalidVertices[neighbour]--;
        }

        inducedPath.removeFromPos(inducedPath.size() - 1);
    }

    private void findLongestInducedPathSmallGraph(int current, int[] invalidVertices, Path inducedPath) {
        if(Thread.currentThread().isInterrupted())
            return;

        inducedPath.add(current);
        int[] neighbours = graph.neighbors(current);
        for(int neighbour : neighbours) {
            invalidVertices[neighbour]++;
        }

        for(int neighbour : neighbours) {
            if(invalidVertices[neighbour] == 1) {
                findLongestInducedPathSmallGraph(neighbour, invalidVertices, inducedPath);
            }
        }

        if(inducedPath.size() > longestInducedPath.size()) {
            longestInducedPath.clear();
            longestInducedPath.addAll(inducedPath.vertices());
        }

        for(int neighbour : neighbours) {
            invalidVertices[neighbour]--;
        }

        inducedPath.removeFromPos(inducedPath.size() - 1);
    }

    private void computeSmallGraph() {
        int[] validVertices = new int[numVertices];
        Path inducedPath = new Path(graph);
        for(int vertex : graph.vertices()) {
            validVertices[vertex] = 1;
            findLongestInducedPathSmallGraph(vertex, validVertices, inducedPath);
            validVertices[vertex] = 0;
        }
    }

    private void computeLargeGraph() {
        int[] eccentricities = getEccentricitiesPairArray();
        int max = 0;
        int pos = 0;
        for(int i = 0; i < numVertices; i++) {
            if(eccentricities[i] > max) {
                max = eccentricities[i];
                pos = i;
            }
        }
        int[] invalidVertices = new int[numVertices];
        Path inducedPath = new Path(graph);
        invalidVertices[pos] = 1;
        findLongestInducedPathHeuristic(pos, pos, invalidVertices, inducedPath, new int[1], new int[1], new boolean[1], false);
        invalidVertices[pos] = 0;
        VertexSet copy = new VertexSet(graph);
        copy.addAll(longestInducedPath.vertices());
        copy.remove(pos);
        for(int vertex : copy) {
            invalidVertices[vertex] = 1;
            findLongestInducedPathHeuristic(vertex, vertex, invalidVertices, inducedPath, new int[1], new int[1], new boolean[1], false);
            invalidVertices[vertex] = 0;
        }
    }

    private void compute() {
        if(graph.numVertices() * graph.numEdges() < 250 * 250) {
            computeSmallGraph();
        } else {
            computeLargeGraph();
        }
    }

    public Path getLongestInducedPath() {
        if(longestInducedPath.isEmpty()) {
            compute();
        }
        return longestInducedPath;
    }
}
