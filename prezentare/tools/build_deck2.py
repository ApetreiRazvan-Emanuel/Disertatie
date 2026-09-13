# -*- coding: utf-8 -*-
"""Dissertation defense deck v2 (Romanian, no em dashes)."""
import os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn

SCRATCH = r"C:\Users\Razvan\AppData\Local\Temp\claude\C--Facultate-Github-Repositories-Disertatie\a9442c31-b9db-4856-8e85-3dd6dd933a26\scratchpad"
FIGS = os.path.join(SCRATCH, "figs")
SHOTS = os.path.join(SCRATCH, "shots")
PDFIMG = os.path.join(SCRATCH, "pdfimg")
OUTDIR = r"C:\Facultate\Github Repositories\Disertatie\prezentare"

INK = RGBColor(0x1B, 0x2A, 0x4A)
BODY = RGBColor(0x33, 0x3F, 0x50)
MUTED = RGBColor(0x6B, 0x72, 0x80)
BLUE = RGBColor(0x2A, 0x78, 0xD6)
AQUA = RGBColor(0x1B, 0xAF, 0x7A)
YELLOW = RGBColor(0xED, 0xA1, 0x00)
VIOLET = RGBColor(0x4A, 0x3A, 0xA7)
CARD = RGBColor(0xF1, 0xF5, 0xF9)
CARD_EDGE = RGBColor(0xDD, 0xE3, 0xEC)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
BANNER = RGBColor(0xFF, 0xF4, 0xE0)
BANNER_EDGE = RGBColor(0xF0, 0xD9, 0xA8)
ZEBRA = RGBColor(0xF6, 0xF8, 0xFB)
WIN_AQUA = RGBColor(0xD8, 0xF3, 0xE8)
WIN_VIOLET = RGBColor(0xE6, 0xE2, 0xF5)

FONT = "Arial"
prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]


def add_slide():
    return prs.slides.add_slide(BLANK)


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


def title_bar(slide, text, subtitle=None):
    tb = box(slide, 0.55, 0.28, 12.2, 0.85)
    para(tb.text_frame, text, 28, INK, bold=True, first=True, space_after=0)
    if subtitle:
        para(tb.text_frame, subtitle, 14, MUTED, space_after=0)
    rule = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.58), Inches(1.06), Inches(1.5), Pt(3.2))
    rule.fill.solid(); rule.fill.fore_color.rgb = BLUE
    rule.line.fill.background(); rule.shadow.inherit = False


def card(slide, x, y, w, h, fill=CARD, edge=CARD_EDGE):
    sh = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
    sh.adjustments[0] = 0.055
    sh.fill.solid(); sh.fill.fore_color.rgb = fill
    sh.line.color.rgb = edge; sh.line.width = Pt(1)
    sh.shadow.inherit = False
    return sh


def pic(slide, path, x, y, w=None, h=None, border=False):
    kw = {}
    if w: kw["width"] = Inches(w)
    if h: kw["height"] = Inches(h)
    p = slide.shapes.add_picture(path, Inches(x), Inches(y), **kw)
    if border:
        p.line.color.rgb = CARD_EDGE
        p.line.width = Pt(1)
    return p


def notes(slide, text):
    slide.notes_slide.notes_text_frame.text = text


