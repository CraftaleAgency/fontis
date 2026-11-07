#!/bin/sh
# Downloads the EU legislation, splits it into units and indexes it (once, or to update it).
set -e
cd "$(dirname "$0")"
docker compose --profile jobs run --rm corpus
docker compose --profile jobs run --rm ingest
