from pathlib import Path
import collections
import csv
import hashlib
import json
import math
import os
import sqlite3
import sys
import tempfile

sys.path.insert(0, str(Path(tempfile.gettempdir()) / "university-rag-analysis-libs"))
sys.path.insert(0, str(Path.home() / "AppData/Local/Temp/university-rag-analysis-libs"))
os.environ.setdefault("MPLCONFIGDIR", str(Path(tempfile.gettempdir()) / "university-rag-matplotlib"))
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
RUN = ROOT / "build/libs/reports/run-2026-11-13-01-00-46"
OLD = OUT.parent / "rag-analysis-2026-10-28"
FIRST = OUT.parent / "rag-analysis-2026-10-27"
SEED = 20261113
QIDS = [1, 2, 3, 4, 9, 10, 11, 12]
IIDS = [1, 2, 3, 4, 5, 6]
INAMES = {1: "Fixed 1000", 2: "Recursive 900 / overlap 100", 3: "Fixed 500", 4: "Fixed 2000", 5: "Recursive 900 / overlap 0", 6: "Recursive 1000 / overlap 0"}
QNAMES = {1: "Hybrid", 2: "Dense", 3: "Lexical", 4: "Hybrid + 2 rewrite", 9: "Hybrid RRF 10", 10: "Hybrid RRF 30", 11: "Hybrid 50 кандидатов", 12: "Hybrid + 1 rewrite"}
QSHORT = {1: "Hybrid", 2: "Dense", 3: "Lexical", 4: "Hybrid\n+2 rewrite", 9: "Hybrid\nRRF 10", 10: "Hybrid\nRRF 30", 11: "Hybrid\n50 кандидатов", 12: "Hybrid\n+1 rewrite"}
checksums = {}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    path = Path(path)
    checksums[path.relative_to(ROOT).as_posix()] = sha(path)
    return json.loads(path.read_bytes())


