package core.algorithms.inducedpath.ilp;

import com.gurobi.gurobi.*;
import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.*;

public class LongestInducedPathILP extends GraphAlgorithm implements InducedPathAlgorithm {
    private final int numVertices;
    private Path longestInducedPath;

    public LongestInducedPathILP(Graph graph) {
        super(graph);
        this.numVertices = graph.numVertices();
        this.longestInducedPath = new Path(graph);
    }

    public LongestInducedPathILP(Graph graph, Path longestInducedPath) {
        super(graph);
        this.numVertices = graph.numVertices();
        this.longestInducedPath = longestInducedPath;
    }

    private void solve() {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            GRBModel model = new GRBModel(env);

            int[] vertices = graph.vertices();

            List<int[]> edges = new ArrayList<>();
            for (int i = 0; i < numVertices; i++) {
                for (int nb : graph.neighbors(vertices[i])) {
                    int j = graph.indexOf(nb);
                    if (j > i) edges.add(new int[]{i, j});
                }
            }
            int numEdges = edges.size();

            GRBVar[] x = new GRBVar[numVertices];
            for (int i = 0; i < numVertices; i++) {
                x[i] = model.addVar(0, 1, 0, GRB.BINARY, "x_" + i);
            }

            GRBVar[] y = new GRBVar[numEdges];
            for (int e = 0; e < numEdges; e++) {
                y[e] = model.addVar(0, 1, 0, GRB.BINARY, "y_" + e);
            }

            GRBLinExpr objective = new GRBLinExpr();
            for (int i = 0; i < numVertices; i++) {
                objective.addTerm(1.0, x[i]);
            }
            model.setObjective(objective, GRB.MAXIMIZE);

            for (int e = 0; e < numEdges; e++) {
                int u = edges.get(e)[0], v = edges.get(e)[1];
                model.addConstr(y[e], GRB.LESS_EQUAL, x[u], "link_u_" + e);
                model.addConstr(y[e], GRB.LESS_EQUAL, x[v], "link_v_" + e);
                GRBLinExpr induced = new GRBLinExpr();
                induced.addTerm(1.0, x[u]);
                induced.addTerm(1.0, x[v]);
                induced.addTerm(-1.0, y[e]);
                model.addConstr(induced, GRB.LESS_EQUAL, 1.0, "induced_" + e);
            }

            List<List<Integer>> incidentEdges = new ArrayList<>();
            for (int i = 0; i < numVertices; i++) incidentEdges.add(new ArrayList<>());
            for (int e = 0; e < numEdges; e++) {
                incidentEdges.get(edges.get(e)[0]).add(e);
                incidentEdges.get(edges.get(e)[1]).add(e);
            }

            for (int i = 0; i < numVertices; i++) {
                GRBLinExpr deg = new GRBLinExpr();
                for (int e : incidentEdges.get(i)) deg.addTerm(1.0, y[e]);
                model.addConstr(deg, GRB.LESS_EQUAL, 2.0, "deg_" + i);
            }

            GRBLinExpr sumY = new GRBLinExpr();
            for (int e = 0; e < numEdges; e++) sumY.addTerm(1.0, y[e]);
            GRBLinExpr sumXm1 = new GRBLinExpr();
            for (int i = 0; i < numVertices; i++) sumXm1.addTerm(1.0, x[i]);
            sumXm1.addConstant(-1.0);
            model.addConstr(sumY, GRB.EQUAL, sumXm1, "tree_edges");

            model.set(GRB.IntParam.LazyConstraints, 1);

            model.setCallback(new GRBCallback() {
                @Override
                protected void callback() {
                    if (where == GRB.CB_POLLING) {
                        if (Thread.currentThread().isInterrupted()) {
                            abort();
                        }
                    }
                    if (where == GRB.CB_MIPSOL) {
                        try {
                            double[] xVals = getSolution(x);
                            double[] yVals = getSolution(y);

                            boolean[] sel = new boolean[numVertices];
                            for (int i = 0; i < numVertices; i++) sel[i] = xVals[i] > 0.5;

                            List<List<Integer>> adj = new ArrayList<>();
                            for (int i = 0; i < numVertices; i++) adj.add(new ArrayList<>());
                            for (int e = 0; e < numEdges; e++) {
                                if (yVals[e] > 0.5) {
                                    adj.get(edges.get(e)[0]).add(edges.get(e)[1]);
                                    adj.get(edges.get(e)[1]).add(edges.get(e)[0]);
                                }
                            }

                            boolean[] visited = new boolean[numVertices];
                            boolean hasCycle = false;

                            for (int i = 0; i < numVertices; i++) {
                                if (!sel[i] || visited[i]) continue;

                                List<Integer> comp = new ArrayList<>();
                                Queue<Integer> q = new LinkedList<>();
                                q.add(i);
                                visited[i] = true;
                                while (!q.isEmpty()) {
                                    int v = q.poll();
                                    comp.add(v);
                                    for (int nb : adj.get(v)) {
                                        if (!visited[nb]) {
                                            visited[nb] = true;
                                            q.add(nb);
                                        }
                                    }
                                }

                                Set<Integer> compSet = new HashSet<>(comp);
                                List<Integer> compEdgeIdx = new ArrayList<>();
                                for (int e = 0; e < numEdges; e++) {
                                    if (yVals[e] > 0.5 && compSet.contains(edges.get(e)[0]) && compSet.contains(edges.get(e)[1])) {
                                        compEdgeIdx.add(e);
                                    }
                                }

                                if (compEdgeIdx.size() >= comp.size()) {
                                    hasCycle = true;
                                    GRBLinExpr cut = new GRBLinExpr();
                                    for (int e : compEdgeIdx) cut.addTerm(1.0, y[e]);
                                    addLazy(cut, GRB.LESS_EQUAL, comp.size() - 1);
                                }
                            }

                            if (!hasCycle) {
                                updateSharedPath(xVals, vertices);
                            }
                        } catch (GRBException ignored) {}
                    }
                }
            });

            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.OPTIMAL || status == GRB.SUBOPTIMAL ||
                status == GRB.INTERRUPTED || status == GRB.TIME_LIMIT) {
                if (model.get(GRB.IntAttr.SolCount) > 0) {
                    double[] xVals = model.get(GRB.DoubleAttr.X, x);
                    updateSharedPath(xVals, vertices);
                }
            }

