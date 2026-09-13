# Variante de stil pentru prezentare

Prezentarea originala (tema alba) ramane neatinsa in `prezentare/Prezentare_Disertatie_LIP.pptx`.
Aici sunt doua variante cu tema inchisa, gandite ca fundalul intunecat al demo-ului
(capturi si clipuri video) sa se contopeasca cu slide-ul, fara dreptunghi vizibil.

## Prezentare_LIP_Midnight.pptx
- Fundal plat #0E1421, exact culoarea canvas-ului din demo.
- Accent verde-turcoaz (culoarea butoanelor din demo), text deschis.
- Capturile din demo nu au chenar: se contopesc complet cu fundalul.
- Figurile din lucrare (fundal alb) sunt puse pe carduri albe rotunjite, ca niste pagini.

## Prezentare_LIP_Aurora.pptx
- Fundal gradient inchis, cu straluciri discrete cyan (dreapta sus) si roz (stanga jos),
  aceeasi identitate cyan-roz ca titlul "Longest Induced Path" din demo.
- Linia de sub titluri si banda de pe primul slide sunt gradient cyan-roz.
- In rest identic cu Midnight (acelasi continut, aceleasi 21 de slide-uri).

## Continut
Ambele variante au exact acelasi continut ca prezentarea v2: aceleasi 21 de slide-uri,
aceleasi tabele, aceleasi note de prezentator (cu indicatii de timp), fara em dash-uri.

## Regenerare
```
python tools/make_figs_dark.py      # figurile matplotlib in varianta dark -> assets_dark/
python tools/build_variant.py all   # sau: midnight / aurora
python tools/render_variant.py <deck.pptx> <folder_iesire>   # QA aproximativ (PIL)
```
Imaginile foto (capturi demo, figuri din lucrare) se iau din `../assets/`.

## Import in Google Slides
File -> Import slides pe .pptx. Fundalul solid (Midnight) si imaginea de fundal (Aurora)
supravietuiesc importului. Clipurile video se insereaza manual din Drive, ca si la varianta alba;
pe aceste teme inchise clipul se contopeste cu fundalul slide-ului.