def read_csv(path):
    checksums[path.relative_to(ROOT).as_posix()] = sha(path)
    with path.open(encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def write_json(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_csv(name, rows):
    rows = list(rows)
    if not rows:
        return
    with (OUT / name).open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def mean(values):
    return float(np.mean(list(values)))


report = read_json(RUN / "report.json")
compare = read_json(RUN / "compare.json")
profiles = {}
datasets = {}
for file in sorted((ROOT / "core/src/main/resources/configurations/indexing").glob("*.json")):
    d = read_json(file)
    profiles[d["id"]] = d
for file in sorted((ROOT / "@data/examples/dataset").glob("*.json")):
    d = read_json(file)
    datasets[d["id"]] = d
    assert d["queryProfileIds"] == QIDS
    assert d["indexProfileIds"] == IIDS
query_profiles = {}
for file in sorted((ROOT / "core/src/main/resources/configurations/query").glob("*.json")):
    d = read_json(file)
    query_profiles[d["id"]] = d
assert all(p["embeddingModel"] == "qwen3-embedding:8b" for p in profiles.values())
assert all(p["parameters"]["contextCharBudget"] == "5500" for p in query_profiles.values())
assert report["queryProfiles"] == [query_profiles[i] for i in QIDS]
assert report["status"] == "COMPLETED"
assert len(report["questions"]) == 1440

db_path = ROOT / "build/libs/.data/university.db"
db = sqlite3.connect(db_path.resolve().as_uri() + "?mode=ro", uri=True)
db.execute("PRAGMA query_only=ON")
checksums[db_path.relative_to(ROOT).as_posix()] = sha(db_path)
db_chunks = collections.defaultdict(dict)
for cfg, cid, content in db.execute("SELECT indexConfigurationId,chunkId,content FROM chunk"):
    db_chunks[cfg][cid] = content
configuration = {r[0]: r[1:] for r in db.execute('SELECT id,hash,documentHash,status FROM indexing_configuration')}
index_lookup = {}
index_rows = []
sources = {}
chunk_spans = {}
for entry in report["indexing"]:
    did, iid, cfg = entry["datasetId"], entry["indexProfileId"], entry["indexConfigurationId"]
    index_lookup[did, iid] = cfg
    assert entry["status"] == "COMPLETED" and entry["failedFiles"] == 0
    log = read_json(ROOT / "build/libs" / entry["operationLog"])
    source_name = Path(datasets[did]["file"]).name
    source_path = ROOT / "build/libs/books" / source_name
    raw = source_path.read_bytes()
    checksums[source_path.relative_to(ROOT).as_posix()] = sha(source_path)
    assert raw == (ROOT / "@data/examples/books" / source_name).read_bytes()
    source = raw.decode("utf-8")
    assert len(source) == len(source.encode("utf-16-le")) // 2
    sources[did] = source
    cfg_hash, doc_hash, status = configuration[cfg]
    matching = [p for p in (ROOT / "core/src/main/resources/configurations/indexing").glob("*.json") if json.loads(p.read_bytes())["id"] == iid]
    assert cfg_hash == sha(matching[0])
    assert doc_hash == hashlib.sha256(raw).hexdigest()
    assert entry["inputTokens"] == log["inputTokens"]
    spans = {}
    previous = -1
    lengths = []
    for cid, content in sorted(db_chunks[cfg].items()):
        position = source.find(content, previous + 1)
        assert position >= 0, (cfg, cid)
        spans[cid] = (position, position + len(content))
        previous = position
        lengths.append(len(content))
    chunk_spans[cfg] = spans
    vectors, vector_bytes = db.execute('SELECT count(*),sum(length(vector)) FROM vector WHERE indexConfigurationId=?', (cfg,)).fetchone()
    assert vectors == len(lengths)
    index_rows.append(dict(dataset_id=did, index_id=iid, configuration_id=cfg, chunks=vectors, input_tokens=entry["inputTokens"], vector_bytes=vector_bytes, mean_chunk_chars=mean(lengths), indexed_text_chars=sum(lengths), source_chars=len(source)))
assert len(index_lookup) == 18

evidence = read_json(FIRST / "experiment-evidence.json")
grouping = read_json(OLD / "evidence-grouping.json")
historical_manifest = read_json(OLD / "provenance.json")
for directory in ["indexing", "query"]:
    for file in (ROOT / "core/src/main/resources/configurations" / directory).glob("*.json"):
        historical_name = "src/main/resources/configurations/" + directory + "/" + file.name
        assert historical_manifest["checksums"][historical_name] == sha(file)
evidence_by_question = {}
for dataset in evidence["datasets"]:
    did = dataset["datasetId"]
    assert hashlib.sha256(sources[did].encode("utf-8")).hexdigest() == dataset["sourceSha256"]
    for question in dataset["questions"]:
        for span in question["evidence"]:
            assert sources[did][span["start"]:span["end"]] == span["quote"]
        evidence_by_question[did, question["questionId"]] = question["evidence"]


def coverage(spans, target):
    left, right = target["start"], target["end"]
    intersections = sorted((max(a, left), min(b, right)) for a, b in spans if min(b, right) > max(a, left))
    total = 0
    end = left
    for a, b in intersections:
        total += max(0, b - max(a, end))
        end = max(end, b)
    return total / (right - left)


rows = []
raw_logs = {}
by_question = {}
for did, dataset in datasets.items():
    for q in dataset["questions"]:
        by_question[did, q["id"]] = q
for item in report["questions"]:
    for field in ["inputTokens", "outputTokens"]:
        item.setdefault(field, 0)
    for field in ["recallAt3", "recallAt5", "reciprocalRank", "ndcgAt3", "ndcgAt5"]:
        item["retrieval"].setdefault(field, None)
    did, qid, iid, query = item["datasetId"], item["questionId"], item["indexProfileId"], item["queryProfileId"]
    assert item["status"] == "COMPLETED"
    assert not item.get("error") and not item["judge"].get("error")
    evaluation = item["judge"]["evaluation"]
    log = read_json(ROOT / "build/libs" / item["operationLog"])
    for field in ["inputTokens", "outputTokens", "totalTokens"]:
        log.setdefault(field, 0)
    key = did, qid, iid, query
    assert key not in raw_logs
    raw_logs[key] = log
    assert log["inputTokens"] == item["inputTokens"] and log["outputTokens"] == item["outputTokens"]
    assert log["totalTokens"] == item["inputTokens"] + item["outputTokens"]
    assert log["answer"] == item["actualAnswer"] and log["query"] == item["question"]
    assert log["queryProfileId"] == query
    cfg = index_lookup[did, iid]
    assert log["scope"] == f"index:{cfg}"
    chunks = log["chunks"]
    assert [c["chunkId"] for c in chunks] == [c["chunkId"] for c in item["retrieval"]["retrievedChunks"]]
    for chunk in chunks:
        assert chunk["indexId"] == cfg
        assert db_chunks[cfg][chunk["chunkId"]] == chunk["content"]
    gold_question = by_question[did, qid]
    assert item["question"] == gold_question["question"] and item["expectedAnswer"] == gold_question["expectedAnswer"]
    gold = gold_question["goldChunksByIndexProfile"][str(iid)]
    selected_ids = {c["chunkId"] for c in chunks}
    gold_ids = {c["chunkId"] for c in gold}
    spans = [chunk_spans[cfg][c["chunkId"]] for c in chunks]
    curated = evidence_by_question[did, qid]
    fractions = [coverage(spans, span) for span in curated]
    groups = grouping["overrides"].get(qid, [[j] for j in range(len(curated))])
    group_covered = [max(fractions[j] for j in group) >= 0.8 for group in groups]
    row = dict(dataset_id=did, question_id=qid, index_id=iid, query_id=query, verdict=evaluation["verdict"], complete=int(evaluation["verdict"] == "complete"), incorrect=int(evaluation["verdict"] == "incorrect"), query_tokens=log["totalTokens"], input_tokens=item["inputTokens"], output_tokens=item["outputTokens"], judge_tokens=item["judge"].get("inputTokens", 0) + item["judge"].get("outputTokens", 0), context_chars=item["retrieval"]["contextUsedChars"], chunks=len(chunks), text_chars=sum(len(c["content"]) for c in chunks), recall_at5=item["retrieval"]["recallAt5"], gold_hit=int(bool(selected_ids & gold_ids)) if gold_ids else None, all_gold=int(gold_ids <= selected_ids) if gold_ids else None, evidence_groups=len(groups), evidence_coverage=mean(group_covered) if groups else None, all_evidence=int(all(group_covered)) if groups else None, evidence_min_fraction=min(fractions) if fractions else None, tags=";".join(item["tags"]), actual_answer=item["actualAnswer"], expected_answer=item["expectedAnswer"], question=item["question"], judge_reason=evaluation["reason"], chunk_ids=" ".join(str(c["chunkId"]) for c in chunks), operation_log=item["operationLog"])
    rows.append(row)
assert len({(r["dataset_id"], r["question_id"]) for r in rows}) == 30
assert collections.Counter(r["verdict"] for r in rows) == {"complete": 894, "not_complete": 207, "incorrect": 339}

grouped = collections.defaultdict(list)
for row in rows:
    grouped[row["index_id"], row["query_id"]].append(row)
metrics = []
for (iid, qid), subset in sorted(grouped.items()):
    counter = collections.Counter(r["verdict"] for r in subset)
    assert len(subset) == 30
    metrics.append(dict(index_id=iid, query_id=qid, index=INAMES[iid], query=QNAMES[qid], n=len(subset), complete=counter["complete"], not_complete=counter["not_complete"], incorrect=counter["incorrect"], complete_rate=counter["complete"] / len(subset), mean_query_tokens=mean(r["query_tokens"] for r in subset), mean_input_tokens=mean(r["input_tokens"] for r in subset), mean_output_tokens=mean(r["output_tokens"] for r in subset), mean_judge_tokens=mean(r["judge_tokens"] for r in subset), mean_context_chars=mean(r["context_chars"] for r in subset), mean_text_chars=mean(r["text_chars"] for r in subset), mean_chunks=mean(r["chunks"] for r in subset), mean_recall_at5=mean(r["recall_at5"] for r in subset if r["recall_at5"] is not None), mean_evidence_coverage=mean(r["evidence_coverage"] for r in subset if r["evidence_coverage"] is not None), tokens_per_complete=sum(r["query_tokens"] for r in subset) / counter["complete"] if counter["complete"] else None))
metric_lookup = {(m["index_id"], m["query_id"]): m for m in metrics}
for item in compare["profileCombinations"]:
    m = metric_lookup[item["indexProfileId"], item["queryProfileId"]]
    for source, target in [("completeAnswers", "complete"), ("incompleteAnswers", "not_complete"), ("incorrectAnswers", "incorrect")]:
        assert item[source] == m[target]
    assert abs(item["meanContextUsedChars"] - m["mean_context_chars"]) < 1e-8
    assert abs(item["meanRecallAt5"] - m["mean_recall_at5"]) < 1e-8
for m in metrics:
    m["pareto"] = not any(other["complete"] >= m["complete"] and other["mean_query_tokens"] <= m["mean_query_tokens"] and (other["complete"] > m["complete"] or other["mean_query_tokens"] < m["mean_query_tokens"]) for other in metrics)

question_keys = sorted({(r["dataset_id"], r["question_id"]) for r in rows})
row_lookup = {(r["dataset_id"], r["question_id"], r["index_id"], r["query_id"]): r for r in rows}
rng = np.random.default_rng(SEED)
book_keys = {did: [j for j, key in enumerate(question_keys) if key[0] == did] for did in sorted(datasets)}
bootstrap = np.concatenate([rng.choice(keys, size=(10000, len(keys)), replace=True) for keys in book_keys.values()], axis=1)
signs = rng.choice([-1, 1], size=(20000, 30))
contrasts = []


def contrast(axis, a, b, label):
    differences = []
    token_differences = []
    wins = losses = ties = 0
    for did, question in question_keys:
        pairs = [(row_lookup[did, question, iid, a], row_lookup[did, question, iid, b]) for iid in IIDS] if axis == "query" else [(row_lookup[did, question, a, qid], row_lookup[did, question, b, qid]) for qid in QIDS]
        differences.append(mean(y["complete"] - x["complete"] for x, y in pairs))
        token_differences.append(mean(y["query_tokens"] - x["query_tokens"] for x, y in pairs))
        for x, y in pairs:
            delta = y["complete"] - x["complete"]
            wins += delta > 0
            losses += delta < 0
            ties += delta == 0
    d = np.array(differences)
    td = np.array(token_differences)
    lo, hi = np.quantile(d[bootstrap].mean(axis=1), [0.025, 0.975])
    p = (1 + np.count_nonzero(np.abs((signs * d).mean(axis=1)) >= abs(d.mean()) - 1e-12)) / (len(signs) + 1)
    contrasts.append(dict(axis=axis, from_id=a, to_id=b, contrast=label, question_clusters=30, paired_rows=wins + losses + ties, complete_delta_pp=float(d.mean() * 100), ci_low_pp=float(lo * 100), ci_high_pp=float(hi * 100), mean_token_delta=float(td.mean()), wins=int(wins), losses=int(losses), ties=int(ties), permutation_p=float(p)))


for a, b, label in [(1, 2, "Hybrid → Dense"), (1, 3, "Hybrid → Lexical"), (1, 9, "RRF 60 → 10"), (1, 10, "RRF 60 → 30"), (1, 11, "20 → 50 кандидатов"), (1, 12, "Без rewrite → 1 rewrite"), (1, 4, "Без rewrite → 2 rewrite"), (12, 4, "1 rewrite → 2 rewrite")]:
    contrast("query", a, b, label)
for a, b, label in [(1, 3, "Fixed 1000 → 500"), (1, 4, "Fixed 1000 → 2000"), (1, 6, "Fixed → Recursive 1000"), (5, 2, "Overlap 0 → 100 (target 900)"), (5, 6, "Recursive target 900 → 1000")]:
    contrast("index", a, b, label)
order = sorted(range(len(contrasts)), key=lambda j: contrasts[j]["permutation_p"])
maximum = 0
for rank, j in enumerate(order):
    maximum = max(maximum, (len(contrasts) - rank) * contrasts[j]["permutation_p"])
    contrasts[j]["holm_p"] = min(1.0, maximum)

disagreement_groups = collections.defaultdict(list)
for r in rows:
    disagreement_groups[r["dataset_id"], r["question_id"], r["expected_answer"], r["actual_answer"]].append(r)
disagreements = []
for (did, qid, expected, answer), items in disagreement_groups.items():
    counts = collections.Counter(r["verdict"] for r in items)
    if len(counts) > 1:
        disagreements.append(dict(dataset_id=did, question_id=qid, actual_answer=answer, reference=expected, n=len(items), complete=counts["complete"], not_complete=counts["not_complete"], incorrect=counts["incorrect"], profiles=";".join(f'I{r["index_id"]}/Q{r["query_id"]}' for r in items)))
disagreements.sort(key=lambda r: -r["n"])

history = []
history_question = []
for day, folder in [("2026-10-27", FIRST), ("2026-10-28", OLD)]:
    prior = read_csv(folder / "profile-metrics.csv")
    for p in prior:
        iid, qid = int(p.get("index_id", p.get("index_profile_id"))), int(p.get("query_id", p.get("query_profile_id")))
        if (iid, qid) not in metric_lookup:
            continue
        m = metric_lookup[iid, qid]
        history.append(dict(previous_analysis=day, index_id=iid, query_id=qid, previous_complete=int(p["complete"]), current_complete=m["complete"], delta_complete=m["complete"] - int(p["complete"]), previous_mean_tokens=float(p.get("mean_query_tokens", p.get("mean_reported_query_tokens"))), current_mean_tokens=m["mean_query_tokens"]))
    previous_questions = read_csv(folder / "question-diagnostics.csv")
    for p in previous_questions:
        iid = int(p.get("index_id", p.get("index_profile_id")))
        qid = int(p.get("query_id", p.get("query_profile_id")))
        key = int(p["dataset_id"]), p["question_id"], iid, qid
        if key not in row_lookup:
            continue
        now = row_lookup[key]
        history_question.append(dict(previous_analysis=day, dataset_id=key[0], question_id=key[1], index_id=iid, query_id=qid, previous_verdict=p["verdict"], current_verdict=now["verdict"], verdict_changed=int(p["verdict"] != now["verdict"]), exact_same_answer=int(p["actual_answer"] == now["actual_answer"]), same_reference=int(p["expected_answer"] == now["expected_answer"])))

question_summary = []
for did, qid in question_keys:
    subset = [r for r in rows if (r["dataset_id"], r["question_id"]) == (did, qid)]
    counts = collections.Counter(r["verdict"] for r in subset)
    question_summary.append(dict(dataset_id=did, question_id=qid, tags=subset[0]["tags"], question=subset[0]["question"], complete=counts["complete"], not_complete=counts["not_complete"], incorrect=counts["incorrect"], n=len(subset), mean_tokens=mean(r["query_tokens"] for r in subset), mean_evidence_coverage=mean(r["evidence_coverage"] for r in subset if r["evidence_coverage"] is not None) if any(r["evidence_coverage"] is not None for r in subset) else None))

tag_rows = []
for tag in sorted({tag for r in rows for tag in r["tags"].split(";")}):
    subset = [r for r in rows if tag in r["tags"].split(";")]
    counts = collections.Counter(r["verdict"] for r in subset)
    tag_rows.append(dict(tag=tag, unique_questions=len({(r["dataset_id"], r["question_id"]) for r in subset}), n=len(subset), complete=counts["complete"], not_complete=counts["not_complete"], incorrect=counts["incorrect"], complete_rate=counts["complete"] / len(subset)))

index_summary = []
for iid in IIDS:
    subset = [r for r in rows if r["index_id"] == iid]
    ind = [r for r in index_rows if r["index_id"] == iid]
    index_summary.append(dict(index_id=iid, index=INAMES[iid], complete=sum(r["complete"] for r in subset), n=len(subset), mean_tokens=mean(r["query_tokens"] for r in subset), mean_context_chars=mean(r["context_chars"] for r in subset), mean_text_chars=mean(r["text_chars"] for r in subset), mean_chunks=mean(r["chunks"] for r in subset), indexing_tokens=sum(r["input_tokens"] for r in ind), chunks=sum(r["chunks"] for r in ind), indexed_char_ratio=sum(r["indexed_text_chars"] for r in ind) / sum(r["source_chars"] for r in ind)))

query_summary = []
for qid in QIDS:
    subset = [r for r in rows if r["query_id"] == qid]
    query_summary.append(dict(query_id=qid, query=QNAMES[qid], complete=sum(r["complete"] for r in subset), incorrect=sum(r["incorrect"] for r in subset), n=len(subset), mean_tokens=mean(r["query_tokens"] for r in subset)))

summary = dict(run=RUN.relative_to(ROOT).as_posix(), evaluations=len(rows), unique_questions=30, counts=dict(collections.Counter(r["verdict"] for r in rows)), query_tokens=sum(r["query_tokens"] for r in rows), judge_tokens=sum(r["judge_tokens"] for r in rows), indexing_tokens=sum(r["input_tokens"] for r in index_rows), judge_conflicting_groups=len(disagreements), records_in_conflicting_groups=sum(r["n"] for r in disagreements), seed=SEED, bootstrap_replicates=10000, sign_flip_replicates=20000, metrics=metrics, contrasts=contrasts, index_summary=index_summary, query_summary=query_summary, tag_summary=tag_rows)
write_json("metrics.json", summary)
write_csv("profile-metrics.csv", metrics)
write_csv("paired-contrasts.csv", contrasts)
write_csv("question-diagnostics.csv", rows)
write_csv("question-summary.csv", question_summary)
write_csv("judge-disagreements.csv", disagreements)
write_csv("history-matched.csv", history)
write_csv("history-question-changes.csv", history_question)
write_csv("indexing-cost.csv", index_rows)
write_csv("index-summary.csv", index_summary)
write_csv("query-summary.csv", query_summary)
write_csv("tag-summary.csv", tag_rows)

plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 10, "axes.titlesize": 13, "axes.spines.top": False, "axes.spines.right": False, "figure.facecolor": "#f5f7fb", "axes.facecolor": "white", "savefig.facecolor": "#f5f7fb"})
images = []


