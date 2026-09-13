# -*- coding: utf-8 -*-
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import numpy as np, os

OUT = r"C:\Users\Razvan\AppData\Local\Temp\claude\C--Facultate-Github-Repositories-Disertatie\a9442c31-b9db-4856-8e85-3dd6dd933a26\scratchpad\figs"
os.makedirs(OUT, exist_ok=True)

INK = "#1B2A4A"; MUTED = "#6B7280"; GRID = "#E5E7EB"
BLUE = "#2a78d6"; YELLOW = "#eda100"; VIOLET = "#4a3aa7"; AQUA = "#1baf7a"; RED = "#e34948"
plt.rcParams.update({"font.family": "Arial", "text.color": INK, "mathtext.fontset": "dejavusans"})

# ------------------------------------------------ IP3 formulation
fig, ax = plt.subplots(figsize=(7.6, 4.6), dpi=200)
ax.axis("off"); ax.set_xlim(0, 1); ax.set_ylim(0, 1)
rows = [
    (INK,    r"$\max\ \sum_{t=0}^{T}\sum_{i\in V} x_i^t$", "maximizăm numărul de vârfuri vizitate"),
    (BLUE,   r"$\sum_{i\in V} x_i^t \leq 1 \quad \forall t$", "cel mult un vârf la fiecare pas de timp"),
    (AQUA,   r"$\sum_{t=0}^{T} x_i^t \leq 1 \quad \forall i \in V$", "fiecare vârf este vizitat cel mult o dată"),
    (YELLOW, r"$x_i^t + x_j^{t+1} \leq 1 \quad \forall (i,j)\notin E$", "pașii consecutivi folosesc doar muchii existente"),
    (RED,    r"$x_i^t + x_j^{\tau} \leq 1 \quad \forall (i,j)\in E,\ \tau \geq t+2$", "fără scurtături: un vecin poate urma doar imediat"),
]
y = 0.93
for col, formula, expl in rows:
    ax.text(0.03, y, formula, fontsize=15.5, color=col, va="top")
    ax.text(0.03, y - 0.085, expl, fontsize=11.5, color=MUTED, va="top")
    y -= 0.19
ax.text(0.03, 0.02, r"$x_i^t = 1$  dacă vârful $i$ este vizitat la momentul $t$   (variabile binare)",
        fontsize=11.5, color=INK, va="bottom")
plt.tight_layout()
plt.savefig(os.path.join(OUT, "ip3.png"), transparent=True, bbox_inches="tight")
plt.close()

# ------------------------------------------------ Branch and bound tree
fig, ax = plt.subplots(figsize=(7.8, 4.9), dpi=200)
ax.axis("off"); ax.set_xlim(0, 10); ax.set_ylim(0, 10)

def node(x, y, w, h, text, fc="#F1F5F9", ec="#DDE3EC", tc=INK, fs=11):
    b = FancyBboxPatch((x - w/2, y - h/2), w, h, boxstyle="round,pad=0.12",
                       facecolor=fc, edgecolor=ec, linewidth=1.4)
    ax.add_patch(b)
    ax.text(x, y, text, ha="center", va="center", fontsize=fs, color=tc)

def arrow(x1, y1, x2, y2, label=None, col="#9AA3B2"):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle="-|>",
                 mutation_scale=14, color=col, lw=1.6))
    if label:
        ax.text((x1+x2)/2 + 0.32, (y1+y2)/2, label, fontsize=10.5, color=INK, ha="left")

