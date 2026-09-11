# RAG Improvement Backlog

This document describes possible improvements to the current RAG pipeline. It is an investigation backlog, not a commitment to implement every item.

The current pipeline is intentionally small:

```text
fixed-size character chunks
    -> document embeddings
    -> cosine similarity top-3
    -> context prompt
    -> structured answer
```

Before adding more stages, each change should be measured against the same evaluation dataset. More complexity is useful only when it improves retrieval or answer quality enough to justify its latency, storage, and maintenance cost.

## 1. Build an evaluation dataset first

### Problem

Without repeatable evaluation, it is impossible to tell whether a change improved retrieval, generation, or only a few hand-picked examples.

### Proposed approach

Create a small versioned dataset containing:

- a question;
- a reference answer;
- the IDs of relevant documents and, where practical, relevant chunks;
- optional tags such as exact-term, multi-hop, summary, and unanswerable.

Evaluate retrieval and generation separately:

- `Recall@K`: whether a relevant chunk appeared in the first K results;
- `MRR`: how early the first relevant result appeared;
- `nDCG@K`: ranking quality when several chunks have different relevance;
- answer completeness and correctness;
- faithfulness: whether answer claims are supported by retrieved context;
- abstention accuracy for questions that cannot be answered from the index;
- latency and number of model calls.

`AnswerEvaluation` can judge the final answer, but it cannot identify whether a failure originated in retrieval or generation. Retrieval needs its own labeled expectations.

