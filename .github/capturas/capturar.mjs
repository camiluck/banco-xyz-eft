// Toma capturas de las pantallas web que se muestran en el video.
import { chromium } from 'playwright';
const out = process.argv[2] || 'capturas';
const repo = 'https://github.com/camiluck/banco-xyz-eft';
const paginas = [
  ['web-eureka', 'http://localhost:8761', 3000, null],
  ['web-kafka-topics', 'http://localhost:8085/ui/clusters/banco-xyz/all-topics', 5000, null],
  ['web-kafka-mensajes', 'http://localhost:8085/ui/clusters/banco-xyz/all-topics/transacciones-completadas/messages', 7000, null],
  ['web-github-readme', repo, 4000, null],
  ['web-github-diagrama', repo, 6000, 'Arquitectura'],
  ['web-github-equivalencia', repo, 4000, 'Equivalencia con el legacy'],
  ['web-github-comparacion', repo, 4000, 'Comparación con el sistema legacy'],
  ['web-github-fechaparser', repo + '/blob/main/batch-service/src/main/java/cl/bancoxyz/batch/comun/FechaParser.java', 5000, null],
  ['web-github-pagoservice', repo + '/blob/main/ms-pagos/src/main/java/cl/bancoxyz/pagos/servicio/PagoService.java', 5000, 'public Pago ejecutar'],
  ['web-github-actions', repo + '/actions', 5000, null],
  ['web-github-despliegue', repo + '/blob/main/despliegue.md', 5000, null],
];
const b = await chromium.launch();
const pg = await b.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1.5, locale: 'es-CL' });
for (const [nombre, url, espera, texto] of paginas) {
  try {
    await pg.goto(url, { waitUntil: 'domcontentloaded', timeout: 45000 });
    await pg.waitForTimeout(espera);
    if (texto) {
      const el = pg.getByText(texto, { exact: false }).first();
      await el.scrollIntoViewIfNeeded({ timeout: 5000 }).catch(() => {});
      await pg.evaluate(() => window.scrollBy(0, -80));
      await pg.waitForTimeout(1500);
    }
    await pg.screenshot({ path: `${out}/${nombre}.png` });
    console.log('ok', nombre);
  } catch (e) { console.log('ERROR', nombre, e.message); }
}
await b.close();