def save(fig, name):
    fig.savefig(OUT / (name + ".png"), dpi=180, bbox_inches="tight")
    fig.savefig(OUT / (name + ".svg"), bbox_inches="tight")
    plt.close(fig)
    images.append(name)


fig, axes = plt.subplots(2, 1, figsize=(13, 10), layout="constrained")
quality = np.array([[metric_lookup[i, q]["complete"] for q in QIDS] for i in IIDS])
cost = np.array([[metric_lookup[i, q]["mean_query_tokens"] for q in QIDS] for i in IIDS])
for ax, values, title, cmap in [(axes[0], quality, "Полные ответы: число из 30", "YlGnBu"), (axes[1], cost, "Сообщённые токены на запрос: среднее, без судьи", "YlOrRd")]:
    image = ax.imshow(values, cmap=cmap, aspect="auto")
    ax.set_xticks(range(8), [f"Q{q}\n{QSHORT[q]}" for q in QIDS], fontsize=9)
    ax.set_yticks(range(6), [f"I{i}  {INAMES[i]}" for i in IIDS])
    ax.set_title(title, loc="left", pad=14)
    for y in range(6):
        for x in range(8):
            ax.text(x, y, f"{values[y,x]:.0f}", ha="center", va="center", color="white" if values[y,x] > (values.max() + values.min()) / 2 else "#172033", fontweight="bold")
    fig.colorbar(image, ax=ax, shrink=0.75)
