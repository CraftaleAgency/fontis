# Benchmark v2 — ranking settings

Retrieval (`/api/search`) for the 120 answerable questions under each ranking setting. The default
(hybrid, full-text weight 0, recitals × 0.6, one score per unit) is tied with vector search.

| Setting | Hit@1 | Hit@5 | MRR@10 | n |
|---|---:|---:|---:|---:|
| `mode=VECTOR` | 78% | 89% | 0.825 | 120 |
| `mode=HYBRID,textWeight=0.0,recitalWeight=0.6,sumLanguages=False` | 78% | 89% | 0.825 | 120 |
| `mode=HYBRID,textWeight=1.0,recitalWeight=0.6,sumLanguages=True` | 77% | 84% | 0.807 | 120 |
| `mode=HYBRID,textWeight=0.3,recitalWeight=0.6,sumLanguages=True` | 77% | 83% | 0.805 | 120 |
| `mode=HYBRID,textWeight=0.0,recitalWeight=0.6,sumLanguages=True` | 77% | 83% | 0.803 | 120 |
| `mode=HYBRID,textWeight=0.3,recitalWeight=0.6,sumLanguages=False` | 70% | 88% | 0.774 | 120 |
| `mode=HYBRID,textWeight=0.0,recitalWeight=1.0,sumLanguages=True` | 68% | 82% | 0.754 | 120 |
| `mode=HYBRID,textWeight=0.0,recitalWeight=1.0,sumLanguages=False` | 68% | 83% | 0.747 | 120 |
| `mode=HYBRID,textWeight=1.0,recitalWeight=1.0,sumLanguages=True` | 67% | 82% | 0.746 | 120 |
| `mode=HYBRID,textWeight=0.3,recitalWeight=1.0,sumLanguages=True` | 66% | 82% | 0.743 | 120 |
| `mode=HYBRID,textWeight=1.0,recitalWeight=0.6,sumLanguages=False` | 57% | 85% | 0.700 | 120 |
| `mode=HYBRID,textWeight=0.3,recitalWeight=1.0,sumLanguages=False` | 53% | 82% | 0.664 | 120 |
| `mode=HYBRID,textWeight=1.0,recitalWeight=1.0,sumLanguages=False` | 41% | 82% | 0.572 | 120 |