def make_table(slide, data, x, y, w, h, col_widths, font_size=13, header_size=13,
               left_col=0, row_height=None, bold_cells=None, fill_cells=None):
    bold_cells = bold_cells or set()
    fill_cells = fill_cells or {}
    tbl = slide.shapes.add_table(len(data), len(data[0]), Inches(x), Inches(y), Inches(w), Inches(h)).table
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
                cell.fill.solid(); cell.fill.fore_color.rgb = INK
                run.font.color.rgb = WHITE; run.font.bold = True
            else:
                cell.fill.solid()
                cell.fill.fore_color.rgb = WHITE if r % 2 else ZEBRA
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
band = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, Inches(0.18))
band.fill.solid(); band.fill.fore_color.rgb = BLUE; band.line.fill.background(); band.shadow.inherit = False
tb = box(s, 1.0, 0.9, 11.3, 0.9)
para(tb.text_frame, "Universitatea Alexandru Ioan Cuza din Iași, Facultatea de Informatică", 15, MUTED, first=True, align=PP_ALIGN.CENTER)
tb = box(s, 1.0, 2.35, 11.3, 1.9)
para(tb.text_frame, "Problema celui mai lung drum indus", 40, INK, bold=True, first=True, align=PP_ALIGN.CENTER, space_after=4)
para(tb.text_frame, "The Longest Induced Path Problem", 20, MUTED, italic=True, align=PP_ALIGN.CENTER, space_after=0)
rule = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(5.92), Inches(4.28), Inches(1.5), Pt(3.2))
rule.fill.solid(); rule.fill.fore_color.rgb = BLUE; rule.line.fill.background(); rule.shadow.inherit = False
tb = box(s, 1.0, 4.75, 11.3, 1.7)
para(tb.text_frame, "Apetrei Răzvan-Emanuel", 20, INK, bold=True, first=True, align=PP_ALIGN.CENTER, space_after=2)
para(tb.text_frame, "Coordonator științific: Lect. dr. Cristian Frăsinaru", 15, BODY, align=PP_ALIGN.CENTER, space_after=2)
para(tb.text_frame, "Sesiunea iulie 2026", 13, MUTED, align=PP_ALIGN.CENTER, space_after=0)
notes(s, "Buna ziua. Ma numesc Razvan Apetrei si astazi va prezint lucrarea mea de disertatie despre problema celui mai lung drum indus, realizata sub indrumarea domnului lector Cristian Frasinaru. (20 s)")

# ============================================================ 2. DEFINITION
s = add_slide()
title_bar(s, "Ce este un drum indus?")
pic(s, os.path.join(FIGS, "def_indus.png"), 1.37, 1.2, w=10.6)
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

# ============================================================ 4. SOTA: ILP
s = add_slide()
title_bar(s, "Stadiul actual: formulările ILP", "Matsypura et al. 2019: drumul ca o plimbare în timp")
pic(s, os.path.join(FIGS, "ip3.png"), 2.07, 1.3, w=9.2)
tb = box(s, 0.8, 6.15, 11.8, 1.0)
para(tb.text_frame, "Formulările ulterioare (Bökler et al. 2020) întăresc modelul, dar numărul de variabile și de restricții crește rapid cu dimensiunea grafului.", 15, BODY, first=True, align=PP_ALIGN.CENTER, space_after=0)
notes(s, "Prima familie de metode exacte: programarea liniara intreaga. Ideea formularilor lui Matsypura: modelam drumul ca o plimbare in timp. Variabila x-i-t este 1 daca varful i este vizitat la pasul t. Maximizam numarul de varfuri vizitate. Restrictiile, pe rand: la fiecare pas vizitam cel mult un varf; fiecare varf apare cel mult o data; doi pasi consecutivi trebuie sa foloseasca o muchie existenta; iar restrictia cheie, cea rosie: un vecin al unui varf de pe drum poate aparea doar imediat dupa el, altfel s-ar crea o scurtatura. Aceste modele se dau unui solver comercial. Problema: numarul de variabile si restrictii creste rapid. (1,5 min)")