fig.suptitle("Один лимит 5500 символов — разное качество и расход токенов", fontsize=16, fontweight="bold")
save(fig, "quality-token-matrix")

fig, ax = plt.subplots(figsize=(12, 7), layout="constrained")
colors = plt.get_cmap("tab10")
markers = ["o", "s", "^", "D", "P", "X"]
for m in metrics:
    ax.scatter(m["mean_query_tokens"], m["complete_rate"] * 100, s=65 if not m["pareto"] else 140, color=colors(QIDS.index(m["query_id"])), marker=markers[m["index_id"] - 1], edgecolors="#172033" if m["pareto"] else "white", linewidth=1.4, zorder=3)
front = sorted([m for m in metrics if m["pareto"]], key=lambda m: m["mean_query_tokens"])
ax.plot([m["mean_query_tokens"] for m in front], [m["complete_rate"] * 100 for m in front], "--", color="#50627a", alpha=0.6)
for j, m in enumerate(front):
    ax.annotate(f'I{m["index_id"]}/Q{m["query_id"]}', (m["mean_query_tokens"], m["complete_rate"] * 100), xytext=(7, 10 if j % 2 else -17), textcoords="offset points", fontweight="bold", fontsize=9)
ax.set(xlabel="Среднее сообщённых токенов на запрос (без судьи)", ylabel="Полные ответы, %", title="Наблюдаемый компромисс качества и токенного бюджета")
ax.grid(alpha=0.2)
query_legend = ax.legend(handles=[Line2D([], [], color=colors(j), marker="o", linestyle="", label=f"Q{q}: {QNAMES[q]}") for j, q in enumerate(QIDS)], loc="center left", bbox_to_anchor=(1, 0.65), fontsize=9)
ax.add_artist(query_legend)
ax.legend(handles=[Line2D([], [], color="#66758c", marker=markers[i - 1], linestyle="", label=f"I{i}: {INAMES[i]}") for i in IIDS], loc="center left", bbox_to_anchor=(1, 0.15), fontsize=8)
save(fig, "quality-tokens-pareto")

