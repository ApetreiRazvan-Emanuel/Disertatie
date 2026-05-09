package core.algorithms.inducedpath.paperimplementation;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Edge;
import org.graph4j.Graph;
import org.graph4j.GraphBuilder;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;
import org.graph4j.util.VertexSet;

/**
 * A version modified by me to be slightly more efficient, still close to the paper pseudocode.
 */
public class ExactEnumOptimized extends GraphAlgorithm implements InducedPathAlgorithm {
    private Path inducedPath;
    private Path pMax;

    public ExactEnumOptimized(Graph graph) {
        super(graph);
        pMax = new Path(graph);
    }

    public ExactEnumOptimized(Graph graph, Path pMax) {
        super(graph);
        this.pMax = pMax;
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
            inducedPathsFromVertex(graph, s, pMax, pTemp);
        }
//        inducedPathsFromVertex(graph, 0, pMax, pTemp);

        inducedPath = pMax;
    }

    private void inducedPathsFromVertex(Graph g, int s, Path pMax, Path pTemp) {
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

                inducedPathsFromVertex(gTemp, t, pMax, pTemp);
            }
        } else {
            if(pTemp.size() > pMax.size()) {
                pMax.clear();
                pMax.addAll(pTemp.vertices());
            }
        }
        pTemp.removeFromPos(pTemp.size() - 1);
    }
}
