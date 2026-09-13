# -*- coding: utf-8 -*-
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import numpy as np, os

OUT = r"C:\Users\Razvan\AppData\Local\Temp\claude\C--Facultate-Github-Repositories-Disertatie\a9442c31-b9db-4856-8e85-3dd6dd933a26\scratchpad\figs"
INK = "#1B2A4A"; MUTED = "#6B7280"
BLUE = "#2a78d6"; YELLOW = "#eda100"; VIOLET = "#4a3aa7"; AQUA = "#1baf7a"; RED = "#e34948"
plt.rcParams.update({"font.family": "Arial", "text.color": INK, "mathtext.fontset": "dejavusans"})

# ---------------- IP3: formula left, explanation right, no overlap
fig, ax = plt.subplots(figsize=(9.4, 4.6), dpi=200)
ax.axis("off"); ax.set_xlim(0, 1); ax.set_ylim(0, 1)
rows = [
    (INK,    r"$\max\ \sum_{t=0}^{T}\sum_{i\in V} x_i^t$", "maximizăm numărul de vârfuri vizitate"),
    (BLUE,   r"$\sum_{i\in V} x_i^t \leq 1 \quad \forall t$", "cel mult un vârf la fiecare pas de timp"),
    (AQUA,   r"$\sum_{t=0}^{T} x_i^t \leq 1 \quad \forall i \in V$", "fiecare vârf este vizitat cel mult o dată"),
    (YELLOW, r"$x_i^t + x_j^{t+1} \leq 1 \quad \forall (i,j)\notin E$", "pașii consecutivi folosesc doar muchii existente"),
    (RED,    r"$x_i^t + x_j^{\tau} \leq 1 \quad \forall (i,j)\in E,\ \tau \geq t+2$", "fără scurtături: un vecin poate urma doar imediat"),
]
ys = [0.90, 0.71, 0.52, 0.33, 0.155]
for (col, formula, expl), y in zip(rows, ys):
    ax.text(0.02, y, formula, fontsize=14.5, color=col, va="center")
    ax.text(0.56, y, expl, fontsize=12, color=MUTED, va="center")
ax.text(0.02, 0.015, r"$x_i^t = 1$  dacă vârful $i$ este vizitat la momentul $t$  (variabile binare)",
        fontsize=11.5, color=INK, va="bottom")
plt.tight_layout()
plt.savefig(os.path.join(OUT, "ip3.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------- B&B: nudge bottom boxes apart
fig, ax = plt.subplots(figsize=(7.8, 4.9), dpi=200)
ax.axis("off"); ax.set_xlim(0, 10); ax.set_ylim(0, 10)

def node(x, y, w, h, text, fc="#F1F5F9", ec="#DDE3EC", fs=11):
    b = FancyBboxPatch((x - w/2, y - h/2), w, h, boxstyle="round,pad=0.12",
                       facecolor=fc, edgecolor=ec, linewidth=1.4)
    ax.add_patch(b)
    ax.text(x, y, text, ha="center", va="center", fontsize=fs, color=INK)

def arrow(x1, y1, x2, y2, label=None):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle="-|>",
                 mutation_scale=14, color="#9AA3B2", lw=1.6))
    if label:
        ax.text((x1+x2)/2 + 0.32, (y1+y2)/2, label, fontsize=10.5, color=INK, ha="left")

node(5, 8.9, 4.6, 1.5, "Relaxare LP (fără întregi)\nmargine superioară: 12,4", fc="#E8F1FC", ec=BLUE)
arrow(3.9, 8.1, 2.6, 6.6, "$y_v = 0$")
arrow(6.1, 8.1, 7.4, 6.6, "$y_v = 1$")
node(2.4, 5.8, 3.6, 1.5, "margine: 11,1\nramificăm mai departe")
node(7.6, 5.8, 3.6, 1.5, "soluție întreagă\ndrum de lungime 10", fc="#D8F3E8", ec=AQUA)
arrow(1.5, 5.0, 1.0, 3.5)
arrow(3.3, 5.0, 4.4, 3.5)
node(1.2, 2.6, 2.9, 1.5, "margine: 9,6 < 10\nramură tăiată", fc="#FBE4E4", ec=RED, fs=10.5)
node(4.8, 2.6, 3.2, 1.5, "ciclu găsit în soluție:\nadăugăm o tăietură", fc="#FFF4E0", ec=YELLOW, fs=10.5)
ax.text(8.0, 3.0, "Marginile vin din relaxarea LP.\nRamurile care nu pot depăși\ncea mai bună soluție se taie.\nConstrângerile de ciclu se adaugă\ndoar când sunt încălcate\n(branch and cut).",
        fontsize=10.5, color=MUTED, ha="center", va="center")
plt.tight_layout()
plt.savefig(os.path.join(OUT, "bnb.png"), transparent=True, bbox_inches="tight")
plt.close()

# ---------------- GA cycle: horizontal pipeline with loop-back
fig, ax = plt.subplots(figsize=(11.2, 3.4), dpi=200)
ax.axis("off"); ax.set_xlim(0, 22); ax.set_ylim(0, 6.6)
steps = [
    ("Populație\ninițială", BLUE),
    ("Evaluare\n(fitness = lungimea)", AQUA),
    ("Selecție\n(supraviețuiesc cei buni)", YELLOW),
    ("Încrucișare\n(combinăm 2 părinți)", VIOLET),
    ("Mutație\n(schimbări mici)", RED),
]
xs = np.linspace(2.1, 19.9, 5)
for (text, col), x in zip(steps, xs):
    b = FancyBboxPatch((x - 1.85, 3.3), 3.7, 2.1, boxstyle="round,pad=0.12",
                       facecolor="white", edgecolor=col, linewidth=2.4)
    ax.add_patch(b)
    ax.text(x, 4.35, text, ha="center", va="center", fontsize=12, color=INK)
for i in range(4):
    ax.add_patch(FancyArrowPatch((xs[i] + 2.0, 4.35), (xs[i+1] - 2.0, 4.35),
                 arrowstyle="-|>", mutation_scale=18, color="#9AA3B2", lw=2.2))
ax.add_patch(FancyArrowPatch((xs[4], 3.15), (xs[1], 3.15),
             arrowstyle="-|>", mutation_scale=18, color="#9AA3B2", lw=2.2,
             connectionstyle="arc3,rad=-0.28"))
ax.text((xs[1]+xs[4])/2, 0.7, "generația următoare: se repetă până la limita de timp",
        ha="center", fontsize=12, color=MUTED, fontstyle="italic")
plt.savefig(os.path.join(OUT, "ga_cycle.png"), transparent=True, bbox_inches="tight")
plt.close()
print("fixed figures done")