fig, ax = plt.subplots(figsize=(12, 8), layout="constrained")
for j, c in enumerate(contrasts):
    color = "#16826a" if c["holm_p"] < 0.05 else "#74849d"
    ax.errorbar(c["complete_delta_pp"], j, xerr=[[max(0, c["complete_delta_pp"] - c["ci_low_pp"])], [max(0, c["ci_high_pp"] - c["complete_delta_pp"])]], fmt="o", color=color, capsize=4)
    ax.text(1.01, j, f'ΔT {c["mean_token_delta"]:+.0f}; pH={c["holm_p"]:.3f}', transform=ax.get_yaxis_transform(), va="center", fontsize=9)
ax.axvline(0, color="#243249", linestyle="--", linewidth=1)
ax.set_yticks(range(len(contrasts)), [c["contrast"] for c in contrasts])
ax.invert_yaxis()
ax.set(xlabel="Изменение доли полных ответов, процентные пункты", title="Парные сравнения: 30 вопросных кластеров, 95% bootstrap-интервалы")
ax.grid(axis="x", alpha=0.2)
fig.text(0.02, -0.02, "Интервалы без поправки; pH — Holm по 13 исследовательским сравнениям. ΔT — токены на запрос.", fontsize=9)
save(fig, "paired-contrasts")

