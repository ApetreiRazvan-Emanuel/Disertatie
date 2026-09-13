"""Convert graph-instances/ files into demo/instances.js (flat edge arrays)."""
import re, json, pathlib

SRC = pathlib.Path(r"C:\Facultate\Github Repositories\Disertatie\Disertatie\src\main\resources\graph-instances")
OUT = pathlib.Path(r"C:\Facultate\Github Repositories\Disertatie\demo\instances.js")
SKIP = {"generated-hard-5000.graphml"}

def parse_graphml(text):
    node_ids = re.findall(r'<node\s+id="([^"]+)"', text)
    idx = {nid: i for i, nid in enumerate(node_ids)}
    edges = []
    for m in re.finditer(r'<edge\b[^>]*?source="([^"]+)"[^>]*?target="([^"]+)"', text):
        s, t = idx.get(m.group(1)), idx.get(m.group(2))
        if s is not None and t is not None and s != t:
            edges.append((s, t))
    return len(node_ids), edges

def parse_dimacs(text):
    edges, max_v = [], 0
    for line in text.splitlines():
        if line.startswith("e "):
            _, a, b = line.split()[:3]
            u, v = int(a) - 1, int(b) - 1
            edges.append((u, v))
            max_v = max(max_v, u, v)
        elif line.startswith("p "):
            max_v = max(max_v, int(line.split()[2]) - 1)
    return max_v + 1, edges

def load_usair_coords(demo_dir, usair_edges, usair_n):
    """Geographic positions from SuiteSparse USAir97_coord.mtx, embedded only
    after verifying the graphml vertex order matches the Pajek order."""
    coord_f = demo_dir / "USAir97_coord.mtx"
    mtx_f = demo_dir / "USAir97.mtx"
    if not (coord_f.exists() and mtx_f.exists()):
        return None
    ref = set()
    for line in mtx_f.read_text().splitlines():
        s = line.strip()
        if not s or s.startswith("%"):
            continue
        parts = s.split()
        if len(parts) == 3:
            a, b = int(parts[0]) - 1, int(parts[1]) - 1
            if a != b:
                ref.add((min(a, b), max(a, b)))
    ours = set()
    for i in range(0, len(usair_edges), 2):
        a, b = usair_edges[i], usair_edges[i + 1]
        ours.add((min(a, b), max(a, b)))
    mapping = None  # graphml vertex -> pajek vertex
    if ours == ref:
        mapping = {v: v for v in range(usair_n)}
    else:
        # graphml is a relabeled copy: recover the permutation by isomorphism
        try:
            import networkx as nx
        except ImportError:
            print("  !! usair labels permuted and networkx missing - coords NOT embedded")
            return None
        G_ours = nx.Graph(list(ours))
        G_ref = nx.Graph(list(ref))
        mapping = nx.vf2pp_isomorphism(G_ours, G_ref)
        if not mapping:
            print("  !! usair graphs not isomorphic?! - coords NOT embedded")
            return None
        print("  usair: labels permuted, recovered mapping via VF2++ isomorphism")
    # coord file: MatrixMarket dense array, column-major (all x's then all y's)
    vals = []
    for line in coord_f.read_text().splitlines():
        s = line.strip()
        if not s or s.startswith("%") or " " in s:
            continue
        vals.append(float(s))
    xs, ys = vals[:usair_n], vals[usair_n:2 * usair_n]
    pos = []
    for v in range(usair_n):
        pv = mapping[v]
        pos.extend((round(xs[pv], 4), round(ys[pv], 4)))
    print(f"  usair: embedded {usair_n} geographic positions ({len(ours)} edges verified)")
    return pos

lib = []
for f in sorted(SRC.iterdir()):
    if f.name in SKIP:
        continue
    text = f.read_text(encoding="utf-8", errors="replace")
    n, edges = parse_graphml(text) if f.suffix == ".graphml" else parse_dimacs(text)
    # dedupe undirected
    seen, flat = set(), []
    for a, b in edges:
        k = (min(a, b), max(a, b))
        if k in seen:
            continue
        seen.add(k)
        flat.extend(k)
    name = f.stem
    lib.append((n, name, flat))
    print(f"{name}: n={n}, m={len(flat)//2}")

lib.sort(key=lambda x: x[0])  # by vertex count

positions = {}
for n, name, flat in lib:
    if name == "usair":
        pos = load_usair_coords(OUT.parent, flat, n)
        if pos:
            positions[name] = pos

with OUT.open("w", encoding="utf-8") as out:
    out.write("/* instances.js — generated from graph-instances/ by build_instances.py.\n")
    out.write(" * Flat edge arrays: [a1,b1,a2,b2,...], vertex ids 0-based.\n")
    out.write(" * pos (optional): [x1,y1,...] fixed geographic layout (usair = USA map). */\n")
    out.write('"use strict";\n\nconst INSTANCE_LIBRARY = {\n')
    for n, name, flat in lib:
        extra = ""
        if name in positions:
            extra = f', pos: {json.dumps(positions[name], separators=(",", ":"))}'
        out.write(f'  {json.dumps(name)}: {{ n: {n}, edges: {json.dumps(flat, separators=(",", ":"))}{extra} }},\n')
    out.write("};\n")

print(f"\nwrote {OUT} ({OUT.stat().st_size / 1024:.0f} KB)")
