# -*- coding: utf-8 -*-
"""Dark deck variants: the slide background matches the demo app, so demo
screenshots and video frames blend in without a visible rectangle.

Usage: python build_variant.py midnight|aurora|all
"""
import os, sys
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn

HERE = os.path.dirname(os.path.abspath(__file__))
VARDIR = os.path.normpath(os.path.join(HERE, ".."))
DARK = os.path.join(VARDIR, "assets_dark")          # dark matplotlib figures
ASSETS = os.path.normpath(os.path.join(VARDIR, "..", "assets"))  # screenshots + thesis figures

C = lambda h: RGBColor(int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16))

THEMES = {
    "midnight": dict(
        name="Midnight",
        outfile="Prezentare_LIP_Midnight.pptx",
        bg=C("#0E1421"), bg_image=None,
        ink=C("#EAF0FA"), body=C("#C6CFDF"), muted=C("#97A2B8"),
        blue=C("#5AA2F2"), aqua=C("#2DD4A0"), yellow=C("#F5BE4A"), violet=C("#A78BFA"),
        card=C("#161F33"), card_edge=C("#2A3550"),
        banner=C("#2B2410"), banner_edge=C("#7A6320"),
        th_fill=C("#24304E"), row_a=C("#0F1727"), row_b=C("#141D31"),
        win_aqua=C("#17493B"), win_violet=C("#332B63"),
        panel_white=C("#FFFFFF"),
        accent=C("#2DD4A0"), rule_image=None,
    ),
    "aurora": dict(
        name="Aurora",
        outfile="Prezentare_LIP_Aurora.pptx",
        bg=C("#0C1224"), bg_image=os.path.join(DARK, "aurora_bg.png"),
        ink=C("#F2F5FC"), body=C("#C9D2E3"), muted=C("#8C97AD"),
        blue=C("#5AA2F2"), aqua=C("#2DD4A0"), yellow=C("#F5BE4A"), violet=C("#A78BFA"),
        card=C("#151E36"), card_edge=C("#313E60"),
        banner=C("#2B2410"), banner_edge=C("#7A6320"),
        th_fill=C("#202C4E"), row_a=C("#111A30"), row_b=C("#16203A"),
        win_aqua=C("#17493B"), win_violet=C("#332B63"),
        panel_white=C("#FFFFFF"),
        accent=C("#33D6E8"), rule_image=os.path.join(DARK, "grad_strip.png"),
    ),
}

FONT = "Arial"

# detailed-table border style: "full" (grid), "groups" (only thick lines
# between algorithms + row lines), "none" (fills only)
TABLE_BORDERS = os.environ.get("TABLE_BORDERS", "full")
OUT_SUFFIX = os.environ.get("OUT_SUFFIX", "")


