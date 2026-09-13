# -*- coding: utf-8 -*-
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np, os

OUT = r"C:\Users\Razvan\AppData\Local\Temp\claude\C--Facultate-Github-Repositories-Disertatie\a9442c31-b9db-4856-8e85-3dd6dd933a26\scratchpad\figs"
os.makedirs(OUT, exist_ok=True)

INK = "#1B2A4A"; MUTED = "#6B7280"; GRID = "#E5E7EB"
BLUE = "#2a78d6"; YELLOW = "#eda100"; VIOLET = "#4a3aa7"; AQUA = "#1baf7a"; RED = "#e34948"

plt.rcParams.update({
    "font.family": "Arial",
    "text.color": INK, "axes.edgecolor": GRID,
})

# ---------------------------------------------------------------- fig 1: definition
# same small graph twice; left = induced path (valid), right = path with a chord (invalid)
pos = {
    0: (0.0, 0.55), 1: (0.9, 1.05), 2: (1.9, 1.1), 3: (2.9, 0.75),
    4: (3.4, -0.15), 5: (2.3, -0.35), 6: (1.2, -0.4), 7: (2.0, 0.35),
}
edges = [(0,1),(1,2),(2,3),(3,4),(0,6),(6,5),(5,4),(6,7),(7,2),(7,5),(1,7)]
path_ok = [0,1,2,3,4]           # induced: no chords among 0,1,2,3,4
path_bad = [0,6,7,2,3]          # 7-2 consecutive fine, but chord 6-5? not in set... use chord demo below
chord = (1,3)                    # we ADD this edge on the right panel to break it

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
                    color=BLUE if onpath else "#C9CFDA",
                    lw=4.0 if onpath else 1.4, zorder=2 if onpath else 1)
    for v, (x, y) in pos.items():
        onp = v in path_ok
        ax.add_patch(plt.Circle((x, y), 0.145,
                     facecolor=BLUE if onp else "white",
                     edgecolor=BLUE if onp else "#9AA3B2", lw=1.6, zorder=4))
        ax.text(x, y, str(v+1), ha="center", va="center", zorder=5,
                color="white" if onp else MUTED, fontsize=11, fontweight="bold")
    ax.set_title(title, fontsize=16, fontweight="bold",
                 color=(AQUA if ok else RED), pad=10)
fig.suptitle("")
plt.tight_layout()
plt.savefig(os.path.join(OUT, "def_indus.png"), transparent=True)
plt.close()

# ---------------------------------------------------------------- fig 2: results bars
# PDF Table 3.2 values (max over 5 runs for GA/ACO; B&C = best of CEC/CUT)
panels = [
    ("yeast — rețea de interacțiuni proteice\n(2 361 vârfuri, rar)", [184, 204, 328, 395], "ACO"),
    ("generated-hard-5000 — instanță generată\n(5 000 vârfuri, 1,3 mil. muchii, dens)", [51, 313, 395, 339], "GA"),
]
algs = ["Branch-and-cut\n(CEC/CUT)", "HLIPP", "GA (propus)", "ACO (propus)"]
cols = [BLUE, YELLOW, VIOLET, AQUA]

fig, axes = plt.subplots(1, 2, figsize=(10.8, 4.1), dpi=200)
for ax, (title, vals, winner) in zip(axes, panels):
    x = np.arange(4)
    bars = ax.bar(x, vals, width=0.62, color=cols, zorder=3)
    for r, v in zip(bars, vals):
        ax.text(r.get_x() + r.get_width()/2, v + 9, str(v), ha="center",
                va="bottom", fontsize=13, fontweight="bold", color=INK)
    ax.set_title(title, fontsize=12.5, fontweight="bold", pad=10, color=INK)
    ax.set_xticks(x); ax.set_xticklabels(algs, fontsize=10.5, color=INK)
    ax.set_ylim(0, 460)
    ax.set_yticks([])
    for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
    ax.spines["bottom"].set_color(GRID)
    ax.tick_params(length=0)
fig.text(0.5, -0.02, "Lungimea celui mai lung drum indus găsit (maxim din 5 rulări, limită 15 min)",
         ha="center", fontsize=11.5, color=MUTED)
plt.tight_layout()
plt.savefig(os.path.join(OUT, "rezultate_mari.png"), transparent=True, bbox_inches="tight")
plt.close()
print("figures done")
