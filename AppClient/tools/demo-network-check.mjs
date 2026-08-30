/**
 * The acceptance gate for the demo build: load it in a real browser, click
 * through every route, switch all three roles and all three languages, and
 * assert that not a single request to /api/ was made.
 *
 * "No XHR in the network tab" is the one property the whole demo build exists
 * for, and it is the property most easily lost by a later change - one new
 * component calling HttpClient against an endpoint the DemoBackend does not
 * know, and the demo starts talking to a server that is not there. Checking it
 * by hand before each release is exactly the kind of check that gets skipped.
 *
 *   npm run build:demo && npm run check:demo
 */
import puppeteer from 'puppeteer';

const PORT = process.env.DEMO_PORT ?? '8099';
const BASE = `http://127.0.0.1:${PORT}/accessible-job-manager/`;
const CHROME = process.env.CHROME_BIN ?? undefined;

const browser = await puppeteer.launch({
  ...(CHROME ? { executablePath: CHROME } : {}),
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});
const page = await browser.newPage();

const requests = [];
page.on('request', r => requests.push({ method: r.method(), url: r.url() }));
const consoleErrors = [];
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
page.on('pageerror', e => consoleErrors.push('pageerror: ' + e.message));

async function visit(hash, label) {
  await page.goto(BASE + hash, { waitUntil: 'networkidle0' });
  await new Promise(r => setTimeout(r, 700));
  const h1 = await page.$eval('h1', el => el.textContent.trim()).catch(() => '(kein h1)');
  console.log(`  ${label.padEnd(28)} h1: ${h1}`);
}

console.log('--- Routen durchklicken ---');
await visit('#/', 'Dashboard (Bewerberin)');
await visit('#/companies', 'Unternehmen');
await visit('#/applications', 'Bewerbungen');
await visit('#/documents', 'Dokumente');
await visit('#/cover-letter-template', 'Anschreiben-Vorlage');
await visit('#/profile', 'Profil');
await visit('#/preferences', 'Einstellungen');
await visit('#/advisor', 'Advisor-Dashboard');
await visit('#/reviewer', 'Reviewer-Dashboard');

console.log('\n--- Rollenumschalter ---');
await page.goto(BASE, { waitUntil: 'networkidle0' });
await new Promise(r => setTimeout(r, 600));
for (const role of ['ADVISOR', 'REVIEWER', 'USER']) {
  await page.select('#demo-role-select', role);
  await new Promise(r => setTimeout(r, 800));
  const nav = await page.$eval('header nav', el => el.getAttribute('aria-label')).catch(() => '(keine nav)');
  console.log(`  ${role.padEnd(10)} -> URL ${new URL(page.url()).hash || '#/'}  nav: ${nav}`);
}

console.log('\n--- Sprachumschaltung ---');
for (const lang of ['de', 'en', 'nl']) {
  await page.select('#lang-select', lang);
  await new Promise(r => setTimeout(r, 500));
  const htmlLang = await page.$eval('html', el => el.lang);
  const skip = await page.$eval('.skip-link', el => el.textContent.trim());
  console.log(`  ${lang} -> <html lang="${htmlLang}">  Skip-Link: "${skip}"`);
}

await browser.close();

const api = requests.filter(r => r.url.includes('/api/'));
console.log('\n=== ERGEBNIS ===');
console.log(`Netzwerkaufrufe gesamt: ${requests.length}`);
console.log(`Aufrufe auf /api/     : ${api.length}`);
if (api.length) api.forEach(r => console.log(`  !! ${r.method} ${r.url}`));
const nonAsset = requests.filter(r => !/\.(js|css|json|ico|png|pdf|woff2?)(\?|$)/.test(r.url) && r.url !== BASE && !r.url.startsWith(BASE + '#'));
console.log(`Nicht-Asset-Aufrufe   : ${nonAsset.length}`);
nonAsset.forEach(r => console.log(`  ? ${r.method} ${r.url}`));
console.log(`Konsolenfehler        : ${consoleErrors.length}`);
consoleErrors.slice(0, 10).forEach(e => console.log(`  !! ${e}`));

if (api.length || consoleErrors.length) {
  console.error('\nFEHLGESCHLAGEN: die Demo hat den Server angesprochen oder Fehler geworfen.');
  process.exit(1);
}
console.log('\nOK: keine /api/-Aufrufe, keine Konsolenfehler.');
