const numberOfNodesInput = document.getElementById('num-nodes-input');


numberOfNodesInput.addEventListener('keydown', function(event) {
    if (event.key === 'Enter') {
        processNumberOfNodes(this.value);
    }
});

numberOfNodesInput.addEventListener('blur', function() {
    processNumberOfNodes(this.value);
});

function processNumberOfNodes(value) {
    if(Number(value) && Number(value) > 0) {
        console.log(graph);
        graph.modifyNumberOfVertices(value);
    }
}

document.getElementById('num-nodes-input').addEventListener('input', function() {
    const icon = document.querySelector('.fas');
    const isValid = Number(this.value) && Number(this.value) > 0;
    icon.style.visibility = isValid ? 'hidden' : 'visible';
});

const edgesList = document.getElementById('edges-list');

var validEdges = [{to: -1, from: -1}];

function addEdgeInput() {
    const newLi = document.createElement('li');
    const newLa = document.createElement('label');
    const newInput = document.createElement('input');
    const newButton = document.createElement('button');
    const newIcon = document.createElement('i');
    newLi.className = "edge";
    newLa.className = "edge-label";
    newLa.textContent = edgesList.children.length;
    newInput.type = 'text';
    newInput.className = 'edge-input';
    newIcon.className = 'fas fa-exclamation-circle';
    newIcon.title = "Invalid edge! Example of valid edge: 2-3";
    newButton.className = "edge-delete-button";
    newButton.textContent = "X";
    newLi.appendChild(newLa);
    newLi.appendChild(newInput);
    newLi.appendChild(newIcon);
    newLi.appendChild(newButton);
    edgesList.appendChild(newLi);
    inputAddEventListeners(newInput);
    deleteEdgeButtonAddEventListeners(newButton);
    newInput.focus();
    validEdges.push({to: -1, from: -1});
}

function findEdgePosition(edge) {
    const edgesList = document.getElementById('edges-list');
    const edges = edgesList.getElementsByClassName('edge');

    for (let i = 0; i < edges.length; i++) {
        if (edges[i] === edge) {
            return i;
        }
    }
    return -1;
}

function parseEdgeInput(edgeInputValue) {
    const pattern = /^(\d+)-(\d+)$/;
    const match = edgeInputValue.match(pattern);

    if (match) {
        const node1 = match[1];
        const node2 = match[2];
        return {
            isValid: true,
            nodes: [node1, node2]
        };
    } else {
        return {
            isValid: false,
            nodes: null
        };
    }
}

function inputAddEventListeners(input) {
    input.addEventListener('keydown', function(event) {
        if (event.key === 'Enter') {
            if(input === edgesList.lastElementChild.children[1]) {
                addEdgeInput();
            }
            else {
                const nextEdge = input.parentElement.nextElementSibling;
                if(nextEdge) {
                    nextEdge.children[1].focus();
                }
            }
        }
    });

    input.addEventListener('input', function() {
        const inputValue = parseEdgeInput(input.value);
        const position = findEdgePosition(input.parentElement);
        const icon = input.parentElement.querySelector('.fas');
        icon.style.visibility = 'hidden';
        if(validEdges[position].from !== -1) {
            graph.removeEdge(validEdges[position].from, validEdges[position].to);
            graph.updateNetwork();
        }
        if(inputValue.isValid === true) {
            const response = graph.addEdge(inputValue.nodes[0], inputValue.nodes[1]);
            if(response === true) {
                validEdges[position].to = inputValue.nodes[0];
                validEdges[position].from = inputValue.nodes[1];
                graph.updateNetwork();
            }
        } else {
            validEdges[position].from = -1;
            validEdges[position].to = -1;
            if(input.value.length !== 0) {
                icon.style.visibility = 'visible';
            }
        }
    });
}

function deleteEdgeButtonAddEventListeners(button) {
    button.addEventListener('click', function() {
        const position = findEdgePosition(button.parentElement);

        deleteEdgeAtPosition(position);
    });
}

function deleteEdgeAtPosition(position) {
    if(edgesList.children.length == 1) {
        return;
    }

    const edge = edgesList.children[position];

    if(validEdges[position].to !== -1) {
        graph.removeEdge(validEdges[position].from, validEdges[position].to);
        graph.updateNetwork();
    }

    var nextEdge = edge.nextElementSibling;
    for(let i = position; i < validEdges.length - 1; i++) {
        const label = nextEdge.querySelector('.edge-label');
        label.textContent = parseInt(label.textContent, 10) - 1;
        nextEdge = nextEdge.nextElementSibling;
    }
    validEdges.splice(position, 1);

    edge.remove();
}


Array.from(edgesList.querySelectorAll('.edge-input')).forEach(input => {
    inputAddEventListeners(input);
});

Array.from(edgesList.querySelectorAll('.edge-delete-button')).forEach(button => {
    deleteEdgeButtonAddEventListeners(button);
});

document.getElementById('graphml-button').addEventListener('click', function() {
    document.getElementById('file-input').click();
});

document.getElementById('file-input').addEventListener('change', function() {
    if (this.files.length > 0) {
        const file = this.files[0];
        uploadFile(file);
    }
});

var stopColoring = false;
var counterActive = false;
function uploadFile(file) {
    const formData = new FormData();
    formData.append('file', file);

    stopColoring = true;
    counterActive = false;
    fetch('/api/upload-graphml', {
        method: 'POST',
        body: formData,
    })
    .then(response => response.json())
    .then(data => {
        // graph.setOptionsNetwork(false);
        console.log('Adjacency List:', data);
        graph.fromAdjacencyList(data);
        graph.regenerateNetwork();
        // graph.setOptionsNetwork(true);
        numberOfNodesInput.value = graph.nodes.length;
        edgesList.replaceChildren(...graph.getEdgesElements());
        validEdges = graph.getValidEdges();
        checkScroll();
    })
    .catch(error => console.error('Error uploading file:', error));
}

function checkScroll() {
    if (edgesList.scrollHeight <= edgesList.clientHeight) {
        edgesList.classList.add('no-gradient');
    } else {
        if (edgesList.scrollTop > 0) {
            edgesList.classList.add('no-gradient');
        } else {
            edgesList.classList.remove('no-gradient');
        }
    }
}

edgesList.addEventListener('scroll', checkScroll);
checkScroll();