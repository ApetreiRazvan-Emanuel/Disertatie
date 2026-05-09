class Graph {
    constructor() {
        this.nodes = [];
        this.edges = [];
        this.nodesDataSet = [];
        this.edgesDataSet = [];
        this.nodeSet = new Set();
        this.heuristic = false;
    }

    fromAdjacencyList(adjacencyList) {
        this.nodes = [];
        this.edges = [];
        this.nodeSet = new Set();
        let maxValue = 0;
        for (const [from, toList] of Object.entries(adjacencyList)) {
            if(from > maxValue) {
                maxValue = from;
            }
            if(toList.length > 0) {
                if(toList[toList.length - 1] > maxValue) {
                    maxValue = toList[toList.length - 1];
                }
            }
        }

        for (let i = 0; i <= maxValue; i++) {
            this.addVertex(i.toString(), i.toString());
        }

        for (const [from, toList] of Object.entries(adjacencyList)) {
            for (const to of toList) {
                this.addEdge(from, to.toString());
            }
        }
    }

    getEdgesElements() {
        const edgesList = [];
        for(let i = 0; i < this.edges.length; i++) {
            edgesList.push(this.getEdgeElement(i));
        }
        return edgesList;
    }

    getValidEdges() {
        const validEdges = [];
        for(let i = 0; i < this.edges.length; i++) {
            const from = parseInt(this.edges[i].from, 10);
            const to = parseInt(this.edges[i].to, 10);
            validEdges.push({from, to});
        }
        return validEdges;
    }

    getEdgeElement(position) {
        const newLi = document.createElement('li');
        const newLa = document.createElement('label');
        const newInput = document.createElement('input');
        const newButton = document.createElement('button');
        const newIcon = document.createElement('i');
        newLi.className = "edge";
        newLa.className = "edge-label";
        newLa.textContent = position;
        newInput.type = 'text';
        newInput.className = 'edge-input';
        newInput.value = this.edges[position].from + "-" + this.edges[position].to;
        newIcon.className = 'fas fa-exclamation-circle';
        newIcon.title = "Invalid edge! Example of valid edge: 2-3";
        newButton.className = "edge-delete-button";
        newButton.textContent = "X";
        newLi.appendChild(newLa);
        newLi.appendChild(newInput);
        newLi.appendChild(newIcon);
        newLi.appendChild(newButton);

        inputAddEventListeners(newInput);
        deleteEdgeButtonAddEventListeners(newButton);
        return newLi;
    }

    updateNetwork() {
        const nodePositions = network.getPositions();
        const scale = network.getScale();
        const viewPosition = network.getViewPosition();

        network.setData(this.getGraphData());

        for (const id in nodePositions) {
            if (nodePositions.hasOwnProperty(id)) {
                const pos = nodePositions[id];
                if(this.nodes.find(node => node.id == id) !== undefined) {
                    network.moveNode(id, pos.x, pos.y);
                }
            }
        }

        if(this.heuristic === true) {
        network.once('stabilized', () => {
            network.moveTo({
                position: viewPosition,
                scale: scale
            });
        });
        } else {
            network.moveTo({
                position: viewPosition,
                scale: scale
            });
        }
    }

    setOldOptions() {
        const nodePositions = network.getPositions();
        const scale = network.getScale();
        const viewPosition = network.getViewPosition();

        network.setOptions({ physics: { enabled: false } });

        for (const id in nodePositions) {
            if (nodePositions.hasOwnProperty(id)) {
                const pos = nodePositions[id];
                if(this.nodes.find(node => node.id == id) !== undefined) {
                    network.moveNode(id, pos.x, pos.y);
                }
            }
        }
        
        network.once('stabilized', () => {
            network.moveTo({
                position: viewPosition,
                scale: scale
            });
        });
    }


    regenerateNetwork() {
        network.setData(this.getGraphData());
    }

    modifyNumberOfVertices(numVertices) {
        if(numVertices == this.nodes.length) {
            return;
        }
        this.nodes = []
        this.nodeSet.clear();
        for(let i = 0; i < numVertices; i++) {
            this.addVertex(i.toString(), i.toString());
        }

        this.edges = this.edges.filter(edge => {
            const fromNode = this.nodes.find(node => node.id === edge.from);
            const toNode = this.nodes.find(node => node.id === edge.to);
            return fromNode !== undefined && toNode !== undefined;
        });

        this.updateNetwork();
    }

    addVertex(id, label) {
        if (!this.nodeSet.has(id)) {
            this.nodes.push({ id, label });
            this.nodeSet.add(id);
        }
    }

    addEdge(from, to) {
        const edgeExists = this.edges.some(edge => (edge.from === from && edge.to === to) || (edge.from === to && edge.to === from));
        if (!edgeExists) {
            this.edges.push({ from, to });
            return true;
        }
        return false;
    }

    removeEdge(from, to) {
        this.edges = this.edges.filter(edge => !(edge.from == from && edge.to == to) && !(edge.from == to && edge.to == from));
    }

    getGraphData() {
        this.nodesDataSet = new vis.DataSet(this.nodes);
        this.edgesDataSet = new vis.DataSet(this.edges);
        return {
            nodes: this.nodesDataSet,
            edges: this.edgesDataSet
        };
    }

    highlightIndividual(individual) {
        this.nodes.forEach(node => {
            node.color = {background: '#D2E5FF'};
        });
        this.edges.forEach(edge => {
            edge.color = undefined;
        });

        for (let i = 0; i < individual.length; i++) {
            if (individual[i] === true) {
                this.nodes[i].color = {background: '#90EE90'};
            }
        }

        for(const edge of this.edges) {
            const fromNode = this.nodes.find(node => node.id === edge.from);
            const toNode = this.nodes.find(node => node.id === edge.to);
            if (fromNode.color.background === '#90EE90' && toNode.color.background === '#90EE90') {
                edge.color = '#90EE90';
            }
        }

        network.setData(this.getGraphData());
    }

    resetColor() {
        this.nodes.forEach(node => {
            node.color = {background: DEFAULT_VERTEX_COLOR};
        });
    
        this.edges.forEach(edge => {
            edge.color = DEFAULT_EDGE_COLOR;
        });

        this.updateNetwork();
    };

    highlightNode(nodeId, color) {
        this.nodesDataSet.update({ id: nodeId, color: { background: color } });
    }

    highlightEdge(fromId, toId, color) {
        const edge = this.edges.find(edge => (edge.from === fromId && edge.to === toId) || (edge.to === fromId && edge.from === toId));
        const edgeId = edge.id;
        this.edgesDataSet.update({ id: edgeId, color: color });
    }

    highlightNodeAndEdges(nodeId, color) {
        const node = this.nodes.find(node => node.id === nodeId);
        node.color = {background: color};
        for(const edge of this.edges) {
            if(edge.from === nodeId || edge.to === nodeId)
                edge.color = color;
        }
        updateNetwork();
    }

    highlightPathWithEccentricity(info) {
        let maxPath = info[0][1][0];
        console.log(maxPath);

        this.nodes.forEach(node => {
            node.color = {background: '#D2E5FF'};
        });
    
        let minEcc = Infinity, maxEcc = -Infinity;
        info.forEach(([vertex, [bestFound, ecc]]) => {
            if (bestFound == maxPath) {
                if (ecc < minEcc) minEcc = ecc;
                if (ecc > maxEcc) maxEcc = ecc;
            }
        });
    
        info.forEach(([vertex, [bestFound, ecc]]) => {
            if (bestFound == maxPath) {
            const intensity = (ecc - minEcc) / (maxEcc - minEcc);
            const greenIntensity = Math.floor(255 * intensity);
            const colorValue = `rgb(0, ${greenIntensity}, 0)`;
            this.nodes[vertex].color = {background: colorValue};
            }
        });
    
        this.edges.forEach(edge => {
            const fromNode = this.nodes.find(node => node.id === edge.from);
            const toNode = this.nodes.find(node => node.id === edge.to);
            console.log(fromNode.color.background, toNode.color.background)
            if (fromNode.color.background !== '#D2E5FF' && toNode.color.background !== '#D2E5FF') {
                edge.color = 'green';
            }
        });
    
        network.setData(this.getGraphData());
    }

    highlightInducedPathWithBorder(individuals) {
        this.nodes.forEach((node, index) => {
            if (individuals[index]) {
                node.borderWidth = 4;
                node.borderWidthSelected = 6;
                node.borderColor = '#ff0000';
            } 
        });
    
        network.setData(this.getGraphData());
    }
}

