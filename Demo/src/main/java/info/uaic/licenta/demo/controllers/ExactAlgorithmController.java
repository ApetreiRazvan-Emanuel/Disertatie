package info.uaic.licenta.demo.controllers;

import info.uaic.licenta.demo.algorithms.exactalgorithm.LongestInducedPathExact;
import info.uaic.licenta.demo.algorithms.genetic.LongestInducedPathGenetic;
import info.uaic.licenta.demo.input.ExactAlgorithmConfig;
import org.graph4j.util.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.List;

@Controller
public class ExactAlgorithmController {

    @GetMapping(value = {"/", "/exact"})
    public String showExactPage() {
        return "exact";
    }

    @PostMapping("/run-exact")
    public ResponseEntity<?> runAlgorithm(@RequestBody ExactAlgorithmConfig config) {

        Path resultPath = new LongestInducedPathExact(config.getGraph()).getLongestInducedPath();
        List<Integer> resultVertices = Arrays.stream(resultPath.vertices()).boxed().toList();
        return ResponseEntity.ok(resultVertices);
    }
}