package core.algorithms.inducedpath.generator;

import org.graph4j.Graph;
import org.graph4j.GraphBuilder;

import java.util.*;

/**
 * Generator of graph instances with a planted longest induced path of known length.
 * Produces non-trivial instances where the planted path is hard to find.
 *
 * Core guarantee: each noise vertex connects to many spread-out path vertices (~20-40%),
 * so including any noise vertex in an induced path forces excluding many path vertices —
 * always a net loss. Noise vertices are also organized into small cliques to prevent
 * long noise-only induced paths.
 *
 * Difficulty is controlled by:
 * - Noise-to-path connection density (higher = easier to identify the path)
 * - Noise subgraph structure (cliques vs chains vs random)
 * - Number and quality of "trap" structures
 */
public class LIPInstanceGenerator {

    public enum Difficulty { EASY, MEDIUM, HARD }

    public record GeneratedInstance(Graph graph, int plantedPathLength, int[] plantedPath, String description) {}

    private final Random random;

    public LIPInstanceGenerator() {
        this.random = new Random();
    }

    public LIPInstanceGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generate a graph instance with a planted induced path of the given length.
     * The planted path is guaranteed to be an induced path, and with high probability
     * it is the longest induced path (or very close to it).
     */
    public GeneratedInstance generate(int totalVertices, int pathLength, Difficulty difficulty) {
        if (pathLength > totalVertices) {
            throw new IllegalArgumentException("pathLength must be <= totalVertices");
        }
        if (pathLength < 3) {
            throw new IllegalArgumentException("pathLength must be >= 3");
        }

        Graph graph = GraphBuilder.empty().estimatedNumVertices(totalVertices).buildGraph();
        for (int i = 0; i < totalVertices; i++) {
            graph.addVertex(i);
        }

        int[] plantedPath = new int[pathLength];
        for (int i = 0; i < pathLength; i++) {
            plantedPath[i] = i;
        }

        // Create planted path edges
        for (int i = 0; i < pathLength - 1; i++) {
            graph.addEdge(plantedPath[i], plantedPath[i + 1]);
        }

        int noiseCount = totalVertices - pathLength;
        if (noiseCount == 0) {
            return new GeneratedInstance(graph, pathLength, plantedPath,
                    String.format("n=%d, plantedPath=%d, trivial (no noise)", totalVertices, pathLength));
        }

        int[] noiseVertices = new int[noiseCount];
        for (int i = 0; i < noiseCount; i++) {
            noiseVertices[i] = pathLength + i;
        }

        // Difficulty-dependent parameters
        double noiseToPathProb; // probability of connecting a noise vertex to each path vertex
        int cliqueSize;         // size of noise cliques (prevents long noise-only paths)
        double interCliqueProb; // probability of edges between different cliques
        int numTraps;           // number of "trap" structures
        int trapLength;         // max length of trap paths

        switch (difficulty) {
            case EASY -> {
                noiseToPathProb = 0.35;  // each noise vertex connects to ~35% of path
                cliqueSize = 4;
                interCliqueProb = 0.15;
                numTraps = 2;
                trapLength = Math.max(3, pathLength / 6);
            }
            case MEDIUM -> {
                noiseToPathProb = 0.25;  // ~25% — slightly less obvious
                cliqueSize = 3;
                interCliqueProb = 0.10;
                numTraps = 4;
                trapLength = Math.max(3, pathLength / 4);
            }
            case HARD -> {
                noiseToPathProb = 0.22;
                cliqueSize = 3;
                interCliqueProb = 0.10;
                numTraps = 6;
                trapLength = Math.max(3, pathLength / 3);
            }
            default -> throw new IllegalArgumentException("Unknown difficulty");
        }

        // Step 1: Connect each noise vertex to a random subset of path vertices
        // This ensures including any noise vertex in an induced path costs ~noiseToPathProb * pathLength
        // path vertices but only gains 1 vertex — always a net loss for prob >= ~3/pathLength
        for (int nv : noiseVertices) {
            for (int p = 0; p < pathLength; p++) {
                if (random.nextDouble() < noiseToPathProb) {
                    graph.addEdge(nv, plantedPath[p]);
                }
            }
            int minConnections = Math.max(5, pathLength / 6);
            int connections = 0;
            for (int p = 0; p < pathLength; p++) {
                if (graph.containsEdge(nv, plantedPath[p])) connections++;
            }
            while (connections < minConnections) {
                int p = random.nextInt(pathLength);
                if (!graph.containsEdge(nv, plantedPath[p])) {
                    graph.addEdge(nv, plantedPath[p]);
                    connections++;
                }
            }
            // Ensure connections are spread across zones of the path
            int numZones = Math.max(4, pathLength / 10);
            int zoneSize = pathLength / numZones;
            for (int z = 0; z < numZones; z++) {
                int zoneStart = z * zoneSize;
                int zoneEnd = Math.min(zoneStart + zoneSize, pathLength);
                boolean hasConnection = false;
                for (int p = zoneStart; p < zoneEnd; p++) {
                    if (graph.containsEdge(nv, plantedPath[p])) { hasConnection = true; break; }
                }
                if (!hasConnection) {
                    int p = zoneStart + random.nextInt(zoneEnd - zoneStart);
                    graph.addEdge(nv, plantedPath[p]);
                }
            }
        }

        // Step 2: Organize noise vertices into small cliques
        // Cliques guarantee that the max induced path within a clique is 2 vertices
        List<List<Integer>> cliques = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>();
        for (int nv : noiseVertices) remaining.add(nv);
        Collections.shuffle(remaining, random);

        while (remaining.size() >= cliqueSize) {
            List<Integer> clique = new ArrayList<>();
            for (int i = 0; i < cliqueSize; i++) {
                clique.add(remaining.remove(remaining.size() - 1));
            }
            cliques.add(clique);
            // Add all edges within the clique (explicit unbox to int to avoid label-based overload)
            for (int i = 0; i < clique.size(); i++) {
                for (int j = i + 1; j < clique.size(); j++) {
                    int ci = clique.get(i), cj = clique.get(j);
                    if (!graph.containsEdge(ci, cj)) {
                        graph.addEdge(ci, cj);
                    }
                }
            }
        }
        // Remaining vertices (less than cliqueSize) — connect them to existing cliques
        for (int v : remaining) {
            if (!cliques.isEmpty()) {
                List<Integer> clique = cliques.get(random.nextInt(cliques.size()));
                for (int c : clique) {
                    if (!graph.containsEdge(v, c)) {
                        graph.addEdge(v, c);
                    }
                }
            }
        }

        // Step 3: Add inter-clique edges (prevents clear clique separation)
        for (int i = 0; i < cliques.size(); i++) {
            for (int j = i + 1; j < cliques.size(); j++) {
                if (random.nextDouble() < interCliqueProb) {
                    int v1 = cliques.get(i).get(random.nextInt(cliques.get(i).size()));
                    int v2 = cliques.get(j).get(random.nextInt(cliques.get(j).size()));
                    if (!graph.containsEdge(v1, v2)) {
                        graph.addEdge(v1, v2);
                    }
                }
            }
        }

        // Step 4: Add trap structures — short paths that branch off the planted path
        // and look promising but dead-end
        addTraps(graph, plantedPath, noiseVertices, noiseCount, numTraps, trapLength);

        // Verify the planted path is still a valid induced path
        if (!verifyInducedPath(graph, plantedPath)) {
            throw new RuntimeException("BUG: planted path is not an induced path!");
        }

        String desc = String.format("n=%d, plantedPath=%d, noise=%d, difficulty=%s, edges=%d, avgDeg=%.1f, cliques=%d",
                totalVertices, pathLength, noiseCount, difficulty,
                graph.numEdges(), 2.0 * graph.numEdges() / totalVertices, cliques.size());

        return new GeneratedInstance(graph, pathLength, plantedPath, desc);
    }