# ============================================================ 5. SOTA: B&B
s = add_slide()
title_bar(s, "Stadiul actual: branch and bound cu tăieturi", "Marzo et al. 2022: formulările CEC și CUT")
tb = box(s, 0.62, 1.5, 5.9, 5.2)
para(tb.text_frame, "Variabile binare aleg vârfurile și muchiile; restricțiile de grad forțează forma de drum.", 15.5, BODY, first=True, bullet=True, space_after=10)
para(tb.text_frame, "Relaxarea LP (fără cerința de întregi) dă o margine superioară pentru fiecare ramură.", 15.5, BODY, bullet=True, space_after=10)
para(tb.text_frame, "Ramificăm pe câte o variabilă; ramurile care nu pot depăși cea mai bună soluție găsită se taie.", 15.5, BODY, bullet=True, space_after=10)
para(tb.text_frame, "Restricțiile de eliminare a ciclurilor sunt exponențial de multe, deci se adaugă doar când o soluție le încalcă (branch and cut).", 15.5, BODY, bullet=True, space_after=10)
para(tb.text_frame, "CEC folosește restricții de ciclu, CUT restricții de conectivitate (tăieturi).", 15.5, INK, bold=True, bullet=True)
pic(s, os.path.join(FIGS, "bnb.png"), 6.7, 1.55, w=6.3)
notes(s, "A doua generatie de metode exacte, si cea mai puternica: branch and bound cu taieturi, formularile CEC si CUT ale lui Marzo. Aici variabilele aleg direct varfurile si muchiile drumului. Cum functioneaza: rezolvam relaxarea liniara, fara cerinta de numere intregi, si obtinem o margine superioara. Apoi ramificam pe o variabila: o data cu varful inclus, o data fara el. Daca marginea unei ramuri e sub cea mai buna solutie gasita, ramura se taie complet. Iar restrictiile care interzic ciclurile, fiind exponential de multe, nu se pun de la inceput: se adauga doar cand solutia curenta chiar contine un ciclu. De aceea se numeste branch and cut. (1,5 min)")

# ============================================================ 6. SOTA: BACKTRACKING
s = add_slide()
title_bar(s, "Stadiul actual: backtracking", "Marzo și Ribeiro 2021: enumerare exactă și euristica HLIPP")
tb = box(s, 0.62, 1.4, 6.2, 4.7)
para(tb.text_frame, "Enumerare exactă: construim recursiv toate drumurile induse maximale, pornind din fiecare vârf al grafului.", 15.5, BODY, first=True, bullet=True, space_after=10)
para(tb.text_frame, "La fiecare pas, vecinii capătului cu un singur vecin pe drum sunt extensii valide (verde); toți ceilalți vecini ai drumului devin interziși (roșu).", 15.5, BODY, bullet=True, space_after=10)
para(tb.text_frame, "Când nu mai putem extinde, ne întoarcem (backtracking) și încercăm altă ramură.", 15.5, BODY, bullet=True, space_after=10)
para(tb.text_frame, "Găsește sigur optimul, dar numărul de drumuri explodează combinatorial.", 15.5, BODY, bullet=True, space_after=10)
para(tb.text_frame, "HLIPP (euristică): abandonează un vârf de start după 10 000 de drumuri fără îmbunătățire.", 15.5, INK, bold=True, bullet=True)
pic(s, os.path.join(SHOTS, "exact_redgreen.png"), 7.15, 1.35, w=5.6, border=True)
tb = box(s, 7.15, 5.9, 5.6, 0.5)
para(tb.text_frame, "captură din demo: albastru = drumul curent, verde = extensii valide, roșu = vârfuri interzise", 11, MUTED, first=True, italic=True, align=PP_ALIGN.CENTER, space_after=0)
card(s, 0.62, 6.5, 12.06, 0.8, fill=BANNER, edge=BANNER_EDGE)
tb = box(s, 0.95, 6.62, 11.4, 0.6)
para(tb.text_frame, "Concluzia: metodele exacte demonstrează optimul, dar devin impracticabile peste câteva sute de vârfuri.", 15.5, INK, bold=True, first=True, align=PP_ALIGN.CENTER, space_after=0)
notes(s, "A treia abordare din literatura: backtracking. Enumeram recursiv toate drumurile induse maximale, pornind din fiecare varf. Imaginea, luata din demo-ul pe care l-am construit, arata exact starea cautarii: drumul curent e albastru, extensiile inca valide sunt verzi, iar rosii sunt varfurile interzise definitiv pentru ca sunt vecine cu drumul. Cand nu mai putem extinde, facem backtracking. Metoda gaseste sigur optimul, dar explodeaza combinatorial. Varianta euristica, HLIPP, taie cautarea: abandoneaza un varf de start dupa 10.000 de drumuri fara imbunatatire. Concluzia intregii sectiuni: metodele exacte demonstreaza optimul, dar peste cateva sute de varfuri devin impracticabile. (1,5 min)")

