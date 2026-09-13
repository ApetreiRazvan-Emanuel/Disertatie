# -*- coding: utf-8 -*-
"""Dark-theme figures for the deck variants (Midnight / Aurora)."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import numpy as np, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "assets_dark"))
os.makedirs(OUT, exist_ok=True)

# dark-theme palette (light ink on the demo's navy background)
INK = "#EAF0FA"; BODY = "#C6CFDF"; MUTED = "#97A2B8"; GRID = "#3A4560"
CARD = "#182036"; EDGE = "#55617A"
BLUE = "#5AA2F2"; YELLOW = "#F5BE4A"; VIOLET = "#A78BFA"; AQUA = "#2DD4A0"; RED = "#F26D6D"
plt.rcParams.update({"font.family": "Arial", "text.color": INK,
                     "mathtext.fontset": "dejavusans"})

# ---------------------------------------------------------------- def_indus
pos = {
    0: (0.0, 0.55), 1: (0.9, 1.05), 2: (1.9, 1.1), 3: (2.9, 0.75),
    4: (3.4, -0.15), 5: (2.3, -0.35), 6: (1.2, -0.4), 7: (2.0, 0.35),
}
edges = [(0,1),(1,2),(2,3),(3,4),(0,6),(6,5),(5,4),(6,7),(7,2),(7,5),(1,7)]
path_ok = [0,1,2,3,4]
chord = (1,3)

fig, axes = plt.subplots(1, 2, figsize=(10.6, 4.0), dpi=200)
for ax, title, ok in [(axes[0], "Drum indus", True), (axes[1], "NU este drum indus", False)]:
    ax.set_xlim(-0.5, 3.9); ax.set_ylim(-0.85, 1.5); ax.axis("off")
    E = edges + ([chord] if not ok else [])
    pe = set()
    for a, b in zip(path_ok, path_ok[1:]):
        pe.add((a, b)); pe.add((b, a))
    for a, b in E:
        onpath = (a, b) in pe
        ischord = (not ok) and set((a, b)) == set(chord)
        if ischord:
            ax.plot(*zip(pos[a], pos[b]), color=RED, lw=3.4, zorder=3, linestyle=(0, (5, 3)))
            mx = (pos[a][0]+pos[b][0])/2; my = (pos[a][1]+pos[b][1])/2
            ax.annotate("coardă (scurtătură)", (mx, my), xytext=(mx+0.35, my+0.42),
                        color=RED, fontsize=12.5, fontweight="bold",
                        arrowprops=dict(arrowstyle="-", color=RED, lw=1.2))
        else:
            ax.plot(*zip(pos[a], pos[b]),
                    color=BLUE if onpath else GRID,
                    lw=4.0 if onpath else 1.4, zorder=2 if onpath else 1)
    for v, (x, y) in pos.items():
        onp = v in path_ok
        ax.add_patch(plt.Circle((x, y), 0.145,
                     facecolor=BLUE if onp else CARD,
                     edgecolor=BLUE if onp else EDGE, lw=1.6, zorder=4))
        ax.text(x, y, str(v+1), ha="center", va="center", zorder=5,
                color="#0E1421" if onp else BODY, fontsize=11, fontweight="bold")
    ax.set_title(title, fontsize=16, fontweight="bold",
                 color=(AQUA if ok else RED), pad=10)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "def_indus.png"), transparent=True)
plt.close()

# ---------------------------------------------------------------- ip3
# large-format: fills the slide width, big fonts (readable from the back row)
fig, ax = plt.subplots(figsize=(12.8, 5.2), dpi=200)
ax.axis("off"); ax.set_xlim(0, 1); ax.set_ylim(0, 1)
rows = [
    (INK,    r"$\max\ \sum_{t=0}^{T}\sum_{i\in V} x_i^t$", "maximizăm numărul de vârfuri vizitate"),
    (BLUE,   r"$\sum_{i\in V} x_i^t \leq 1 \quad \forall t$", "alegem cel mult un vârf la fiecare pas de timp"),
    (AQUA,   r"$\sum_{t=0}^{T} x_i^t \leq 1 \quad \forall i \in V$", "ne asigurăm că fiecare vârf\neste vizitat cel mult o dată"),
    (YELLOW, r"$x_i^t + x_j^{t+1} \leq 1 \quad \forall (i,j)\notin E$", "alegem doar muchii care aparțin grafului"),
    (RED,    r"$x_i^t + x_j^{\tau} \leq 1 \quad \forall (i,j)\in E,\ \tau \geq t+2$", "prevenim scurtăturile: pot exista muchii\ndoar între vârfuri vizitate consecutiv"),
]
ys = [0.90, 0.71, 0.52, 0.33, 0.15]
for (col, formula, expl), y in zip(rows, ys):
    ax.text(0.02, y, formula, fontsize=21, color=col, va="center")
    ax.text(0.58, y, expl, fontsize=16, color=MUTED, va="center")
ax.text(0.02, 0.002, r"$x_i^t = 1$  dacă vârful $i$ este vizitat la momentul $t$  (variabile binare)",
        fontsize=15, color=INK, va="bottom")
plt.tight_layout()
plt.savefig(os.path.join(OUT, "ip3.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- bnb
# large-format: no side paragraph (the slide bullets explain it), big fonts
fig, ax = plt.subplots(figsize=(7.6, 5.4), dpi=200)
ax.axis("off"); ax.set_xlim(-0.35, 10.2); ax.set_ylim(0, 10)

def node(x, y, w, h, text, fc=CARD, ec=EDGE, fs=14):
    b = FancyBboxPatch((x - w/2, y - h/2), w, h, boxstyle="round,pad=0.12",
                       facecolor=fc, edgecolor=ec, linewidth=2.0)
    ax.add_patch(b)
    ax.text(x, y, text, ha="center", va="center", fontsize=fs, color=INK)

def arrow(x1, y1, x2, y2, label=None, lx=0.30):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle="-|>",
                 mutation_scale=16, color=EDGE, lw=2.0))
    if label:
        ax.text((x1+x2)/2 + lx, (y1+y2)/2, label, fontsize=14, color=INK, ha="left")

node(5, 8.8, 6.2, 1.8, "Relaxare LP (fără întregi)\nmargine superioară: 12,4", fc="#16283F", ec=BLUE)
arrow(3.6, 7.8, 2.6, 6.4, "$y_v = 0$", lx=-1.55)
arrow(6.4, 7.8, 7.4, 6.4, "$y_v = 1$")
node(2.4, 5.5, 4.4, 1.8, "margine: 11,1\nramificăm mai departe")
node(7.6, 5.5, 4.4, 1.8, "soluție întreagă\ndrum de lungime 10", fc="#12312A", ec=AQUA)
arrow(1.7, 4.5, 1.4, 3.2)
arrow(3.1, 4.5, 5.9, 3.1)
node(2.0, 2.1, 3.9, 1.8, "margine: 9,6 < 10\nramură tăiată", fc="#391D22", ec=RED)
node(7.5, 2.1, 4.6, 1.8, "ciclu găsit în soluție:\nadăugăm o tăietură", fc="#33290F", ec=YELLOW)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "bnb.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- ga_cycle
# large-format: taller boxes, bigger fonts, spans the full slide width
fig, ax = plt.subplots(figsize=(12.6, 3.9), dpi=200)
ax.axis("off"); ax.set_xlim(0, 24); ax.set_ylim(0, 7.4)
steps = [
    ("Populație\ninițială", BLUE),
    ("Evaluare\n(fitness =\nlungimea)", AQUA),
    ("Selecție\n(supraviețuiesc\ncei buni)", YELLOW),
    ("Încrucișare\n(combinăm\n2 părinți)", VIOLET),
    ("Mutație\n(schimbări\nmici)", RED),
]
xs = np.linspace(2.5, 21.5, 5)
for (text, col), x in zip(steps, xs):
    b = FancyBboxPatch((x - 2.15, 3.55), 4.3, 2.85, boxstyle="round,pad=0.14",
                       facecolor=CARD, edgecolor=col, linewidth=2.8)
    ax.add_patch(b)
    ax.text(x, 4.97, text, ha="center", va="center", fontsize=14.5, color=INK)
for i in range(4):
    ax.add_patch(FancyArrowPatch((xs[i] + 2.35, 4.97), (xs[i+1] - 2.35, 4.97),
                 arrowstyle="-|>", mutation_scale=22, color=EDGE, lw=2.6))
ax.add_patch(FancyArrowPatch((xs[4], 3.35), (xs[1], 3.35),
             arrowstyle="-|>", mutation_scale=22, color=EDGE, lw=2.6,
             connectionstyle="arc3,rad=-0.22"))
ax.text((xs[1]+xs[4])/2, 0.55, "generația următoare: se repetă până la limita de timp",
        ha="center", fontsize=14, color=MUTED, fontstyle="italic")
plt.savefig(os.path.join(OUT, "ga_cycle.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- usair
# real geographic positions (USAir97 Pajek coordinates), so the network
# is recognizably the map of the US (same instance as usair.graphml,
# verified isomorphic: identical degree sequence, hubs O'Hare 139 etc.)
import networkx as nx
UDATA = os.path.join(HERE, "usair97_data")
M = nx.Graph()
with open(os.path.join(UDATA, "USAir97.mtx")) as f:
    hdr = True
    for line in f:
        if line.startswith("%"): continue
        if hdr: hdr = False; continue
        a, b = line.split()[:2]
        i, j = int(a) - 1, int(b) - 1
        if i != j: M.add_edge(i, j)
with open(os.path.join(UDATA, "USAir97_coord.mtx")) as f:
    clines = [l for l in f if not l.startswith("%")]
cvals = [float(x) for x in " ".join(clines[1:]).split()]
NUS = 332
upos = {i: (cvals[i], 1.0 - cvals[NUS + i]) for i in range(NUS)}  # flip y: north up
unames = [l.strip() for l in
          open(os.path.join(UDATA, "USAir97_nodename.txt"), encoding="latin-1")]
udeg = dict(M.degree())
fig, ax = plt.subplots(figsize=(7.4, 6.0), dpi=200)
ax.axis("off"); ax.set_aspect("equal")
nx.draw_networkx_edges(M, upos, ax=ax, edge_color="#33405E", width=0.35, alpha=0.55)
sizes = [6 + udeg[n] * 1.6 for n in M.nodes()]
cols = [BLUE if udeg[n] > 25 else "#3E6EA8" for n in M.nodes()]
nx.draw_networkx_nodes(M, upos, ax=ax, node_size=sizes, node_color=cols, linewidths=0)
# zoom on the continental US + the Alaska / Hawaii insets; the few Pacific
# and Caribbean territories fall outside the frame on purpose
ax.set_xlim(0.30, 0.97); ax.set_ylim(0.395, 0.905)

def ulabel(target, text, dx, dy, ha="left"):
    for i, nm in enumerate(unames):
        if target.lower() in nm.lower():
            x, y = upos[i]
            ax.annotate(text, (x, y), xytext=(x + dx, y + dy), ha=ha,
                        fontsize=10.5, color=INK, zorder=6,
                        arrowprops=dict(arrowstyle="-", color=MUTED, lw=0.9))
            return

ulabel("Chicago O", "Chicago", 0.015, 0.075)
ulabel("Dallas/Fort", "Dallas/Fort Worth", -0.055, -0.045, ha="right")
ulabel("Hartsfield", "Atlanta", 0.055, 0.02)
ulabel("Anchorage Intl", "Alaska", -0.02, 0.06, ha="right")
ulabel("Honolulu", "Hawaii", -0.028, 0.0, ha="right")
ax.set_title("usair: rețeaua de transport aerian din SUA, la pozițiile geografice reale\n"
             "332 de aeroporturi, 2 126 de rute",
             fontsize=13, color=INK, fontweight="bold", pad=8)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "usair.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- rezultate_mari
panels = [
    ("yeast: rețea de interacțiuni proteice\n(2 361 vârfuri, graf rar)", [184, 204, 328, 395]),
    ("generated-hard-5000: instanță generată\n(5 000 vârfuri, 1,3 mil. muchii, graf dens)", [51, 313, 395, 339]),
]
algs = ["CEC / CUT\n(exacte)", "HLIPP", "GA (propus)", "ACO (propus)"]
cols = [BLUE, YELLOW, VIOLET, AQUA]
fig, axes = plt.subplots(1, 2, figsize=(10.8, 4.1), dpi=200)
for ax, (title, vals) in zip(axes, panels):
    x = np.arange(4)
    bars = ax.bar(x, vals, width=0.62, color=cols, zorder=3)
    for r, v in zip(bars, vals):
        ax.text(r.get_x() + r.get_width()/2, v + 9, str(v), ha="center",
                va="bottom", fontsize=13, fontweight="bold", color=INK)
    ax.set_title(title, fontsize=12.5, fontweight="bold", pad=10, color=INK)
    ax.set_xticks(x); ax.set_xticklabels(algs, fontsize=10.5, color=INK)
    ax.set_ylim(0, 460); ax.set_yticks([])
    for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
    ax.spines["bottom"].set_color(GRID)
    ax.tick_params(length=0)
fig.text(0.5, -0.02, "Lungimea celui mai lung drum indus găsit (maxim din 5 rulări, limită 15 min)",
         ha="center", fontsize=11.5, color=MUTED)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "rezultate_mari.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- greu (why metaheuristics are hard)
# replaces the thesis figure p27: same story, dark theme, Romanian labels.
# one wrong pick (v4) kills the path; the right pick (v5) lets it grow.
gpos = {
    1: (0.0, 0.42), 2: (1.0, 0.72), 3: (2.0, 0.42),
    4: (3.0, 1.05), 5: (3.05, -0.18), 6: (4.15, 1.15),
    7: (4.15, -0.42), 8: (5.15, 0.05),
}
gedges = [(1,2),(2,3),(3,4),(3,5),(3,6),(4,5),(4,6),(5,7),(6,7),(7,8)]

panels = [
    ("Alegem v4: fundătură", RED, [1,2,3,4], {5,6}, "drumul se oprește la 4 vârfuri"),
    ("Alegem v5: drumul crește", AQUA, [1,2,3,5,7,8], {4,6}, "drumul ajunge la 6 vârfuri"),
]
fig, axes = plt.subplots(2, 1, figsize=(7.2, 6.6), dpi=200)
for ax, (title, tcol, path, banned, verdict) in zip(axes, panels):
    ax.set_xlim(-0.55, 5.75); ax.set_ylim(-0.95, 1.75); ax.axis("off")
    pe = set()
    for a, b in zip(path, path[1:]):
        pe.add((a, b)); pe.add((b, a))
    for a, b in gedges:
        onpath = (a, b) in pe
        ax.plot(*zip(gpos[a], gpos[b]),
                color=BLUE if onpath else GRID,
                lw=4.2 if onpath else 1.4, zorder=2 if onpath else 1)
    for v, (x, y) in gpos.items():
        if v in path:
            fc, ec, tc = BLUE, BLUE, "#0E1421"
        elif v in banned:
            fc, ec, tc = "#391D22", RED, RED
        else:
            fc, ec, tc = CARD, EDGE, BODY
        ax.add_patch(plt.Circle((x, y), 0.20, facecolor=fc, edgecolor=ec,
                                lw=2.0, zorder=4))
        ax.text(x, y, f"$v_{v}$", ha="center", va="center", zorder=5,
                color=tc, fontsize=13, fontweight="bold")
        if v in banned:
            ax.text(x, y - 0.40, "interzis", ha="center", va="top",
                    color=RED, fontsize=11.5, zorder=5)
    ax.set_title(title, fontsize=17, fontweight="bold", color=tcol, pad=6, loc="left")
    ax.text(5.65, 1.45, verdict, fontsize=13.5, color=tcol, ha="right", va="center")
fig.text(0.02, 0.012,
         "albastru = drumul indus   ·   roșu = vecinii vârfurilor alese, interziși definitiv",
         fontsize=12.5, color=MUTED)
plt.tight_layout(rect=(0, 0.045, 1, 1))
plt.savefig(os.path.join(OUT, "greu.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- aco_feromon (directional pheromone, replaces p33)
fig, ax = plt.subplots(figsize=(7.4, 5.4), dpi=200)
ax.axis("off"); ax.set_xlim(-0.6, 6.6); ax.set_ylim(-2.9, 3.1)
fpos = {1: (0.0, 0.0), 2: (1.3, 0.0), 3: (2.6, 0.0), 4: (3.9, 0.0),
        5: (5.4, 0.0), 6: (4.6, 1.35), 7: (5.8, 2.2)}
for a, b in [(1,2),(2,3),(3,4)]:
    ax.plot(*zip(fpos[a], fpos[b]), color=BLUE, lw=4.2, zorder=2)
for a, b in [(4,5),(4,6),(6,7)]:
    ax.plot(*zip(fpos[a], fpos[b]), color=GRID, lw=1.6, zorder=1)
for v, (x, y) in fpos.items():
    if v <= 4:
        fc, ec, tc = BLUE, BLUE, "#0E1421"
    elif v == 5:
        fc, ec, tc = "#391D22", RED, RED
    else:
        fc, ec, tc = CARD, EDGE, BODY
    ax.add_patch(plt.Circle((x, y), 0.24, facecolor=fc, edgecolor=ec, lw=2.0, zorder=4))
    ax.text(x, y, f"$v_{v}$", ha="center", va="center", zorder=5,
            color=tc, fontsize=13.5, fontweight="bold")
ax.text(1.3, -0.62, "drumul curent", color=BLUE, fontsize=12.5, ha="center")
ax.text(5.4, -0.62, "fundătură", color=RED, fontsize=12.5, ha="center")
ax.text(5.15, 2.75, "continuare posibilă mai lungă", color=MUTED, fontsize=12, ha="center")
ax.add_patch(FancyArrowPatch((4.25, 0.30), (5.10, 0.30), arrowstyle="-|>",
             mutation_scale=18, color=RED, lw=2.6, zorder=6))
ax.text(4.67, 0.60, r"$\tau(v_4 \rightarrow v_5) = 1{,}0$   spre fundătură",
        color=RED, fontsize=13, ha="center", va="bottom")
ax.add_patch(FancyArrowPatch((5.10, -0.30), (4.25, -0.30), arrowstyle="-|>",
             mutation_scale=18, color=AQUA, lw=2.6, zorder=6))
ax.text(4.67, -1.00, r"$\tau(v_5 \rightarrow v_4) = 2{,}8$" + "\ndinspre fundătură",
        color=AQUA, fontsize=13, ha="center", va="top")
ax.text(2.9, -2.35, r"$\tau(v \rightarrow u) \; \neq \; \tau(u \rightarrow v)$" + "   feromonul depinde de direcție",
        color=INK, fontsize=14.5, ha="center", va="center",
        bbox=dict(boxstyle="round,pad=0.55", facecolor=CARD, edgecolor=EDGE, lw=1.6))
plt.tight_layout()
plt.savefig(os.path.join(OUT, "aco_feromon.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- aco_bidir (why we search twice)
# double-search trick, like computing a graph diameter with two BFS runs
bx = list(range(9))
by = [0.0, 0.30, 0.05, 0.35, 0.10, 0.40, 0.15, 0.45, 0.20]
bdecoys = [((2, -0.80), 2), ((3.15, 1.10), 3), ((6.0, -0.70), 6)]

def bidir_panel(ax, path, start, title, found_text, start_label="start",
                label_dx=-0.15):
    ax.set_xlim(-0.7, 9.5); ax.set_ylim(-1.25, 1.75); ax.axis("off")
    for d, ((dx, dy), anchor) in enumerate(bdecoys):
        ax.plot([dx, bx[anchor]], [dy, by[anchor]], color=GRID, lw=1.3, zorder=1)
        ax.scatter([dx], [dy], s=430, facecolor=CARD, edgecolor=EDGE,
                   linewidth=1.5, zorder=3)
        ax.text(dx, dy, str(10 + d), ha="center", va="center", zorder=5,
                color=BODY, fontsize=10)
    for i in range(8):
        onp = i in path and (i + 1) in path
        ax.plot([bx[i], bx[i+1]], [by[i], by[i+1]],
                color=BLUE if onp else GRID, lw=4.0 if onp else 1.4,
                zorder=2 if onp else 1)
    for i in range(9):
        onp = i in path
        ax.scatter([bx[i]], [by[i]], s=640,
                   facecolor=BLUE if onp else CARD,
                   edgecolor=YELLOW if i == start else (BLUE if onp else EDGE),
                   linewidth=3.2 if i == start else 1.8, zorder=4)
        ax.text(bx[i], by[i], str(i + 1), ha="center", va="center", zorder=5,
                color="#0E1421" if onp else BODY, fontsize=11.5,
                fontweight="bold")
    sx, sy = bx[start], by[start]
    ax.annotate(start_label, (sx, sy + 0.26), xytext=(sx + label_dx, sy + 1.05),
                color=YELLOW, fontsize=13.5, fontweight="bold", ha="center",
                arrowprops=dict(arrowstyle="-|>", color=YELLOW, lw=1.8))
    ax.text(9.3, by[0] - 0.75, found_text, fontsize=13.5, ha="right", va="bottom",
            color=BLUE, fontweight="bold")
    ax.set_title(title, fontsize=15.5, fontweight="bold", color=INK, pad=4, loc="left")

fig, axes = plt.subplots(2, 1, figsize=(11.8, 5.0), dpi=200)
bidir_panel(axes[0], set(range(4, 9)), 4,
            "Prima căutare: startul pică în mijlocul drumului lung",
            "găsește 5 vârfuri")
axes[0].text(1.5, -1.22, "jumătatea stângă rămâne negăsită", fontsize=12.5,
             color=MUTED, ha="center", va="top", clip_on=False)
bidir_panel(axes[1], set(range(0, 9)), 8,
            "A doua căutare: pornim din capătul drumului găsit",
            "găsește toate cele 9 vârfuri",
            start_label="noul start = capătul găsit", label_dx=-1.4)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "aco_bidir.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- ga_reprezentare (replaces p39)
fig, ax = plt.subplots(figsize=(7.6, 5.2), dpi=200)
ax.axis("off"); ax.set_xlim(-0.5, 7.8); ax.set_ylim(-1.5, 2.7)
rpos = {2: (0.0, 0.75), 3: (0.8, 1.8), 0: (1.9, 2.0), 1: (3.0, 1.8),
        6: (3.7, 0.7), 4: (1.2, -0.3), 5: (2.6, -0.3)}
rpath = [2, 3, 0, 1, 6]
redges_path = [(2,3),(3,0),(0,1),(1,6)]
redges_gray = [(3,4),(0,5),(2,4),(4,5),(5,6)]
for a, b in redges_gray:
    ax.plot(*zip(rpos[a], rpos[b]), color=GRID, lw=1.5, zorder=1)
for a, b in redges_path:
    ax.plot(*zip(rpos[a], rpos[b]), color=BLUE, lw=4.2, zorder=2)
for v, (x, y) in rpos.items():
    onp = v in rpath
    ax.scatter([x], [y], s=780, facecolor=BLUE if onp else CARD,
               edgecolor=BLUE if onp else EDGE, linewidth=2.0, zorder=4)
    ax.text(x, y, str(v), ha="center", va="center", zorder=5,
            color="#0E1421" if onp else BODY, fontsize=14, fontweight="bold")
ax.text(1.85, -1.15, "albastru = drumul indus din graf", color=MUTED, fontsize=12, ha="center")
ax.text(5.85, 2.35, "individ = vectorul ordonat", color=INK, fontsize=14.5,
        fontweight="bold", ha="center")
cw = 0.62
for i, val in enumerate(rpath):
    x0 = 4.45 + i * cw
    ax.add_patch(plt.Rectangle((x0, 0.95), cw, 0.75, facecolor=CARD,
                               edgecolor=EDGE, lw=1.8, zorder=3))
    ax.text(x0 + cw/2, 1.325, str(val), ha="center", va="center", zorder=4,
            color=BLUE, fontsize=17, fontweight="bold")
    ax.text(x0 + cw/2, 0.72, str(i), ha="center", va="top",
            color=MUTED, fontsize=11)
ax.text(4.45 + 2.5*cw, 0.28, "(pozițiile în vector)", color=MUTED, fontsize=11, ha="center")
ax.text(5.85, -0.55, "fitness = lungimea drumului = 5", color=INK, fontsize=14,
        ha="center", va="center",
        bbox=dict(boxstyle="round,pad=0.5", facecolor=CARD, edgecolor=EDGE, lw=1.6))
plt.tight_layout()
plt.savefig(os.path.join(OUT, "ga_reprezentare.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- ga_mutatie (replaces p44)
# same graph in all three stages; the re-extended path is LONGER (8 > 6)
mpos = {
    "v8":  (0.00, 1.15), "v7":  (0.95, 0.62),
    "v1":  (0.30, -0.60), "v2":  (1.25, -0.18),
    "v3":  (2.20, 0.32), "v4":  (3.30, 0.55), "v5":  (4.40, 0.32),
    "v6":  (5.35, -0.35),
    "v9":  (5.45, 0.95), "v10": (6.40, 1.20), "v11": (7.25, 0.60),
}
medges = [("v1","v2"),("v2","v3"),("v3","v4"),("v4","v5"),("v5","v6"),
          ("v3","v7"),("v7","v8"),("v5","v9"),("v9","v10"),("v10","v11")]
ORIG = ["v1","v2","v3","v4","v5","v6"]
MID = {"v3","v4","v5"}
CUT = {"v1","v2","v6"}
FINAL = ["v8","v7","v3","v4","v5","v9","v10","v11"]

def mlabel(name):
    idx = name[1:]
    return rf"$v_{{{idx}}}$"

def mpanel(ax, title, path, node_color, faded=frozenset(), cross=frozenset(),
           verdict=None, vcol=AQUA):
    ax.set_xlim(-0.55, 8.1); ax.set_ylim(-1.15, 1.75); ax.axis("off")
    pe = set()
    for a, b in zip(path, path[1:]):
        pe.add((a, b)); pe.add((b, a))
    for a, b in medges:
        onp = (a, b) in pe
        if onp:
            ca, cb = node_color.get(a), node_color.get(b)
            col = ca if (ca == cb and ca) else AQUA
        else:
            col = GRID
        ax.plot(*zip(mpos[a], mpos[b]), color=col,
                lw=4.0 if onp else 1.4, zorder=2 if onp else 1,
                alpha=0.35 if (a in faded or b in faded) and not onp else 1.0)
    for v, (x, y) in mpos.items():
        col = node_color.get(v)
        fc = col if col else CARD
        ec = col if col else EDGE
        tc = "#0E1421" if col else BODY
        al = 0.4 if v in faded else 1.0
        ax.scatter([x], [y], s=520, facecolor=fc, edgecolor=ec, linewidth=1.8,
                   zorder=4, alpha=al)
        ax.text(x, y, mlabel(v), ha="center", va="center", zorder=5, color=tc,
                fontsize=10.5, fontweight="bold", alpha=al)
        if v in cross:
            ax.plot([x-0.16, x+0.16], [y-0.16, y+0.16], color=RED, lw=2.2, zorder=6)
            ax.plot([x-0.16, x+0.16], [y+0.16, y-0.16], color=RED, lw=2.2, zorder=6)
    if verdict:
        ax.text(8.0, -0.85, verdict, fontsize=14, ha="right", va="center",
                color=vcol, fontweight="bold")
    ax.set_title(title, fontsize=14.5, fontweight="bold", color=INK, pad=4, loc="left")

fig, axes = plt.subplots(3, 1, figsize=(8.2, 6.9), dpi=200)
mpanel(axes[0], "1. Individul original: drum indus cu 6 vârfuri",
       ORIG, {v: BLUE for v in ORIG},
       verdict="6 vârfuri", vcol=BLUE)
mpanel(axes[1], "2. Mutația taie 3 vârfuri de la capete (v1, v2, v6)",
       ["v3","v4","v5"], {v: VIOLET for v in MID},
       cross=CUT, verdict="păstrăm mijlocul", vcol=VIOLET)
node_color3 = {v: VIOLET for v in MID}
node_color3.update({v: AQUA for v in FINAL if v not in MID})
mpanel(axes[2], "3. Re-extindem cu DFS, în ambele direcții",
       FINAL, node_color3, faded=CUT,
       verdict="8 vârfuri > 6", vcol=AQUA)
fig.text(0.5, 0.008,
         "albastru = drumul original   ·   violet = mijlocul păstrat   ·   verde = extensia DFS",
         fontsize=11.5, color=MUTED, ha="center")
plt.tight_layout(rect=(0, 0.03, 1, 1))
plt.savefig(os.path.join(OUT, "ga_mutatie.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- ga_crossover (replaces p45)
# union has internal edges (chords), so DFS inside G[U] is really needed;
# the child ends up longer than both parents (6 > 4 and 3)
xpos = {"v2": (0.0, 1.5), "v3": (0.7, 1.6), "v0": (1.5, 2.15), "v1": (2.4, 1.6),
        "v5": (2.5, 0.75), "v4": (1.3, 0.75), "v6": (1.3, -0.1), "v7": (2.6, -0.1)}
xedges = [("v2","v3"),("v3","v0"),("v0","v1"),("v1","v5"),("v3","v4"),
          ("v1","v4"),("v4","v5"),("v4","v6"),("v5","v7")]
XUNION = {"v2","v3","v0","v1","v5","v4","v6"}
xpanels = [
    ("Părintele 1", BLUE, ["v2","v3","v0","v1"], None,
     "p1: drum cu 4 vârfuri", None),
    ("Părintele 2", AQUA, ["v6","v4","v5"], None,
     "p2: drum cu 3 vârfuri", None),
    ("Uniunea + DFS", YELLOW, ["v2","v3","v0","v1","v5"], XUNION,
     "uniunea are coarde (roșu):\nDFS găsește drumul de 5", None),
    ("Copilul, extins", RED, ["v2","v3","v0","v1","v5","v7"], None,
     "copil: 6 vârfuri,\nmai lung decât ambii părinți", "v7"),
]
fig, axes = plt.subplots(1, 4, figsize=(12.8, 4.9), dpi=200)
for ax, (title, col, path, union, caption, added) in zip(axes, xpanels):
    ax.set_xlim(-0.45, 3.05); ax.set_ylim(-0.85, 2.85); ax.axis("off")
    ax.set_aspect("equal")
    pe = set()
    for a, b in zip(path, path[1:]):
        pe.add((a, b)); pe.add((b, a))
    for a, b in xedges:
        if (a, b) in pe:
            ax.plot(*zip(xpos[a], xpos[b]), color=col, lw=4.2, zorder=2)
        elif union and a in union and b in union:
            ax.plot(*zip(xpos[a], xpos[b]), color=RED, lw=2.4, zorder=2,
                    linestyle=(0, (3, 2)))
        else:
            ax.plot(*zip(xpos[a], xpos[b]), color=GRID, lw=1.4, zorder=1)
    for v, (x, y) in xpos.items():
        if v in path:
            fc, ec, tc = col, col, "#0E1421"
        elif union and v in union:
            fc, ec, tc = VIOLET, VIOLET, "#0E1421"
        else:
            fc, ec, tc = CARD, EDGE, BODY
        ax.scatter([x], [y], s=760, facecolor=fc, edgecolor=ec, linewidth=2.0,
                   zorder=4)
        ax.text(x, y, f"$v_{v[1]}$", ha="center", va="center", zorder=5,
                color=tc, fontsize=13, fontweight="bold")
    if added:
        ax.annotate("adăugat prin\nextindere", (xpos[added][0] - 0.05, xpos[added][1] - 0.22),
                    xytext=(1.8, -0.72), color=RED, fontsize=12, ha="center",
                    arrowprops=dict(arrowstyle="-|>", color=RED, lw=1.8))
    ax.set_title(title, fontsize=17, fontweight="bold", color=col, pad=6)
    ax.text(1.3, -1.0, caption, fontsize=12, color=MUTED,
            ha="center", va="top", clip_on=False)
plt.tight_layout(rect=(0, 0.10, 1, 1))
plt.savefig(os.path.join(OUT, "ga_crossover.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- ga_ruleta (replaces p47)
fig, ax = plt.subplots(figsize=(6.8, 5.2), dpi=200)
sizes = [1, 2, 3, 4, 5]
labels = [f"individ {i}\nfitness = {i}" for i in range(1, 6)]
cols = [BLUE, RED, YELLOW, VIOLET, AQUA]
wedges, texts, autotexts = ax.pie(
    sizes, labels=labels, colors=cols, startangle=90, counterclock=False,
    autopct="%.0f%%", pctdistance=0.68, labeldistance=1.12,
    wedgeprops=dict(edgecolor="#0E1421", linewidth=2.5),
    textprops=dict(color=INK, fontsize=13))
for at in autotexts:
    at.set_color("#0E1421"); at.set_fontsize(13.5); at.set_fontweight("bold")
ax.text(0, -1.42, "aria feliei = probabilitatea de a fi selectat (proporțională cu fitness-ul)",
        ha="center", fontsize=12.5, color=MUTED)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "ga_ruleta.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------------------------------------------------------- aurora background (1920x1080)
from PIL import Image
W, H = 1920, 1080
yy, xx = np.mgrid[0:H, 0:W].astype(float)
u = xx / W; v = yy / H
t = (u * 0.62 + v * 0.38)
c1 = np.array([10, 15, 30]); c2 = np.array([22, 32, 59])          # #0A0F1E -> #16203B
base = c1[None, None, :] + (c2 - c1)[None, None, :] * t[:, :, None]

def glow(cx, cy, radius, color, strength):
    d2 = ((xx - cx) ** 2 + (yy - cy) ** 2) / (radius ** 2)
    g = np.exp(-d2)[:, :, None] * strength
    return g * np.array(color)[None, None, :]

img = base
img = img + glow(W * 1.02, -H * 0.08, 640, (34, 211, 238), 0.16)   # cyan, top-right
img = img + glow(-W * 0.05, H * 1.06, 700, (236, 72, 153), 0.11)   # magenta, bottom-left
img = img + glow(W * 0.55, H * 0.35, 900, (90, 120, 220), 0.045)   # faint blue center
img = np.clip(img, 0, 255).astype(np.uint8)
Image.fromarray(img, "RGB").save(os.path.join(OUT, "aurora_bg.png"))

# thin cyan->pink gradient strip for title underlines / bands
Wg, Hg = 900, 12
gx = np.linspace(0, 1, Wg)
ca = np.array([34, 211, 238]); cb = np.array([236, 72, 153])
strip = (ca[None, :] + (cb - ca)[None, :] * gx[:, None]).astype(np.uint8)
strip = np.tile(strip[None, :, :], (Hg, 1, 1))
Image.fromarray(strip, "RGB").save(os.path.join(OUT, "grad_strip.png"))

print("dark figures done ->", OUT)
