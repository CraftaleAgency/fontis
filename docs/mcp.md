# MCP server

Fontis' retrieval over EU legislation is available to any [MCP](https://modelcontextprotocol.io)
client — Claude, IDE assistants, agents — as three tools. Nothing is generated on the Fontis side:
the client's own model writes the answer from the returned text, with the article to cite.

| Tool | What it returns |
|---|---|
| `search_eu_law(question, lang?, limit?)` | The units that answer the question, best first: citation ("GDPR, Articolo 17 — Diritto alla cancellazione"), EUR-Lex link, date from which it applies, the matching text (3,000 characters, from several chunks) and the language it was found in; `relevant: false` when the closest passage is below the similarity guard (0.45), i.e. the corpus does not cover the question |
| `get_eu_law_unit(act, unitId, lang?)` | The full text of one article, recital or annex (`art_17`, `rct_65`, `anx_III`) with the units it refers to |
| `list_eu_acts()` | The acts, with CELEX number, entry into force, date of application, date of the text and notes |

Only public EU legislation is reachable: uploaded documents are never searched, whoever calls.

## Endpoint

Streamable HTTP at `/mcp` (Spring AI MCP server, `spring.ai.mcp.server` in `application.yaml`). In
the Docker stack, Caddy routes `/mcp` straight to the backend, so the public URL is
`https://<domain>/mcp`. Locally: `http://localhost:8080/mcp`.

```bash
# Claude Code
claude mcp add --transport http fontis https://<domain>/mcp
```

Requests are limited per client (`FONTIS_MCP_RATE_LIMIT_PER_MINUTE`, default 60): every search
embeds the query on the GPU. `FONTIS_MCP_ENABLED=false` switches the server off.

## Not yet verified

The server was written against Spring AI 2.0.1 (`spring-ai-starter-mcp-server-webmvc`, tools
registered through a `ToolCallbackProvider`) and has not been run yet. Check with the MCP Inspector
(`npx @modelcontextprotocol/inspector`) that the three tools are listed and that `search_eu_law`
answers before announcing it.
