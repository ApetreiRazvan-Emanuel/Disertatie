package benchmark;

import core.algorithms.inducedpath.InducedPathAlgorithm;
import core.algorithms.inducedpath.aco.LongestInducedPathACO;
import core.algorithms.inducedpath.genetic.LongestInducedPathGenetic;
import core.algorithms.inducedpath.paperimplementation.CEC;
import core.algorithms.inducedpath.paperimplementation.CUT;
import core.algorithms.inducedpath.paperimplementation.ExactEnumOptimized;
import core.algorithms.inducedpath.paperimplementation.HLIPP10000;
import core.io.GraphMLReader;
import org.graph4j.Graph;
import org.graph4j.util.Path;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class FullBenchmarkRunner {

    static final String RES = "src/main/resources/graph-instances/";
    static long TIME_LIMIT_MS = 15 * 60 * 1000;
    static double TIME_LIMIT_SEC = TIME_LIMIT_MS / 1000.0;
    static final int STOCHASTIC_RUNS = 5;

    static final String[] INSTANCES = {
        "high-tech", "karate", "mexican", "sawmill", "chesapeake",
        "tailorS1", "tailorS2", "romeo and juliet", "die hard",
        "attiro", "dolphins", "krebs", "prison", "huck",
        "sanjuansur", "jean", "david", "ieeebus", "sfi", "anna",
        "usair", "494bus", "662bus", "yeast", "generated-hard-5000"
    };

    static final String[] DETERMINISTIC_ALGOS = {"CEC", "CUT", "ExactEnumOptimized", "HLIPP10000"};
    static final String[] STOCHASTIC_ALGOS = {"GA", "ACO"};

    public static void main(String[] args) throws Exception {
        String singleInstance = null;
        String singleAlgo = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--time" -> { TIME_LIMIT_MS = Long.parseLong(args[++i]) * 1000; TIME_LIMIT_SEC = TIME_LIMIT_MS / 1000.0; }
                case "--instance" -> singleInstance = args[++i];
                case "--algo" -> singleAlgo = args[++i];
            }
        }

        String[] instances = singleInstance != null ? new String[]{singleInstance} : INSTANCES;

        String outDir = "src/main/output/";
        Files.createDirectories(Paths.get(outDir));
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String csvFile = outDir + "benchmark_" + ts + ".csv";
        String summaryFile = outDir + "benchmark_summary_" + ts + ".txt";

        try (PrintWriter csv = new PrintWriter(new BufferedWriter(new FileWriter(csvFile)));
             PrintWriter summary = new PrintWriter(new BufferedWriter(new FileWriter(summaryFile)))) {

            csv.println("Instance,|V|,|E|,Algorithm,Run,Result,Time(s),Valid");
            csv.flush();

            System.out.printf("Benchmark started: %s%n", ts);
            System.out.printf("Time limit: %.0f seconds (%d min)%n", TIME_LIMIT_SEC, TIME_LIMIT_MS / 60000);
            System.out.printf("Instances: %d, Stochastic runs: %d%n%n", instances.length, STOCHASTIC_RUNS);

            Map<String, Map<String, String>> tableData = new LinkedHashMap<>();

            for (String inst : instances) {
                System.out.println("========== " + inst + " ==========");
                Graph graph;
                try {
                    graph = new GraphMLReader().parseGraphMLFile(RES + inst + ".graphml").readGraph();
                } catch (Exception e) {
                    System.err.println("Failed to load " + inst + ": " + e.getMessage());
                    continue;
                }
                int V = graph.numVertices();
                long E = graph.numEdges();
                System.out.printf("|V|=%d, |E|=%d%n", V, E);

                String instKey = String.format("%s (%d, %d)", inst, V, E);
                tableData.put(instKey, new LinkedHashMap<>());

                for (String algo : DETERMINISTIC_ALGOS) {
                    if (singleAlgo != null && !singleAlgo.equals(algo)) continue;
                    System.out.printf("  %-20s ...", algo);
                    System.out.flush();
                    Result r = runWithTimeout(graph, algo);
                    boolean valid = r.path != null && validateInducedPath(graph, r.path);
                    System.out.printf(" result=%-4d time=%8.3fs valid=%s%n", r.size, r.timeSeconds, valid);
                    csv.printf("%s,%d,%d,%s,1,%d,%.3f,%s%n", inst, V, E, algo, r.size, r.timeSeconds, valid);
                    csv.flush();
                    tableData.get(instKey).put(algo, String.format("%d (%.1fs)", r.size, r.timeSeconds));
                }

                for (String algo : STOCHASTIC_ALGOS) {
                    if (singleAlgo != null && !singleAlgo.equals(algo)) continue;
                    System.out.printf("  %-20s %d runs:%n", algo, STOCHASTIC_RUNS);
                    int[] sizes = new int[STOCHASTIC_RUNS];
                    double[] times = new double[STOCHASTIC_RUNS];
                    boolean allValid = true;
                    for (int run = 0; run < STOCHASTIC_RUNS; run++) {
                        System.out.printf("    Run %d/%d ...", run + 1, STOCHASTIC_RUNS);
                        System.out.flush();
                        Result r = runWithTimeout(graph, algo);
                        sizes[run] = r.size;
                        times[run] = r.timeSeconds;
                        boolean valid = r.path != null && validateInducedPath(graph, r.path);
                        if (!valid) allValid = false;
                        System.out.printf(" result=%-4d time=%8.3fs valid=%s%n", r.size, r.timeSeconds, valid);
                        csv.printf("%s,%d,%d,%s,%d,%d,%.3f,%s%n", inst, V, E, algo, run + 1, r.size, r.timeSeconds, valid);
                        csv.flush();
                    }
                    int[] sorted = sizes.clone();
                    Arrays.sort(sorted);
                    int min = sorted[0], max = sorted[STOCHASTIC_RUNS - 1], median = sorted[STOCHASTIC_RUNS / 2];
                    double avg = Arrays.stream(sizes).average().orElse(0);
                    double avgTime = Arrays.stream(times).average().orElse(0);
                    System.out.printf("    => min=%d median=%d avg=%.1f max=%d avgTime=%.3fs%n",
                            min, median, avg, max, avgTime);
                    tableData.get(instKey).put(algo,
                            String.format("%d/%d/%.0f/%d (%.1fs)", min, median, avg, max, avgTime));
                }
            }

            summary.println("Benchmark Summary - " + ts);
            summary.printf("Time limit: %.0f seconds%n%n", TIME_LIMIT_SEC);

            String headerFmt = "%-30s";
            String[] allAlgos = singleAlgo != null ? new String[]{singleAlgo} :
                    new String[]{"CEC", "CUT", "ExactEnumOptimized", "HLIPP10000", "GA", "ACO"};
            StringBuilder header = new StringBuilder(String.format(headerFmt, "Instance (V, E)"));
            for (String a : allAlgos) header.append(String.format(" | %-24s", a));
            summary.println(header);
            summary.println("-".repeat(header.length()));

            for (var entry : tableData.entrySet()) {
                StringBuilder row = new StringBuilder(String.format(headerFmt, entry.getKey()));
                for (String a : allAlgos) {
                    String val = entry.getValue().getOrDefault(a, "-");
                    row.append(String.format(" | %-24s", val));
                }
                summary.println(row);
            }
            summary.println("\nFor stochastic algorithms (GA, ACO): format is min/median/avg/max (avgTime)");
            summary.flush();

            System.out.println("\nResults saved to: " + csvFile);
            System.out.println("Summary saved to: " + summaryFile);
        }
    }

    static Result runWithTimeout(Graph graph, String algo) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Result> future = executor.submit(() -> {
            long t0 = System.nanoTime();
            InducedPathAlgorithm algorithm = createAlgorithm(graph, algo);
            Path path = algorithm.getLongestInducedPath();
            double elapsed = (System.nanoTime() - t0) / 1e9;
            int size = path != null ? path.size() : 0;
            return new Result(size, elapsed, path);
        });
        try {
            Result r = future.get(TIME_LIMIT_MS + 60_000, TimeUnit.MILLISECONDS);
            executor.shutdownNow();
            return r;
        } catch (TimeoutException e) {
            future.cancel(true);
            executor.shutdownNow();
            System.out.print(" [TIMEOUT]");
            return new Result(0, TIME_LIMIT_SEC, null);
        } catch (Exception e) {
            future.cancel(true);
            executor.shutdownNow();
            System.out.print(" [ERROR: " + e.getMessage() + "]");
            return new Result(0, 0, null);
        }
    }

    static InducedPathAlgorithm createAlgorithm(Graph graph, String algo) {
        return switch (algo) {
            case "CEC" -> new CEC(graph, TIME_LIMIT_SEC);
            case "CUT" -> new CUT(graph, TIME_LIMIT_SEC);
            case "ExactEnumOptimized" -> new ExactEnumOptimized(graph);
            case "HLIPP10000" -> new HLIPP10000(graph);
            case "GA" -> {
                LongestInducedPathGenetic ga = new LongestInducedPathGenetic(graph);
                ga.setTimeLimitMs(TIME_LIMIT_MS);
                yield ga;
            }
            case "ACO" -> {
                LongestInducedPathACO aco = new LongestInducedPathACO(graph);
                aco.setTimeLimitMs(TIME_LIMIT_MS);
                yield aco;
            }
            default -> throw new IllegalArgumentException("Unknown algorithm: " + algo);
        };
    }

    static boolean validateInducedPath(Graph graph, Path path) {
        if (path == null || path.size() <= 1) return path != null;
        int[] verts = path.vertices();
        for (int i = 0; i < verts.length - 1; i++) {
            if (!graph.containsEdge(verts[i], verts[i + 1])) return false;
        }
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 2; j < verts.length; j++) {
                if (graph.containsEdge(verts[i], verts[j])) return false;
            }
        }
        Set<Integer> seen = new HashSet<>();
        for (int v : verts) {
            if (!seen.add(v)) return false;
        }
        return true;
    }

    record Result(int size, double timeSeconds, Path path) {}
}
