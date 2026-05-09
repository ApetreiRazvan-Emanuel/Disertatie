importScripts('graphAlgorithm.js');
importScripts("../constants.js");

class LongestInducedPathExact {
    constructor(graph, delay, maxPaths) {
        this.pause = false;
        this.delay = delay;
        this.maxPaths = 0;
        this.numberOfPaths = 0;
        this.lastImprov = 0;
        this.truncated = false;
        this.graph = graph;
        this.numVertices = graph.adjList.length;
        this.longestInducedPath = [];
    }

    checkAdjacency(vertex1, vertex2) {
        return this.graph.neighbors(vertex1).includes(vertex2);
    }

    updateVertexColor(node, color) {
        const type = "colorChangeVertex";
        postMessage({ type, node, color });
    }

    updateEdgeColor(from, to, color) {
        const type = "colorChangeEdge";
        postMessage({ type, from, to, color });
    }

    async findLongestInducedPath(start, current, invalidVertices, inducedPath, backwards) {
        if(this.truncated === true) {
            return;
        }
        while(this.pause === true) {
            await sleep(10);
        }
        if(backwards === false) {
            inducedPath.push(current);
        } else {
            inducedPath.unshift(current);
        }
        // postMessage({ type: "debug", message: current });
        let neighbors = this.graph.neighbors(current);
        // postMessage({ type: "debug", message: neighbors });
        neighbors.forEach(neighbour => invalidVertices[neighbour]++);

        for(let i = 0; i < neighbors.length; i++) {
            if(invalidVertices[neighbors[i]] === 1) {
                this.updateEdgeColor(current, neighbors[i], CANDIDATE_EGE_COLOR);
                while(this.pause === true) {
                    await sleep(10);
                }
                await sleep(this.delay / 2);
                this.updateVertexColor(neighbors[i], CANDIDATE_VERTEX_COLOR);
                while(this.pause === true) {
                    await sleep(10);
                }
                await sleep(this.delay / 2);
            }
        }

        await sleep(this.delay);

        var foundNeighbor = false;

        for(let i = 0; i < neighbors.length; i++) {
            let neighbour = neighbors[i];
            if (invalidVertices[neighbour] === 1) {
                foundNeighbor = true;
                while(this.pause === true) {
                    await sleep(10);
                }
                await sleep(this.delay / 2);
                this.updateEdgeColor(current, neighbour, SELECTED_EGE_COLOR);
                while(this.pause === true) {
                    await sleep(10);
                }
                await sleep(this.delay);
                this.updateVertexColor(neighbour, SELECTED_VERTEX_COLOR);
                for(let j = i + 1; j < neighbors.length; j++) {
                    if(invalidVertices[neighbors[j]] === 1) {
                        while(this.pause === true) {
                            await sleep(10);
                        }
                        await sleep(this.delay / 2);
                        this.updateEdgeColor(current, neighbors[j], INVALID_EDGE_COLOR);
                        while(this.pause === true) {
                            await sleep(10);
                        }
                        await sleep(this.delay / 2);
                        this.updateVertexColor(neighbors[j], INVALID_VERTEX_COLOR);
                        const invalidVertexNeighbours = this.graph.neighbors(neighbors[j]);
                        for(let k = 0; k < invalidVertexNeighbours.length; k++) {
                            while(this.pause === true) {
                                await sleep(10);
                            }
                            await sleep(this.delay / 2);
                            this.updateEdgeColor(neighbors[j], invalidVertexNeighbours[k], INVALID_EDGE_COLOR);
                        }
                        if(invalidVertexNeighbours.length !== 0) {
                            await sleep(this.delay / 2);
                        }
                    }
                }
                
                await this.findLongestInducedPath(start, neighbour, invalidVertices, inducedPath, backwards);

                var exists = false;
                for(let j = i + 1; j < neighbors.length; j++) {
                    if(invalidVertices[neighbors[j]] === 1) {
                        exists = true;
                    }
                }
                if(exists === true) {
                    while(this.pause === true) {
                        await sleep(10);
                    }
                    await sleep(this.delay);
                    this.updateEdgeColor(current, neighbour, DEFAULT_EDGE_COLOR);
                    await sleep(this.delay);
                    this.updateEdgeColor(current, neighbour, INVALID_EDGE_COLOR);
                    while(this.pause === true) {
                        await sleep(10);
                    }
                    await sleep(this.delay);
                    this.updateVertexColor(neighbour, INVALID_VERTEX_COLOR);
                    for(let j = i + 1; j < neighbors.length; j++) {
                        if(invalidVertices[neighbors[j]] === 1) {
                            while(this.pause === true) {
                                await sleep(10);
                            }
                            await sleep(this.delay);
                            this.updateEdgeColor(current, neighbors[j], CANDIDATE_EGE_COLOR);
                            while(this.pause === true) {
                                await sleep(10);
                            }
                            await(sleep(this.delay));
                            this.updateVertexColor(neighbors[j], CANDIDATE_VERTEX_COLOR);
                            const furtherVertexNeighbours = this.graph.neighbors(neighbors[j]);
                            for(let k = 0; k < furtherVertexNeighbours.length; k++) {
                                if(invalidVertices[furtherVertexNeighbours[k]] === 0) {
                                    while(this.pause === true) {
                                        await sleep(10);
                                    }
                                    await sleep(this.delay / 2);
                                    this.updateEdgeColor(neighbors[j], furtherVertexNeighbours[k], DEFAULT_EDGE_COLOR);
                                }
                            }
                        }
                    }
                }
            }
        };

        if(foundNeighbor === false) {
                if(backwards === false) {
                // postMessage({ type: "debug", message: current });
                let startingNodeNeighbors = this.graph.neighbors(start);
                // postMessage({ type: "debug", message: start });
                for(let i = 0; i < startingNodeNeighbors.length; i++) {
                    let neighbour = startingNodeNeighbors[i];
                    if(invalidVertices[neighbour] === 1) {
                        while(this.pause === true) {
                            await sleep(10);
                        }
                        await sleep(this.delay);
                        this.updateEdgeColor(start, neighbour, CANDIDATE_EGE_COLOR);
                        while(this.pause === true) {
                            await sleep(10);
                        }
                        await(sleep(this.delay));
                        this.updateVertexColor(neighbour, CANDIDATE_VERTEX_COLOR);
                        const furtherVertexNeighbours = this.graph.neighbors(neighbour);
                        for(let k = 0; k < furtherVertexNeighbours.length; k++) {
                            while(this.pause === true) {
                                await sleep(10);
                            }
                            await sleep(this.delay / 2);
                            if(invalidVertices[furtherVertexNeighbours[k]] === 0) {
                                this.updateEdgeColor(neighbour, furtherVertexNeighbours[k], DEFAULT_EDGE_COLOR);
                            }
                        }
                    }
                }
                for(let i = 0; i < startingNodeNeighbors.length; i++) {
                    let neighbour = startingNodeNeighbors[i];
                    if (invalidVertices[neighbour] === 1) {
                        while(this.pause === true) {
                            await sleep(10);
                        }
                        await sleep(this.delay / 2);
                        this.updateEdgeColor(start, neighbour, SELECTED_EGE_COLOR);
                        while(this.pause === true) {
                            await sleep(10);
                        }
                        await sleep(this.delay);
                        this.updateVertexColor(neighbour, SELECTED_VERTEX_COLOR);
                        for(let j = i + 1; j < startingNodeNeighbors.length; j++) {
                            if(invalidVertices[startingNodeNeighbors[j]] === 1) {
                                while(this.pause === true) {
                                    await sleep(10);
                                }
                                await sleep(this.delay / 2);
                                this.updateEdgeColor(start, startingNodeNeighbors[j], INVALID_EDGE_COLOR);
                                while(this.pause === true) {
                                    await sleep(10);
                                }
                                await sleep(this.delay / 2);
                                this.updateVertexColor(startingNodeNeighbors[j], INVALID_VERTEX_COLOR);
                                const invalidVertexNeighbours = this.graph.neighbors(startingNodeNeighbors[j]);
                                for(let k = 0; k < invalidVertexNeighbours.length; k++) {
                                    while(this.pause === true) {
                                        await sleep(10);
                                    }
                                    await sleep(this.delay / 2);
                                    this.updateEdgeColor(startingNodeNeighbors[j], invalidVertexNeighbours[k], INVALID_EDGE_COLOR);
                                }
                            }
                        }
                        
                        await this.findLongestInducedPath(start, neighbour, invalidVertices, inducedPath, true);
        
                        while(this.pause === true) {
                            await sleep(10);
                        }
                        await sleep(this.delay);
                        this.updateEdgeColor(start, neighbour, INVALID_EDGE_COLOR);
                        while(this.pause === true) {
                            await sleep(10);
                        }
                        await sleep(this.delay);
                        this.updateVertexColor(neighbour, INVALID_VERTEX_COLOR);
                        for(let j = i + 1; j < startingNodeNeighbors.length; j++) {
                            if(invalidVertices[startingNodeNeighbors[j]] === 1) {
                                while(this.pause === true) {
                                    await sleep(10);
                                }
                                await sleep(this.delay);
                                this.updateEdgeColor(start, startingNodeNeighbors[j], CANDIDATE_EGE_COLOR);
                                while(this.pause === true) {
                                    await sleep(10);
                                }
                                await(sleep(this.delay));
                                this.updateVertexColor(startingNodeNeighbors[j], CANDIDATE_VERTEX_COLOR);
                                const furtherVertexNeighbours = this.graph.neighbors(startingNodeNeighbors[j]);
                                for(let k = 0; k < furtherVertexNeighbours.length; k++) {
                                    while(this.pause === true) {
                                        await sleep(10);
                                    }
                                    await sleep(this.delay / 2);
                                    if(invalidVertices[furtherVertexNeighbours[k]] === 0) {
                                        this.updateEdgeColor(startingNodeNeighbors[j], furtherVertexNeighbours[k], DEFAULT_EDGE_COLOR);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            this.numberOfPaths++;

            if (inducedPath.length > this.longestInducedPath.length) {
                this.longestInducedPath = inducedPath.slice();
                this.lastImprov = this.numberOfPaths;
                postMessage({ type: "update", path: this.longestInducedPath });
            }

            if(this.numberOfPaths - this.lastImprov > this.maxPaths) {
                this.truncated = true;
            }
        }
        while(this.pause === true) {
            await sleep(10);
        }
        await sleep(this.delay);
        neighbors.forEach(neighbour => invalidVertices[neighbour]--);
        for(let i = 0; i < neighbors.length; i++) {
            if(invalidVertices[neighbors[i]] === 0) {
                this.updateVertexColor(neighbors[i], DEFAULT_VERTEX_COLOR);
                while(this.pause === true) {
                    await sleep(10);
                }
                await(sleep(this.delay / 2));
                this.updateEdgeColor(current, neighbors[i], DEFAULT_EDGE_COLOR);
            }
        }
        while(this.pause === true) {
            await sleep(10);
        }
        await sleep(this.delay / 2);
        this.updateVertexColor(current, DEFAULT_VERTEX_COLOR);
        inducedPath.pop();
    }

    async compute() {
        let invalidVertices = new Array(this.numVertices).fill(0);
        for (let i = 0; i < this.numVertices; i++) {
            this.numberOfPaths = 0;
            this.truncated = false;
            this.lastImprov = 0;
            let inducedPath = [];
            invalidVertices[i] = 1;
            this.updateVertexColor(i, START_VERTEX_COLOR);
            await this.findLongestInducedPath(i, i, invalidVertices, inducedPath, false);
            this.updateVertexColor(i, DEFAULT_VERTEX_COLOR);
            invalidVertices[i] = 0;
        }
    }

    async getLongestInducedPath() {
        if (this.longestInducedPath.length === 0) {
            await this.compute();
        }
        return this.longestInducedPath;
    }
}

var graph = null;
var inducedPathFinder = null;
onmessage = async function(e) {
    if(e.data.type === 'update') {
        if(inducedPathFinder !== null) {
            inducedPathFinder.delay = e.data.delay;
        }
    }
    if(e.data.type === 'pause') {
        if(inducedPathFinder !== null) {
            inducedPathFinder.pause = e.data.value;
        }
    }
    if(e.data.type === 'start') {
        const vertices = e.data.vertices;
        const edges = e.data.edges;
        const delay = e.data.delay;
        const maxPaths = e.data.maxPaths;
        graph = new GraphAlgorithmRepresentation(vertices, edges);
        inducedPathFinder = new LongestInducedPathExact(graph, delay, maxPaths);
        const longestInducedPathFound = await inducedPathFinder.getLongestInducedPath();
        const type = "finish";
        postMessage({type, longestInducedPathFound});
        const outputDelay = 5000 / longestInducedPathFound.length;
        for(let i = 0; i < longestInducedPathFound.length; i++) {
            const from = longestInducedPathFound[i].toString();

            await sleep(outputDelay);

            if(i === 0 || i === longestInducedPathFound.length - 1) {
                inducedPathFinder.updateVertexColor(from, START_VERTEX_COLOR);
            } else {
                inducedPathFinder.updateVertexColor(from, SELECTED_VERTEX_COLOR);
            }

            if(i !== longestInducedPathFound.length - 1) {
                await sleep(outputDelay);
                const to = longestInducedPathFound[i + 1].toString();
                inducedPathFinder.updateEdgeColor(from, to, SELECTED_EGE_COLOR);
            }
        }
    }
}