import json, subprocess, pathlib
from diagrams import DIAGRAMS

here = pathlib.Path(__file__).parent
assets = here / "assets"
assets.mkdir(exist_ok=True)

cfg = assets / "mmconfig.json"
cfg.write_text(json.dumps({
    "theme": "base",
    "themeVariables": {
        "primaryColor": "#ede9fe",
        "primaryBorderColor": "#6d28d9",
        "primaryTextColor": "#1f2937",
        "secondaryColor": "#f5f3ff",
        "tertiaryColor": "#faf9ff",
        "lineColor": "#7c3aed",
        "fontFamily": "Apple SD Gothic Neo, Pretendard, sans-serif",
        "fontSize": "16px",
    },
    "flowchart": {"htmlLabels": True, "curve": "basis"},
}), encoding="utf-8")

for name, src in DIAGRAMS.items():
    mmd = assets / f"{name}.mmd"
    mmd.write_text(src, encoding="utf-8")
    png = assets / f"{name}.png"
    r = subprocess.run(
        ["npx", "-y", "@mermaid-js/mermaid-cli",
         "-i", str(mmd), "-o", str(png),
         "-c", str(cfg), "-w", "1600", "-s", "2", "-b", "white"],
        capture_output=True, text=True,
    )
    ok = png.exists() and png.stat().st_size > 0
    print(f"{name}: {'OK' if ok else 'FAIL'} (rc={r.returncode})", flush=True)
    if not ok:
        print(r.stderr[-400:], flush=True)

print("DONE", flush=True)