const container = document.getElementById('graph-wrapper');
const graph = new Graph();

// graph.fromAdjacencyList({0: [1, 3, 4], 1: [0, 2, 4, 5], 2: [1, 5, 6], 3: [0, 7], 4: [0, 1], 5: [1, 2], 6: [2, 7], 7: []})
// graph.fromAdjacencyList({0: [1, 7, 8, 14], 1: [2, 5, 9, 14], 2: [3, 4, 6], 3: [4, 10], 4: [6, 13], 5: [10], 6: [9], 7: [10, 11], 8: [11], 9: [], 10: [12, 13], 11: [12], 12: [], 13: [], 14: []});
// graph.fromAdjacencyList({0: [1, 2], 1: [0, 11], 2: [0, 4, 11], 3: [4, 10], 4: [3, 2, 5, 7, 10], 5: [4, 6], 6: [5, 11], 7: [8, 4, 29], 8: [7, 9], 9: [8], 10: [3, 4, 11, 12], 11: [10, 1, 2, 6, 13, 15, 116], 12: [10, 14], 13: [11, 14], 14: [12, 13, 16, 18, 32], 15: [11, 16], 16: [14, 15, 17, 29, 30, 112], 17: [16, 18], 18: [17, 19, 14, 33], 19: [18, 20], 20: [19, 21], 21: [20, 22], 22: [21, 23, 24, 31], 23: [22, 69, 71], 24: [22, 25, 26], 25: [24, 29], 26: [24, 27, 31, 114], 27: [26, 28], 28: [27, 30], 29: [16, 7, 25, 37], 30: [16, 28, 31], 31: [22, 30, 26, 112, 113], 32: [14, 36], 33: [18, 35, 36, 42], 34: [35, 36], 35: [34, 33], 36: [34, 32, 33, 37, 38, 39], 37: [36, 29, 64], 38: [36, 39], 39: [36, 38, 40, 41], 40: [39, 41], 41: [39, 40, 48], 42: [43, 33], 43: [42, 44], 44: [43, 45, 48], 45: [44, 46, 47], 46: [45, 48, 68], 47: [45, 48], 48: [46, 41, 44, 47, 49, 50, 53, 65, 68], 49: [48, 56], 50: [48, 51, 57], 51: [50, 52], 52: [51, 53], 53: [52, 48, 54, 55, 58], 54: [53, 55, 58], 55: [53, 54, 56, 57, 58], 56: [55, 49], 57: [55, 50], 58: [53, 55, 54, 59, 60, 62], 59: [58, 60, 61], 60: [58, 59, 61, 63], 61: [59, 60, 65, 66], 62: [58, 63], 63: [62, 60, 64], 64: [37, 63, 65, 67], 65: [48, 61, 64, 66], 66: [61, 65], 67: [64, 68, 80, 115], 68: [46, 48, 67, 69, 74, 76], 69: [68, 23, 70, 73, 74], 70: [69, 71, 72], 71: [23, 70], 72: [70], 73: [69, 74], 74: [69, 68, 73, 76, 117], 75: [76, 117], 76: [75, 68, 74, 77, 79, 81], 77: [76, 78], 78: [77, 79], 79: [76, 78, 80, 95, 96, 97, 98], 80: [67, 79], 81: [76, 82, 95], 82: [81, 83, 84], 83: [82, 84], 84: [82, 83, 85, 87, 88], 85: [84, 86], 86: [85], 87: [84, 88], 88: [84, 87, 89, 91], 89: [88, 90], 90: [89, 91], 91: [88, 90, 92, 93, 99, 101], 92: [91, 93], 93: [91, 92, 94, 95, 99], 94: [93, 95], 95: [79, 81, 93, 94, 96], 96: [79, 95], 97: [79, 99], 98: [79, 99], 99: [91, 93, 97, 98, 100, 102, 103, 105], 100: [99, 101], 101: [91, 100], 102: [99, 103, 104, 109], 103: [99, 102, 104], 104: [102, 103, 105, 106, 107], 105: [99, 104, 106], 106: [104, 105], 107: [104, 108], 108: [107, 109], 109: [102, 108, 110, 111], 110: [109], 111: [109], 112: [16, 31], 113: [31, 114], 114: [26, 113], 115: [67], 116: [11], 117: [74, 75]});
// graph.fromAdjacencyList({0: [1], 1: [0, 2, 3], 2: [1, 4, 5, 6], 3: [1, 9, 12], 4: [2], 5: [2, 7, 8, 9, 10], 6: [2, 8, 7, 9, 11], 7: [5, 8, 6, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22], 8: [5, 7, 6], 9: [5, 6, 3, 11], 10: [5, 12], 11: [6, 9, 33, 35, 34], 12: [3, 10], 13: [7, 23, 15, 18], 14: [7, 17], 15: [7, 13, 23, 16], 16: [7, 15, 18], 17: [7, 14, 24], 18: [7, 13, 16, 24, 25, 19], 19: [7, 18], 20: [7, 22, 28, 32, 21, 33, 34], 21: [7, 32, 20, 33], 22: [7, 26, 27, 28, 20], 23: [13, 24, 15, 27], 24: [23, 17, 18], 25: [18], 26: [22, 29, 27], 27: [22, 26, 29, 23], 28: [22, 31, 20], 29: [26, 30, 27], 30: [29], 31: [28], 32: [20, 21], 33: [20, 21, 11], 34: [20, 35, 11], 35: [34, 11]});
graph.fromAdjacencyList({0: [1, 7, 10], 1: [0, 2, 6], 2: [3, 5, 8], 3: [4], 4: [9], 5: [7], 6: [], 7: [], 8: [9], 9: [], 10: [11], 11: []});
const data = graph.getGraphData();
var options = {
    edges: {
        smooth: {
            enabled: false
        }
    },
    physics: {
        enabled: false
    }
};
var network = new vis.Network(container, data, options);

