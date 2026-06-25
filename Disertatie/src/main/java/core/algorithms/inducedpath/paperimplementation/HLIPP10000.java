package core.algorithms.inducedpath.paperimplementation;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Edge;
import org.graph4j.Graph;
import org.graph4j.GraphBuilder;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

public class HLIPP10000 extends GraphAlgorithm implements InducedPathAlgorithm {
    private Path inducedPath;
    private int maxPaths = 10000;
    private Path pMax;

    public HLIPP10000(Graph graph) {
        super(graph);
        pMax = new Path(graph);
    }

    public HLIPP10000(Graph graph, Path pMax) {
        super(graph);
        this.pMax = pMax;
    }

    public int getMaxPaths() {
        return maxPaths;
    }

    public void setMaxPaths(int maxPaths) {
        this.maxPaths = maxPaths;
    }

    public Path getLongestInducedPath() {
        if(inducedPath != null) {
            return inducedPath;
        }
        compute();
        return inducedPath;
    }

    private void compute() {
        Path pTemp = new Path(graph);


        for(int s : graph.vertices()) {
            if(Thread.currentThread().isInterrupted())
                return;
            firstInducedPaths(graph, s, pMax, pTemp, new int[1], new int[1], new boolean[1]);
        }

        inducedPath = pMax;
    }

    private void firstInducedPaths(Graph g, int s, Path pMax, Path pTemp, int[] numberOfPaths, int[] lastImprov, boolean[] truncated) {
        if(Thread.currentThread().isInterrupted())
            return;
        pTemp.add(s);
        int[] nS = g.neighbors(s);
        if(nS.length != 0) {
            for (int t : nS) {
                Graph gTemp = GraphBuilder.vertices(g.vertices().clone()).buildGraph();
                gTemp.removeVertices(nS);
                gTemp.removeVertex(s);
                gTemp.addVertex(t);
                for (Edge edge : g.edges()) {
                    if (gTemp.containsVertex(edge.source()) && gTemp.containsVertex(edge.target())) {
                        gTemp.addEdge(edge);
                    }
                }

                firstInducedPaths(gTemp, t, pMax, pTemp, numberOfPaths, lastImprov, truncated);
                if(truncated[0]) {
                    return;
                }
            }
        } else {
            numberOfPaths[0] += 1;
            if(pTemp.size() > pMax.size()) {
                pMax.clear();
                pMax.addAll(pTemp.vertices());
                lastImprov[0] = numberOfPaths[0];
            }
            if(numberOfPaths[0] - lastImprov[0] > maxPaths) {
                pTemp.clear();
                truncated[0] = true;
                return;
            }
        }
        pTemp.removeFromPos(pTemp.size() - 1);
    }
}
