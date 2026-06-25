package benchmark;

import core.algorithms.inducedpath.generator.LIPInstanceGenerator;
import core.algorithms.inducedpath.generator.LIPInstanceGenerator.Difficulty;
import core.algorithms.inducedpath.generator.LIPInstanceGenerator.GeneratedInstance;

import java.io.FileWriter;
import java.util.Arrays;

public class GeneratedInstanceTest {
    static final String RES = "src/main/resources/graph-instances/";

    public static void main(String[] args) throws Exception {
        LIPInstanceGenerator gen = new LIPInstanceGenerator(42);

        GeneratedInstance gi = gen.generate(5000, 1600, Difficulty.HARD);
        boolean valid = LIPInstanceGenerator.verifyInducedPath(gi.graph(), gi.plantedPath());
        System.out.printf("%s%nValid: %s%n", gi.description(), valid);

        // Save graph
        try (FileWriter fw = new FileWriter(RES + "generated-hard-5000.graphml")) {
            fw.write(LIPInstanceGenerator.toGraphML(gi.graph()));
        }
        System.out.println("Saved graph to " + RES + "generated-hard-5000.graphml");

        // Save optimal path
        try (FileWriter fw = new FileWriter("src/main/output/solutions/generated-hard-5000_optimal.txt")) {
            for (int i = 0; i < gi.plantedPath().length; i++) {
                if (i > 0) fw.write(" ");
                fw.write(String.valueOf(gi.plantedPath()[i]));
            }
            fw.write("\n");
        }
        System.out.printf("Saved optimal path (length %d) to solutions/%n", gi.plantedPathLength());
    }
}