    private void addTraps(Graph graph, int[] plantedPath, int[] noiseVertices, int noiseCount,
                          int numTraps, int maxTrapLength) {
        int pathLength = plantedPath.length;

        for (int t = 0; t < numTraps; t++) {
            // Attach trap near an endpoint of the planted path (makes it tempting to extend)
            int attachIdx;
            if (random.nextDouble() < 0.5) {
                attachIdx = random.nextInt(Math.min(5, pathLength)); // near start
            } else {
                attachIdx = pathLength - 1 - random.nextInt(Math.min(5, pathLength)); // near end
            }
            int attachPoint = plantedPath[attachIdx];

            // Find noise vertices that could form a short trap
            // (they shouldn't already be heavily connected to the path)
            List<Integer> candidates = new ArrayList<>();
            for (int nv : noiseVertices) {
                candidates.add(nv);
            }
            Collections.shuffle(candidates, random);

            int actualTrapLen = Math.min(maxTrapLength, candidates.size());
            if (actualTrapLen < 2) continue;

            // Connect first trap vertex to attachment point (if not already connected)
            int first = candidates.get(0);
            if (!graph.containsEdge(attachPoint, first)) {
                graph.addEdge(attachPoint, first);
            }

            // The trap is just noise vertices that are already clique-connected,
            // so any induced path through them is at most 2 vertices long.
            // The trap "looks" like it could extend the path but hits the clique wall.
        }
    }