# ============================================================ 7. WHY HARD FOR METAHEURISTICS
s = add_slide()
title_bar(s, "De ce e greu să construiești o metaeuristică?")
tb = box(s, 0.62, 1.5, 6.1, 5.3)
para(tb.text_frame, "Spațiul de căutare este uriaș, dar foarte puține submulțimi de vârfuri formează drumuri induse valide.", 16.5, BODY, first=True, bullet=True, space_after=12)
para(tb.text_frame, "Fiecare vârf ales interzice definitiv toți ceilalți vecini ai săi; o singură alegere greșită (v4 în loc de v5) blochează extinderea.", 16.5, BODY, bullet=True, space_after=12)
para(tb.text_frame, "Operatorii standard de mutație și încrucișare produc aproape mereu soluții invalide (cu coarde).", 16.5, BODY, bullet=True, space_after=12)
para(tb.text_frame, "Algoritmii se blochează ușor în maxime locale, deci sunt necesare mecanisme dedicate de explorare.", 16.5, INK, bold=True, bullet=True)
pic(s, os.path.join(PDFIMG, "p27_0.png"), 7.0, 1.5, w=5.8, border=True)
notes(s, "Inainte de algoritmii propusi, de ce e greu sa faci o metaeuristica pentru aceasta problema. Spatiul de cautare e urias, dar solutiile valide sunt extrem de rare: aproape orice submultime de varfuri contine o coarda. In figura: daca aleg v4, varfurile v5 si v6 devin interzise si drumul moare; daca alegeam v5, drumul continua prin v7 si v8. O singura decizie greseste tot. De aceea operatorii clasici de mutatie si incrucisare produc aproape mereu solutii invalide, iar algoritmii se blocheaza in maxime locale. Ambii algoritmi pe care ii propun sunt construiti special in jurul acestor probleme. (1 min)")

# ============================================================ 8. ACO HIGH LEVEL
s = add_slide()
title_bar(s, "Algoritmul 1: Ant Colony Optimization", "prima abordare ACO pentru această problemă")
tb = box(s, 0.62, 1.5, 6.3, 5.2)
para(tb.text_frame, "Inspirat din colonii de furnici: fiecare furnică construiește un drum indus, ghidată de feromonul de pe muchii.", 17, BODY, first=True, bullet=True, space_after=12)
para(tb.text_frame, "La fiecare iterație: furnicile explorează, cel mai lung drum câștigă, feromonul se evaporă, apoi se depune pe drumurile bune.", 17, BODY, bullet=True, space_after=12)
para(tb.text_frame, "Feedback pozitiv: muchiile drumurilor bune devin tot mai atractive pentru furnicile următoare.", 17, BODY, bullet=True, space_after=12)
para(tb.text_frame, "Câte o furnică pentru fiecare vârf de start cu grad peste 2, rulate în paralel.", 17, BODY, bullet=True)
pic(s, os.path.join(SHOTS, "aco_pheromone.png"), 7.15, 1.35, w=5.6, border=True)
tb = box(s, 7.15, 5.95, 5.6, 0.6)
para(tb.text_frame, "verde = feromon acumulat, punctele colorate = furnici explorând (demo-ul interactiv)", 11.5, MUTED, first=True, italic=True, align=PP_ALIGN.CENTER, space_after=0)
notes(s, "Primul algoritm propus: optimizare cu colonii de furnici, prima abordare de acest fel pentru problema noastra. Ideea generala: furnicile sunt cautari care construiesc drumuri induse, ghidate de feromonul depus pe muchii. O iteratie are patru faze: furnicile exploreaza, cel mai lung drum castiga, feromonul se evapora putin peste tot, apoi se depune pe drumurile bune. Asa apare feedback-ul pozitiv: muchiile bune devin tot mai atractive. AICI: daca ai inserat clipul video de 30 de secunde de pe Drive, il rulezi acum in locul imaginii statice; altfel descrii imaginea din demo. (1,5 min)")

