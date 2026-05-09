package info.uaic.licenta.demo.controllers;

import info.uaic.licenta.demo.algorithms.heuristics.LongestInducedPathHeuristic;
import info.uaic.licenta.demo.input.HeuristicConfig;
import org.graph4j.util.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.List;

@Controller
public class HeuristicAlgorithmController {

    @GetMapping( "/heuristic")
    public String showExactPage() {
        return "heuristic";
    }

    @PostMapping("/run-heuristic")
    public ResponseEntity<?> runAlgorithm(@RequestBody HeuristicConfig config) {
        Path resultPath = new LongestInducedPathHeuristic(config.getGraph(), config.getMaxPaths()).getLongestInducedPath();
        List<Integer> resultVertices = Arrays.stream(resultPath.vertices()).boxed().toList();
        return ResponseEntity.ok(resultVertices);
    }
}