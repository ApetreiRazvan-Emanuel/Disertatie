package core.algorithms.inducedpath.heuristics;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;
import org.graph4j.util.VertexSet;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Computes the longest induced path within a graph using backtracking with a limit on the paths generated.
 * @author Apetrei Razvan-Emanuel
 */

public class LongestInducedPathHeuristic extends GraphAlgorithm implements InducedPathAlgorithm {
    private int numVertices;
    private int maxPaths = 400000; // 200000, 10000
    Path longestInducedPath;

    public LongestInducedPathHeuristic(Graph graph) {
        super(graph);
        longestInducedPath = new Path(graph);
        numVertices = graph.numVertices();
    }

    /**
     * Constructor used to modify the default of paths generated.
     * @param graph the graph
     * @param maxPaths the maximum amount of induced paths that will be generated for each vertex
     */

    public LongestInducedPathHeuristic(Graph graph, int maxPaths) {
        super(graph);
        longestInducedPath = new Path(graph);
        numVertices = graph.numVertices();
        this.maxPaths = maxPaths;
    }

    /**
     * Constructor used in a multithreading environment to get the longest induced path found even if the algorithm
     * gets interrupted.
     * @param graph The graph.
     * @param longestInducedPath A Path updated during the execution of the algorithm.
     */

    public LongestInducedPathHeuristic(Graph graph, Path longestInducedPath) {
        super(graph);
        this.longestInducedPath = longestInducedPath;
        numVertices = graph.numVertices();
    }

    /**
     * Computes the Eccentricity of a vertex. The eccentricity is the maximum distance from a given vertex to any other
     * vertex in a graph.
     * @param startNode the vertex
     * @return the eccentricity of the vertex
     */

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

    /**
     * Returns an array which will contain the eccentricities of each node.
     * @return an array that contains the eccentricities of each node
     */

    public int[] getEccentricitiesPairArray() {
        int[] eccentricities = new int[numVertices];

        for (int startNode = 0; startNode < numVertices; startNode++) {
            eccentricities[startNode] = getEccentricityBFS(startNode);
        }

        return eccentricities;
    }

    /**
     * Computes the induced path using a heuristic. The heuristic is adding a limit to the number of paths generated
     * with backtracking. This algorithm returns to the start vertex once it can't extend the path anymore to attempt
     * to extend the induced path in another direction.
     * @param startVertex the starting vertex of the induced path
     * @param current the current vertex
     * @param invalidVertices an array used to validate vertices when they are added to the induced path
     * @param inducedPath used to hold the current induced path
     * @param numberOfPaths used to hold the current amount of paths generated
     * @param lastImprov used to keep track of the last time we've found an improvement
     * @param truncated if this is set to true, then the method will stop
     * @param backwards used to keep track of the direction of the search
     */

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

    /**
     * Computes the longest induced path using backtracking. Method called when the graph is small since the heuristic
     * takes longer on small instances.
     * @param current the current vertex
     * @param invalidVertices an array used to validate vertices when they are added to the induced path
     * @param inducedPath used to hold the current induced path
     */

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

    /**
     * Calls a method to find the longest induced path for each vertex in the graph.
     */

    private void computeSmallGraph() {
        int[] validVertices = new int[numVertices];
        Path inducedPath = new Path(graph);
        for(int vertex : graph.vertices()) {
            validVertices[vertex] = 1;
            findLongestInducedPathSmallGraph(vertex, validVertices, inducedPath);
            validVertices[vertex] = 0;
        }
    }

    /**
     * Calculates the eccentricities of each node with another method. Takes the node with the highest eccentricity,
     * calculates the maximum induced path from that node using findLongestInducedPathHeuristic method then calls
     * findLongestInducedPathHeuristic for each node within that maximum induced path, except the first one as it is
     * the initial node with the highest eccentricity.
     */

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

    /**
     * Based on the graph size, it computes the longest induced path with the exact algorithm or the heuristic.
     */

    private void compute() {
        if(graph.numVertices() * graph.numEdges() < 250 * 250) {
            computeSmallGraph();
        } else {
            computeLargeGraph();
        }
    }

    /**
     * Calculates the longest induced path on the first call and returns it. On subsequent calls, it only returns the
     * longest induced path already calculated.
     * @return the longest induced path of the graph found by the algorithm
     */

    @Override
    public Path getLongestInducedPath() {
        if(longestInducedPath.isEmpty()) {
            compute();
        }
        return longestInducedPath;
    }
}
