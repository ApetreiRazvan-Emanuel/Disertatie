import time, os
from playwright.sync_api import sync_playwright

DEMO = r"C:\Facultate\Github Repositories\Disertatie\demo\index.html"
OUT = r"C:\Users\Razvan\AppData\Local\Temp\claude\C--Facultate-Github-Repositories-Disertatie\a9442c31-b9db-4856-8e85-3dd6dd933a26\scratchpad\shots"
os.makedirs(OUT, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    page = browser.new_page(viewport={"width": 1760, "height": 990}, device_scale_factor=2)
    page.goto("file:///" + DEMO.replace("\\", "/"))
    page.wait_for_timeout(1500)

    # ensure planted instance with defaults, regenerate for a fresh layout
    page.click("#gen-planted") if page.locator("#gen-planted").count() else None
    page.click("#btn-generate")
    page.wait_for_timeout(1200)

    # full app screenshot for the demo slide
    page.screenshot(path=os.path.join(OUT, "demo_full.png"))

    # ---- ACO: run fast to build pheromone, then pause and shoot the canvas
    page.click('button.tab[data-tab="aco"]')
    page.wait_for_timeout(300)
    # max speed
    page.eval_on_selector("#aco-speed", "el => { el.value = el.max; el.dispatchEvent(new Event('input', {bubbles:true})); }")
    # show pheromone values off (cleaner), elitist default
    page.click("#aco-play")
    page.wait_for_timeout(22000)
    page.click("#aco-play")  # pause
    page.wait_for_timeout(800)
    page.locator("#center").screenshot(path=os.path.join(OUT, "aco_pheromone.png"))
    page.screenshot(path=os.path.join(OUT, "aco_full.png"))

    # ---- Exact: step to a mid-search state with red/green
    page.click('button.tab[data-tab="exact"]')
    page.wait_for_timeout(500)
    page.click("#ex-reset")
    page.wait_for_timeout(400)
    for i in range(34):
        page.click("#ex-step")
        page.wait_for_timeout(140)
    page.wait_for_timeout(600)
    page.locator("#center").screenshot(path=os.path.join(OUT, "exact_redgreen.png"))
    page.screenshot(path=os.path.join(OUT, "exact_full.png"))

    # ---- GA: run a few generations, screenshot population + chart
    page.click('button.tab[data-tab="ga"]')
    page.wait_for_timeout(400)
    page.eval_on_selector("#ga-speed", "el => { el.value = el.max; el.dispatchEvent(new Event('input', {bubbles:true})); }")
    page.click("#ga-play")
    page.wait_for_timeout(12000)
    page.click("#ga-play")
    page.wait_for_timeout(600)
    page.screenshot(path=os.path.join(OUT, "ga_full.png"))

    browser.close()
print("done")
