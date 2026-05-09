package info.uaic.licenta.demo.algorithms;

import info.uaic.licenta.demo.algorithms.exceptions.GraphMLParseException;
import org.graph4j.Graph;
import org.graph4j.GraphBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Class used to read .graphml files and return the graph within them.
 * To use it, create an instance then use parseGraphMLFile with a string or path object for the graphml file. Then call
 * the readGraph() method which will return the graph within the graphml file.
 *
 * @author Apetrei Razvan-Emanuel
 */

public class GraphMLReader {
    Document graphMLDocument;

    /**
     *
     * @param filePath the path as a string to the graphml file
     * @return returns this class
     */
    public GraphMLReader parseGraphMLFile(String filePath) throws IOException, GraphMLParseException, ParserConfigurationException, SAXException {
        return parseGraphMLFile(Paths.get(filePath));
    }

    /**
     * Checks if the input is valid and initializes the variables needed to extract the graph within the graphml file.
     * @param path the path to the graphml file
     * @return returns this class
     */

    public GraphMLReader parseGraphMLFile(Path path) throws IOException, GraphMLParseException, ParserConfigurationException, SAXException {
        if (Files.notExists(path)) {
            throw new FileNotFoundException("File not found: " + path);
        }

        String fileName = path.getFileName().toString();
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            String extension = fileName.substring(i + 1);
            if (!extension.equalsIgnoreCase("graphml")) {
                throw new GraphMLParseException("File must have a .graphml extension: " + path);
            }
        } else {
            throw new GraphMLParseException("File must have a .graphml extension: " + path);
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        this.graphMLDocument = db.parse(path.toFile());
        graphMLDocument.getDocumentElement().normalize();
        return this;
    }

    public GraphMLReader parseGraphMLFile(InputStream inputStream) throws ParserConfigurationException, IOException, SAXException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        this.graphMLDocument = db.parse(inputStream);
        graphMLDocument.getDocumentElement().normalize();
        return this;
    }

    /**
     * Method used to read the graph within the graphml file after the necessary objects are instantiated by
     * parseGraphMLFile method. It uses the methods getGraphElement, initializeGraph, parseNodes and parseEdges to
     * construct the graph.
     *
     * @return the graph inside the graphml file
     * @throws GraphMLParseException in case the content of the graphml file contains errors
     */

    public Graph<String, String> readGraph() throws GraphMLParseException {
        if(graphMLDocument == null) {
            throw new GraphMLParseException("You must first parse a GraphML file");
        }

        Element graphElement = getGraphElement();
        Graph<String, String> graph = initializeGraph(graphElement);
        parseNodes(graph, graphElement);
        parseEdges(graph, graphElement);

        return graph;
    }

    /**
     * Finds the graph element within the graphml file.
     * @return the graph element
     */
    private Element getGraphElement() throws GraphMLParseException {
        NodeList graphList = graphMLDocument.getDocumentElement().getElementsByTagName("graph");
        if (graphList.getLength() == 0) {
            throw new GraphMLParseException("The .graphml file does not contain a <graph> element.");
        }
        if (graphList.getLength() > 1) {
            System.out.println("GraphMLReader: Warning, more graphs were found inside the GraphML file. Only the first one will be used!");
        }
        return (Element) graphList.item(0);
    }

    /**
     * Initializes the graph, digraph or graph based on the specifications within the graphml document.
     * @param graphElement the graph node within the graphml document
     * @return a graph or digraph based on the graphml document
     */

    private Graph<String, String> initializeGraph(Element graphElement) {
        String graphType = graphElement.getAttribute("edgedefault");
        if (graphType.isEmpty()) {
            System.out.println("GraphMLReader: Warning, no edgedefault attribute found for the graph! Undirected edges will be used.");
        }

        int numNodes = graphElement.getElementsByTagName("node").getLength();
        if (numNodes == 0) {
            System.out.println("GraphMLReader: Warning, the graph inside the graphml file contains no nodes!");
        }

        return graphType.equals("directed") ?
                GraphBuilder.empty().estimatedNumVertices(numNodes).buildDigraph() :
                GraphBuilder.empty().estimatedNumVertices(numNodes).buildGraph();
    }

    /**
     * Adds the nodes within the graphml file to the graph.
     * @param graph the graph instance
     * @param graphElement the graph element within the graphml
     * @throws GraphMLParseException In case something there is an error within the graphml document
     */

    private void parseNodes(Graph<String, String> graph, Element graphElement) throws GraphMLParseException {
        NodeList graphNodesList = graphElement.getElementsByTagName("node");
        for (int i = 0; i < graphNodesList.getLength(); i++) {
            Element nodeElement = (Element) graphNodesList.item(i);
            String vertexId = nodeElement.getAttribute("id");
            if (vertexId.isEmpty()) {
                throw new GraphMLParseException("Element 'node' with index " + i + " is missing required 'id' attribute.");
            }
            graph.addVertex(vertexId);
        }
    }

    /**
     * Adds the edges within the graphml file to the graph.
     * @param graph the graph instance
     * @param graphElement the graph element within the graphml
     * @throws GraphMLParseException In case something there is an error within the graphml document
     */

    private void parseEdges(Graph<String, String> graph, Element graphElement) throws GraphMLParseException {
        NodeList graphEdgesList = graphElement.getElementsByTagName("edge");
        if (graphEdgesList.getLength() == 0) {
            System.out.println("GraphMLReader: Warning, the graph inside the graphml file contains no edges!");
        }

        for (int i = 0; i < graphEdgesList.getLength(); i++) {
            Element edgeElement = (Element) graphEdgesList.item(i);
            String source = edgeElement.getAttribute("source");
            String target = edgeElement.getAttribute("target");
            String edgeId = edgeElement.getAttribute("id");

            if (source.isEmpty() || target.isEmpty()) {
                throw new GraphMLParseException("Edge " + (edgeId.isEmpty() ? "at index " + i : "with id " + edgeId) + " is missing 'source' or 'target' attribute.");
            }
            if (graph.findVertex(source) == -1 || graph.findVertex(target) == -1) {
                throw new GraphMLParseException("Edge " + (edgeId.isEmpty() ? "at index " + i : "with id " + edgeId) + " references non-existent nodes.");
            }
            graph.addEdge(source, target);
        }
    }
}
