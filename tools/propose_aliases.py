#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从标注集里反推「该给哪条规则补哪些别名」—— B4 别名扩展的候选生成器。

动机：B4（B3 + 别名扩展）如果凭语感手写别名，那就只是一份个人词表，
既说不清收益从哪来、也无法复核。这里改成数据驱动：
把每条 query 里**语料词表覆盖不到（OOV）的 token** 按它所属的规则归集起来，
按出现频次排序 —— 高频 OOV token 就是「用户在说、但语料里没有」的词，
它们正是别名扩展该收录的东西。

为什么必须归集到「规则」而不是全局统计：别名是补在某一条规则的 tags 里的，
全局高频只说明「这个词常见」，不说明「它该补进哪条规则」。

一个关键判断：OOV token 在 BM25 里是 score-neutral 的（df=0 ⟹ tf=0 ⟹ 对每篇文档
贡献恰好为 0），所以候选表里必然混着大量「怎么 / 多少 / 吗」这类疑问虚词，
它们**补进去也是零收益**。脚本不自动过滤，而是原样列出频次，由人来判断哪些是
真正的内容词缺口 —— 过滤规则一旦写死，就会把「咋整」「老板」这类土味内容词一起误杀。

用法：
    python tools/propose_aliases.py [--top 12] [--rule 30]
"""

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_semantic_gap import tokenize  # noqa: E402  复刻生产分词器，避免两份实现漂移

ROOT = Path(__file__).resolve().parent.parent
RULES_PATH = ROOT / "backend/src/test/resources/ai/rules.json"
EVAL_PATH = ROOT / "backend/src/test/resources/ai/eval-set.json"

# 疑问/功能虚词：这些不是「词表缺口」，是问句的结构件。列在这里只为在报告里
# 与人眼对照，脚本不会据此过滤掉任何候选（见文件头说明）。
FUNCTION_WORDS = {
    "怎么", "什么", "多少", "可以", "能不能", "是不是", "有没", "没有", "的话",
    "为什", "什么", "怎样", "如何", "哪些", "哪个", "会不", "要不", "可以",
}


def search_text(rule: dict) -> str:
    return f"{rule['title']} {rule['tags']} {rule['content']}"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--top", type=int, default=12, help="每条规则展示的候选数")
    ap.add_argument("--rule", type=int, help="只看某一条规则")
    args = ap.parse_args()

    rules = json.loads(RULES_PATH.read_text(encoding="utf-8"))["rules"]
    samples = json.loads(EVAL_PATH.read_text(encoding="utf-8"))["samples"]

    vocab = set()
    for r in rules:
        vocab.update(tokenize(search_text(r)))

    # rule_id -> token -> 出现次数
    per_rule = defaultdict(Counter)
    # rule_id -> token -> 该 token 出现在哪些 query 上（人工核对时看语境）
    contexts = defaultdict(lambda: defaultdict(list))
    all_oov = Counter()

    for s in samples:
        relevant = s["relevant"]
        if not relevant:  # 超纲题没有「该补哪条规则」的说法
            continue
        oov = [t for t in tokenize(s["query"]) if t not in vocab]
        for t in set(oov):
            all_oov[t] += 1
            for rid in relevant:
                per_rule[rid][t] += 1
                if len(contexts[rid][t]) < 2:
                    contexts[rid][t].append(s["query"])

    print(f"语料词表 {len(vocab)} 个 token；覆盖不到的 query token 共 {len(all_oov)} 种")
    print(f"其中出现 ≥3 次的：{sum(1 for v in all_oov.values() if v >= 3)} 种\n")

    targets = [r for r in rules if args.rule is None or r["id"] == args.rule]
    for r in targets:
        cand = per_rule.get(r["id"], Counter())
        if not cand:
            continue
        print(f"── 规则 {r['id']}｜{r['title']}")
        print(f"   现有 tags: {r['tags']}")
        for token, n in cand.most_common(args.top):
            mark = "虚词" if token in FUNCTION_WORDS else "  "
            sample = "／".join(contexts[r["id"]][token])
            print(f"   {mark} {token:<6} ×{n:<3} 例：{sample}")
        print()


if __name__ == "__main__":
    main()