old28 = [h for h in history if h["previous_analysis"] == "2026-10-28"]
fig, axes = plt.subplots(1, 2, figsize=(13, 6), layout="constrained")
prior_quality = np.array([[next(h for h in old28 if h["index_id"] == i and h["query_id"] == q)["previous_complete"] for q in QIDS] for i in IIDS])
delta = quality - prior_quality
image = axes[0].imshow(delta, cmap="RdYlGn", vmin=-max(1, abs(delta).max()), vmax=max(1, abs(delta).max()), aspect="auto")
axes[0].set_xticks(range(8), [f"Q{q}" for q in QIDS])
axes[0].set_yticks(range(6), [f"I{i}" for i in IIDS])
axes[0].set_title("Изменение complete из 30\n13 ноября против анализа 28 октября")
for y in range(6):
    for x in range(8):
        axes[0].text(x, y, f"{delta[y,x]:+.0f}", ha="center", va="center", fontweight="bold")
totals = [sum(h["previous_complete"] for h in old28), sum(m["complete"] for m in metrics)]
axes[1].bar(["Октябрь: те же\n48 комбинаций", "Ноябрь: те же\n48 комбинаций"], [t / 1440 * 100 for t in totals], color=["#8394af", "#197b95"], width=0.55)
for j, total in enumerate(totals):
    axes[1].text(j, total / 1440 * 100 + 1, f"{total}/1440\n{total/1440:.1%}", ha="center", fontweight="bold")