# ============================================================ 9. ACO COMPONENTS
s = add_slide()
title_bar(s, "ACO: componentele și îmbunătățirile")
tb = box(s, 0.62, 1.5, 5.9, 5.2)
para(tb.text_frame, "Construcția drumului: DFS ghidat de feromoni, cu backtracking (mersul înainte clasic, ca la TSP, se blochează imediat în fundături).", 16, BODY, first=True, bullet=True, space_after=11)
para(tb.text_frame, "Explorare bidirecțională: a doua căutare pornește din capătul celui mai bun drum găsit.", 16, BODY, bullet=True, space_after=11)
para(tb.text_frame, "Feromon direcțional: aceeași muchie poate fi excelentă într-un sens și o capcană în celălalt.", 16, INK, bold=True, bullet=True, space_after=11)
para(tb.text_frame, "Evaporare adaptivă la stagnare, restart cu listă tabu ca să nu recadă în același maxim local, rulare în paralel.", 16, BODY, bullet=True)
pic(s, os.path.join(PDFIMG, "p33_0.png"), 6.75, 1.5, w=6.1, border=True)
notes(s, "Acum componentele. Prima adaptare majora: constructia drumului nu e mersul inainte clasic de la TSP, care s-ar bloca imediat in fundaturi, ci un DFS ghidat de feromoni, cu backtracking. A doua: explorarea bidirectionala, adica a doua cautare porneste din capatul drumului gasit, ca sa creasca si in directia opusa. Contributia cheie e feromonul directional: tau de la v la u este independent de tau de la u la v. In figura: muchia dintre v4 si v5 e o capcana parcursa spre dreapta, fiindca v5 e o fundatura, dar e valoroasa spre stanga, fiindca de acolo drumul continua. Un feromon nedirectional ar amesteca cele doua semnale. Peste toate acestea: evaporare adaptiva la stagnare, restarturi cu lista tabu si rulare in paralel. (1,5 min)")

# ============================================================ 10. GA HIGH LEVEL
s = add_slide()
title_bar(s, "Algoritmul 2: algoritm genetic", "cum funcționează un algoritm genetic")
pic(s, os.path.join(FIGS, "ga_cycle.png"), 0.92, 1.55, w=11.5)
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
pic(s, os.path.join(PDFIMG, "p39_0.png"), 6.55, 1.45, w=6.3, border=True)
notes(s, "Reprezentarea: fiecare individ este un vector ordonat de varfuri care formeaza un drum indus valid, iar fitness-ul este pur si simplu lungimea drumului. Pastrarea ordinii in vector face operatiile la capete foarte rapide, iar toti operatorii sunt construiti astfel incat rezultatul sa ramana mereu un drum indus valid. (40 s)")

# ============================================================ 12. GA MUTATION
s = add_slide()
title_bar(s, "GA: mutația")
pic(s, os.path.join(PDFIMG, "p44_0.png"), 3.24, 1.25, h=5.0, border=True)
tb = box(s, 0.8, 6.45, 11.8, 0.9)
para(tb.text_frame, "Tăiem câteva vârfuri de la ambele capete, apoi re-extindem drumul prin DFS în ambele direcții: o căutare locală în jurul soluției curente.", 15.5, BODY, first=True, align=PP_ALIGN.CENTER, space_after=0)
notes(s, "Mutatia: taiem cateva varfuri de la ambele capete ale drumului, pastram mijlocul, apoi re-extindem prin DFS in ambele directii. Practic este o cautare locala in jurul solutiei curente: pastram ce e bun si incercam alte capete. (30 s)")

