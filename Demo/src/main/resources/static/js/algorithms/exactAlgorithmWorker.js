importScripts('graphAlgorithm.js');
importScripts("../constants.js");

class LongestInducedPathExact {
    constructor(graph, delay) {
        this.pause = false;
        this.delay = delay;
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

    // findLongestInducedPathDelay(current, start, invalidVertices, inducedPath) {
    //     setTimeout(() => {
    //         this.findLongestInducedPath(current, start, invalidVertices, inducedPath);
    //     }, this.delay);
    // }

    // async findLongestInducedPath(startNode, invalidVertices, initialPath) {
    //     let stack = [{ current: startNode, index: 0, path: [...initialPath] }];
    //     let longestInducedPath = [];
    
    //     while (stack.length > 0) {
    //         let top = stack.pop();
    //         let { current, index, path, lastNeighbor } = top;
    //         path.push(current);
    
    //         while(stop) {
    //             await sleep(10);
    //         }
    //         let neighbors = this.graph.neighbors(current);
    //         neighbors.forEach(neighbour => invalidVertices[neighbour]++);

    //         if(lastNeighbor !== -1) {
    //             this.updateEdgeColor(current, lastNeighbor, INVALID_EDGE_COLOR);
    //             this.updateVertexColor(lastNeighbor, INVALID_VERTEX_COLOR);

    //             for(let j = index; j < neighbors.length; j++) {
    //                 if(invalidVertices[neighbors[j]] === 1) {
    //                     this.updateVertexColor(neighbors[j], DEFAULT_VERTEX_COLOR);
    //                     await sleep(delay / 10);
    //                     this.updateEdgeColor(current, neighbors[j], SELECTED_EGE_COLOR);
    //                 }
    //             }
    //         } else {
    //             for(let i = 0; i < neighbors.length; i++) {
    //                 if(invalidVertices[neighbors[i]] === 1) {
    //                     this.updateEdgeColor(current, neighbors[i], SELECTED_EGE_COLOR);
    //                 }
    //                 await sleep(delay / 10);
    //             }
    //         }

    //         if (index < neighbors.length) {
    //             let neighbour = neighbors[index];
    //             while(index < neighbors.length && invalidVertices[neighbour] !== 1) {
    //                 index++;
    //                 neighbour = neighbors[index];
    //             }
    //             if (index < neighbors.length) {
    //                 this.updateVertexColor(neighbour, SELECTED_VERTEX_COLOR);
    //                 for(let j = index + 1; j < neighbors.length; j++) {
    //                     if(invalidVertices[neighbors[j]] === 1) {
    //                         this.updateEdgeColor(current, neighbors[j], INVALID_EDGE_COLOR);
    //                         await sleep(delay / 10);
    //                         this.updateVertexColor(neighbors[j], INVALID_VERTEX_COLOR);
    //                     }
    //                 }
    //                 stack.push({ current, index: index + 1, path, neighbour });
    //                 stack.push({ current: neighbour, index: 0, path: [...path], lastNeighbor: -1 });
    //             }
    //         }
    //         if (index >= neighbors.length) {
    //             if (path.length > longestInducedPath.length) {
    //                 longestInducedPath = [...path];
    //             }
    //             path.pop();
    //             neighbors.forEach(neighbour => invalidVertices[neighbour]--);
    //         }
    //     }
    
    //     this.longestInducedPath = longestInducedPath;
    // }

    async findLongestInducedPath(current, invalidVertices, inducedPath) {
        while(this.pause === true) {
            await sleep(10);
        }
        inducedPath.push(current);
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

        await sleep(this.delay / 2);

        for(let i = 0; i < neighbors.length; i++) {
            let neighbour = neighbors[i];
            if (invalidVertices[neighbour] === 1) {
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
                
                await this.findLongestInducedPath(neighbour, invalidVertices, inducedPath);

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

        if (inducedPath.length > this.longestInducedPath.length) {
            this.longestInducedPath = inducedPath.slice();
            postMessage({ type: "update", path: this.longestInducedPath });
        }
        while(this.pause === true) {
            await sleep(10);
        }
        await sleep(this.delay / 2);
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
            let inducedPath = [];
            invalidVertices[i] = 1;
            this.updateVertexColor(i, START_VERTEX_COLOR);
            await this.findLongestInducedPath(i, invalidVertices, inducedPath);
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
        graph = new GraphAlgorithmRepresentation(vertices, edges);
        inducedPathFinder = new LongestInducedPathExact(graph, delay);
        const longestInducedPathFound = await inducedPathFinder.getLongestInducedPath();
        const type = "finish";
        const outputDelay = 5000 / longestInducedPathFound.length;
        postMessage({type, longestInducedPathFound});
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