axes[1].set(ylim=(0, 85), ylabel="Полные ответы, %", title="Описательный повтор, не причинный эффект")
save(fig, "history-matched")

fig, axes = plt.subplots(1, 2, figsize=(13, 6), layout="constrained")
positive = [r for r in rows if r["all_evidence"] is not None]
bins = [("Ни одной полной\nгруппы", [r for r in positive if r["evidence_coverage"] == 0]), ("Часть групп", [r for r in positive if 0 < r["evidence_coverage"] < 1]), ("Все группы", [r for r in positive if r["evidence_coverage"] == 1])]
bottom = np.zeros(3)
for verdict, label, color in [("complete", "Полный", "#1d9477"), ("not_complete", "Неполный", "#edb04b"), ("incorrect", "Неверный", "#cf5864")]:
    heights = [sum(r["verdict"] == verdict for r in subset) / len(subset) * 100 if subset else 0 for _, subset in bins]
    axes[0].bar(range(3), heights, bottom=bottom, color=color, label=label)
    bottom += heights
axes[0].set_xticks(range(3), [f"{name}\nn={len(subset)}" for name, subset in bins])
axes[0].set(ylabel="Доля автоматических вердиктов, %", title="Диагностическое покрытие доказательств")
axes[0].legend(loc="upper center", bbox_to_anchor=(0.5, -0.16), ncol=3)
major = [r for r in tag_rows if r["tag"] in ["exact-term", "paraphrase", "multi-fact", "multi-hop", "unanswerable"]]
axes[1].barh([f'{r["tag"]}\n{r["unique_questions"]} вопросов' for r in major], [r["complete_rate"] * 100 for r in major], color="#327d9f")
axes[1].set(xlim=(0, 100), xlabel="Полные ответы, %", title="Категории повторно используют одни вопросы")
for j, r in enumerate(major):
    axes[1].text(r["complete_rate"] * 100 + 1, j, f'{r["complete"]}/{r["n"]}', va="center", fontsize=9)
