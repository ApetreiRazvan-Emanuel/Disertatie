package info.uaic.licenta.demo.controllers;

import info.uaic.licenta.demo.algorithms.GraphMLReader;
import info.uaic.licenta.demo.utils.GraphUtils;
import org.graph4j.Graph;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Controller
public class GraphMLController {

    @PostMapping("/api/upload-graphml")
    public ResponseEntity<?> uploadGraphML(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty");
        }

        try {
            GraphMLReader graphReader = new GraphMLReader();
            graphReader.parseGraphMLFile(file.getInputStream());
            Graph<String, String> graph = graphReader.readGraph();

            Map<Integer, List<Integer>> adjacencyList = GraphUtils.returnAdjacencyList(graph);
            return ResponseEntity.ok(adjacencyList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error parsing file: " + e.getMessage());
        }
    }
}