def build(theme_key):
    T = THEMES[theme_key]
    INK, BODY, MUTED = T["ink"], T["body"], T["muted"]
    BLUE, AQUA, YELLOW, VIOLET = T["blue"], T["aqua"], T["yellow"], T["violet"]
    CARD, CARD_EDGE = T["card"], T["card_edge"]
    BANNER, BANNER_EDGE = T["banner"], T["banner_edge"]
    WIN_AQUA, WIN_VIOLET = T["win_aqua"], T["win_violet"]

    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)
    BLANK = prs.slide_layouts[6]

    def add_slide():
        s = prs.slides.add_slide(BLANK)
        s.background.fill.solid()
        s.background.fill.fore_color.rgb = T["bg"]
        if T["bg_image"]:
            s.shapes.add_picture(T["bg_image"], 0, 0,
                                 width=prs.slide_width, height=prs.slide_height)
        return s

    def box(slide, x, y, w, h):
        tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
        tb.text_frame.word_wrap = True
        return tb

    def para(tf, text, size, color=BODY, bold=False, italic=False, first=False,
             align=PP_ALIGN.LEFT, space_after=6, bullet=False):
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        p.alignment = align
        p.space_after = Pt(space_after)
        r = p.add_run()
        r.text = text
        r.font.size = Pt(size); r.font.color.rgb = color
        r.font.bold = bold; r.font.italic = italic; r.font.name = FONT
        pPr = p._p.get_or_add_pPr()
        for tag in ("a:buNone", "a:buChar", "a:buAutoNum"):
            for el in pPr.findall(qn(tag)):
                pPr.remove(el)
        if bullet:
            pPr.append(pPr.makeelement(qn("a:buFont"), {"typeface": "Arial"}))
            pPr.append(pPr.makeelement(qn("a:buChar"), {"char": "•"}))
            pPr.set("indent", "-137160"); pPr.set("marL", "137160")
        else:
            pPr.append(pPr.makeelement(qn("a:buNone"), {}))
        return p

    def rule(slide, x, y, w):
        if T["rule_image"]:
            slide.shapes.add_picture(T["rule_image"], Inches(x), Inches(y),
                                     width=Inches(w), height=Pt(3.2))
        else:
            r = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(x), Inches(y),
                                       Inches(w), Pt(3.2))
            r.fill.solid(); r.fill.fore_color.rgb = T["accent"]
            r.line.fill.background(); r.shadow.inherit = False

    def title_bar(slide, text, subtitle=None):
        tb = box(slide, 0.55, 0.28, 12.2, 0.85)
        para(tb.text_frame, text, 28, INK, bold=True, first=True, space_after=0)
        if subtitle:
            para(tb.text_frame, subtitle, 14, MUTED, space_after=0)
        rule(slide, 0.58, 1.06, 1.5)

    def card(slide, x, y, w, h, fill=CARD, edge=CARD_EDGE):
        sh = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y),
                                    Inches(w), Inches(h))
        sh.adjustments[0] = 0.055
        sh.fill.solid(); sh.fill.fore_color.rgb = fill
        sh.line.color.rgb = edge; sh.line.width = Pt(1)
        sh.shadow.inherit = False
        return sh

    def pic(slide, path, x, y, w=None, h=None):
        kw = {}
        if w: kw["width"] = Inches(w)
        if h: kw["height"] = Inches(h)
        return slide.shapes.add_picture(path, Inches(x), Inches(y), **kw)

    def pdf_pic(slide, path, x, y, w=None, h=None):
        """Thesis figure with white background: frame it on a white rounded
        panel so it reads as an intentional card on the dark slide."""
        p = pic(slide, path, x, y, w, h)
        pad = Inches(0.12)
        sh = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE,
                                    p.left - pad, p.top - pad,
                                    p.width + 2 * pad, p.height + 2 * pad)
        sh.adjustments[0] = 0.045
        sh.fill.solid(); sh.fill.fore_color.rgb = T["panel_white"]
        sh.line.color.rgb = CARD_EDGE; sh.line.width = Pt(1)
        sh.shadow.inherit = False
        el = sh._element
        el.getparent().remove(el)
        p._element.addprevious(el)
        return p

    def notes(slide, text):
        slide.notes_slide.notes_text_frame.text = text

    def make_table(slide, data, x, y, w, h, col_widths, font_size=13, header_size=13,
                   left_col=0, row_height=None, bold_cells=None, fill_cells=None):
        bold_cells = bold_cells or set()
        fill_cells = fill_cells or {}
        tbl = slide.shapes.add_table(len(data), len(data[0]), Inches(x), Inches(y),
                                     Inches(w), Inches(h)).table
        for i, cw in enumerate(col_widths):
            tbl.columns[i].width = Inches(cw)
        if row_height:
            tbl.rows[0].height = Inches(row_height[0])
            for r in range(1, len(data)):
                tbl.rows[r].height = Inches(row_height[1])
        for r, row in enumerate(data):
            for c, val in enumerate(row):
                cell = tbl.cell(r, c)
                cell.margin_left = Inches(0.06); cell.margin_right = Inches(0.03)
                cell.margin_top = Inches(0.01); cell.margin_bottom = Inches(0.01)
                cell.vertical_anchor = MSO_ANCHOR.MIDDLE
                p = cell.text_frame.paragraphs[0]
                p.alignment = PP_ALIGN.LEFT if c == left_col else PP_ALIGN.CENTER
                run = p.add_run(); run.text = val
                run.font.name = FONT
                run.font.size = Pt(header_size if r == 0 else font_size)
                if r == 0:
                    cell.fill.solid(); cell.fill.fore_color.rgb = T["th_fill"]
                    run.font.color.rgb = INK; run.font.bold = True
                else:
                    cell.fill.solid()
                    cell.fill.fore_color.rgb = T["row_a"] if r % 2 else T["row_b"]
                    run.font.color.rgb = BODY
                    if c == left_col:
                        run.font.color.rgb = INK
                    if (r, c) in bold_cells:
                        run.font.bold = True; run.font.color.rgb = INK
                    if (r, c) in fill_cells:
                        cell.fill.fore_color.rgb = fill_cells[(r, c)]
                        run.font.bold = True; run.font.color.rgb = INK
        return tbl

    # ============================================================ 1. TITLE
    s = add_slide()
    if T["rule_image"]:
        s.shapes.add_picture(T["rule_image"], 0, 0, width=prs.slide_width, height=Inches(0.18))
    else:
        band = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, Inches(0.18))
        band.fill.solid(); band.fill.fore_color.rgb = T["accent"]
        band.line.fill.background(); band.shadow.inherit = False
    tb = box(s, 1.0, 0.9, 11.3, 0.9)
    para(tb.text_frame, "Universitatea Alexandru Ioan Cuza din Iași, Facultatea de Informatică", 15, MUTED, first=True, align=PP_ALIGN.CENTER)
    tb = box(s, 1.0, 2.35, 11.3, 1.9)
    para(tb.text_frame, "Problema celui mai lung drum indus", 40, INK, bold=True, first=True, align=PP_ALIGN.CENTER, space_after=4)
    para(tb.text_frame, "The Longest Induced Path Problem", 20, MUTED, italic=True, align=PP_ALIGN.CENTER, space_after=0)
    rule(s, 5.92, 4.28, 1.5)
    tb = box(s, 1.0, 4.75, 11.3, 1.7)
    para(tb.text_frame, "Apetrei Răzvan-Emanuel", 20, INK, bold=True, first=True, align=PP_ALIGN.CENTER, space_after=2)
    para(tb.text_frame, "Coordonator științific: Lect. dr. Cristian Frăsinaru", 15, BODY, align=PP_ALIGN.CENTER, space_after=2)
    para(tb.text_frame, "Sesiunea iulie 2026", 13, MUTED, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "Buna ziua. Ma numesc Razvan Apetrei si astazi va prezint lucrarea mea de disertatie despre problema celui mai lung drum indus, realizata sub indrumarea domnului lector Cristian Frasinaru. (20 s)")

    # ============================================================ 2. DEFINITION
    s = add_slide()
    title_bar(s, "Ce este un drum indus?")
    pic(s, os.path.join(DARK, "def_indus.png"), 1.37, 1.2, w=10.6)
    tb = box(s, 0.8, 5.4, 11.8, 1.9)
    para(tb.text_frame, "Alegem o mulțime de vârfuri; subgraful indus păstrează toate muchiile dintre ele.", 17, BODY, first=True, bullet=True)
    para(tb.text_frame, "Dacă subgraful este exact un drum, fără nicio scurtătură (coardă), avem un drum indus.", 17, BODY, bullet=True)
    para(tb.text_frame, "LIP: găsiți drumul indus cu număr maxim de vârfuri. NP-complet, chiar și pe grafuri bipartite.", 17, INK, bold=True, bullet=True)
    notes(s, "Definitia pe scurt: alegem varfuri, iar subgraful indus de ele pastreaza automat toate muchiile dintre ele. Daca acest subgraf este exact un drum, deci fara scurtaturi intre varfuri neconsecutive, avem un drum indus. In stanga, drumul 1-5 este indus. In dreapta, acelasi drum nu mai este indus, pentru ca muchia 2-4 este o coarda. Problema cere drumul indus de lungime maxima si este NP-completa chiar si pe grafuri bipartite. (1,5 min)")

    # ============================================================ 3. MOTIVATION
    s = add_slide()
    title_bar(s, "De ce merită studiată?")
    cards = [
        ("🌐", "Rețele de comunicații și transport",
         "Distanța în cel mai rău caz între două noduri, când nodurile intermediare pot ceda și traficul e forțat pe rute ocolitoare."),
        ("🐍", "Snake-in-the-box (coduri corectoare de erori)",
         "Drumurile induse în hipercuburi sunt exact șerpii folosiți în codurile Gray corectoare de erori, o problemă deschisă celebră."),
        ("📢", "Rețele sociale",
         "Cel mai lung lanț pe care o informație se poate propaga din persoană în persoană, de la sursă la ultimul destinatar."),
    ]
    for i, (emoji, t, d) in enumerate(cards):
        x = 0.62 + i * 4.12
        card(s, x, 1.4, 3.9, 3.6)
        tb = box(s, x + 0.25, 1.65, 3.4, 3.2)
        para(tb.text_frame, emoji, 34, INK, first=True, align=PP_ALIGN.CENTER, space_after=8)
        para(tb.text_frame, t, 16, INK, bold=True, align=PP_ALIGN.CENTER, space_after=8)
        para(tb.text_frame, d, 13.5, BODY, align=PP_ALIGN.CENTER, space_after=0)
    card(s, 0.62, 5.4, 12.06, 1.35, fill=BANNER, edge=BANNER_EDGE)
    tb = box(s, 0.95, 5.57, 11.4, 1.05)
    para(tb.text_frame, "Golul din literatură: doar metode exacte (ILP, backtracking), nicio metaeuristică.", 18, INK, bold=True, first=True, align=PP_ALIGN.CENTER, space_after=2)
    para(tb.text_frame, "Exact aici intervine această lucrare.", 15, BODY, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "Trei motivatii practice: in retele de comunicatii si transport, lungimea drumului indus maxim da distanta in cel mai rau caz cand nodurile intermediare cedeaza. A doua: problema snake-in-the-box, unde drumurile induse in hipercuburi sunt exact serpii din codurile Gray corectoare de erori. A treia: in retele sociale, e cel mai lung lant de propagare a unei informatii. Si motivatia stiintifica: in literatura exista doar metode exacte, nicio metaeuristica. Acesta e golul pe care il umple lucrarea. (1,5 min)")

    # ============================================================ 4. SOTA: ILP + B&B (combined)
    s = add_slide()
    title_bar(s, "Stadiul actual: programare liniară întreagă",
              "Matsypura et al. 2019; Marzo et al. 2022: formulările CEC și CUT")
    tb = box(s, 0.62, 1.45, 5.85, 5.6)
    para(tb.text_frame, "Modelăm problema cu variabile binare: ce vârfuri și muchii intră în drum; restricțiile forțează forma de drum indus, fără scurtături.", 16.5, BODY, first=True, bullet=True, space_after=12)
    para(tb.text_frame, "Relaxarea LP (fără cerința de întregi) dă o margine superioară pentru fiecare ramură.", 16.5, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Ramificăm pe câte o variabilă; ramurile care nu pot depăși cea mai bună soluție găsită se taie.", 16.5, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Restricțiile de eliminare a ciclurilor sunt exponențial de multe, deci se adaugă doar când o soluție le încalcă (branch and cut).", 16.5, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "CEC folosește restricții de ciclu, CUT restricții de conectivitate (tăieturi).", 16.5, INK, bold=True, bullet=True)
    pic(s, os.path.join(DARK, "bnb.png"), 6.65, 1.75, w=6.35)
    notes(s, "Prima familie de metode exacte: programarea liniara intreaga. Modelam problema cu variabile binare care spun ce varfuri si muchii intra in drum, iar restrictiile forteaza forma de drum indus, fara scurtaturi; modelul se da unui solver. Cele mai puternice variante, CEC si CUT ale lui Marzo, il rezolva prin branch and bound cu taieturi: relaxarea liniara da o margine superioara, ramificam pe cate o variabila, iar ramurile care nu pot depasi cea mai buna solutie gasita se taie complet. Restrictiile care interzic ciclurile sunt exponential de multe, asa ca se adauga doar cand o solutie le incalca, de aceea branch and cut. CEC foloseste restrictii de ciclu, CUT restrictii de conectivitate. Formularea detaliata e pe slide-ul de backup, daca apar intrebari. (1,5 min)")

    # ============================================================ 6. SOTA: BACKTRACKING
    s = add_slide()
    title_bar(s, "Stadiul actual: backtracking", "Marzo și Ribeiro 2021: enumerare exactă și euristica HLIPP")
    tb = box(s, 0.62, 1.4, 6.2, 4.7)
    para(tb.text_frame, "Enumerare exactă: construim recursiv toate drumurile induse maximale, pornind din fiecare vârf al grafului.", 15.5, BODY, first=True, bullet=True, space_after=10)
    para(tb.text_frame, "La fiecare pas, vecinii capătului cu un singur vecin pe drum sunt extensii valide (verde); toți ceilalți vecini ai drumului devin interziși (roșu).", 15.5, BODY, bullet=True, space_after=10)
    para(tb.text_frame, "Când nu mai putem extinde, ne întoarcem (backtracking) și încercăm altă ramură.", 15.5, BODY, bullet=True, space_after=10)
    para(tb.text_frame, "Găsește sigur optimul, dar numărul de drumuri explodează combinatorial.", 15.5, BODY, bullet=True, space_after=10)
    para(tb.text_frame, "HLIPP (euristică): abandonează un vârf de start după 10 000 de drumuri fără îmbunătățire.", 15.5, INK, bold=True, bullet=True)
    pic(s, os.path.join(ASSETS, "exact_redgreen.png"), 7.15, 1.35, w=5.6)
    tb = box(s, 7.15, 5.9, 5.6, 0.5)
    para(tb.text_frame, "captură din demo: albastru = drumul curent, verde = extensii valide, roșu = vârfuri interzise", 11, MUTED, first=True, italic=True, align=PP_ALIGN.CENTER, space_after=0)
    card(s, 0.62, 6.5, 12.06, 0.8, fill=BANNER, edge=BANNER_EDGE)
    tb = box(s, 0.95, 6.62, 11.4, 0.6)
    para(tb.text_frame, "Concluzia: metodele exacte demonstrează optimul, dar devin impracticabile peste câteva sute de vârfuri.", 15.5, INK, bold=True, first=True, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "A treia abordare din literatura: backtracking. Enumeram recursiv toate drumurile induse maximale, pornind din fiecare varf. Imaginea, luata din demo-ul pe care l-am construit, arata exact starea cautarii: drumul curent e albastru, extensiile inca valide sunt verzi, iar rosii sunt varfurile interzise definitiv pentru ca sunt vecine cu drumul. Cand nu mai putem extinde, facem backtracking. Metoda gaseste sigur optimul, dar explodeaza combinatorial. Varianta euristica, HLIPP, taie cautarea: abandoneaza un varf de start dupa 10.000 de drumuri fara imbunatatire. Concluzia intregii sectiuni: metodele exacte demonstreaza optimul, dar peste cateva sute de varfuri devin impracticabile. (1,5 min)")

    # ============================================================ 7. WHY HARD
    s = add_slide()
    title_bar(s, "De ce e greu să construiești o metaeuristică?")
    tb = box(s, 0.62, 1.5, 6.1, 5.3)
    para(tb.text_frame, "Spațiul de căutare este uriaș, dar foarte puține submulțimi de vârfuri formează drumuri induse valide.", 16.5, BODY, first=True, bullet=True, space_after=12)
    para(tb.text_frame, "Fiecare vârf ales interzice definitiv toți ceilalți vecini ai săi; o singură alegere greșită (v4 în loc de v5) blochează extinderea.", 16.5, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Operatorii standard de mutație și încrucișare produc aproape mereu soluții invalide (cu coarde).", 16.5, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Algoritmii se blochează ușor în maxime locale, deci sunt necesare mecanisme dedicate de explorare.", 16.5, INK, bold=True, bullet=True)
    pic(s, os.path.join(DARK, "greu.png"), 6.85, 1.35, w=6.0)
    notes(s, "Inainte de algoritmii propusi, de ce e greu sa faci o metaeuristica pentru aceasta problema. Spatiul de cautare e urias, dar solutiile valide sunt extrem de rare: aproape orice submultime de varfuri contine o coarda. In figura, aceeasi situatie cu doua alegeri: sus, daca aleg v4, vecinii lui v5 si v6 devin interzisi si drumul moare la 4 varfuri; jos, daca aleg v5, drumul continua prin v7 si v8 pana la 6 varfuri. O singura decizie schimba tot. De aceea operatorii clasici de mutatie si incrucisare produc aproape mereu solutii invalide, iar algoritmii se blocheaza in maxime locale. Ambii algoritmi pe care ii propun sunt construiti special in jurul acestor probleme. (1 min)")

    # ============================================================ 8. ACO HIGH LEVEL
    s = add_slide()
    title_bar(s, "Algoritmul 1: Ant Colony Optimization", "prima abordare ACO pentru această problemă")
    tb = box(s, 0.62, 1.5, 6.3, 5.2)
    para(tb.text_frame, "Inspirat din colonii de furnici: fiecare furnică construiește un drum indus, ghidată de feromonul de pe muchii.", 17, BODY, first=True, bullet=True, space_after=12)
    para(tb.text_frame, "La fiecare iterație: furnicile explorează, cel mai lung drum câștigă, feromonul se evaporă, apoi se depune pe drumurile bune.", 17, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Feedback pozitiv: muchiile drumurilor bune devin tot mai atractive pentru furnicile următoare.", 17, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Câte o furnică pentru fiecare vârf de start cu grad peste 2, rulate în paralel.", 17, BODY, bullet=True)
    pic(s, os.path.join(ASSETS, "aco_pheromone.png"), 7.15, 1.35, w=5.6)
    tb = box(s, 7.15, 5.95, 5.6, 0.6)
    para(tb.text_frame, "verde = feromon acumulat, punctele colorate = furnici explorând (demo-ul interactiv)", 11.5, MUTED, first=True, italic=True, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "Primul algoritm propus: optimizare cu colonii de furnici, prima abordare de acest fel pentru problema noastra. Ideea generala: furnicile sunt cautari care construiesc drumuri induse, ghidate de feromonul depus pe muchii. O iteratie are patru faze: furnicile exploreaza, cel mai lung drum castiga, feromonul se evapora putin peste tot, apoi se depune pe drumurile bune. Asa apare feedback-ul pozitiv: muchiile bune devin tot mai atractive. AICI: daca ai inserat clipul video de 30 de secunde de pe Drive, il rulezi acum in locul imaginii statice; altfel descrii imaginea din demo. Pe tema aceasta inchisa, clipul se contopeste cu fundalul slide-ului. (1,5 min)")

    # ============================================================ 9. ACO COMPONENTS
    s = add_slide()
    title_bar(s, "ACO: componentele și îmbunătățirile")
    tb = box(s, 0.62, 1.5, 5.9, 5.2)
    para(tb.text_frame, "Construcția drumului: DFS ghidat de feromoni, cu backtracking (mersul înainte clasic, ca la TSP, se blochează imediat în fundături).", 16, BODY, first=True, bullet=True, space_after=11)
    para(tb.text_frame, "Explorare bidirecțională: a doua căutare pornește din capătul celui mai bun drum găsit.", 16, BODY, bullet=True, space_after=11)
    para(tb.text_frame, "Feromon direcțional: aceeași muchie poate fi excelentă într-un sens și o capcană în celălalt.", 16, INK, bold=True, bullet=True, space_after=11)
    para(tb.text_frame, "Evaporare adaptivă la stagnare, restart cu listă tabu ca să nu recadă în același maxim local, rulare în paralel.", 16, BODY, bullet=True)
    pic(s, os.path.join(DARK, "aco_feromon.png"), 6.7, 1.45, w=6.25)
    notes(s, "Acum componentele. Prima adaptare majora: constructia drumului nu e mersul inainte clasic de la TSP, care s-ar bloca imediat in fundaturi, ci un DFS ghidat de feromoni, cu backtracking. A doua: explorarea bidirectionala, adica a doua cautare porneste din capatul drumului gasit, ca sa creasca si in directia opusa. Contributia cheie e feromonul directional: tau de la v la u este independent de tau de la u la v. In figura: muchia dintre v4 si v5 e o capcana parcursa spre dreapta, fiindca v5 e o fundatura, dar e valoroasa spre stanga, fiindca de acolo drumul continua. Un feromon nedirectional ar amesteca cele doua semnale. Peste toate acestea: evaporare adaptiva la stagnare, restarturi cu lista tabu si rulare in paralel. (1,5 min)")

    # ============================================================ 9b. ACO BIDIRECTIONAL
    s = add_slide()
    title_bar(s, "ACO: de ce căutăm de două ori?",
              "explorarea bidirecțională, același truc ca la calculul diametrului")
    pic(s, os.path.join(DARK, "aco_bidir.png"), 1.07, 1.35, w=11.2)
    tb = box(s, 0.8, 6.0, 11.8, 1.2)
    para(tb.text_frame, "O căutare crește drumul doar dinspre vârful de start: dacă startul cade în mijlocul unui drum lung, găsim cel mult o jumătate a lui.", 15.5, BODY, first=True, bullet=True, space_after=6)
    para(tb.text_frame, "Prima căutare găsește o extremitate; a doua, pornită din acel capăt, poate reconstrui drumul întreg.", 15.5, INK, bold=True, bullet=True, space_after=0)
    notes(s, "De ce cautam de doua ori: e acelasi truc folosit la calculul diametrului unui graf cu doua parcurgeri BFS. O cautare creste drumul doar dinspre vârful de start, deci startul ramane un capat al drumului gasit. Sus: daca startul pica in mijlocul drumului lung, gasim cel mult o jumatate, aici 5 varfuri din 9. Jos: a doua cautare porneste exact din capatul gasit de prima si poate parcurge drumul intreg, toate cele 9 varfuri. In ACO, la fiecare iteratie, a doua cautare porneste din capatul celui mai bun drum gasit, cu feromonul deja depus pe muchii. (1 min)")

    # ============================================================ 10. GA HIGH LEVEL
    s = add_slide()
    title_bar(s, "Algoritmul 2: algoritm genetic", "cum funcționează un algoritm genetic")
    pic(s, os.path.join(DARK, "ga_cycle.png"), 0.52, 1.5, w=12.3)
    tb = box(s, 0.8, 5.35, 11.8, 1.7)
    para(tb.text_frame, "Menținem o populație de soluții candidate care evoluează generație după generație.", 16.5, BODY, first=True, bullet=True)
    para(tb.text_frame, "Cei mai buni indivizi supraviețuiesc selecției și produc urmași prin încrucișare și mutație.", 16.5, BODY, bullet=True)
    para(tb.text_frame, "În plus: injectăm indivizi noi pentru diversitate și repornim parțial populația la stagnare.", 16.5, BODY, bullet=True)
    notes(s, "Al doilea algoritm propus: un algoritm genetic. Mai intai ideea generala, pentru cine nu e familiar: mentinem o populatie de solutii candidate. La fiecare generatie evaluam fitness-ul, selectam indivizii buni, ii combinam prin incrucisare, ii perturbam prin mutatie, si repetam pana la limita de timp. Peste schema clasica am adaugat doua mecanisme de diversitate: injectam constant indivizi noi generati de la zero, iar daca populatia stagneaza, o repornim partial, pastrand elitele. (1 min)")

    # ============================================================ 11. GA REPRESENTATION
    s = add_slide()
    title_bar(s, "GA: reprezentarea unui individ")
    tb = box(s, 0.62, 1.7, 5.7, 4.6)
    para(tb.text_frame, "Individ = vector ordonat de vârfuri care formează un drum indus valid în graf.", 17, BODY, first=True, bullet=True, space_after=12)
    para(tb.text_frame, "Fitness = lungimea drumului (numărul de vârfuri).", 17, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Ordinea păstrată în vector face operațiile la capetele drumului foarte rapide.", 17, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Toți operatorii păstrează prin construcție proprietatea de drum indus.", 17, INK, bold=True, bullet=True)
    pic(s, os.path.join(DARK, "ga_reprezentare.png"), 6.5, 1.5, w=6.45)
    notes(s, "Reprezentarea: fiecare individ este un vector ordonat de varfuri care formeaza un drum indus valid, iar fitness-ul este pur si simplu lungimea drumului. Pastrarea ordinii in vector face operatiile la capete foarte rapide, iar toti operatorii sunt construiti astfel incat rezultatul sa ramana mereu un drum indus valid. (40 s)")

    # ============================================================ 12. GA MUTATION
    s = add_slide()
    title_bar(s, "GA: mutația")
    pic(s, os.path.join(DARK, "ga_mutatie.png"), 3.55, 1.2, h=5.15)
    tb = box(s, 0.8, 6.45, 11.8, 0.9)
    para(tb.text_frame, "Tăiem câteva vârfuri de la capete și re-extindem prin DFS în ambele direcții: o căutare locală care poate găsi un drum mai lung decât originalul.", 15.5, BODY, first=True, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "Mutatia: taiem cateva varfuri de la ambele capete ale drumului, pastram mijlocul, apoi re-extindem prin DFS in ambele directii. In exemplu: drumul original v1-v6 are 6 varfuri si e maximal, capetele lui sunt infundate. Taiem v1, v2 si v6, pastram mijlocul v3-v4-v5, iar DFS-ul gaseste alte capete: prin v7 si v8 in stanga, prin v9, v10, v11 in dreapta. Rezultatul are 8 varfuri, mai lung decat originalul de 6. Practic e o cautare locala in jurul solutiei curente: pastram ce e bun si incercam alte capete. (40 s)")

    # ============================================================ 13. GA CROSSOVER
    s = add_slide()
    title_bar(s, "GA: încrucișarea")
    pic(s, os.path.join(DARK, "ga_crossover.png"), 0.87, 1.55, w=11.6)
    tb = box(s, 0.8, 6.5, 11.8, 0.9)
    para(tb.text_frame, "Combinăm vârfurile celor doi părinți, căutăm cu DFS un drum indus în subgraful uniunii, apoi îl extindem în tot graful: diversificare fără reparații costisitoare.", 15.5, BODY, first=True, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "Incrucisarea combina doi parinti prin uniunea multimilor lor de varfuri. Uniunea a doua drumuri induse nu mai e un drum indus, asa ca nu reparam nimic: rulam DFS-ul nostru in subgraful indus de uniune, gasim acolo cel mai bun drum, apoi il extindem cu varfuri din tot graful. Rolul operatorului este diversificarea: schimbari mari, dar mereu valide prin constructie. (40 s)")

    # ============================================================ 14. GA SELECTION
    s = add_slide()
    title_bar(s, "GA: selecția")
    tb = box(s, 0.62, 1.6, 6.0, 4.8)
    para(tb.text_frame, "Turneu (implicit): doi indivizi aleși la întâmplare, câștigă cel cu fitness mai mare.", 17, BODY, first=True, bullet=True, space_after=12)
    para(tb.text_frame, "Alternativă testată: ruleta, unde probabilitatea de supraviețuire este proporțională cu fitness-ul.", 17, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Elitism: cei mai buni indivizi trec direct în generația următoare, deci soluția cea mai bună nu se pierde niciodată.", 17, INK, bold=True, bullet=True)
    pic(s, os.path.join(DARK, "ga_ruleta.png"), 6.95, 1.55, w=5.85)
    notes(s, "Selectia: varianta implicita este turneul, simpla si rapida: alegem doi indivizi la intamplare si castiga cel cu fitness mai mare. Am testat si ruleta, in care probabilitatea de supravietuire e proportionala cu fitness-ul, ca in figura. Peste ambele aplicam elitism: cei mai buni indivizi trec direct in generatia urmatoare, deci cea mai buna solutie gasita nu se pierde niciodata. (40 s)")

    # ============================================================ 15. EXPERIMENTS
    s = add_slide()
    title_bar(s, "Experimente: instanțe și metodologie")
    card(s, 0.62, 1.35, 3.95, 4.45)
    tb = box(s, 0.9, 1.55, 3.45, 4.1)
    para(tb.text_frame, "24 de grafuri reale", 16.5, INK, bold=True, first=True, space_after=4)
    para(tb.text_frame, "33 până la 2 361 de vârfuri", 12.5, MUTED, space_after=8)
    para(tb.text_frame, "Rețele sociale: karate, dolphins, david", 13.5, BODY, bullet=True)
    para(tb.text_frame, "Rețele electrice: ieeebus, 494bus, 662bus", 13.5, BODY, bullet=True)
    para(tb.text_frame, "Transport aerian: usair", 13.5, BODY, bullet=True)
    para(tb.text_frame, "Biologie: yeast, rețeaua de interacțiuni proteice", 13.5, BODY, bullet=True)
    pic(s, os.path.join(DARK, "usair.png"), 4.85, 1.5, w=4.15)
    card(s, 9.25, 1.35, 3.45, 4.45)
    tb = box(s, 9.5, 1.55, 2.95, 4.1)
    para(tb.text_frame, "Generator propriu", 16.5, INK, bold=True, first=True, space_after=4)
    para(tb.text_frame, "contribuție a lucrării", 12.5, MUTED, space_after=8)
    para(tb.text_frame, "Drum plantat de lungime cunoscută (1 600)", 13.5, BODY, bullet=True)
    para(tb.text_frame, "Zgomot organizat în clici, capcane la capete", 13.5, BODY, bullet=True)
    para(tb.text_frame, "generated-hard-5000: 5 000 vârfuri, 1,3 mil. muchii", 13.5, INK, bold=True, bullet=True)
    notes(s, "Evaluarea: cele 24 de grafuri reale standard din literatura, de la retele sociale la reteaua electrica si reteaua de interactiuni proteice yeast. In mijloc vedeti reteaua usair, transportul aerian din SUA: 332 de aeroporturi, la pozitiile geografice reale. Sunt date reale, cu structura tipica hub-and-spoke. La acestea se adauga o instanta produsa de generatorul propriu, care planteaza un drum indus de lungime cunoscuta si il ingroapa in zgomot organizat in clici, cu capcane la capete. Asa stim o margine inferioara a optimului si putem masura cat de aproape ajunge fiecare algoritm. Configuratia experimentelor apare pe slide-ul cu tabelul detaliat. (1 min)")

    # ============================================================ 16. RESULTS TABLE (max)
    s = add_slide()
    title_bar(s, "Rezultate: aceleași resurse, 15 minute pe instanță")
    data = [
        ("Instanță", "|V|", "OPT", "CEC", "CUT", "HLIPP", "GA (propus)", "ACO (propus)"),
        ("karate", "34", "9", "9", "9", "9", "9", "9"),
        ("dolphins", "62", "24", "24", "24", "23", "24", "24"),
        ("sanjuansur", "75", "38", "38", "38", "36", "38", "38"),
        ("ieeebus", "118", "47", "47", "47", "47", "47", "47"),
        ("usair", "332", "46", "46", "46", "38", "46", "46"),
        ("494bus", "494", "142", "142", "142", "109", "142", "142"),
        ("662bus", "662", "305", "305", "305", "237", "283", "302"),
        ("yeast", "2 361", "?", "142", "184", "204", "328", "395"),
        ("generated-hard-5000", "5 000", "≥ 1 600", "51", "51", "313", "395", "339"),
    ]
    bold = set()
    opt_rows = {1: {3, 4, 5, 6, 7}, 2: {3, 4, 6, 7}, 3: {3, 4, 6, 7}, 4: {3, 4, 5, 6, 7},
                5: {3, 4, 6, 7}, 6: {3, 4, 6, 7}, 7: {3, 4}}
    for r, cols_ in opt_rows.items():
        for c in cols_:
            bold.add((r, c))
    make_table(s, data, 0.62, 1.35, 12.06, 5.5,
               [2.75, 0.95, 1.3, 1.15, 1.15, 1.3, 1.73, 1.73],
               font_size=14, header_size=13.5,
               row_height=(0.5, 0.5),
               bold_cells=bold,
               fill_cells={(8, 7): WIN_AQUA, (9, 6): WIN_VIOLET})
    notes(s, "Tabelul cu rezultate, valorile maxime din 5 rulari pentru GA si ACO. Cu aldin sunt valorile care ating optimul demonstrat. Pe instantele mici toata lumea gaseste optimul, desi HLIPP il rateaza uneori, ca la dolphins sau sanjuansur. Pana la 662 de varfuri, CEC si CUT demonstreaza optimul, iar ACO il atinge si el, mult mai repede. Pe 662bus ACO ajunge la 302 din 305. Apoi povestea se schimba: pe yeast, cu 2.361 de varfuri, metodele exacte raman la 184, HLIPP la 204, iar ACO ajunge la 395, cel mai bun rezultat cunoscut pentru aceasta instanta. Pe instanta generata, densa, cu 5.000 de varfuri, castiga algoritmul genetic cu 395, fata de 339 la ACO si 51 la metodele exacte. (2 min)")

    # ============================================================ 17. RESULTS TABLE (detail variant)
    s = add_slide()
    title_bar(s, "Rezultate detaliate: min / medie / max și timpi")

    LN_ORDER = ["a:lnL", "a:lnR", "a:lnT", "a:lnB"]

    def set_borders(cell, l=None, r=None, t=None, b=None):
        """each side: None or (width_pt, (r,g,b))"""
        tcPr = cell._tc.get_or_add_tcPr()
        sides = {"a:lnL": l, "a:lnR": r, "a:lnT": t, "a:lnB": b}
        for tag in reversed(LN_ORDER):
            spec = sides[tag]
            if spec is None:
                continue
            for el in tcPr.findall(qn(tag)):
                tcPr.remove(el)
            wpt, col = spec
            ln = tcPr.makeelement(qn(tag), {"w": str(int(wpt * 12700)),
                                            "cap": "flat", "cmpd": "sng", "algn": "ctr"})
            sf = ln.makeelement(qn("a:solidFill"), {})
            clr = sf.makeelement(qn("a:srgbClr"), {"val": "%02X%02X%02X" % col})
            sf.append(clr); ln.append(sf)
            tcPr.insert(0, ln)

    groups = [("CEC", 4, 2), ("CUT", 6, 2), ("HLIPP", 8, 2),
              ("GA (propus)", 10, 4), ("ACO (propus)", 14, 4)]
    sub = ["drum", "timp"] * 3 + ["min", "med", "max", "timp"] * 2
    rows_data = [
        ("karate", "34", "78", "9", ["9","0,1 s","9","0,1 s","9","0,1 s","9","9","9","10 s","9","9","9","0,2 s"]),
        ("dolphins", "62", "159", "24", ["24","0,2 s","24","0,2 s","23","1,4 s","24","24","24","28 s","24","24","24","0,3 s"]),
        ("sanjuansur", "75", "144", "38", ["38","0,2 s","38","0,1 s","36","2,6 s","38","38","38","40 s","38","38","38","0,3 s"]),
        ("ieeebus", "118", "179", "47", ["47","0,3 s","47","0,2 s","47","8,2 s","47","47","47","62 s","47","47","47","0,3 s"]),
        ("usair", "332", "2 126", "46", ["46","124 s","46","104 s","38","51 s","45","45,8","46","243 s","46","46","46","16 s"]),
        ("494bus", "494", "586", "142", ["142","17 s","142","89 s","109","221 s","142","142","142","108 s","142","142","142","2,5 s"]),
        ("662bus", "662", "906", "305", ["305","900 s","305","900 s","237","900 s","268","277","283","900 s","287","297,6","302","900 s"]),
        ("yeast", "2 361", "6 646", "?", ["142","900 s","184","900 s","204","900 s","279","300,2","328","900 s","384","387,4","395","900 s"]),
        ("generated-5000", "5 000", "1,3 mil.", "≥ 1 600", ["51","900 s","51","900 s","313","900 s","375","386,8","395","900 s","334","336,2","339","900 s"]),
    ]
    ncols = 18
    nrows = 2 + len(rows_data)
    col_w = [1.55, 0.62, 0.9, 0.62] + [0.55, 0.74] * 3 + [0.5, 0.58, 0.5, 0.74] * 2
    tw = sum(col_w)
    tbl = s.shapes.add_table(nrows, ncols, Inches((13.333 - tw) / 2), Inches(1.3),
                             Inches(tw), Inches(5.2)).table
    for i, cw in enumerate(col_w):
        tbl.columns[i].width = Inches(cw)
    tbl.rows[0].height = Inches(0.32); tbl.rows[1].height = Inches(0.28)
    for r in range(2, nrows):
        tbl.rows[r].height = Inches(0.45)
    for c in range(4):
        tbl.cell(0, c).merge(tbl.cell(1, c))
    for _, c0, span in groups:
        tbl.cell(0, c0).merge(tbl.cell(0, c0 + span - 1))

    def cell_text(cell, text, size, color, bold=False, align=PP_ALIGN.CENTER):
        cell.margin_left = Inches(0.03); cell.margin_right = Inches(0.03)
        cell.margin_top = Inches(0.01); cell.margin_bottom = Inches(0.01)
        cell.vertical_anchor = MSO_ANCHOR.MIDDLE
        p = cell.text_frame.paragraphs[0]
        p.alignment = align
        run = p.add_run(); run.text = text
        run.font.name = FONT; run.font.size = Pt(size)
        run.font.color.rgb = color; run.font.bold = bold

    # header rows
    for r in (0, 1):
        for c in range(ncols):
            cl = tbl.cell(r, c)
            cl.fill.solid(); cl.fill.fore_color.rgb = T["th_fill"]
    cell_text(tbl.cell(0, 0), "Instanță", 11.5, INK, bold=True, align=PP_ALIGN.LEFT)
    cell_text(tbl.cell(0, 1), "|V|", 11.5, INK, bold=True)
    cell_text(tbl.cell(0, 2), "|E|", 11.5, INK, bold=True)
    cell_text(tbl.cell(0, 3), "OPT", 11.5, INK, bold=True)
    for name, c0, span in groups:
        cell_text(tbl.cell(0, c0), name, 11.5, INK, bold=True)
    for i, lab in enumerate(sub):
        cell_text(tbl.cell(1, 4 + i), lab, 10, MUTED, bold=False)
    # data rows; values (not times) equal to the proven OPT are bold
    value_cols = {4, 6, 8, 10, 11, 12, 14, 15, 16}
    win_rows = {9: (14, 17, WIN_AQUA), 10: (10, 13, WIN_VIOLET)}
    for ri, (inst, nv, ne, opt, vals) in enumerate(rows_data):
        r = ri + 2
        base = T["row_a"] if r % 2 else T["row_b"]
        wc0, wc1, wfill = win_rows.get(r, (-1, -1, None))
        for c in range(ncols):
            cl = tbl.cell(r, c)
            cl.fill.solid()
            cl.fill.fore_color.rgb = wfill if wc0 <= c <= wc1 else base
        cell_text(tbl.cell(r, 0), inst, 10.5, INK, align=PP_ALIGN.LEFT)
        cell_text(tbl.cell(r, 1), nv, 10.5, BODY)
        cell_text(tbl.cell(r, 2), ne, 10.5, BODY)
        cell_text(tbl.cell(r, 3), opt, 10.5, BODY)
        for i, v in enumerate(vals):
            c = 4 + i
            win = wc0 <= c <= wc1
            hit = c in value_cols and v == opt
            cell_text(tbl.cell(r, c), v, 10.5, INK if (win or hit) else BODY,
                      bold=win or hit)
    # white grid: thick verticals between algorithms, thin everywhere else
    THICK = (1.75, (0xFF, 0xFF, 0xFF))
    THIN = (0.75, (0xFF, 0xFF, 0xFF))
    thick_cols = {c0 for _, c0, _ in groups}
    merge_cont = {c0 + k for _, c0, span in groups for k in range(1, span)}
    if TABLE_BORDERS != "none":
        for r in range(nrows):
            for c in range(ncols):
                if r == 0 and c in merge_cont:
                    lnl = None
                elif c in thick_cols:
                    lnl = THICK
                elif TABLE_BORDERS == "full" or c == 0:
                    lnl = THIN
                else:
                    lnl = None
                set_borders(tbl.cell(r, c), l=lnl,
                            r=THIN if c == ncols - 1 else None,
                            t=THIN if r == 0 else None,
                            b=THIN)
    # configuration banner (moved here from the experiments slide)
    card(s, 0.62, 6.45, 12.06, 0.72)
    tb = box(s, 0.95, 6.56, 11.4, 0.5)
    para(tb.text_frame, "Configurație: Java 21 + Graph4J, Gurobi pentru ILP, limită 15 min pe instanță, GA și ACO rulate de câte 5 ori", 14, BODY, first=True, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "Varianta detaliata a tabelului, cu minim, medie si maxim pe 5 rulari, plus timpii de rulare. De remarcat consistenta: pe toate instantele pana la 494bus, GA si ACO gasesc optimul in absolut fiecare rulare. Si viteza: ACO rezolva 494bus in 2,5 secunde, fata de 17 secunde pentru CEC si aproape 10 minute pentru GA. NOTA: acest slide si cel anterior arata aceleasi rezultate in doua formate; pastreaza in prezentarea finala varianta preferata si muta cealalta la backup. (1 min)")

    # ============================================================ 18. RESULTS STORY
    s = add_slide()
    title_bar(s, "Unde câștigă fiecare algoritm")
    pic(s, os.path.join(DARK, "rezultate_mari.png"), 1.87, 1.15, w=9.6)
    chips = [
        ("Grafuri mici (≤ 662 vârfuri)", "Metodele exacte demonstrează optimul; ACO îl atinge de zeci de ori mai repede (494bus: 2,5 s față de 16,6 s, iar GA are nevoie de 562 s).", BLUE),
        ("Grafuri mari și rare", "ACO domină: yeast 395, cel mai bun rezultat cunoscut. Feromonul direcțional funcționează excelent pe grafuri rare.", AQUA),
        ("Grafuri mari și dense", "GA domină: gradul mare al vârfurilor diluează semnalul feromonului, dar încrucișarea pe populație face față.", VIOLET),
    ]
    for i, (t, d, col) in enumerate(chips):
        x = 0.62 + i * 4.12
        card(s, x, 5.15, 3.9, 1.9)
        bar = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(x), Inches(5.15), Pt(4), Inches(1.9))
        bar.fill.solid(); bar.fill.fore_color.rgb = col; bar.line.fill.background(); bar.shadow.inherit = False
        tb = box(s, x + 0.22, 5.3, 3.55, 1.65)
        para(tb.text_frame, t, 14.5, INK, bold=True, first=True, space_after=4)
        para(tb.text_frame, d, 11.8, BODY, space_after=0)
    notes(s, "Concluzia experimentala intr-o imagine. Pe grafurile mici, pana in 662 de varfuri, metodele exacte raman alegerea corecta pentru ca demonstreaza optimul, dar ACO atinge aceleasi valori de zeci de ori mai repede. Pe grafurile mari si rare, ca yeast, domina ACO. Pe grafurile mari si dense, ca instanta generata, domina GA, pentru ca gradul mare dilueaza semnalul feromonului, in timp ce incrucisarea pe populatie face fata. Cele doua metaeuristici sunt deci complementare. (1 min)")

    # ============================================================ 19. DEMO
    s = add_slide()
    title_bar(s, "Demo interactiv", "aplicație web dezvoltată pentru această lucrare")
    pic(s, os.path.join(ASSETS, "demo_full.png"), 1.57, 1.35, w=10.2)
    notes(s, "Slide de tranzitie pentru demo-ul live, 1-2 minute, dupa prezentare sau inainte de concluzii, cum permite timpul. Pe tema inchisa, captura demo-ului se contopeste cu fundalul slide-ului. Scenariul repetat: 1) demo-ul e deja deschis in browser cu instanta plantata generata (40 varfuri, drum 20); 2) Run pe tab-ul ACO la viteza 1x, narezi cum creste feromonul verde cateva iteratii; 3) Pauza; 4) bifezi Reveal planted path, iar suprapunerea aurie arata cat de aproape e drumul gasit de cel plantat. Plan B daca ceva nu merge: presentation-assets/demo-backup.mp4 (87 s) contine exact acest scenariu inregistrat.")

    # ============================================================ 20. CONCLUSIONS
    s = add_slide()
    title_bar(s, "Concluzii")
    tb = box(s, 0.62, 1.4, 12.0, 3.9)
    para(tb.text_frame, "Implementările existente (ILP, backtracking) nu scalează: peste câteva sute de vârfuri devin impracticabile.", 17, BODY, first=True, bullet=True, space_after=12)
    para(tb.text_frame, "Am propus primele metaeuristici pentru LIP: un ACO cu feromon direcțional și un algoritm genetic. Ambele scalează mult mai bine pe instanțe mari.", 17.5, INK, bold=True, bullet=True, space_after=12)
    para(tb.text_frame, "Cei doi algoritmi sunt complementari: ACO câștigă pe grafuri mari și rare (yeast: 395, cel mai bun rezultat cunoscut), GA pe grafuri mari și dense (395 față de 51 al metodelor exacte).", 17, BODY, bullet=True, space_after=12)
    para(tb.text_frame, "Optim atins pe toate instanțele mici și medii, plus un generator de instanțe cu drum plantat pentru evaluare controlată.", 17, BODY, bullet=True)
    card(s, 0.62, 5.35, 12.06, 1.15)
    tb = box(s, 0.95, 5.5, 11.4, 0.9)
    para(tb.text_frame, "Direcții viitoare", 14, INK, bold=True, first=True, space_after=4)
    para(tb.text_frame, "hibridizare ACO + GA sau pornire din soluții ILP; selecție adaptivă a algoritmului după densitatea grafului; componentă memetică (căutare locală)", 13.5, BODY, space_after=0)
    tb = box(s, 0.62, 6.7, 12.0, 0.6)
    para(tb.text_frame, "Vă mulțumesc! Întrebări?", 18, INK, bold=True, first=True, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "In concluzie: metodele existente nu scaleaza dincolo de cateva sute de varfuri. Lucrarea propune primele metaeuristici pentru aceasta problema, un ACO cu feromon directional si un algoritm genetic, iar amandoua scaleaza mult mai bine pe instante mari. Sunt si complementare: ACO castiga pe grafuri mari si rare, cu cel mai bun rezultat cunoscut pe yeast, iar GA pe grafuri mari si dense. Pe langa algoritmi, lucrarea aduce si un generator de instante cu drum plantat pentru evaluare controlata. Directii viitoare: hibridizare, selectie adaptiva dupa structura grafului si componente memetice. Va multumesc. (1 min)")

    # ============================================================ 21. BACKUP FULL TABLE
    s = add_slide()
    title_bar(s, "Backup: rezultate complete (Tabelul 3.1 din lucrare)")
    full = [
        ("Instanță", "|V|", "|E|", "OPT", "CEC", "CUT", "HLIPP", "GA*", "ACO*"),
        ("high-tech", "33", "91", "13", "13", "13", "13", "13", "13"),
        ("karate", "34", "78", "9", "9", "9", "9", "9", "9"),
        ("mexican", "35", "117", "16", "16", "16", "16", "16", "16"),
        ("sawmill", "36", "62", "18", "18", "18", "18", "18", "18"),
        ("tailorS1", "39", "158", "13", "13", "13", "13", "13", "13"),
        ("chesapeake", "39", "170", "16", "16", "16", "16", "16", "16"),
        ("tailorS2", "39", "223", "15", "15", "15", "15", "15", "15"),
        ("romeo & juliet", "41", "120", "9", "9", "9", "9", "9", "9"),
        ("die hard", "47", "237", "10", "10", "10", "10", "10", "10"),
        ("attiro", "59", "128", "31", "31", "31", "30", "31", "31"),
        ("krebs", "62", "153", "17", "17", "17", "17", "17", "17"),
        ("dolphins", "62", "159", "24", "24", "24", "23", "24", "24"),
        ("prison", "67", "142", "36", "36", "36", "36", "36", "36"),
        ("huck", "69", "297", "9", "9", "9", "9", "9", "9"),
        ("sanjuansur", "75", "144", "38", "38", "38", "36", "38", "38"),
        ("jean", "77", "254", "11", "11", "11", "11", "11", "11"),
        ("david", "87", "406", "19", "19", "19", "19", "19", "19"),
        ("ieeebus", "118", "179", "47", "47", "47", "47", "47", "47"),
        ("sfi", "118", "200", "13", "13", "13", "13", "13", "13"),
        ("anna", "138", "493", "20", "20", "20", "20", "20", "20"),
        ("usair", "332", "2 126", "46", "46", "46", "38", "46", "46"),
        ("494bus", "494", "586", "142", "142", "142", "109", "142", "142"),
        ("662bus", "662", "906", "305", "305", "305", "237", "277", "298"),
        ("yeast", "2 361", "6 646", "?", "142", "184", "204", "300", "387"),
        ("gen-hard-5000", "5 000", "1 310 158", "≥1600", "51", "51", "313", "387", "336"),
    ]
    make_table(s, full, 1.2, 1.2, 10.9, 5.6,
               [2.1, 0.95, 1.35, 1.1, 1.0, 1.0, 1.15, 1.1, 1.15],
               font_size=10, header_size=10.5,
               row_height=(0.3, 0.205))
    tb = box(s, 1.2, 6.95, 10.9, 0.45)
    para(tb.text_frame, "* GA/ACO: media pe 5 rulări (rotunjită), conform Tabelului 3.1 din lucrare.", 11, MUTED, first=True, space_after=0)
    notes(s, "Slide de backup pentru intrebari: rezultatele complete pe toate cele 25 de instante, identice cu Tabelul 3.1 din lucrare (GA si ACO ca medii pe 5 rulari).")

    # ============================================================ 22. BACKUP: ILP FORMULATION
    s = add_slide()
    title_bar(s, "Backup: formulările ILP", "Matsypura et al. 2019: drumul ca o plimbare în timp")
    pic(s, os.path.join(DARK, "ip3.png"), 0.67, 1.35, w=12.0)
    tb = box(s, 0.8, 6.55, 11.8, 0.8)
    para(tb.text_frame, "Formulările ulterioare (Bökler et al. 2020) întăresc modelul, dar numărul de variabile și de restricții crește rapid cu dimensiunea grafului.", 15, BODY, first=True, align=PP_ALIGN.CENTER, space_after=0)
    notes(s, "Slide de backup cu formularea IP3 a lui Matsypura, pentru intrebari despre modelul ILP. Variabila x-i-t este 1 daca varful i este vizitat la pasul t; restrictiile, de sus in jos, urmeaza explicatiile din dreapta.")

    outname = T["outfile"]
    if OUT_SUFFIX:
        outname = outname.replace(".pptx", OUT_SUFFIX + ".pptx")
    out = os.path.join(VARDIR, outname)
    prs.save(out)

    # page numbers
    prs2 = Presentation(out)
    for i, slide in enumerate(prs2.slides, start=1):
        if i == 1:
            continue
        tb = slide.shapes.add_textbox(Inches(12.5), Inches(7.05), Inches(0.7), Inches(0.35))
        p = tb.text_frame.paragraphs[0]
        p.alignment = PP_ALIGN.RIGHT
        r = p.add_run(); r.text = str(i)
        r.font.size = Pt(11); r.font.color.rgb = MUTED; r.font.name = FONT
    prs2.save(out)
    print("saved", out, "slides:", len(prs2.slides._sldIdLst))


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    for key in (THEMES if which == "all" else [which]):
        build(key)
