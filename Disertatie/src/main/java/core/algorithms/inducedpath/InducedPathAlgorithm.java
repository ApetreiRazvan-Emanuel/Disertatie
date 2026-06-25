package core.algorithms.inducedpath;

import org.graph4j.Graph;
import org.graph4j.util.Path;

/**
 * The contract for induced path algorithms. An induced path is a path where there are no additional edges connecting
 * any pair of non-consecutive vertices in the path. In other words, the induced subgraph containing only the vertices
 * within this path contains no cycles.
 *
 * @author Apetrei Razvan-Emanuel
 */

public interface InducedPathAlgorithm {
    /**
     *
     * @return the input graph.
     */
    Graph getGraph();

    /**
     *
     * @return the longest induced cycle for the input graph.
     */
    Path getLongestInducedPath();
}
