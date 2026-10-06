import re, sys, html, asyncio, markdown
from pathlib import Path
from playwright.async_api import async_playwright

BASE = Path(__file__).parent
SRC = Path(sys.argv[1]); OUT = Path(sys.argv[2])
ESTUDIANTE = sys.argv[3] if len(sys.argv) > 3 else "[Nombre del estudiante]"
import os
MODO = os.environ.get("MODO", "informe")
TITULO = "Informe Técnico<br>Modernización del Backend del Banco XYZ" if MODO == "informe" else "Guion del video<br>Modernización del Backend del Banco XYZ"
SUBT = "Evaluación Final Transversal · Desarrollo Backend Avanzado: Spring Cloud y Batch" if MODO == "informe" else "Qué decir, qué mostrar y qué ejecutar en cada momento · 5 a 7 minutos"
PIE = "Informe técnico · Banco XYZ" if MODO == "informe" else "Guion del video · Banco XYZ"

md = SRC.read_text(encoding="utf-8")
# quitar título y nota inicial (van en la portada)
md = re.sub(r"\A# .*?\n", "", md)
md = re.sub(r"\A\s*>.*?\n\n---\n", "", md, flags=re.S)

# bloques mermaid -> placeholders
diagramas = []
def guardar(m):
    diagramas.append(m.group(1))
    return f"\n\nMERMAID_{len(diagramas)-1}\n\n"
md = re.sub(r"```mermaid\n(.*?)```", guardar, md, flags=re.S)

cuerpo = markdown.markdown(md, extensions=["tables", "fenced_code", "sane_lists"])
for i, d in enumerate(diagramas):
    cuerpo = cuerpo.replace(f"<p>MERMAID_{i}</p>", f'<figure class="diag"><pre class="mermaid">{html.escape(d)}</pre></figure>')

# índice + ids
toc = []
def ancla(m):
    n = len(toc)
    texto = re.sub("<.*?>", "", m.group(2))
    toc.append((m.group(1), texto, n))
    return f'<h{m.group(1)} id="s{n}">{m.group(2)}</h{m.group(1)}>'
cuerpo = re.sub(r"<h([23])>(.*?)</h\1>", ancla, cuerpo)
indice = "".join(
    f'<li class="t{lvl}"><a href="#s{n}">{t}</a></li>' for lvl, t, n in toc)

