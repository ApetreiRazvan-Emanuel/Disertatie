const outputWrapper = document.getElementById("output-wrapper");
const runAlgorithmBtn = document.getElementById("run-algorithm-btn");
const generateInputBtn = document.getElementById("generate-parameters-btn");
const populationSizeInput = document.getElementById("population-size-input");
const maxGenerationInput = document.getElementById("max-generation-input");
const dynamicMaxGenerationInput = document.getElementById("dynamic-max-generation-input");
const maxGenerationIncreaseInput = document.getElementById("max-generation-increase-input");
const elitismInput = document.getElementById("elitism-input");
const mutationRateInput = document.getElementById("mutation-rate-input");
const mutationCountInput = document.getElementById("mutation-count-input");
const randomMutationRateInput = document.getElementById("random-mutation-rate-input");
const probabilityCrossoverInput = document.getElementById("probability-crossover-input");
const selectionPressureInput = document.getElementById("selection-pressure-input");
const processEachComponentInput = document.getElementById("process-each-component-separately-input");
const algorithmOutput = document.getElementById("algorithm-output");

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

generateInputBtn.onclick = function() {
    const numVertices = graph.nodes.length;
    const numEdges = graph.edges.length;
    populationSizeInput.value = numVertices;
    maxGenerationInput.value = Math.ceil((numVertices + (numEdges / 2)) / 2);
    dynamicMaxGenerationInput.value = "true";
    maxGenerationIncreaseInput.value = 100;
    elitismInput.value = Math.ceil(populationSizeInput.value / 20);
    mutationRateInput.value = 0.1;
    mutationCountInput.value = 5;
    randomMutationRateInput.value = 0.85;
    probabilityCrossoverInput.value = 0.3;
    selectionPressureInput.value = 2;
    processEachComponentInput.value = isConnected() ? false : true;

    validatePopulationSize();
    validateMaxGeneration();
    validateElitism();
    validateMutationRate();
    validateMutationCount();
    validateRandomMutationRate();
    validateProbabilityCrossover();
    validateSelectionPressure();
    validateProcessEachComponent();
}

function DFS(current, visited, graphAlg) {
    visited[current] = true;
    let neighbors = graphAlg.neighbors(current);
    for(let i = 0; i < neighbors.length; i++) {
        if(visited[neighbors[i]] === false) {
            DFS(neighbors[i], visited, graphAlg);
        }
    }
}

function isConnected() {
    let graphAlg = new GraphAlgorithmRepresentation(graph.nodes, graph.edges);
    let visited = new Array(graphAlg.numVertices()).fill(false);
    let first = true;
    for(let i = 0; i < graphAlg.numVertices(); i++) {
        if(visited[i] === false) {
            if(first === false) {
                return false;
            }
            first = false;
            DFS(i, visited, graphAlg);
        }
    }
    return true;
}

