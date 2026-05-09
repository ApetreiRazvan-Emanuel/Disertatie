package info.uaic.licenta.demo.controllers;

import info.uaic.licenta.demo.input.GeneticAlgorithmConfig;
import info.uaic.licenta.demo.algorithms.genetic.LongestInducedPathGenetic;
import org.graph4j.util.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.List;

@Controller
public class GeneticAlgorithmController {

    @GetMapping( "/genetic")
    public String showExactPage() {
        return "genetic";
    }

    @PostMapping("/run-genetic")
    public ResponseEntity<?> runAlgorithm(@RequestBody GeneticAlgorithmConfig config) {

        Path resultPath = new LongestInducedPathGenetic(config).getLongestInducedPath();
        List<Integer> resultVertices = Arrays.stream(resultPath.vertices()).boxed().toList();
        return ResponseEntity.ok(resultVertices);
    }
}