const modal = document.getElementById("myModal");

const btn = document.getElementById("random-button");

const span = document.getElementsByClassName("close")[0];


btn.onclick = function() {
    modal.style.display = "block";
}

const cancelBtn = document.querySelector(".cancel-btn");

cancelBtn.onclick = function() {
    modal.style.display = "none";
}

window.onclick = function(event) {
    if (event.target == modal) {
        modal.style.display = "none";
    }
}

document.getElementById("graphForm").onsubmit = function(event) {
    event.preventDefault();
    const nodes = document.getElementById("nodes").value;
    const edges = document.getElementById("edges").value;
    console.log("Generating graph with", nodes, "nodes and", edges, "edges");
    const randomGraph = generateRandomGraph(nodes, edges);
    graph.nodes = randomGraph.vertices;
    graph.edges = randomGraph.edges;
    // graph.setOptionsNetwork(false);
    graph.regenerateNetwork();
    // graph.setOptionsNetwork(true);
    edgesList.replaceChildren(...graph.getEdgesElements());
    validEdges = graph.getValidEdges();

    modal.style.display = "none";
}

function generateRandomGraph(numNodes, numEdges) {
    if(numEdges > (numNodes * (numNodes - 1)) / 2 ) {
        numEdges = (numNodes * (numNodes - 1)) / 2;
    }
    const vertices = [];

    for(let i = 0; i < numNodes; i++) {
        const id = i.toString();
        const label = i.toString();
        vertices.push({ id, label });
    }
    const edges = [];

    function getRandomWeightedIndex(center, max, count) {
        let weights = [];
        for (let i = 0; i < max; i++) {
            weights.push(1 / (Math.abs(center - i) + 1));
        }
        let sum = weights.reduce((a, b) => a + b, 0);
        let acc = 0;
        let chances = weights.map(w => (acc += w / sum));
        let random = Math.random();
        for (let i = 0; i < chances.length; i++) {
            if (random < chances[i]) return i;
        }
        return count - 1;
    }

    while (edges.length < numEdges) {
        var from = Math.floor(Math.random() * numNodes);
        var to = getRandomWeightedIndex(from, numNodes, numNodes);

        if (from !== to && !edges.some(e => (e.from == from && e.to == to) || (e.from == to && e.to == from))) {
            from = from.toString();
            to = to.toString();
            edges.push({ from, to });
        }
    }

    return { vertices, edges };
}