package core.algorithms.inducedpath.ilp;

import com.gurobi.gurobi.*;
import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.*;

/**
 * ILP v2 for Longest Induced Path.
 *
 * Same base formulation as {@link LongestInducedPathILP} (x/y binaries, edge linking,
 * induced constraint, degree <= 2, tree equality, lazy cycle elimination), plus:
 * - Warm start: a fast multi-start greedy heuristic provides an incumbent as a MIP
 *   start, so Gurobi begins with a strong lower bound instead of searching for one.
 * - Triangle cuts added upfront: for every triangle {u,v,w}, x_u + x_v + x_w <= 2.
 *   An induced path can contain at most 2 vertices of any clique; adding these
 *   statically avoids many lazy-callback rounds on dense graphs.
 */
public class LongestInducedPathILP extends GraphAlgorithm implements InducedPathAlgorithm {
    private static final int MAX_TRIANGLE_CUTS = 200_000;

    private final int numVertices;
    private Path longestInducedPath;
    private double timeLimitSeconds = 0;
    private int mipFocus = 0; // 0 = Gurobi default; 2/3 emphasize proving optimality

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

    public LongestInducedPathILP(Graph graph, Path longestInducedPath, double timeLimitSeconds) {
        this(graph, longestInducedPath);
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public void setMipFocus(int mipFocus) {
        this.mipFocus = mipFocus;
    }

    /** Multi-start greedy: random start vertex, extend both ends preferring vertices
     *  with more remaining valid neighbors. Returns vertex indices of the best path found. */
    private List<Integer> greedyWarmStart(int[][] adj, long budgetMs) {
        Random rnd = new Random(12345);
        List<Integer> best = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        int starts = 0;
        while (System.currentTimeMillis() - t0 < budgetMs || starts < 20) {
            starts++;
            if (starts > 100_000) break;
            int start = rnd.nextInt(numVertices);
            List<Integer> path = new ArrayList<>();
            boolean[] inPath = new boolean[numVertices];
            int[] invalid = new int[numVertices];
            path.add(start);
            inPath[start] = true;
            invalid[start]++;
            for (int nb : adj[start]) invalid[nb]++;

            for (int side = 0; side < 2; side++) {
                boolean forward = side == 0;
                while (true) {
                    int endpoint = forward ? path.get(path.size() - 1) : path.get(0);
                    int bestNb = -1, bestScore = -1;
                    int seen = 0;
                    for (int nb : adj[endpoint]) {
                        if (inPath[nb] || invalid[nb] != 1) continue;
                        int residual = 0;
                        for (int nb2 : adj[nb]) {
                            if (nb2 != endpoint && !inPath[nb2] && invalid[nb2] <= 1) residual++;
                        }
                        seen++;
                        // residual-neighbor heuristic with random tie-breaking
                        int score = residual * 1000 + rnd.nextInt(1000);
                        if (score > bestScore) { bestScore = score; bestNb = nb; }
                    }
                    if (bestNb == -1) break;
                    if (forward) path.add(bestNb); else path.add(0, bestNb);
                    inPath[bestNb] = true;
                    invalid[bestNb]++;
                    for (int nb : adj[bestNb]) invalid[nb]++;
                }
            }
            if (path.size() > best.size()) best = path;
        }
        return best;
    }

    private void solve() {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            GRBModel model = new GRBModel(env);
            if (timeLimitSeconds > 0) {
                model.set(GRB.DoubleParam.TimeLimit, timeLimitSeconds);
            }
            if (mipFocus > 0) {
                model.set(GRB.IntParam.MIPFocus, mipFocus);
            }

            int[] vertices = graph.vertices();

            // index-based adjacency
            int[][] adj = new int[numVertices][];
            for (int i = 0; i < numVertices; i++) {
                int[] nbs = graph.neighbors(vertices[i]);
                adj[i] = new int[nbs.length];
                for (int j = 0; j < nbs.length; j++) adj[i][j] = graph.indexOf(nbs[j]);
            }

            List<int[]> edges = new ArrayList<>();
            Map<Long, Integer> edgeIndex = new HashMap<>();
            for (int i = 0; i < numVertices; i++) {
                for (int j : adj[i]) {
                    if (j > i) {
                        edgeIndex.put(((long) i << 32) | j, edges.size());
                        edges.add(new int[]{i, j});
                    }
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
            for (int i = 0; i < numVertices; i++) objective.addTerm(1.0, x[i]);
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

            // Triangle cuts: at most 2 vertices of a triangle on an induced path.
            boolean[][] adjMat = null;
            if (numVertices <= 3000) {
                adjMat = new boolean[numVertices][numVertices];
                for (int i = 0; i < numVertices; i++) {
                    for (int j : adj[i]) adjMat[i][j] = true;
                }
            }
            int triangleCuts = 0;
            if (adjMat != null) {
                outer:
                for (int e = 0; e < numEdges; e++) {
                    int u = edges.get(e)[0], v = edges.get(e)[1];
                    for (int w : adj[u]) {
                        if (w > v && adjMat[v][w]) {
                            GRBLinExpr tri = new GRBLinExpr();
                            tri.addTerm(1.0, x[u]);
                            tri.addTerm(1.0, x[v]);
                            tri.addTerm(1.0, x[w]);
                            model.addConstr(tri, GRB.LESS_EQUAL, 2.0, "tri_" + triangleCuts);
                            if (++triangleCuts >= MAX_TRIANGLE_CUTS) break outer;
                        }
                    }
                }
            }

            // Warm start from greedy heuristic
            List<Integer> warm = greedyWarmStart(adj, Math.min(2000, (long) (timeLimitSeconds > 0 ? timeLimitSeconds * 50 : 2000)));
            if (warm.size() >= 2) {
                for (int i = 0; i < numVertices; i++) x[i].set(GRB.DoubleAttr.Start, 0.0);
                for (int e = 0; e < numEdges; e++) y[e].set(GRB.DoubleAttr.Start, 0.0);
                for (int idx : warm) x[idx].set(GRB.DoubleAttr.Start, 1.0);
                for (int k = 0; k < warm.size() - 1; k++) {
                    int a = warm.get(k), b = warm.get(k + 1);
                    Integer e = edgeIndex.get(((long) Math.min(a, b) << 32) | Math.max(a, b));
                    if (e != null) y[e].set(GRB.DoubleAttr.Start, 1.0);
                }
            }

            model.set(GRB.IntParam.LazyConstraints, 1);

            final List<int[]> edgesF = edges;
            final int numEdgesF = numEdges;
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

                            List<List<Integer>> solAdj = new ArrayList<>();
                            for (int i = 0; i < numVertices; i++) solAdj.add(new ArrayList<>());
                            for (int e = 0; e < numEdgesF; e++) {
                                if (yVals[e] > 0.5) {
                                    solAdj.get(edgesF.get(e)[0]).add(edgesF.get(e)[1]);
                                    solAdj.get(edgesF.get(e)[1]).add(edgesF.get(e)[0]);
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
                                    for (int nb : solAdj.get(v)) {
                                        if (!visited[nb]) {
                                            visited[nb] = true;
                                            q.add(nb);
                                        }
                                    }
                                }

                                Set<Integer> compSet = new HashSet<>(comp);
                                List<Integer> compEdgeIdx = new ArrayList<>();
                                for (int e = 0; e < numEdgesF; e++) {
                                    if (yVals[e] > 0.5 && compSet.contains(edgesF.get(e)[0]) && compSet.contains(edgesF.get(e)[1])) {
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
            System.err.println("ILPv2 Error: " + e.getMessage());
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