function validatePopulationSize() {
    const populationSize = populationSizeInput.valueAsNumber;
    const isValid = Number.isInteger(populationSize) && populationSize > 0;
    populationSizeInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateMaxGeneration() {
    const maxGeneration = maxGenerationInput.valueAsNumber;
    const isValid = Number.isInteger(maxGeneration) && maxGeneration > 0;
    maxGenerationInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateDynamicMaxGeneration() {
    const dynamicMaxGeneration = dynamicMaxGenerationInput.value;
    const isValid = dynamicMaxGeneration === "true" || dynamicMaxGeneration === "false";
    dynamicMaxGenerationInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateMaxGenerationIncrease() {
    const maxGenerationIncrease = maxGenerationIncreaseInput.valueAsNumber;
    const isValid = Number.isInteger(maxGenerationIncrease) && maxGenerationIncrease >= 0;
    maxGenerationIncreaseInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateElitism() {
    const elitism = elitismInput.valueAsNumber;
    const isValid = Number.isInteger(elitism) && elitism >= 0;
    elitismInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateMutationRate() {
    const mutationRate = mutationRateInput.valueAsNumber;
    const isValid = !isNaN(mutationRate) && mutationRate >= 0 && mutationRate <= 1;
    mutationRateInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateMutationCount() {
    const mutationCount = mutationCountInput.valueAsNumber;
    const isValid = Number.isInteger(mutationCount) && mutationCount >= 0;
    mutationCountInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateRandomMutationRate() {
    const randomMutationRate = randomMutationRateInput.valueAsNumber;
    const isValid = !isNaN(randomMutationRate) && randomMutationRate >= 0 && randomMutationRate <= 1;
    randomMutationRateInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateProbabilityCrossover() {
    const probabilityCrossover = probabilityCrossoverInput.valueAsNumber;
    const isValid = !isNaN(probabilityCrossover) && probabilityCrossover >= 0 && probabilityCrossover <= 1;
    probabilityCrossoverInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateSelectionPressure() {
    const selectionPressure = selectionPressureInput.valueAsNumber;
    const isValid = !isNaN(selectionPressure) && selectionPressure > 0;
    selectionPressureInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

function validateProcessEachComponent() {
    const processEachComponent = processEachComponentInput.value;
    const isValid = processEachComponent === "true" || processEachComponent === "false";
    processEachComponentInput.nextElementSibling.style.visibility = isValid ? 'hidden' : 'visible';
}

populationSizeInput.addEventListener('input', validatePopulationSize);
maxGenerationInput.addEventListener('input', validateMaxGeneration);
dynamicMaxGenerationInput.addEventListener('input', validateDynamicMaxGeneration);
maxGenerationIncreaseInput.addEventListener('input', validateMaxGenerationIncrease);
elitismInput.addEventListener('input', validateElitism);
mutationRateInput.addEventListener('input', validateMutationRate);
mutationCountInput.addEventListener('input', validateMutationCount);
randomMutationRateInput.addEventListener('input', validateRandomMutationRate);
probabilityCrossoverInput.addEventListener('input', validateProbabilityCrossover);
selectionPressureInput.addEventListener('input', validateSelectionPressure);
processEachComponentInput.addEventListener('input', validateProcessEachComponent);

function areWarningsVisible() {
    const warnings = outputWrapper.querySelectorAll('.input-warning');

    for (let i = 0; i < warnings.length; i++) {
        const visibility = window.getComputedStyle(warnings[i]).visibility;
        if (visibility !== 'hidden') {
            return true;
        }
    }
    return false;
}

runAlgorithmBtn.onclick = function() {
    stopColoring = true;
    if (areWarningsVisible()) {
        alert('Please fix all input warnings before running the algorithm.');
        return;
    }

    const vertices = graph.nodes;
    const edges = graph.edges;
    const adjList = {};
    for (let i = 0; i < vertices.length; i++) {
        adjList[i] = [];
    }

    for(let i = 0; i < edges.length; i++) {
        const from = parseInt(edges[i].from, 10);
        const to = parseInt(edges[i].to, 10);
        adjList[from].push(to);
        adjList[to].push(from);
    }

    const algorithmConfig = {
        adjacencyList: adjList,
        popSize: populationSizeInput.valueAsNumber,
        maxGeneration: maxGenerationInput.valueAsNumber,
        dynamicMaxGeneration: dynamicMaxGenerationInput.value === "true",
        maxGenerationIncrease: maxGenerationIncreaseInput.valueAsNumber,
        elitism: elitismInput.valueAsNumber,
        mutationRate: mutationRateInput.valueAsNumber,
        mutationCount: mutationCountInput.valueAsNumber,
        randomMutationRate: randomMutationRateInput.valueAsNumber,
        probabilityCrossover: probabilityCrossoverInput.valueAsNumber,
        selectionPressure: selectionPressureInput.valueAsNumber,
        processEachComponentSeparately: processEachComponentInput.value === "true"
    };

    counterActive = true;
    let startTime = performance.now();
    algorithmOutput.value = "The genetic algorithm is running! Please wait";
    fetch('/run-genetic', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(algorithmConfig)
    })
    .then(response => response.json())
    .then(async data => {
        console.log('Success:', data);
        let elapsedTime = performance.now() - startTime;
        let elapsedTimeInSeconds = elapsedTime / 1000;
        let elapsedTimeString = elapsedTimeInSeconds.toFixed(3);
        algorithmOutput.value = "Algorithm completed successfully!\nTime Taken: " + elapsedTimeString + " seconds\nThe length of the induced path: " + data.length + "\nThe longest induced path found:\n" + data.toString();
        graph.resetColor();
        var outputDelay = Math.ceil(2500 / data.length);
        if(graph.nodes.length > 50) {
            outputDelay = 1;
        }
        stopColoring = false;
        for(let i = 0; i < data.length; i++) {
            const from = data[i].toString();

            await sleep(1);
            if(stopColoring === true) {
                break;
            }

            if(i === 0 || i === data.length - 1) {
                graph.highlightNode(from.toString(), START_VERTEX_COLOR);
            } else {
                graph.highlightNode(from.toString(), SELECTED_VERTEX_COLOR);
            }

            if(i !== data.length - 1) {
                await sleep(1);
                if(stopColoring === true) {
                    break;
                }
                const to = data[i + 1].toString();
                graph.highlightEdge(from.toString(), to.toString(), SELECTED_EGE_COLOR);
            }
        }
    })
    .catch((error) => {
        console.error('Error:', error);
        algorithmOutput.value = "Failed to run the algorithm!";
    });
};

options = {
    edges: {
        smooth: {
            enabled: false,
        }
    },
    physics: {
        enabled: true,
        barnesHut: {
          gravitationalConstant: -2000,
          centralGravity: 0.3,
          springLength: 12,
          springConstant: 0.04,
          damping: 0.09,
          avoidOverlap: 0.1
        },
        solver: 'barnesHut'
      }
};
network = new vis.Network(container, data, options);
network.setData(graph.getGraphData());
graph.heuristic = true;