save(fig, "evidence-and-question-types")

fig, axes = plt.subplots(1, 2, figsize=(13, 6), layout="constrained")
total = summary["query_tokens"] + summary["judge_tokens"] + summary["indexing_tokens"]
labels = ["Индексация", "Запросы (логический учёт)", "Судья"]
values = [summary["indexing_tokens"], summary["query_tokens"], summary["judge_tokens"]]
axes[0].barh(labels, [v / 1e6 for v in values], color=["#7e94b4", "#247d98", "#b090bd"])
for j, v in enumerate(values):
    axes[0].text(v / 1e6 + 0.03, j, f"{v/1e6:.3f} млн", va="center")
axes[0].set(xlabel="Сообщённые токены, млн", title="Расход исследовательского запуска по стадиям")
axes[0].set_xlim(0, max(values) / 1e6 * 1.3)
axes[1].bar([f'I{r["index_id"]}' for r in index_summary], [r["mean_context_chars"] for r in index_summary], color="#4389a1", label="Контекст с оформлением")
axes[1].bar([f'I{r["index_id"]}' for r in index_summary], [r["mean_text_chars"] for r in index_summary], color="#92c6c2", label="Только текст чанков")
axes[1].axhline(5500, color="#ad5660", linestyle="--", label="Общий предел 5500")
axes[1].set(ylabel="Символы, среднее по 240 запросам", title="Одинаковый лимит ≠ одинаковый контекст")
axes[1].legend(fontsize=8, loc="lower left")
save(fig, "token-accounting")

thumbs = []
for name in images:
    with Image.open(OUT / (name + ".png")) as img:
        img = img.convert("RGB")
        img.thumbnail((760, 510))
        tile = Image.new("RGB", (790, 550), "white")
        tile.paste(img, ((790 - img.width) // 2, 24))
        ImageDraw.Draw(tile).text((15, 530), name, fill="black")
        thumbs.append(tile)
sheet = Image.new("RGB", (1580, math.ceil(len(thumbs) / 2) * 550), "#e9eef5")
for j, thumb in enumerate(thumbs):
    sheet.paste(thumb, ((j % 2) * 790, (j // 2) * 550))
sheet.save(OUT / "figure-preview.png")

for path in [ROOT / "rag/src/main/kotlin/university/cli/service/retrieval/QueryExpansionService.kt", ROOT / "rag/src/main/kotlin/university/cli/service/retrieval/ContextSelectionService.kt", ROOT / "rag/src/main/kotlin/university/cli/service/retrieval/ContextFormatter.kt", ROOT / "rag/src/main/kotlin/university/cli/service/retrieval/SearchService.kt", ROOT / "evaluation/src/main/kotlin/university/cli/service/evaluation/JudgeService.kt", ROOT / "llm-ollama/src/main/kotlin/university/cli/service/llm/OllamaService.kt", ROOT / "core/src/main/resources/prompts/answer_evaluation.txt"]:
    checksums[path.relative_to(ROOT).as_posix()] = sha(path)
write_json("provenance.json", dict(source_report=RUN.relative_to(ROOT).as_posix(), started_at=report["startedAt"], completed_at=report["completedAt"], analysis_date="2026-11-13", checksums=checksums, seed=SEED, checks=["1440 report records and all operation logs validated", "C/N/I match compare.json", "18 index hashes and source hashes match current inputs", "All logged chunks match read-only SQLite contents", "All curated evidence quotes match source offsets", "Only this research directory was written by the analysis script"], limitations=["Automatic judge verdicts, no human calibration", "30 reused development questions in three fixed books", "Logical token ledger repeats cached rewrite usage; physical call cost is not fully identifiable", "Historical comparisons use saved CSV artifacts, not reruns", "Evidence groups are heuristic source-span diagnostics, not human-validated semantic truth"]))
db.close()
print(json.dumps({k: v for k, v in summary.items() if k not in ["metrics", "contrasts", "index_summary", "query_summary", "tag_summary"]}, ensure_ascii=False))
print("Pareto profiles:", ", ".join(f'I{m["index_id"]}/Q{m["query_id"]}' for m in metrics if m["pareto"]))
print("All input, integrity and comparison checks passed; generated six PNG/SVG figures and supporting tables.")