# ============================================================ 13. GA CROSSOVER
s = add_slide()
title_bar(s, "GA: încrucișarea")
pic(s, os.path.join(PDFIMG, "p45_0.png"), 2.17, 1.3, w=9.0, border=True)
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
pic(s, os.path.join(PDFIMG, "p47_0.png"), 7.0, 1.7, w=5.7, border=True)
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
pic(s, os.path.join(FIGS, "usair.png"), 4.85, 1.5, w=4.15)
card(s, 9.25, 1.35, 3.45, 4.45)
tb = box(s, 9.5, 1.55, 2.95, 4.1)
para(tb.text_frame, "Generator propriu", 16.5, INK, bold=True, first=True, space_after=4)
para(tb.text_frame, "contribuție a lucrării", 12.5, MUTED, space_after=8)
para(tb.text_frame, "Drum plantat de lungime cunoscută (1 600)", 13.5, BODY, bullet=True)
para(tb.text_frame, "Zgomot organizat în clici, capcane la capete", 13.5, BODY, bullet=True)
para(tb.text_frame, "generated-hard-5000: 5 000 vârfuri, 1,3 mil. muchii", 13.5, INK, bold=True, bullet=True)
card(s, 0.62, 6.05, 12.06, 0.95, fill=CARD, edge=CARD_EDGE)
tb = box(s, 0.95, 6.21, 11.4, 0.7)
para(tb.text_frame, "Java 21 + Graph4J, Gurobi pentru ILP, limită 15 min pe instanță, GA și ACO rulate de câte 5 ori", 15, BODY, first=True, align=PP_ALIGN.CENTER, space_after=0)
notes(s, "Evaluarea: cele 24 de grafuri reale standard din literatura, de la retele sociale la reteaua electrica si reteaua de interactiuni proteice yeast. In mijloc vedeti reteaua usair, transportul aerian din SUA: 332 de aeroporturi. Sunt date reale, cu structura tipica hub-and-spoke. La acestea se adauga o instanta produsa de generatorul propriu, care planteaza un drum indus de lungime cunoscuta si il ingroapa in zgomot organizat in clici, cu capcane la capete. Asa stim o margine inferioara a optimului si putem masura cat de aproape ajunge fiecare algoritm. Metodologia: aceleasi resurse pentru toti, 15 minute pe instanta, iar algoritmii stohastici rulati de cate 5 ori. (1 min)")

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
det = [
    ("Instanță", "OPT", "CEC (t)", "CUT (t)", "HLIPP (t)", "GA min/med/max (t)", "ACO min/med/max (t)"),
    ("karate", "9", "9 (0,1 s)", "9 (0,1 s)", "9 (0,1 s)", "9 / 9 / 9 (10 s)", "9 / 9 / 9 (0,2 s)"),
    ("dolphins", "24", "24 (0,2 s)", "24 (0,2 s)", "23 (1,4 s)", "24 / 24 / 24 (28 s)", "24 / 24 / 24 (0,3 s)"),
    ("sanjuansur", "38", "38 (0,2 s)", "38 (0,1 s)", "36 (2,6 s)", "38 / 38 / 38 (40 s)", "38 / 38 / 38 (0,3 s)"),
    ("ieeebus", "47", "47 (0,3 s)", "47 (0,2 s)", "47 (8,2 s)", "47 / 47 / 47 (62 s)", "47 / 47 / 47 (0,3 s)"),
    ("usair", "46", "46 (124 s)", "46 (104 s)", "38 (51 s)", "45 / 45,8 / 46 (243 s)", "46 / 46 / 46 (16 s)"),
    ("494bus", "142", "142 (17 s)", "142 (89 s)", "109 (221 s)", "142 / 142 / 142 (562 s)", "142 / 142 / 142 (2,5 s)"),
    ("662bus", "305", "305 (127 s)", "305 (811 s)", "237 (265 s)", "268 / 277 / 283 (900 s)", "287 / 297,6 / 302 (900 s)"),
    ("yeast", "?", "142 (900 s)", "184 (900 s)", "204 (900 s)", "279 / 300,2 / 328 (900 s)", "384 / 387,4 / 395 (900 s)"),
    ("generated-hard-5000", "≥ 1 600", "51 (900 s)", "51 (900 s)", "313 (900 s)", "375 / 386,8 / 395 (900 s)", "334 / 336,2 / 339 (900 s)"),
]
make_table(s, det, 0.42, 1.35, 12.5, 5.5,
           [2.15, 0.85, 1.35, 1.35, 1.4, 2.7, 2.7],
           font_size=11, header_size=11.5,
           row_height=(0.5, 0.5),
           fill_cells={(8, 6): WIN_AQUA, (9, 5): WIN_VIOLET})
