// Smoke test of the running app (frontend on :3000, backend and models up), using the installed Chrome.
// Usage: node e2e/smoke.mjs [screenshot-dir]
import { chromium } from "playwright-core";

const base = process.env.FONTIS_WEB_URL ?? "http://localhost:3000";
const out = process.argv[2];
const browser = await chromium.launch({ channel: "chrome" });
const page = await browser.newPage({ viewport: { width: 1200, height: 900 } });
const check = (ok, message) => {
  if (!ok) throw new Error(message);
  console.log(`✓ ${message}`);
};

await page.goto(`${base}/it`);
check(await page.locator('[role="note"]').innerText().then((t) => t.includes("intelligenza artificiale")),
  "AI disclosure shown in Italian");

// Ask an example question and wait for the cited answer (the model may need to load first).
await page.getByRole("button", { name: /vietati nell'UE/ }).click();
await page.getByText("Generato da IA").waitFor({ timeout: 180_000 });
const sources = page.locator('li[id^="source-"]');
check((await sources.count()) > 0, `answer cites ${await sources.count()} source(s)`);
check(await sources.first().locator("a").getAttribute("href").then((h) => h.includes("eur-lex.europa.eu/legal-content/IT/")),
  "sources link to EUR-Lex in Italian");
if (out) await page.screenshot({ path: `${out}/ask-it.png`, fullPage: true });

// An off-topic question is refused, not answered.
await page.getByLabel(/Fai una domanda/).fill("Chi ha vinto i Mondiali di calcio del 2006?");
await page.getByRole("button", { name: "Chiedi", exact: true }).click();
await page.getByText("Nessuna fonte del corpus").waitFor({ timeout: 120_000 });
check(true, "off-topic question refused");
if (out) await page.screenshot({ path: `${out}/refusal-it.png`, fullPage: true });

// Both questions are in the answer log.
await page.goto(`${base}/it/log`);
await page.getByText("Chi ha vinto i Mondiali").first().waitFor();
check(true, "answer log lists the questions");
if (out) await page.screenshot({ path: `${out}/log-it.png`, fullPage: true });

await browser.close();
