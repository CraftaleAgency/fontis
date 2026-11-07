# Deployment

Fontis runs as one Docker Compose stack on a single server, in any EU data centre. Everything is
in [`deploy/`](../deploy).

```
internet ──443──► Caddy ──► frontend (Next.js) ──/api──► backend (Spring Boot) ──► Postgres + pgvector
                                                                 │
                                                                 └──► Ollama (GPU, same or separate server)
```

Only Caddy is published. The backend, Postgres and the actuator port (metrics, health) are reachable
only inside the Docker network; Grafana is bound to `127.0.0.1`.

## 1. Choose where the models run

`qwen3:14b` and `bge-m3` need about 11 GB of GPU memory together (they run on a 12 GB RTX 3060).

| Option | When |
|---|---|
| **GPU server running the whole stack** (`--profile gpu`) | Simplest: one machine. |
| **Separate GPU machine**, e.g. one you already own, reached over a private network or VPN (`OLLAMA_BASE_URL=http://10.0.0.5:11434`) | Keep the GPU on-premises, run the web stack on a small cloud VM. |
| **Ollama on the Docker host** (`OLLAMA_BASE_URL=http://host.docker.internal:11434`) | Ollama already installed outside Docker. |

EU providers with GPU servers or GPU instances include Hetzner (Germany, Finland), Scaleway
(France) and OVHcloud (France): compare current offers, as GPU types and prices change often. The
web stack alone (no GPU) runs comfortably on 2 vCPU and 4 GB of RAM.

## 2. Install

On a fresh Linux server with Docker and the Compose plugin:

```bash
git clone https://github.com/Fbiondo00/fontis.git && cd fontis/deploy
cp .env.example .env && nano .env      # domain, passwords, administrator, OLLAMA_BASE_URL
docker compose up -d --build           # add --profile gpu to run Ollama here
./ingest.sh                            # once: download and index the EU legislation (~7 min on one GPU)
```

Point the domain's DNS `A`/`AAAA` record at the server first: Caddy obtains the HTTPS certificate
on the first request. With the `gpu` profile, pull the models once:

```bash
docker compose exec ollama ollama pull qwen3:14b
docker compose exec ollama ollama pull bge-m3
```

Sign in at `https://<domain>/en/login` with `FONTIS_ADMIN_EMAIL` and `FONTIS_ADMIN_PASSWORD`, then
add users and upload documents.

## 3. Monitoring

```bash
docker compose --profile monitoring up -d
ssh -L 3001:127.0.0.1:3001 <server>    # then open http://localhost:3001
```

Grafana starts with the **Fontis** dashboard provisioned — see [observability](observability.md).

## 4. Operations

| Task | Command |
|---|---|
| Update | `git pull && docker compose up -d --build` (database migrations run on start) |
| Logs | `docker compose logs -f backend` |
| Back up the database | `docker compose exec postgres pg_dump -U fontis fontis \| gzip > fontis-$(date +%F).sql.gz` |
| Add newly downloaded legislation | `./ingest.sh` (only units without embeddings are embedded) |

## Public demo checklist

- `FONTIS_RATE_LIMIT_PER_MINUTE` (default 20 questions per client per minute) protects the GPU;
  clients over the limit get HTTP 429 and a message in their language.
- Use a long, unique `FONTIS_ADMIN_PASSWORD`: there is no password change page yet.
- Uploaded documents are restricted by default: anonymous visitors only search the EU legislation
  and documents explicitly marked public.
- The answer log keeps no IP address and is deleted after `FONTIS_LOG_RETENTION_DAYS`.