network.on("oncontext", function (params) {
    params.event.preventDefault();
    const nodeId = this.getNodeAt(params.pointer.DOM);
    const edgeId = this.getEdgeAt(params.pointer.DOM);

    if (nodeId !== undefined) {
        const node = graph.nodes.find(node => node.id === nodeId);
        console.log(node.color)
        const color = prompt("Enter a color for this node:");
        if (color) {
            graph.highlightNode(nodeId, color);
        }
    } else if (edgeId !== undefined) {
        const edge = graph.edges.find(edge => edge.id === edgeId);
        console.log(edge.color)
        const color = prompt("Enter a color for this edge:");
        if (color) {
            graph.highlightEdge(edgeId, color);
        }
    }
});

// graph.highlightIndividual([true, true, false, false, true, false, true, false, false, false, true, false, true, false, true, true, true, false, true, false, false, true, false, true, false, true, false, false, false, true, false, true, false, true, false, true, true, false, false, true, true, true, false, false, false, true, false, true, true, false, true, false, false, true, false, false, false, true, false]);
// graph.highlightIndividual([false, false, false, false, true, true, true, true, false, false, false, true, false, true, true, false, false, false, false, false, false, false, true, true, true, true, false, false, false, true, false, false, true, true, false, false, true, false, false, false, false, false, true, true, true, false, false, false, true, true, false, false, false, false, false, true, true, false, true, false, true, false, false, true, true, false, false, true, false, true, false, false, false, false, true, false, true, false, false, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false]);
// graph.highlightIndividual([false, true, true, true, false, true, false, false, false, false, true, false, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false]);
network.setData(graph.getGraphData());
// graph.setOldOptions();
// #90EE90
// #00c04b
// const node = graph.nodes.find(node => node.id == 0);
// console.log(node);
// node.color = {background: '#00c04b'};
// console.log(graph)
// for(const edge of graph.edges) {
//     const fromNode = graph.nodes.find(node => node.id === edge.from);
//     const toNode = graph.nodes.find(node => node.id === edge.to);
//     console.log(fromNode, toNode, fromNode.color.background, toNode.color.background)
//     if((fromNode.id == 0 || toNode.id == 0) && (fromNode.color.background == '#90EE90' || toNode.color.background == '#90EE90'))
//         edge.color = '#00c04b';
// }
// graph.highlightPathWithEccentricity([[0, [47, 14]], [1, [47, 14]], [6, [47, 14]], [107, [47, 14]], [108, [47, 14]], [110, [47, 14]], [111, [47, 14]], [5, [47, 13]], [9, [47, 13]], [11, [47, 13]], [19, [47, 13]], [27, [47, 13]], [28, [47, 13]], [88, [47, 13]], [104, [47, 13]], [109, [47, 13]], [4, [47, 12]], [8, [47, 12]], [15, [47, 12]], [18, [47, 12]], [20, [47, 12]], [26, [47, 12]], [30, [47, 12]], [84, [47, 12]], [91, [47, 12]], [103, [47, 12]], [105, [47, 12]], [7, [47, 11]], [16, [47, 11]], [21, [47, 11]], [24, [47, 11]], [33, [47, 11]], [42, [47, 11]], [82, [47, 11]], [99, [47, 11]], [22, [47, 10]], [29, [47, 10]], [36, [47, 10]], [39, [47, 10]], [43, [47, 10]], [55, [47, 10]], [56, [47, 10]], [57, [47, 10]], [58, [47, 10]], [60, [47, 10]], [62, [47, 10]], [81, [47, 10]], [95, [47, 10]], [41, [47, 9]], [44, [47, 9]], [49, [47, 9]], [50, [47, 9]], [63, [47, 9]], [76, [47, 9]], [79, [47, 9]], [48, [47, 8]], [64, [47, 8]], [80, [47, 8]], [67, [47, 7]], [116, [46, 14]], [2, [46, 13]], [10, [46, 13]], [13, [46, 13]], [14, [46, 12]], [25, [46, 11]], [32, [46, 11]], [75, [46, 10]], [117, [46, 10]], [23, [46, 9]], [69, [46, 9]], [74, [46, 9]], [3, [45, 13]], [102, [45, 12]], [31, [45, 11]], [72, [45, 11]], [70, [45, 10]], [71, [45, 10]], [53, [45, 9]], [12, [44, 13]], [106, [44, 13]], [114, [44, 13]], [112, [44, 12]], [113, [44, 12]], [73, [44, 10]], [37, [44, 9]], [68, [44, 8]], [59, [43, 11]], [93, [43, 11]], [45, [43, 10]], [51, [43, 10]], [52, [43, 10]], [61, [43, 10]], [46, [43, 9]], [65, [43, 9]], [115, [43, 8]], [101, [42, 13]], [17, [42, 12]], [100, [42, 12]], [94, [42, 11]], [66, [42, 10]], [97, [42, 10]], [98, [42, 10]], [86, [41, 14]], [89, [41, 14]], [85, [41, 13]], [90, [41, 13]], [92, [41, 12]], [38, [41, 11]], [83, [40, 12]], [40, [40, 10]], [54, [40, 10]], [47, [40, 9]], [87, [39, 13]], [35, [39, 12]], [34, [39, 11]], [77, [36, 10]], [78, [36, 10]], [96, [35, 10]]]);
// graph.highlightInducedPathWithBorder([true, true, false, false, true, true, true, true, false, false, false, true, false, false, false, false, true, false, true, true, true, true, true, false, true, false, true, true, true, true, true, false, false, true, false, false, true, false, false, true, false, true, false, false, false, false, false, false, true, true, false, false, false, false, false, true, true, false, true, false, true, false, false, true, true, false, false, true, false, false, false, false, false, false, false, false, true, false, false, true, true, true, true, false, true, false, false, false, true, false, false, true, false, false, false, false, false, false, false, true, false, false, false, true, true, false, false, true, true, true, true, false, false, false, false, false, false, false]);

// graph.highlightNode('65', 'red');
// graph.highlightNode('67', 'red');
console.log(graph)
edgesList.replaceChildren(...graph.getEdgesElements());
numberOfNodesInput.value = graph.nodes.length;
validEdges = graph.getValidEdges();