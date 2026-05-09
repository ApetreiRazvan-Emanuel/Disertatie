package core.algorithms.inducedcycle;

import org.graph4j.Graph;
import org.graph4j.util.Cycle;

/**
 * The contract for induced cycle algorithms. An induced cycle in a graph is a cycle that forms a simple circuit
 * where no additional edges connect any pair of non-consecutive vertices in the cycle.
 *
 * @author Apetrei Razvan-Emanuel
 */

public interface InducedCycleAlgorithm {
    /**
     *
     * @return the input graph.
     */
    Graph getGraph();

    /**
     *
     * @return the longest induced cycle for the input graph.
     */
    Cycle getLongestInducedCycle();
}