            model.dispose();
            env.dispose();
        } catch (GRBException e) {
            System.err.println("ILP Error: " + e.getMessage());
        }
    }

    private synchronized void updateSharedPath(double[] xVals, int[] vertices) {
        int count = 0;
        for (int i = 0; i < numVertices; i++) {
            if (xVals[i] > 0.5) count++;
        }
        if (count <= longestInducedPath.size()) return;

        boolean[] selected = new boolean[numVertices];
        for (int i = 0; i < numVertices; i++) selected[i] = xVals[i] > 0.5;

        int[] degree = new int[numVertices];
        int startVertex = -1;
        for (int i = 0; i < numVertices; i++) {
            if (!selected[i]) continue;
            for (int nb : graph.neighbors(vertices[i])) {
                int nbIdx = graph.indexOf(nb);
                if (selected[nbIdx]) degree[i]++;
            }
            if (degree[i] <= 1) startVertex = i;
        }

        if (startVertex == -1 && count == 1) {
            for (int i = 0; i < numVertices; i++) {
                if (selected[i]) { startVertex = i; break; }
            }
        }
        if (startVertex == -1) return;

        Path newPath = new Path(graph, count);
        boolean[] visited = new boolean[numVertices];
        int current = startVertex;
        while (current != -1) {
            newPath.add(vertices[current]);
            visited[current] = true;
            int next = -1;
            for (int nb : graph.neighbors(vertices[current])) {
                int nbIdx = graph.indexOf(nb);
                if (selected[nbIdx] && !visited[nbIdx]) {
                    next = nbIdx;
                    break;
                }
            }
            current = next;
        }

        if (newPath.size() > longestInducedPath.size()) {
            longestInducedPath.clear();
            for (int v : newPath) longestInducedPath.add(v);
        }
    }

    @Override
    public Path getLongestInducedPath() {
        if (longestInducedPath.isEmpty()) solve();
        return longestInducedPath;
    }
}