notes(s, "Varianta detaliata a tabelului, cu minim, medie si maxim pe 5 rulari, plus timpii de rulare. De remarcat consistenta: pe toate instantele pana la 494bus, GA si ACO gasesc optimul in absolut fiecare rulare. Si viteza: ACO rezolva 494bus in 2,5 secunde, fata de 17 secunde pentru CEC si aproape 10 minute pentru GA. NOTA: acest slide si cel anterior arata aceleasi rezultate in doua formate; pastreaza in prezentarea finala varianta preferata si muta cealalta la backup. (1 min)")

# ============================================================ 18. RESULTS STORY
s = add_slide()
title_bar(s, "Unde câștigă fiecare algoritm")
pic(s, os.path.join(FIGS, "rezultate_mari.png"), 1.87, 1.15, w=9.6)
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
pic(s, os.path.join(SHOTS, "demo_full.png"), 1.57, 1.35, w=10.2, border=True)
notes(s, "Slide de tranzitie pentru demo-ul live, 1-2 minute, dupa prezentare sau inainte de concluzii, cum permite timpul. Scenariul repetat: 1) demo-ul e deja deschis in browser cu instanta plantata generata (40 varfuri, drum 20); 2) Run pe tab-ul ACO la viteza 1x, narezi cum creste feromonul verde cateva iteratii; 3) Pauza; 4) bifezi Reveal planted path, iar suprapunerea aurie arata cat de aproape e drumul gasit de cel plantat. Plan B daca ceva nu merge: presentation-assets/demo-backup.mp4 (87 s) contine exact acest scenariu inregistrat.")

# ============================================================ 20. CONCLUSIONS
s = add_slide()
title_bar(s, "Concluzii")
tb = box(s, 0.62, 1.4, 12.0, 3.9)
para(tb.text_frame, "Implementările existente (ILP, backtracking) nu scalează: peste câteva sute de vârfuri devin impracticabile.", 17, BODY, first=True, bullet=True, space_after=12)
para(tb.text_frame, "Am propus primele metaeuristici pentru LIP: un ACO cu feromon direcțional și un algoritm genetic. Ambele scalează mult mai bine pe instanțe mari.", 17.5, INK, bold=True, bullet=True, space_after=12)
para(tb.text_frame, "Cei doi algoritmi sunt complementari: ACO câștigă pe grafuri mari și rare (yeast: 395, cel mai bun rezultat cunoscut), GA pe grafuri mari și dense (395 față de 51 al metodelor exacte).", 17, BODY, bullet=True, space_after=12)
para(tb.text_frame, "Optim atins pe toate instanțele mici și medii, plus un generator de instanțe cu drum plantat pentru evaluare controlată.", 17, BODY, bullet=True)
card(s, 0.62, 5.35, 12.06, 1.15, fill=CARD, edge=CARD_EDGE)
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

out = os.path.join(OUTDIR, "Prezentare_Disertatie_LIP.pptx")
prs.save(out)

# page numbers
from pptx import Presentation as P2
prs2 = P2(out)
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
