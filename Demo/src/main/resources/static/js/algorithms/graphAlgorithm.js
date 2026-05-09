class GraphAlgorithmRepresentation {
    constructor(vertices, edges) {
        this.adjList = new Array(vertices.length);
        for (let i = 0; i < vertices.length; i++) {
            this.adjList[i] = [];
        }

        for(let i = 0; i < edges.length; i++) {
            const from = parseInt(edges[i].from, 10);
            const to = parseInt(edges[i].to, 10);
            this.addEdge(from, to);
        }
    }

    addEdge(v, w) {
        this.adjList[v].push(w);
        this.adjList[w].push(v);
    }

    numVertices() {
        return this.adjList.length;
    }

    neighbors(vertex) {
        return this.adjList[vertex];
    }
}