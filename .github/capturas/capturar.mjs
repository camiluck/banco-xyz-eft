// Toma capturas de las pantallas web que se muestran en el video.
import { chromium } from 'playwright';
const out = process.argv[2] || 'capturas';
const repo = 'https://github.com/camiluck/banco-xyz-eft';
// [nombre, url, espera ms, texto al que hacer scroll (h2/strong), página completa]
const paginas = [
  ['web-eureka', 'http://localhost:8761', 3000, null, true],
  ['web-kafka-topics', 'http://localhost:8085/ui/clusters/banco-xyz/all-topics', 5000, null, false],
  ['web-kafka-mensajes', 'http://localhost:8085/ui/clusters/banco-xyz/all-topics/transacciones-completadas/messages', 7000, null, false],
  ['web-github-readme', repo, 4000, null, false],
  ['web-github-diagrama', repo, 7000, '3. Arquitectura', false],
  ['web-github-equivalencia', repo, 4000, 'Equivalencia con el legacy', false],
  ['web-github-comparacion', repo, 4000, '5. Comparación con el sistema legacy', false],
  ['web-github-fechaparser', repo + '/blob/main/batch-service/src/main/java/cl/bancoxyz/batch/comun/FechaParser.java#L10-L22', 6000, null, false],
  ['web-github-pagoservice', repo + '/blob/main/ms-pagos/src/main/java/cl/bancoxyz/pagos/servicio/PagoService.java#L95-L120', 6000, null, false],
  ['web-github-pagoservice2', repo + '/blob/main/ms-pagos/src/main/java/cl/bancoxyz/pagos/servicio/PagoService.java#L143-L150', 6000, null, false],
  ['web-github-actions', repo + '/actions/workflows/ci.yml', 5000, null, false],
  ['web-github-despliegue', repo + '/blob/main/despliegue.md', 5000, null, false],
];
const b = await chromium.launch();
const pg = await b.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1.5, locale: 'es-CL' });
for (const [nombre, url, espera, texto, completa] of paginas) {
  try {
    await pg.goto(url, { waitUntil: 'domcontentloaded', timeout: 45000 });
    await pg.waitForTimeout(espera);
    if (texto) {
      await pg.evaluate((t) => {
        const el = [...document.querySelectorAll('article h2, article strong, article h3')].find(e => e.textContent.trim().startsWith(t));
        if (el) { el.scrollIntoView({ block: 'start' }); window.scrollBy(0, -20); }
      }, texto);
      await pg.waitForTimeout(2500);
    }
    await pg.screenshot({ path: `${out}/${nombre}.png`, fullPage: completa });
    console.log('ok', nombre);
  } catch (e) { console.log('ERROR', nombre, e.message); }
}
await b.close();
