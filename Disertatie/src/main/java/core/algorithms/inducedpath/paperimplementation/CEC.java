package core.algorithms.inducedpath.paperimplementation;

import com.gurobi.gurobi.*;
import core.algorithms.inducedpath.InducedPathAlgorithm;
import org.graph4j.Graph;
import org.graph4j.alg.GraphAlgorithm;
import org.graph4j.util.Path;

import java.util.*;

/**
 * CEC formulation from Marzo, Melo, Ribeiro, Santos (2022).
 * "New formulations and branch-and-cut procedures for the longest induced path problem"
 *
 * Operates on extended graph G_s with a universal vertex s adjacent to all vertices.
 * Searches for the longest induced cycle through s (equivalent to longest induced path).
 * Uses cycle elimination constraints separated lazily via branch-and-cut.
 * Includes vertex-based and edge-based clique inequalities (Bron-Kerbosch enumeration).
 */
public class CEC extends GraphAlgorithm implements InducedPathAlgorithm {

    private static final int MAX_CLIQUES = 500;

    private final int n;
    private Path longestInducedPath;
    private double timeLimitSeconds = 900;

    public CEC(Graph graph) {
        super(graph);
        this.n = graph.numVertices();
        this.longestInducedPath = new Path(graph);
    }

    public CEC(Graph graph, double timeLimitSeconds) {
        this(graph);
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public void setTimeLimitSeconds(double seconds) {
        this.timeLimitSeconds = seconds;
    }

    @Override
    public Path getLongestInducedPath() {
        if (longestInducedPath.isEmpty()) solve();
        return longestInducedPath;
    }

    private void solve() {
        try {
            int[] vertices = graph.vertices();

            int[][] adj = new int[n][];
            boolean[][] adjMat = n <= 4000 ? new boolean[n][n] : null;
            for (int i = 0; i < n; i++) {
                int[] nbs = graph.neighbors(vertices[i]);
                adj[i] = new int[nbs.length];
                for (int j = 0; j < nbs.length; j++) {
                    adj[i][j] = graph.indexOf(nbs[j]);
                }
                if (adjMat != null)
                    for (int j : adj[i]) adjMat[i][j] = true;
            }

            List<int[]> origEdges = new ArrayList<>();
            Map<Long, Integer> origEdgeIdx = new HashMap<>();
            for (int i = 0; i < n; i++) {
                for (int j : adj[i]) {
                    if (j > i) {
                        origEdgeIdx.put(ek(i, j), origEdges.size());
                        origEdges.add(new int[]{i, j});
                    }
                }
            }
            int nOrig = origEdges.size();
            int nTotal = nOrig + n;

            List<List<Integer>> incident = new ArrayList<>();
            for (int i = 0; i < n; i++) incident.add(new ArrayList<>());
            for (int e = 0; e < nOrig; e++) {
                incident.get(origEdges.get(e)[0]).add(e);
                incident.get(origEdges.get(e)[1]).add(e);
            }

            GRBEnv env = new GRBEnv(true);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();
            GRBModel model = new GRBModel(env);
            model.set(GRB.DoubleParam.TimeLimit, timeLimitSeconds);

            GRBVar[] y = new GRBVar[n];
            for (int i = 0; i < n; i++)
                y[i] = model.addVar(0, 1, 0, GRB.BINARY, "y_" + i);

            GRBVar[] x = new GRBVar[nTotal];
            for (int e = 0; e < nTotal; e++)
                x[e] = model.addVar(0, 1, 0, GRB.BINARY, "x_" + e);

            GRBLinExpr obj = new GRBLinExpr();
            for (int i = 0; i < n; i++) obj.addTerm(1.0, y[i]);
            model.setObjective(obj, GRB.MAXIMIZE);

            // (2.17) degree(v) in G_s = 2*y_v
            for (int i = 0; i < n; i++) {
                GRBLinExpr lhs = new GRBLinExpr();
                for (int e : incident.get(i)) lhs.addTerm(1.0, x[e]);
                lhs.addTerm(1.0, x[nOrig + i]);
                GRBLinExpr rhs = new GRBLinExpr();
                rhs.addTerm(2.0, y[i]);
                model.addConstr(lhs, GRB.EQUAL, rhs, "deg_" + i);
            }

            // (2.18) degree(s) = 2
            GRBLinExpr sDeg = new GRBLinExpr();
            for (int i = 0; i < n; i++) sDeg.addTerm(1.0, x[nOrig + i]);
            model.addConstr(sDeg, GRB.EQUAL, 2.0, "s_deg");

            // (2.20) link1: x_e <= y_v
            for (int i = 0; i < n; i++) {
                for (int e : incident.get(i))
                    model.addConstr(x[e], GRB.LESS_EQUAL, y[i], "l1_" + e + "_" + i);
                model.addConstr(x[nOrig + i], GRB.LESS_EQUAL, y[i], "l1s_" + i);
            }

            // (2.21) link2: x_e >= y_u + y_v - 1 for edges in E (induced property)
            for (int e = 0; e < nOrig; e++) {
                int u = origEdges.get(e)[0], v = origEdges.get(e)[1];
                GRBLinExpr lhs = new GRBLinExpr();
                lhs.addTerm(1.0, x[e]);
                lhs.addTerm(-1.0, y[u]);
                lhs.addTerm(-1.0, y[v]);
                model.addConstr(lhs, GRB.GREATER_EQUAL, -1.0, "l2_" + e);
            }

            // clique inequalities (Bron-Kerbosch)
            if (adjMat != null) {
                List<List<Integer>> cliques = enumerateCliques(adjMat);
                for (int c = 0; c < cliques.size(); c++) {
                    List<Integer> K = cliques.get(c);
                    if (K.size() < 3) continue;
                    GRBLinExpr vClq = new GRBLinExpr();
                    for (int v : K) vClq.addTerm(1.0, y[v]);
                    model.addConstr(vClq, GRB.LESS_EQUAL, 2.0, "cqv_" + c);

                    GRBLinExpr eClq = new GRBLinExpr();
                    for (int a = 0; a < K.size(); a++)
                        for (int b = a + 1; b < K.size(); b++) {
                            Integer ei = origEdgeIdx.get(ek(K.get(a), K.get(b)));
                            if (ei != null) eClq.addTerm(1.0, x[ei]);
                        }
                    model.addConstr(eClq, GRB.LESS_EQUAL, 1.0, "cqe_" + c);
                }
            }

            // warm start
            List<Integer> warm = greedyWarmStart(adj);
            if (warm.size() >= 2) {
                for (int i = 0; i < n; i++) y[i].set(GRB.DoubleAttr.Start, 0.0);
                for (int e = 0; e < nTotal; e++) x[e].set(GRB.DoubleAttr.Start, 0.0);
                for (int idx : warm) y[idx].set(GRB.DoubleAttr.Start, 1.0);
                for (int k = 0; k < warm.size() - 1; k++) {
                    int a = warm.get(k), b = warm.get(k + 1);
                    Integer ei = origEdgeIdx.get(ek(Math.min(a, b), Math.max(a, b)));
                    if (ei != null) x[ei].set(GRB.DoubleAttr.Start, 1.0);
                }
                x[nOrig + warm.get(0)].set(GRB.DoubleAttr.Start, 1.0);
                x[nOrig + warm.get(warm.size() - 1)].set(GRB.DoubleAttr.Start, 1.0);
            }

            // lazy cycle elimination
            model.set(GRB.IntParam.LazyConstraints, 1);
            final List<int[]> edgesF = origEdges;
            final int nOrigF = nOrig;

            model.setCallback(new GRBCallback() {
                @Override
                protected void callback() {
                    if (where == GRB.CB_MIPSOL) {
                        try {
                            double[] yV = getSolution(y);
                            double[] xV = getSolution(x);
                            separateCycles(yV, xV, y, x, nOrigF, edgesF);
                        } catch (GRBException ignored) {}
                    }
                }

                private void separateCycles(double[] yV, double[] xV, GRBVar[] y, GRBVar[] x,
                                             int nOrigE, List<int[]> edges) throws GRBException {
                    boolean[] sel = new boolean[n];
                    for (int i = 0; i < n; i++) sel[i] = yV[i] > 0.5;

                    List<List<Integer>> solAdj = new ArrayList<>();
                    for (int i = 0; i < n; i++) solAdj.add(new ArrayList<>());
                    for (int e = 0; e < nOrigE; e++) {
                        if (xV[e] > 0.5) {
                            solAdj.get(edges.get(e)[0]).add(edges.get(e)[1]);
                            solAdj.get(edges.get(e)[1]).add(edges.get(e)[0]);
                        }
                    }

                    boolean[] visited = new boolean[n];
                    for (int i = 0; i < n; i++) {
                        if (!sel[i] || visited[i]) continue;
                        List<Integer> comp = new ArrayList<>();
                        Queue<Integer> q = new LinkedList<>();
                        q.add(i); visited[i] = true;
                        int compEdges = 0;
                        while (!q.isEmpty()) {
                            int v = q.poll();
                            comp.add(v);
                            for (int nb : solAdj.get(v)) {
                                compEdges++;
                                if (!visited[nb]) { visited[nb] = true; q.add(nb); }
                            }
                        }
                        compEdges /= 2;

                        boolean connToS = false;
                        for (int v : comp)
                            if (xV[nOrigE + v] > 0.5) { connToS = true; break; }

                        if (!connToS && compEdges >= comp.size()) {
                            GRBLinExpr cut = new GRBLinExpr();
                            for (int v : comp) cut.addTerm(1.0, y[v]);
                            addLazy(cut, GRB.LESS_EQUAL, comp.size() - 1);
                        }
                    }
                }
            });

            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if ((status == GRB.OPTIMAL || status == GRB.SUBOPTIMAL ||
                 status == GRB.TIME_LIMIT || status == GRB.INTERRUPTED)
                && model.get(GRB.IntAttr.SolCount) > 0) {
                double[] yV = model.get(GRB.DoubleAttr.X, y);
                double[] xV = model.get(GRB.DoubleAttr.X, x);
                extractPath(yV, xV, nOrig, vertices, origEdges);
            }

            model.dispose();
            env.dispose();
        } catch (GRBException e) {
            System.err.println("MarzoCEC Error: " + e.getMessage());
        }
    }

    private synchronized void extractPath(double[] yV, double[] xV, int nOrig,
                                           int[] vertices, List<int[]> origEdges) {
        int count = 0;
        for (int i = 0; i < n; i++) if (yV[i] > 0.5) count++;
        if (count <= longestInducedPath.size()) return;

        int endA = -1, endB = -1;
        for (int i = 0; i < n; i++) {
            if (xV[nOrig + i] > 0.5) {
                if (endA == -1) endA = i; else endB = i;
            }
        }
        if (endA == -1) return;
        if (endB == -1) {
            if (count == 1) {
                Path p = new Path(graph, 1);
                p.add(vertices[endA]);
                if (p.size() > longestInducedPath.size()) {
                    longestInducedPath.clear();
                    longestInducedPath.add(vertices[endA]);
                }
            }
            return;
        }

        List<List<Integer>> pathAdj = new ArrayList<>();
        for (int i = 0; i < n; i++) pathAdj.add(new ArrayList<>());
        for (int e = 0; e < nOrig; e++) {
            if (xV[e] > 0.5) {
                pathAdj.get(origEdges.get(e)[0]).add(origEdges.get(e)[1]);
                pathAdj.get(origEdges.get(e)[1]).add(origEdges.get(e)[0]);
            }
        }

        Path newPath = new Path(graph, count);
        boolean[] visited = new boolean[n];
        int cur = endA;
        while (cur != -1) {
            newPath.add(vertices[cur]);
            visited[cur] = true;
            int next = -1;
            for (int nb : pathAdj.get(cur))
                if (!visited[nb]) { next = nb; break; }
            cur = next;
        }

        if (newPath.size() > longestInducedPath.size()) {
            longestInducedPath.clear();
            for (int v : newPath) longestInducedPath.add(v);
        }
    }

    private List<Integer> greedyWarmStart(int[][] adj) {
        Random rnd = new Random(42);
        List<Integer> best = new ArrayList<>();
        for (int attempt = 0; attempt < Math.min(200, n * 2); attempt++) {
            int start = rnd.nextInt(n);
            List<Integer> path = new ArrayList<>();
            boolean[] inPath = new boolean[n];
            int[] invalid = new int[n];
            path.add(start);
            inPath[start] = true;
            invalid[start]++;
            for (int nb : adj[start]) invalid[nb]++;

            for (int side = 0; side < 2; side++) {
                while (true) {
                    int ep = side == 0 ? path.get(path.size() - 1) : path.get(0);
                    int bestNb = -1, bestScore = -1;
                    for (int nb : adj[ep]) {
                        if (inPath[nb] || invalid[nb] != 1) continue;
                        int res = 0;
                        for (int nb2 : adj[nb])
                            if (nb2 != ep && !inPath[nb2] && invalid[nb2] <= 1) res++;
                        int score = res * 1000 + rnd.nextInt(1000);
                        if (score > bestScore) { bestScore = score; bestNb = nb; }
                    }
                    if (bestNb == -1) break;
                    if (side == 0) path.add(bestNb); else path.add(0, bestNb);
                    inPath[bestNb] = true;
                    invalid[bestNb]++;
                    for (int nb : adj[bestNb]) invalid[nb]++;
                }
            }
            if (path.size() > best.size()) best = path;
        }
        return best;
    }

    private List<List<Integer>> enumerateCliques(boolean[][] adjMat) {
        List<List<Integer>> result = new ArrayList<>();
        Set<Integer> P = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) P.add(i);
        bronKerbosch(new ArrayList<>(), P, new LinkedHashSet<>(), adjMat, result);
        return result;
    }