Reference: [RAGAS: Automated Evaluation of Retrieval Augmented Generation](https://arxiv.org/abs/2309.15217).

## 2. Token-aware structural chunking

### Problem

`FixedSizeChunkerService` currently splits every 1,000 characters. This can cut through words, sentences, lists, and definitions. There is no overlap, so information spanning a boundary may disappear from both useful embeddings.

### Proposed approach

Add a structural chunker that:

1. splits by headings and paragraphs;
2. splits oversized paragraphs by sentence;
3. packs blocks using a token budget rather than a character count;
4. applies a small overlap at boundaries;
5. never emits blank chunks.

Initial parameters to evaluate:

```text
target size: 300-500 tokens
maximum size: 600-800 tokens
overlap: 50-100 tokens or 10-20%
```

These are starting points, not universal defaults. The best values depend on document structure, embedding model, and question type.

### Trade-offs

- Smaller chunks improve retrieval precision but may lose surrounding evidence.
- Larger chunks preserve context but create less focused embeddings.
- Overlap improves boundary recall but increases index size and duplicate results.
- A tokenizer adds model-specific dependencies and versioning concerns.

## 3. Preserve document structure and metadata

### Problem

The retriever currently works mainly with chunk content and numeric IDs. A chunk may contain phrases such as "this requirement" without identifying its section or subject.

### Proposed metadata

- relative source path;
- document title;
- section and subsection headings;
- chunk ordinal and character/token offsets;
- indexing configuration ID;
- optional document type, date, or tags.

Metadata can be used in three different places:

1. included in the text used to create an embedding;
2. used as retrieval filters;
3. shown to the answer model and user as source information.

Do not blindly prepend every metadata field to embeddings. Compare variants because noisy paths or generated IDs may reduce semantic quality.

## 4. Contextual embeddings or late chunking

### Problem

Embedding every chunk independently removes information from the rest of the document.

### Option A: contextual embeddings

Generate a short chunk-specific description from the whole document and prepend it before embedding:

```text
This section describes admission requirements for master's applicants.
<original chunk>
```

Store the generated context separately from the original chunk so that the answer model can receive clean source text.

### Option B: late chunking

Encode a longer document first and perform pooling for individual chunks afterwards. This preserves document-level context without generating summaries, but it requires embedding-model access below Ollama's simple `/api/embed` abstraction.

### Recommendation

Try inexpensive structural metadata first. Evaluate LLM-generated contextualization only after a baseline exists because it makes indexing slower and nondeterministic.

References:

- [Anthropic Contextual Retrieval](https://www.anthropic.com/engineering/contextual-retrieval)
- [Late Chunking paper](https://arxiv.org/abs/2409.04701)

## 5. Hybrid dense and lexical retrieval

### Problem

Dense embeddings can miss exact identifiers, error codes, class names, abbreviations, and uncommon terminology.

### Why two retrieval mechanisms are needed

Dense and lexical retrieval answer different questions:

- dense retrieval asks whether the question and chunk have a similar meaning;
- lexical retrieval asks whether the chunk contains the same words, phrases, identifiers, or codes.

For example, dense retrieval can connect `documents needed for admission` with `passport, certificate and application must be submitted` even when the wording differs. FTS5 is more reliable for exact values such as `URAG-1042`, `JdbcVectorRepository`, a regulation number, or an uncommon abbreviation.

The intended flow is:

```text
                     +-> query embedding -> sqlite-vec top-N -------+
user question -------+                                            +-> RRF -> candidates -> LLM
                     +-> normalized FTS query -> FTS5/BM25 top-N --+
```

Embedding-model-specific query preprocessing belongs only to the dense branch. The lexical branch receives terms derived from the original user question.

### Indexing

Every chunk needs one shared identity in both indexes:

```text
(indexConfigurationId, chunkId)
```

During indexing, the same chunk is processed twice:

```text
chunk content
    +-> EmbedService.embedDocument() -> vector table
    +-> original text                 -> FTS5 table
```

A minimal FTS5 table could be:

```sql
CREATE VIRTUAL TABLE chunk_fts USING fts5(
    content,
    indexConfigurationId UNINDEXED,
    chunkId UNINDEXED,
    tokenize = 'unicode61'
);
```

`content` is tokenized and indexed. The two `UNINDEXED` columns are stored only to associate a lexical hit with the vector/chunk identity.

Insertion:

```sql
INSERT INTO chunk_fts(content, indexConfigurationId, chunkId)
VALUES (?, ?, ?);
```

Replacement or deletion of an indexing configuration must update both indexes:

```sql
DELETE FROM vector
WHERE indexConfigurationId = ?;

DELETE FROM chunk_fts
WHERE indexConfigurationId = ?;
```

Vector and FTS changes should be committed in one transaction. Otherwise a failed reindex can leave vectors from one document version and lexical data from another.

### Dense branch at query time

The existing semantic branch remains mostly unchanged:

```kotlin
val queryVector = embedService.embedQuery(model, question)
val denseCandidates = vectorService.getTopRelevant(
    configurationId,
    queryVector,
    limit = 20,
)
```

It produces a list ordered by cosine distance, where a smaller distance is better.

### Lexical branch at query time

FTS5 searches the same configuration independently:

```sql
SELECT
    indexConfigurationId,
    chunkId,
    rank
FROM chunk_fts
WHERE chunk_fts MATCH ?
  AND indexConfigurationId = ?
ORDER BY rank
LIMIT ?;
```

SQLite's FTS5 `rank` is an optimized representation of the default `bm25()` ranking. Numerically smaller values represent better matches.

The raw user question should not be passed directly to `MATCH`. Quotes, parentheses, `*`, `-`, and operators such as `AND`, `OR`, and `NOT` have special FTS5 syntax and may change the query or cause a syntax error.

A safe initial query builder should:

1. extract words and identifiers;
2. quote or escape each term as an FTS phrase;
3. join the terms with `OR` so that one missing word does not eliminate a useful result.

Example:

```text
What does error URAG-1042 mean?
```

can become approximately:

```text
"error" OR "URAG" OR "1042"
```

With the default `unicode61` tokenizer, a hyphen normally separates tokens. This is acceptable as a baseline, but technical identifiers should be evaluated separately. Future options include custom token characters, prefix indexes, a normalized identifier column, or the trigram tokenizer. Porter stemming is not a good default for a multilingual corpus.

### Why raw scores must not be added

Cosine distance and BM25 rank have unrelated scales. This is invalid:

```text
combined = cosineDistance + bm25Score
```

A cosine distance such as `0.21` cannot be meaningfully added to a BM25 value such as `-7.4`. Their distributions also change with the embedding model, query, tokenizer, and corpus size. Min-max normalization can be unstable when one branch contains an outlier.

### Reciprocal Rank Fusion

RRF combines positions instead of raw scores:

```text
RRF(chunk) = sum(1 / (k + rank))
```

Using `k = 60`, a chunk ranked second by both branches receives:

```text
1 / (60 + 2) + 1 / (60 + 2) = 0.03226
```

A chunk ranked first by dense retrieval but absent from lexical retrieval receives:

```text
1 / (60 + 1) = 0.01639
```

The result supported by both retrieval methods is therefore promoted without requiring score calibration.

Example:

| Chunk | Dense rank | Lexical rank | RRF score |
|---|---:|---:|---:|
| 12 | 2 | 2 | 0.03226 |
| 47 | 4 | 1 | 0.03202 |
| 15 | 1 | - | 0.01639 |
| 63 | - | 3 | 0.01587 |

Candidates must be deduplicated by `(indexConfigurationId, chunkId)`. If a chunk occurs in only one list, it still receives that branch's contribution.

Initial values to evaluate:

```text
dense candidates: 20
lexical candidates: 20
RRF k: 60
fused candidates: 8
chunks passed to the answer model: 3-5
```

These values are a baseline, not fixed production defaults.

### Suggested service boundaries

Indexing:

```text
DocumentIndexingService
    +-> ChunkerService
    +-> EmbedService
    +-> VectorService
    +-> LexicalIndexService
```

Retrieval:

```text
HybridRetrievalService
    +-> EmbedService
    +-> VectorService
    +-> LexicalSearchService
    +-> RankFusionService
```

Possible concrete classes:

- `JdbcLexicalRepository`: FTS insert, delete, and search SQL;
- `LexicalSearchService`: safe FTS query construction and lexical retrieval;
- `RankFusionService`: score-free RRF combination;
- `HybridRetrievalService`: orchestration of both branches.

Preserve the original cosine distance, BM25 rank, branch positions, and final RRF score in diagnostics. This makes evaluation and relevance debugging much easier.

### Relationship with current JSONL chunk storage

Two implementation paths are possible.

The minimal approach keeps JSONL as the source used to read chunk content and stores another copy in the contentful FTS table. It is simple but duplicates text.

The longer-term approach introduces a normal SQLite `chunk` table as the source of truth:

```sql
CREATE TABLE chunk (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    indexConfigurationId INTEGER NOT NULL,
    chunkId INTEGER NOT NULL,
    content TEXT NOT NULL,
    UNIQUE(indexConfigurationId, chunkId)
);
```

FTS5 can then use `content='chunk'` and `content_rowid='id'`. This avoids storing text twice but requires insert, update, and delete triggers to keep the external-content FTS index synchronized. Moving JSONL into SQLite should be treated as a separate storage refactor rather than a prerequisite for the first hybrid-search experiment.

### Evaluation plan

Compare at least three variants on the same questions:

```text
dense only
lexical only
dense + lexical RRF
```

Measure `Recall@3`, `Recall@5`, `MRR`, final answer quality, and query latency. Include evaluation cases for exact identifiers, paraphrases, multi-language questions, and questions with no answer in the corpus. Add a reranker only after the value of hybrid candidate generation is measured.

### Trade-offs

- Adds a migration, FTS repository, and synchronization logic.
- Increases index size because chunks are represented in two indexes.
- Requires a safe FTS query builder.
- Adds a small amount of query work, but vector and lexical branches may run independently.
- Usually improves robustness for technical and university-domain documents because it combines semantic similarity with exact textual evidence.

References:

- [SQLite FTS5 documentation](https://www.sqlite.org/fts5.html)
- [Reciprocal Rank Fusion paper](https://doi.org/10.1145/1571941.1572114)
- [Anthropic Contextual Retrieval](https://www.anthropic.com/engineering/contextual-retrieval)

## 6. Do not compare raw distances across embedding models

### Problem

The all-index search embeds the question once per embedding model, retrieves results from each configuration, and globally sorts raw cosine distances. Distances produced by different model families or dimensions are not calibrated against each other.

### Safer options

1. Require one embedding model for a single search operation.
2. Group search results by model and combine ranks with Reciprocal Rank Fusion.
3. Calibrate score distributions for every model using the evaluation dataset.

The first option is simplest and most predictable. Rank fusion is appropriate when cross-model search is a real requirement.

## 7. Retrieve broadly, then rerank

### Problem

The current fixed top-3 retrieval has no second-stage relevance judgment. Dense similarity measures semantic proximity, not whether a passage contains evidence needed to answer the question.

### Proposed pipeline

```text
retrieve 20-40 candidates
    -> rerank against the question
    -> keep 5-8 chunks within the context budget
```

Possible rerankers:

- a dedicated Qwen3 Reranker model;
- another cross-encoder reranker;
- an LLM returning a structured relevance score;
- initially, hybrid rank fusion without an extra model call.

### Trade-offs

- Reranking usually improves precision.
- It adds latency and another model lifecycle concern.
- Pointwise LLM scoring can be slow and inconsistent; batching or listwise scoring may help.
- Candidate count and final K must be evaluated rather than copied from another system.

## 8. Relevance thresholds and adaptive context size

### Problem

Top-K always returns something, even when every result is irrelevant. Conversely, a multi-part question may require more than three passages.

### Proposed approach

- Introduce a minimum relevance threshold calibrated for the selected embedding model.
- Apply the threshold after reranking when a reranker is present.
- Allow zero results and make the answer model abstain.
- Select chunks until a token budget is reached instead of using only a fixed count.
- Optionally stop when the score drops sharply relative to the preceding candidate.

Thresholds should be model-specific. A hard-coded cosine distance should not be reused across unrelated embedding models.

## 9. Neighbor expansion and parent-child retrieval

### Problem

The best matching chunk may contain only part of a definition, procedure, or argument.

### Proposed approach

Retrieve small chunks for precision, then expand each selected result with:

- the previous and next chunk;
- its enclosing section;
- or a larger parent chunk created during indexing.

After expansion:

1. merge adjacent ranges from the same document;
2. remove duplicate overlap;
3. enforce a token budget;
4. preserve source offsets for citations.

This often provides a better precision/context balance than increasing the indexed chunk size.

## 10. Context assembly and citations

### Problem

Retrieved chunks are passed with only a chunk number. The user cannot verify the answer, and the model has little source metadata.

### Proposed context format

```text
[Source 1]
File: regulations.txt
Section: Admission requirements
Chunks: 11-13
Content: ...
```

Ask the structured answer model to return source IDs alongside the answer. Validate that every returned source ID was present in the supplied context.

Context should be deduplicated and limited. Simply sending more chunks can reduce answer quality because language models often use evidence near the beginning and end more reliably than evidence in the middle.

Reference: [Lost in the Middle](https://arxiv.org/abs/2307.03172).

## 11. Query transformation for difficult questions

These techniques should be optional fallbacks rather than the first improvement.

### Multi-query retrieval

Generate several semantic rewrites of the question, retrieve for each, then combine ranks. This can improve recall for ambiguous phrasing but multiplies embedding and search work.

### Query decomposition

Split multi-part or multi-hop questions into subquestions, retrieve evidence for each, and merge the results. This is more useful than generic rewriting when an answer requires several facts.

### HyDE

Generate a hypothetical answer or document, embed it, and retrieve real passages near that representation. HyDE can improve zero-shot dense retrieval, but the generated text may bias retrieval toward invented details.

Reference: [Precise Zero-Shot Dense Retrieval without Relevance Labels](https://arxiv.org/abs/2212.10496).

## 12. Storage and retrieval efficiency

### Current concerns

- Chunks are stored in JSONL files and loaded for retrieval.
- Search across all configurations performs work per configuration.
- Embeddings are requested one chunk at a time.

### Possible improvements

- Store chunk content and metadata in SQLite so retrieval can return complete results directly.
- Search vectors globally per compatible embedding model instead of issuing a query per document configuration.
- Batch document embeddings through Ollama's array input support.
- Cache query embeddings for repeated questions during a session.
- Record the embedding dimension and model version in the indexing configuration.

These changes mainly improve indexing and query latency. They should not be confused with relevance improvements, though faster evaluation makes relevance work easier.

## 13. Recommended implementation order

### Phase 1: measurable baseline

1. Build the evaluation dataset.
2. Record retrieval and answer metrics.
3. Add retrieval diagnostics showing source, distance, and rank.

### Phase 2: high-value retrieval changes

1. Implement token-aware structural chunking with overlap.
2. Preserve section and source metadata.
3. Add neighbor expansion.
4. Add FTS5/BM25 and Reciprocal Rank Fusion.

### Phase 3: precision and answer grounding

1. Retrieve a larger candidate pool.
2. Add reranking.
3. Calibrate relevance thresholds.
4. Add citations to `StructuredAnswer`.

### Phase 4: advanced and conditional strategies

1. Contextual embeddings or late chunking.
2. Multi-query retrieval.
3. Query decomposition.
4. HyDE for difficult zero-shot queries.

Each phase should be accepted only after comparing it with the previous baseline on quality, latency, indexing time, and storage size.
