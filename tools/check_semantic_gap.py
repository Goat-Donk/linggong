#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
语义鸿沟检查：query 的 token 有多少落在规则语料词表之外（OOV）。

为什么需要它：A-2 的 Hit@k / MRR 要等到指标引擎写完才有，但"口语化会不会打穿检索器"
这件事在生成阶段就能先量化——BM25 是词袋模型，query 切出的 token 若大量不在语料词表里，
就没有任何词项能参与打分，必然漏召回。OOV 率就是这个风险的先行指标。

分词逻辑与生产代码 Bm25ContentRetriever#tokenize 保持一致：
  - 连续中文（CJK）切 2-gram；中文单字单独成 token
  - ASCII 连续段作为整词，转小写

用法：
    python tools/check_semantic_gap.py /tmp/rules.tsv
    python tools/check_semantic_gap.py /tmp/rules.tsv --category colloquial
"""

import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

CJK_RUN = re.compile(r"[一-龥]+")   # 与 Bm25ContentRetriever.CJK_RUN 逐字符对齐
ASCII_RUN = re.compile(r"[A-Za-z0-9]+")

EVAL_PATH = Path(__file__).resolve().parent.parent / "backend/src/test/resources/ai/eval-set.json"


def tokenize(text):
    """与 Bm25ContentRetriever#tokenize 对齐。"""
    if not text or not text.strip():
        return []
    tokens, last = [], 0
    for m in CJK_RUN.finditer(text):
        tokens.extend(w.lower() for w in ASCII_RUN.findall(text[last:m.start()]))
        run = m.group()
        if len(run) == 1:
            tokens.append(run)
        else:
            tokens.extend(run[i:i + 2] for i in range(len(run) - 1))
        last = m.end()
    tokens.extend(w.lower() for w in ASCII_RUN.findall(text[last:]))
    return tokens


def load_corpus(tsv_path):
    """读 tb_ai_rule dump（id \\t title \\t tags \\t content），返回 (词表, {id: 索引文本})。"""
    vocab, docs, raw = Counter(), {}, {}
    for line in Path(tsv_path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 4:
            continue
        rid, title, tags, content = parts[0], parts[1], parts[2], parts[3]
        # 与 IndexedRule.searchText 一致：title + tags + content
        text = f"{title} {tags} {content}"
        toks = tokenize(text)
        vocab.update(toks)
        docs[int(rid)] = text
        raw[int(rid)] = (title, toks)
    return vocab, docs, raw


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    tsv = sys.argv[1]
    only_cat = sys.argv[sys.argv.index("--category") + 1] if "--category" in sys.argv else None

    vocab, docs, raw = load_corpus(tsv)
    print(f"规则语料：{len(docs)} 条，词表 {len(vocab)} 个 token")

    data = json.loads(EVAL_PATH.read_text(encoding="utf-8"))
    samples = [s for s in data["samples"] if not only_cat or s["category"] == only_cat]

    by_cat = defaultdict(list)
    for s in samples:
        toks = tokenize(s["query"])
        if not toks:
            continue
        oov = [t for t in toks if t not in vocab]
        by_cat[s["category"]].append({
            "id": s["id"], "query": s["query"], "toks": len(toks),
            "oov": len(oov), "rate": len(oov) / len(toks), "oov_tokens": oov,
        })

    print("\n各分类 OOV 率（query token 不在规则词表内的比例）：")
    print("-" * 72)
    print(f"{'分类':<14}{'样本':>5}{'平均OOV率':>11}{'中位':>8}{'完全OOV':>10}  {'平均token数':>10}")
    for cat in ["direct", "colloquial", "typo", "negation", "cross", "out_of_scope"]:
        rows = by_cat.get(cat)
        if not rows:
            continue
        rates = sorted(r["rate"] for r in rows)
        avg = sum(rates) / len(rates)
        med = rates[len(rates) // 2]
        full = sum(1 for r in rows if r["rate"] == 1.0)
        mtok = sum(r["toks"] for r in rows) / len(rows)
        print(f"{cat:<14}{len(rows):>5}{avg:>10.1%}{med:>8.1%}{full:>10}  {mtok:>10.1f}")

    # 只看 OOV 最严重的样本 —— 这些就是最可能漏召回的 bad case 候选
    print("\nOOV 最高的 12 条（A-2 跑分时的 bad case 候选）：")
    print("-" * 72)
    allrows = [r for rows in by_cat.values() for r in rows]
    for r in sorted(allrows, key=lambda x: -x["rate"])[:12]:
        toks = "/".join(r["oov_tokens"][:6])
        print(f"  {r['id']} {r['rate']:>5.0%} ({r['oov']}/{r['toks']})  {r['query']}")
        print(f"        未命中token: {toks}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