    private void bronKerbosch(List<Integer> R, Set<Integer> P, Set<Integer> X,
                               boolean[][] adjMat, List<List<Integer>> result) {
        if (result.size() >= MAX_CLIQUES) return;
        if (P.isEmpty() && X.isEmpty()) {
            if (R.size() >= 3) result.add(new ArrayList<>(R));
            return;
        }
        int pivot = -1, maxDeg = -1;
        for (int v : P) {
            int d = 0;
            for (int u : P) if (adjMat[v][u]) d++;
            if (d > maxDeg) { maxDeg = d; pivot = v; }
        }
        for (int v : X) {
            int d = 0;
            for (int u : P) if (adjMat[v][u]) d++;
            if (d > maxDeg) { maxDeg = d; pivot = v; }
        }

        List<Integer> cands = new ArrayList<>();
        for (int v : P)
            if (pivot == -1 || !adjMat[pivot][v]) cands.add(v);

        for (int v : cands) {
            R.add(v);
            Set<Integer> newP = new LinkedHashSet<>(), newX = new LinkedHashSet<>();
            for (int u : P) if (adjMat[v][u]) newP.add(u);
            for (int u : X) if (adjMat[v][u]) newX.add(u);
            bronKerbosch(R, newP, newX, adjMat, result);
            R.remove(R.size() - 1);
            P.remove(v);
            X.add(v);
            if (result.size() >= MAX_CLIQUES) return;
        }
    }

    private static long ek(int u, int v) {
        return ((long) Math.min(u, v) << 32) | Math.max(u, v);
    }
}
