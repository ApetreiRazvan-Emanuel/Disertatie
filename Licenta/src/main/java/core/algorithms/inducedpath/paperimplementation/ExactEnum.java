package core.algorithms.inducedpath.paperimplementation;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Edge;
import org.graph4j.GraphBuilder;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.Graph;
import org.graph4j.util.EdgeSet;
import org.graph4j.util.Path;
import org.graph4j.util.VertexSet;

public class ExactEnum extends GraphAlgorithm implements InducedPathAlgorithm {
    private Path inducedPath;
    private Path pMax;
    public ExactEnum(Graph graph) {
        super(graph);
        pMax = new Path(graph);
    }

    public ExactEnum(Graph graph, Path pMax) {
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
            inducedPathsFromVertex(graph, s, pMax, pTemp);
        }

        inducedPath = pMax;
    }

    private void inducedPathsFromVertex(Graph g, int s, Path pMax, Path pTemp) {
        if(Thread.currentThread().isInterrupted())
            return;
        pTemp.add(s);
        int[] nS = g.neighbors(s);
        if(nS.length != 0) {
            for (int t : nS) {
                VertexSet vTemp = new VertexSet(g, g.vertices());
                vTemp.remove(s);
                vTemp.removeAll(nS);
                vTemp.add(t);

                EdgeSet eTemp = new EdgeSet(g);
                for (Edge edge : g.edges()) {
                    if (vTemp.contains(edge.source()) && vTemp.contains(edge.target())) {
                        eTemp.add(edge);
                    }
                }

                Graph gTemp = GraphBuilder.vertices(vTemp.vertices()).buildGraph();
                for (int[] edge : eTemp.edges()) {
                    gTemp.addEdge(edge[0], edge[1]);
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
