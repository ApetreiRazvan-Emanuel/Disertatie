const visualizeAlgorithmBtn = document.getElementById("visualize-algorithm-btn");
const runAlgorithmBtn = document.getElementById("run-algorithm-btn");
const delayInput = document.getElementById("delay-input");
const algorithmOutput = document.getElementById("algorithm-output");

var worker = null;
var algorithmWorking = false;
var algorithmPaused = false;
visualizeAlgorithmBtn.onclick = function() {
    if(algorithmWorking === true) {
        worker.terminate();
        worker = null;
    }
    graph.resetColor();
    if(worker === null) {
        worker = new Worker("/js/algorithms/exactAlgorithmWorker.js");
        worker.onmessage = function(e) {
            if(e.data.type === "colorChangeVertex") {
                const nodeId = e.data.node.toString();
                const color = e.data.color;
                graph.highlightNode(nodeId, color);
            }
            if(e.data.type === "colorChangeEdge") {
                const from = e.data.from.toString();
                const to = e.data.to.toString();
                const color = e.data.color;
                graph.highlightEdge(to, from, color);
            }
            if(e.data.type === "finish") {
                const longestInducedPathFound = e.data.longestInducedPathFound;
                algorithmOutput.value = "The algorithm has finished its execution!\nThe Longest induced path found:\n" + longestInducedPathFound.toString();
                algorithmWorking = false;
                console.log(longestInducedPathFound);
            }
            if(e.data.type === "update") {
                const longestInducedPathFound = e.data.path;
                algorithmOutput.value = "The algorithm is currently running!\nThe Longest induced path found so far:\n" + longestInducedPathFound.toString();
            }
            if(e.data.type === "debug") {
                console.log(e.data.message);
            }
        }
    }
    const vertices = graph.nodes;
    const edges = graph.edges;
    const type = "start";
    const delay = delayInput.value;

    worker.postMessage({ type, vertices, edges, delay });
    algorithmPaused = false;
    algorithmWorking = true;
    algorithmOutput.value = "The algorithm is currently running!";
}

runAlgorithmBtn.onclick = async function() {
    stopColoring = true;
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
        adjacencyList: adjList
    };

    counterActive = true;
    let startTime = performance.now();
    fetch('/run-exact', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(algorithmConfig)
    })
    .then(response => response.json())
    .then(async data => {
        counterActive = false;
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

            await sleep(outputDelay);
            if(stopColoring === true) {
                break;
            }

            if(i === 0 || i === data.length - 1) {
                graph.highlightNode(from.toString(), START_VERTEX_COLOR);
            } else {
                graph.highlightNode(from.toString(), SELECTED_VERTEX_COLOR);
            }

            if(i !== data.length - 1) {
                await sleep(outputDelay);
                if(stopColoring === true) {
                    break;
                }
                const to = data[i + 1].toString();
                graph.highlightEdge(from.toString(), to.toString(), SELECTED_EGE_COLOR);
            }
        }
    })
    .catch((error) => {
        counterActive = false;
        console.error('Error:', error);
        algorithmOutput.value = "Failed to run the algorithm!";
    });

    algorithmOutput.value = "The Exact Algorithm is running!\nPlease wait";
    // while(counterActive === true) {
    //     await sleep(1);
    //     if(counterActive === false) {
    //         break;
    //     }
    //     let elapsedTime = performance.now() - startTime;
    //     let elapsedTimeInSeconds = elapsedTime / 1000;
    //     let elapsedTimeString = elapsedTimeInSeconds.toFixed(3);
    //     algorithmOutput.value = "The exact algorithm is running!\nTime Taken: " + elapsedTimeString + " seconds\nPlease wait";
    // }
}

delayInput.addEventListener('blur', function(event) {
    if(algorithmWorking === true) {
        const type = 'update';
        const delay = delayInput.value;
        worker.postMessage({ type, delay })
    }
  });

  document.addEventListener('keydown', function(event) {
    if (event.key === ' ' && algorithmWorking === true) {
      if (event.target.tagName !== 'INPUT' && event.target.tagName !== 'TEXTAREA') {
        event.preventDefault();
        const type = 'pause';
        algorithmPaused = !algorithmPaused;
        const value = algorithmPaused;
        if(value === true) {
            algorithmOutput.value = "The algorithm is currently paused!\n" + algorithmOutput.innerText.split("\n").slice(1).join("\n");
        } else {
            algorithmOutput.value = "The algorithm is currently running!\n" + algorithmOutput.innerText.split("\n").slice(1).join("\n");
        }
        worker.postMessage({ type, value })
      }
    }
  });