#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 B4 别名扩展表 —— 口语说法 + 同音形近错别字。

## 为什么是「词驱动」而不是「query 驱动」

别名是补进 tb_ai_rule.tags 的生产资产。如果做法是「把评测集里出现过的错别字
一个个抄进去」，那得到的是一份对评测集过拟合的词表：换一批用户就失效，而且
数字好看也说明不了任何问题。

这里改成词驱动：**只要语料里出现了正确词，就给它补上错别字/口语说法**。
错别字是「词」的属性而不是「某条 query」的属性 —— 「岗位」会被写成「刚位」，
这件事与它出现在哪条规则里无关。

## 证据来源（严格切分，防过拟合）

评测集按样本 id 奇偶切成两半：**偶数为推导集 A，奇数为验证集 B**。
本表的所有词条**只依据 A 半推导**，B 半留给 B4 的留出验证（见 BaselineLadderTest）。
所以下表里的每条 evidence 都是 A 半的样本 id。

## 已知边界（写进报告，不藏着）

别名只能补「整词」缺口。2-gram 分词下，口语里大量 OOV 其实是跨边界碎片
（「钱咋」「候退」）和疑问虚词（「怎么」「多少」），这些**补不进去**，
也不是别名该解决的问题 —— 它们要靠向量召回。所以 B4 的收益必然有限，
这个「有限」本身就是要测出来的结论，不是失败。

用法：
    python tools/gen_aliases.py
"""

import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RULES_PATH = ROOT / "backend/src/test/resources/ai/rules.json"
OUT_PATH = ROOT / "backend/src/test/resources/ai/aliases.json"

# (触发词, 该触发词出现时要补的别名, A 半证据)
WORD_TRIGGERED = [
    ("雇主", ["老板"], "q258/q276/q280/q286"),
    ("担保金", ["押金"], "q228"),
    ("岗位", ["刚位"], "q296/q300"),
    ("报名", ["报明"], "q306"),
    ("撤销", ["撤消"], "q304/q306"),
    ("审核", ["审合"], "q302"),
    ("放鸽子", ["放各子"], "q308"),
    ("到岗", ["到刚"], "q310"),
    ("工资", ["工姿", "公资"], "q312/q314"),
    ("抽成", ["抽诚"], "q316"),
    ("充值", ["充直"], "q318"),
    ("提现", ["题现", "取出来"], "q320/q268"),
    ("解除", ["解初"], "q324"),
    ("发布岗位", ["招人"], "q226"),
    ("招聘", ["招人"], "q226"),
    ("结算", ["给钱"], "q260"),
    ("低", ["底了"], "q322"),
]

# 触发词在该规则正文里没出现、但有 A 半直接证据的，按规则 id 定点补
PINNED = {
    "20": {"找活": "q222"},
}


def search_text(rule: dict) -> str:
    return f"{rule['title']} {rule['tags']} {rule['content']}"


def main() -> None:
    rules = json.loads(RULES_PATH.read_text(encoding="utf-8"))["rules"]

    terms = defaultdict(list)       # rule_id -> [alias]
    evidence = defaultdict(dict)    # rule_id -> {alias: 证据}

    for r in rules:
        text = search_text(r)
        rid = str(r["id"])
        for trigger, aliases, ev in WORD_TRIGGERED:
            if trigger in text:
                for a in aliases:
                    if a in text:       # 语料里已有，补了也是零收益（OOV 才算缺口）
                        continue
                    if a not in terms[rid]:
                        terms[rid].append(a)
                        evidence[rid][a] = f"{ev} ← 触发词「{trigger}」"

    for rid, mapping in PINNED.items():
        for a, ev in mapping.items():
            if a not in terms[rid]:
                terms[rid].append(a)
                evidence[rid][a] = f"{ev} ← 定点补"

    out = {
        "meta": {
            "purpose": "B4 别名扩展输入：补进 tb_ai_rule.tags，使 searchText 覆盖用户口语/错别字说法",
            "generator": "tools/gen_aliases.py",
            "derivation": "评测集 id 偶数为推导集 A、奇数为验证集 B；本表只依据 A 半推导",
            "mechanism": "别名写入 tags（searchText = title + ' ' + tags + ' ' + content），"
                         "不进入给 LLM 的 Content 正文（那只有 title + content）",
            "limitation": "只能补整词缺口；2-gram 跨边界碎片与疑问虚词补不进去，需向量召回",
            "ruleCount": len(rules),
            "aliasedRuleCount": len([k for k, v in terms.items() if v]),
            "totalAliases": sum(len(v) for v in terms.values()),
        },
        "aliases": {
            str(r["id"]): {
                "terms": terms.get(str(r["id"]), []),
                "evidence": evidence.get(str(r["id"]), {}),
            }
            for r in rules
        },
    }

    OUT_PATH.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"已写入 {OUT_PATH}")
    print(f"覆盖规则 {out['meta']['aliasedRuleCount']}/{len(rules)} 条，"
          f"别名共 {out['meta']['totalAliases']} 个\n")
    for r in rules:
        rid = str(r["id"])
        if terms.get(rid):
            print(f"  {rid}  {r['title']}")
            for a in terms[rid]:
                print(f"        + {a:<8} {evidence[rid][a]}")


if __name__ == "__main__":
    main()
