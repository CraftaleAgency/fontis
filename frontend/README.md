# Fontis web app

Next.js app for asking questions in English, Italian, German, French or Spanish. It talks only to
its own `/api/*` routes, which are forwarded to the Spring Boot backend (`FONTIS_API_URL`, default
`http://localhost:8080`).

```bash
npm install
npm run dev                                   # http://localhost:3000 → redirects to your browser language
npm run build && npm start                    # production
FONTIS_WEB_URL=http://localhost:3000 npm run smoke   # end-to-end check against a running stack
```

| Path | Page |
|---|---|
| `/{en,it,de,fr,es}` | Ask a question; answers with clickable citations and EUR-Lex links |
| `/{locale}/log` | Answer log: recent questions, outcomes, cited sources, model |

UI texts are in `lib/i18n.ts`; backend types and helpers in `lib/api.ts`.

The smoke test (`e2e/smoke.mjs`) uses the installed Chrome through `playwright-core`: it checks the
AI disclosure, asks an example question and waits for a cited answer, checks that an off-topic
question is refused and that both appear in the answer log.