node(5, 8.9, 4.6, 1.5, "Relaxare LP (fără întregi)\nmargine superioară: 12,4", fc="#E8F1FC", ec=BLUE)
arrow(3.9, 8.1, 2.6, 6.6, "$y_v = 0$")
arrow(6.1, 8.1, 7.4, 6.6, "$y_v = 1$")
node(2.4, 5.8, 3.6, 1.5, "margine: 11,1\nramificăm mai departe")
node(7.6, 5.8, 3.6, 1.5, "soluție întreagă\ndrum de lungime 10", fc="#D8F3E8", ec=AQUA)
arrow(1.5, 5.0, 0.9, 3.4)
arrow(3.3, 5.0, 3.9, 3.4)
node(0.9, 2.6, 3.1, 1.5, "margine: 9,6 < 10\nramură tăiată", fc="#FBE4E4", ec=RED)
node(4.2, 2.6, 3.4, 1.5, "ciclu găsit în soluție:\nadăugăm o tăietură (cut)", fc="#FFF4E0", ec=YELLOW)
ax.text(7.6, 3.1, "Marginile vin din relaxarea LP.\nRamurile care nu pot depăși\ncea mai bună soluție se taie.\nConstrângerile de ciclu se adaugă\ndoar când sunt încălcate\n(branch and cut).",
        fontsize=10.5, color=MUTED, ha="center", va="center")
plt.tight_layout()
plt.savefig(os.path.join(OUT, "bnb.png"), transparent=True, bbox_inches="tight")
plt.close()

# ------------------------------------------------ GA generic cycle
fig, ax = plt.subplots(figsize=(7.6, 4.9), dpi=200)
ax.axis("off"); ax.set_xlim(-1.45, 1.45); ax.set_ylim(-1.12, 1.12)
steps = [
    ("Populație\n(soluții candidate)", 90, BLUE),
    ("Evaluare\n(fitness)", 18, AQUA),
    ("Selecție\n(supraviețuiesc cei buni)", -54, YELLOW),
    ("Încrucișare\n(combinăm 2 părinți)", -126, VIOLET),
    ("Mutație\n(schimbări mici)", -198, RED),
]
R = 0.78
centers = []
for text, ang, col in steps:
    a = np.deg2rad(ang)
    x, y = R*np.cos(a), R*np.sin(a)
    centers.append((x, y))
    b = FancyBboxPatch((x-0.42, y-0.16), 0.84, 0.32, boxstyle="round,pad=0.05",
                       facecolor="white", edgecolor=col, linewidth=2.2)
    ax.add_patch(b)
    ax.text(x, y, text, ha="center", va="center", fontsize=11, color=INK)
for i in range(len(centers)):
    x1, y1 = centers[i]; x2, y2 = centers[(i+1) % len(centers)]
    v = np.array([x2-x1, y2-y1]); d = np.linalg.norm(v); u = v/d
    p1 = np.array([x1, y1]) + u*0.40; p2 = np.array([x2, y2]) - u*0.40
    ax.add_patch(FancyArrowPatch(p1, p2, arrowstyle="-|>", mutation_scale=17,
                 color="#9AA3B2", lw=2.0, connectionstyle="arc3,rad=-0.25"))
ax.text(0, 0, "se repetă\ngenerație\ndupă generație", ha="center", va="center",
        fontsize=12.5, color=MUTED, fontstyle="italic")
plt.tight_layout()
plt.savefig(os.path.join(OUT, "ga_cycle.png"), transparent=True, bbox_inches="tight")
plt.close()

# ------------------------------------------------ usair network
import networkx as nx
G = nx.read_graphml(r"C:\Facultate\Github Repositories\Disertatie\Disertatie\src\main\resources\graph-instances\usair.graphml")
G = nx.Graph(G)
pos = nx.spring_layout(G, seed=7, k=0.6/np.sqrt(len(G)))
deg = dict(G.degree())
fig, ax = plt.subplots(figsize=(6.4, 5.2), dpi=200)
ax.axis("off")
nx.draw_networkx_edges(G, pos, ax=ax, edge_color="#C9CFDA", width=0.35, alpha=0.6)
sizes = [6 + deg[n]*1.6 for n in G.nodes()]
cols = [BLUE if deg[n] > 25 else "#86b6ef" for n in G.nodes()]
nx.draw_networkx_nodes(G, pos, ax=ax, node_size=sizes, node_color=cols, linewidths=0)
ax.set_title("usair: rețeaua de transport aerian din SUA\n332 de aeroporturi, 2 126 de rute", fontsize=13, color=INK, fontweight="bold", pad=8)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "usair.png"), transparent=True, bbox_inches="tight")
plt.close()

# ------------------------------------------------ results bars (no em dash, renamed)
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
print("figures2 done")