    public GeneratedInstance generate(int pathLength, Difficulty difficulty) {
        double multiplier = switch (difficulty) {
            case EASY -> 1.5;
            case MEDIUM -> 2.0;
            case HARD -> 3.0;
        };
        int totalVertices = (int) (pathLength * multiplier);
        return generate(totalVertices, pathLength, difficulty);
    }

    /**
     * Generate an instance with a target average degree, producing sparse graphs
     * similar to real-world networks like yeast (avgDeg ~5.6) or usair (avgDeg ~12.8).
     */
    public GeneratedInstance generateSparse(int totalVertices, int pathLength, double targetAvgDeg) {
        if (pathLength > totalVertices) throw new IllegalArgumentException("pathLength must be <= totalVertices");
        if (pathLength < 3) throw new IllegalArgumentException("pathLength must be >= 3");

        Graph graph = GraphBuilder.empty().estimatedNumVertices(totalVertices).buildGraph();
        for (int i = 0; i < totalVertices; i++) graph.addVertex(i);

        int[] plantedPath = new int[pathLength];
        for (int i = 0; i < pathLength; i++) plantedPath[i] = i;
        for (int i = 0; i < pathLength - 1; i++) graph.addEdge(plantedPath[i], plantedPath[i + 1]);

        int noiseCount = totalVertices - pathLength;
        if (noiseCount == 0) {
            return new GeneratedInstance(graph, pathLength, plantedPath,
                    String.format("n=%d, plantedPath=%d, trivial (no noise)", totalVertices, pathLength));
        }

        int[] noiseVertices = new int[noiseCount];
        for (int i = 0; i < noiseCount; i++) noiseVertices[i] = pathLength + i;

        int targetEdges = (int) (totalVertices * targetAvgDeg / 2);
        int pathEdges = pathLength - 1;
        int edgeBudget = targetEdges - pathEdges;

        // Budget split: ~40% noise-to-path, ~40% noise cliques, ~20% inter-clique/noise-noise
        int noiseToPathBudget = (int) (edgeBudget * 0.4);
        int cliqueBudget = (int) (edgeBudget * 0.4);
        int interBudget = edgeBudget - noiseToPathBudget - cliqueBudget;

        // Step 1: noise-to-path connections — each noise vertex gets a few spread-out path connections
        int connectionsPerNoise = Math.max(2, noiseToPathBudget / noiseCount);
        int numZones = Math.max(3, connectionsPerNoise);
        int zoneSize = Math.max(1, pathLength / numZones);

        for (int nv : noiseVertices) {
            // Pick spread-out positions along the path
            List<Integer> zones = new ArrayList<>();
            for (int z = 0; z < numZones; z++) zones.add(z);
            Collections.shuffle(zones, random);
            int added = 0;
            for (int z = 0; z < numZones && added < connectionsPerNoise; z++) {
                int zStart = zones.get(z) * zoneSize;
                int zEnd = Math.min(zStart + zoneSize, pathLength);
                if (zEnd <= zStart) continue;
                int p = zStart + random.nextInt(zEnd - zStart);
                if (!graph.containsEdge(nv, plantedPath[p])) {
                    graph.addEdge(nv, plantedPath[p]);
                    added++;
                }
            }
            // Fill remaining randomly
            int attempts = 0;
            while (added < connectionsPerNoise && attempts < connectionsPerNoise * 3) {
                int p = random.nextInt(pathLength);
                if (!graph.containsEdge(nv, plantedPath[p])) {
                    graph.addEdge(nv, plantedPath[p]);
                    added++;
                }
                attempts++;
            }
        }

        // Step 2: Organize noise into small cliques (size 3-4)
        int cliqueSize = 3;
        List<List<Integer>> cliques = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>();
        for (int nv : noiseVertices) remaining.add(nv);
        Collections.shuffle(remaining, random);

        int cliqueEdgesUsed = 0;
        while (remaining.size() >= cliqueSize && cliqueEdgesUsed < cliqueBudget) {
            List<Integer> clique = new ArrayList<>();
            for (int i = 0; i < cliqueSize; i++) clique.add(remaining.remove(remaining.size() - 1));
            cliques.add(clique);
            for (int i = 0; i < clique.size(); i++) {
                for (int j = i + 1; j < clique.size(); j++) {
                    int ci = clique.get(i), cj = clique.get(j);
                    if (!graph.containsEdge(ci, cj)) {
                        graph.addEdge(ci, cj);
                        cliqueEdgesUsed++;
                    }
                }
            }
        }
        // Leftover noise vertices join random cliques
        for (int v : remaining) {
            if (!cliques.isEmpty()) {
                int c = cliques.get(random.nextInt(cliques.size())).get(0);
                if (!graph.containsEdge(v, c)) graph.addEdge(v, c);
            }
        }

        // Step 3: Inter-clique edges
        int interEdgesAdded = 0;
        int maxAttempts = interBudget * 3;
        for (int a = 0; a < maxAttempts && interEdgesAdded < interBudget; a++) {
            if (cliques.size() < 2) break;
            int ci = random.nextInt(cliques.size()), cj = random.nextInt(cliques.size());
            if (ci == cj) continue;
            int v1 = cliques.get(ci).get(random.nextInt(cliques.get(ci).size()));
            int v2 = cliques.get(cj).get(random.nextInt(cliques.get(cj).size()));
            if (!graph.containsEdge(v1, v2)) {
                graph.addEdge(v1, v2);
                interEdgesAdded++;
            }
        }

        // Verify
        if (!verifyInducedPath(graph, plantedPath)) {
            throw new RuntimeException("BUG: planted path is not an induced path!");
        }

        String desc = String.format("n=%d, plantedPath=%d, noise=%d, edges=%d, avgDeg=%.1f, targetAvgDeg=%.1f, cliques=%d",
                totalVertices, pathLength, noiseCount,
                graph.numEdges(), 2.0 * graph.numEdges() / totalVertices, targetAvgDeg, cliques.size());

        return new GeneratedInstance(graph, pathLength, plantedPath, desc);
    }

    public static boolean verifyInducedPath(Graph graph, int[] path) {
        for (int i = 0; i < path.length - 1; i++) {
            if (!graph.containsEdge(path[i], path[i + 1])) {
                return false;
            }
        }
        for (int i = 0; i < path.length; i++) {
            for (int j = i + 2; j < path.length; j++) {
                if (graph.containsEdge(path[i], path[j])) {
                    return false;
                }
            }
        }
        return true;
    }

    public static String toGraphML(Graph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<graphml xmlns=\"http://graphml.graphstruct.org/xmlns\">\n");
        sb.append("  <graph id=\"G\" edgedefault=\"undirected\">\n");

        int[] vertices = graph.vertices();
        for (int v : vertices) {
            sb.append("    <node id=\"").append(v).append("\"/>\n");
        }

        int edgeId = 0;
        for (var edge : graph.edges()) {
            sb.append("    <edge id=\"e").append(edgeId++).append("\" source=\"")
                    .append(edge.source()).append("\" target=\"").append(edge.target()).append("\"/>\n");
        }

        sb.append("  </graph>\n");
        sb.append("</graphml>\n");
        return sb.toString();
    }
}
