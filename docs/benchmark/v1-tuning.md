# Fontis benchmark — v1 ranking settings

Retrieval on the 75 answerable questions under each ranking setting (`fontis-bench eval --run v1-tuning tune`), best MRR first. `textWeight` weighs full-text ranks in the fusion (vector ranks weigh 1), `recitalWeight` multiplies the score of recitals, `sumLanguages` adds up a unit's scores across languages.

| Setting | Hit@1 | Hit@5 | MRR@10 | n |
|---|---:|---:|---:|---:|
| `mode=HYBRID,textWeight=0.0,recitalWeight=0.6,sumLanguages=False` | 79% | 84% | 0.815 | 75 |
| `mode=HYBRID,textWeight=1.0,recitalWeight=0.6,sumLanguages=True` | 76% | 81% | 0.784 | 75 |
| `mode=HYBRID,textWeight=0.3,recitalWeight=0.6,sumLanguages=True` | 75% | 80% | 0.776 | 75 |
| `mode=HYBRID,textWeight=0.0,recitalWeight=0.6,sumLanguages=True` | 75% | 80% | 0.774 | 75 |
| `mode=HYBRID,textWeight=0.3,recitalWeight=0.6,sumLanguages=False` | 71% | 83% | 0.761 | 75 |
| `mode=HYBRID,textWeight=0.0,recitalWeight=1.0,sumLanguages=True` | 69% | 79% | 0.735 | 75 |
| `mode=VECTOR` | 68% | 80% | 0.735 | 75 |
| `mode=HYBRID,textWeight=0.0,recitalWeight=1.0,sumLanguages=False` | 68% | 80% | 0.735 | 75 |
| `mode=HYBRID,textWeight=1.0,recitalWeight=1.0,sumLanguages=True` | 68% | 79% | 0.728 | 75 |
| `mode=HYBRID,textWeight=0.3,recitalWeight=1.0,sumLanguages=True` | 67% | 79% | 0.724 | 75 |
| `mode=HYBRID,textWeight=1.0,recitalWeight=0.6,sumLanguages=False` | 53% | 81% | 0.661 | 75 |
| `mode=HYBRID,textWeight=0.3,recitalWeight=1.0,sumLanguages=False` | 55% | 80% | 0.656 | 75 |
| `mode=HYBRID,textWeight=1.0,recitalWeight=1.0,sumLanguages=False` | 40% | 79% | 0.552 | 75 |
