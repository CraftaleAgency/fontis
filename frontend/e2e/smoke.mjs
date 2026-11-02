// Smoke test of the running stack (web app, backend, models), using the installed Chrome.
// Usage: FONTIS_WEB_URL=http://localhost:3000 FONTIS_ADMIN_EMAIL=... FONTIS_ADMIN_PASSWORD=... node e2e/smoke.mjs [screenshot-dir]
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { chromium } from "playwright-core";

const base = process.env.FONTIS_WEB_URL ?? "http://localhost:3000";
const admin = { email: process.env.FONTIS_ADMIN_EMAIL, password: process.env.FONTIS_ADMIN_PASSWORD };
const alice = { email: `alice.${Date.now()}@example.eu`, password: "alice-password-123" };
const out = process.argv[2];

const browser = await chromium.launch({ channel: "chrome" });
const page = await browser.newPage({ viewport: { width: 1200, height: 900 } });
const check = (ok, message) => {
  if (!ok) throw new Error(message);
  console.log(`✓ ${message}`);
};
const shot = async (name) => out && page.screenshot({ path: `${out}/${name}.png`, fullPage: true });

/** Asks on the Italian page and returns the text of the last answer (or refusal). */
async function askItalian(question) {
  await page.goto(`${base}/it`);
  await page.getByLabel(/Fai una domanda/).fill(question);
  await page.getByRole("button", { name: "Chiedi", exact: true }).click();
  const answer = page.locator("article").last().locator("p, div").filter({ hasNotText: question }).first();
  await page.waitForFunction(() => !document.body.innerText.includes("Cerco nella normativa"), null, { timeout: 180_000 });
  return answer.innerText();
}

async function signIn({ email, password }) {
  await page.goto(`${base}/it/login`);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Accedi" }).click();
  await page.getByRole("button", { name: /Esci/ }).waitFor();
}

async function signOut() {
  await page.getByRole("button", { name: /Esci/ }).click();
  await page.getByRole("link", { name: "Accedi" }).waitFor();
}

// ---------- Anonymous: public legislation, refusals, no log ----------

await page.goto(`${base}/it`);
check(await page.locator('[role="note"]').innerText().then((t) => t.includes("intelligenza artificiale")),
  "AI disclosure shown in Italian");
await page.getByRole("button", { name: /vietati nell'UE/ }).click();
await page.getByText("Generato da IA").waitFor({ timeout: 180_000 });
const sources = page.locator('li[id^="source-"]');
check((await sources.count()) > 0, `answer cites ${await sources.count()} source(s)`);
check(await sources.first().locator("a").getAttribute("href").then((h) => h.includes("eur-lex.europa.eu/legal-content/IT/")),
  "sources link to EUR-Lex in Italian");
await shot("ask-it");

check((await askItalian("Chi ha vinto i Mondiali di calcio del 2006?")).includes("Nessuna fonte"),
  "off-topic question refused");

await page.goto(`${base}/it/log`);
await page.getByText("Solo gli amministratori").waitFor();
check(true, "answer log closed to anonymous visitors");

// ---------- Administrator: a user, a restricted document ----------

await signIn(admin);
await page.goto(`${base}/it/users`);
await page.getByLabel("Email").fill(alice.email);
await page.getByLabel("Nome").fill("Alice");
await page.getByLabel("Password").fill(alice.password);
await page.getByRole("textbox", { name: /^Gruppi/ }).fill("legale");
await page.getByRole("button", { name: "Aggiungi un utente" }).click();
await page.getByText(`${alice.email} aggiunto.`).waitFor();
check(true, "administrator added a user in group 'legale'");

const policy = join(mkdtempSync(join(tmpdir(), "fontis-")), "lavoro-agile.txt");
writeFileSync(policy,
  "Regolamento interno sul lavoro agile di Acme Srl.\n" +
  "Ogni dipendente in lavoro agile riceve un'indennità mensile di 40 euro per le spese di connessione.\n" +
  "Il lavoro agile è consentito fino a tre giorni alla settimana.");
await page.goto(`${base}/it/documents`);
await page.locator('input[type="file"]').setInputFiles(policy);
await page.getByLabel("Titolo").fill("Regolamento lavoro agile");
await page.getByRole("textbox", { name: /^Gruppi/ }).fill("legale");
await page.getByRole("button", { name: "Carica un documento" }).click();
await page.getByText(/«Regolamento lavoro agile» aggiunto/).waitFor({ timeout: 120_000 });
check(true, "administrator uploaded a document restricted to 'legale'");
await shot("documents-it");

const question = "A quanto ammonta l'indennità mensile per il lavoro agile?";
check((await askItalian(question)).includes("40"), "administrator gets the answer from the document");
await shot("ask-document-it");
await signOut();

// ---------- Permissions ----------

check(!(await askItalian(question)).includes("40"), "anonymous visitor does not see the restricted document");

await signIn(alice);
check((await askItalian(question)).includes("40"), "member of 'legale' gets the answer from the document");
await signOut();

// ---------- Clean up, so the test can run again ----------

await signIn(admin);
await page.goto(`${base}/it/documents`);
const row = page.locator("li").filter({ hasText: "Regolamento lavoro agile" });
await row.getByRole("button", { name: "Elimina" }).click();
await row.getByRole("button", { name: "Conferma eliminazione" }).click();
await row.waitFor({ state: "detached" });
await page.goto(`${base}/it/users`);
const user = page.locator("li").filter({ hasText: alice.email });
await user.getByRole("button", { name: "Elimina" }).click();
await user.waitFor({ state: "detached" });
check(true, "test document and user deleted");

await browser.close();
