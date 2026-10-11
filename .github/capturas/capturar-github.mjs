// Capturas de las secciones del readme en GitHub (sin levantar el sistema).
import { chromium } from 'playwright';
const out = process.argv[2] || 'capturas';
const repo = 'https://github.com/camiluck/banco-xyz-eft';
const secciones = [
  ['web-github-diagrama', 'h2', '3. Arquitectura'],
  ['web-github-equivalencia', 'strong', 'Equivalencia con el legacy'],
  ['web-github-comparacion', 'h2', '5. Comparación con el sistema legacy'],
];
const b = await chromium.launch();
const pg = await b.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1.5, locale: 'es-CL' });
await pg.goto(repo, { waitUntil: 'networkidle', timeout: 60000 });
await pg.waitForTimeout(6000);
for (const [nombre, tag, texto] of secciones) {
  const el = pg.locator(tag, { hasText: texto }).first();
  await el.scrollIntoViewIfNeeded();
  const box = await el.boundingBox();
  if (box) await pg.evaluate((y) => window.scrollBy(0, y - 30), box.y);
  await pg.waitForTimeout(3000);
  await pg.screenshot({ path: `${out}/${nombre}.png` });
  console.log('ok', nombre, box && box.y);
}
await pg.goto(repo + '/actions/workflows/ci.yml', { waitUntil: 'networkidle', timeout: 60000 });
await pg.waitForTimeout(4000);
await pg.screenshot({ path: `${out}/web-github-actions.png` });
await b.close();