mermaid_js = (BASE / "node_modules/mermaid/dist/mermaid.min.js").read_text(encoding="utf-8")
css = """
@page { size: Letter; margin: 22mm 20mm 20mm 20mm; }
:root { --azul:#16365c; --acento:#2d6cb5; --gris:#5b6472; --borde:#d6dbe3; --fondo:#f3f6fa; }
* { box-sizing: border-box; }
body { font-family: 'Inter', 'DejaVu Sans', sans-serif; font-size: 10.2pt; line-height: 1.5; color:#1d2430; }
h2 { color: var(--azul); font-size: 15pt; margin: 22pt 0 8pt; padding-bottom: 4pt; border-bottom: 2px solid var(--acento); page-break-after: avoid; }
h3 { color: var(--azul); font-size: 11.5pt; margin: 14pt 0 6pt; page-break-after: avoid; }
p { margin: 0 0 7pt; text-align: justify; }
ul, ol { margin: 0 0 8pt; padding-left: 18pt; } li { margin-bottom: 2pt; }
code { font-family: 'DejaVu Sans Mono', monospace; font-size: 8.6pt; background: var(--fondo); padding: 0 3px; border-radius: 3px; color:#0f3d6e; }
table { width: 100%; border-collapse: collapse; margin: 6pt 0 12pt; font-size: 8.7pt; page-break-inside: auto; }
tr { page-break-inside: avoid; }
th { background: var(--azul); color: #fff; text-align: left; padding: 5px 6px; font-weight: 600; }
td { border-bottom: 1px solid var(--borde); padding: 4.5px 6px; vertical-align: top; }
tbody tr:nth-child(even) td { background: var(--fondo); }
figure.diag { margin: 8pt 0 14pt; padding: 10pt; border: 1px solid var(--borde); border-radius: 6px; text-align: center; page-break-inside: avoid; }
figure.diag svg { max-width: 100%; max-height: 600px; height: auto; }
pre.mermaid { margin:0; background:none; }
.portada { height: 230mm; display: flex; flex-direction: column; justify-content: space-between; page-break-after: always; }
.portada .banda { background: var(--azul); color: #fff; padding: 26pt 24pt; border-radius: 8px; }
.portada .inst { font-size: 10pt; letter-spacing: .08em; text-transform: uppercase; opacity: .85; }
.portada h1 { font-size: 25pt; line-height: 1.2; margin: 14pt 0 6pt; }
.portada .sub { font-size: 12.5pt; opacity: .92; }
.portada table.datos { font-size: 10.5pt; width: 70%; }
.portada table.datos td { border: none; padding: 4px 0; background: none !important; }
.portada table.datos td:first-child { color: var(--gris); width: 38%; }
.toc { page-break-after: always; }
.toc ol { list-style: none; padding-left: 0; }
.toc li.t2 { font-weight: 600; margin-top: 6pt; }
.toc li.t3 { padding-left: 16pt; color: var(--gris); font-size: 9.5pt; }
.toc a { color: inherit; text-decoration: none; }
blockquote { page-break-inside: avoid; margin: 6pt 0 10pt; padding: 8pt 12pt; background: #fff8e6; border-left: 4px solid #e0a526; border-radius: 4px; }
blockquote p { margin: 0 0 5pt; } blockquote p:last-child { margin-bottom: 0; }
pre { background: #1f2733; color: #e8edf3; padding: 8pt 10pt; border-radius: 5px; font-size: 8.6pt; white-space: pre-wrap; page-break-inside: avoid; }
pre code { background: none; color: inherit; padding: 0; }
hr { border: none; border-top: 1px dashed var(--borde); margin: 14pt 0; }
"""
fecha = "Octubre 2026"
doc = f"""<!doctype html><html lang="es"><head><meta charset="utf-8"><style>{css}</style></head><body>
<section class="portada">
  <div class="banda">
    <div class="inst">Duoc UC · Desarrollo Backend III (PBY2203)</div>
    <h1>{TITULO}</h1>
    <div class="sub">{SUBT}</div>
  </div>
  <table class="datos">
    <tr><td>Estudiante</td><td>{html.escape(ESTUDIANTE)}</td></tr>
    <tr><td>Asignatura</td><td>Desarrollo Backend III</td></tr>
    <tr><td>Evaluación</td><td>Evaluación Final Transversal (Semana 9)</td></tr>
    <tr><td>Repositorio</td><td>github.com/camiluck/banco-xyz-eft</td></tr>
    <tr><td>Fecha</td><td>{fecha}</td></tr>
  </table>
</section>
{("<section class=\"toc\"><h2 style=\"border:none\">Índice</h2><ol>" + indice + "</ol></section>") if MODO == "informe" else ""}
{cuerpo}
<script>{mermaid_js}</script>
<script>
mermaid.initialize({{ startOnLoad:false, theme:'base', securityLevel:'loose',
  themeVariables:{{ fontFamily:'Inter, DejaVu Sans, sans-serif', fontSize:'16px', primaryColor:'#e8f0fa',
  primaryBorderColor:'#2d6cb5', primaryTextColor:'#16365c', lineColor:'#5b6472', clusterBkg:'#f7f9fc', clusterBorder:'#c3ccd8' }},
  flowchart:{{ htmlLabels:true, curve:'basis' }} }});
mermaid.run({{ querySelector: 'pre.mermaid' }}).then(() => document.body.setAttribute('data-listo','1'))
  .catch(e => {{ document.body.setAttribute('data-listo','error:'+e); }});
</script></body></html>"""
(BASE / "informe.html").write_text(doc, encoding="utf-8")

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page()
        await pg.goto((BASE / "informe.html").as_uri())
        await pg.wait_for_selector("body[data-listo]", timeout=60000)
        estado = await pg.get_attribute("body", "data-listo")
        print("mermaid:", estado)
        await pg.pdf(path=str(OUT), format="Letter", print_background=True, display_header_footer=True,
            header_template="<div></div>",
            footer_template=('<div style="font-size:8px;color:#5b6472;width:100%;padding:0 20mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif"><span>'+PIE+'</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>'),
            margin={"top":"20mm","bottom":"18mm","left":"20mm","right":"20mm"})
        await b.close()
asyncio.run(main())
