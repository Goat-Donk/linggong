#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 tb_ai_rule 的知识库快照成评测用测试资源。

为什么需要快照：A-3 基线要求「不连 DB 也能复现」。如果评测每次都去查库，
那么任何人改一条规则，历史基线数字就再也复现不出来了 —— 评测报告会变成
一份「当时大概是这个数」的不可信文档。

为什么带指纹：快照与线上库一旦漂移，基线的解释力就没了。指纹写进文件，
方便在报告里声明「本次基线对应的语料指纹是 xxxx」，也方便日后比对。

用法：
    python tools/snapshot_rules.py                 # 从 docker 容器 linggong-mysql 导出
    python tools/snapshot_rules.py --from-file x.json   # 从已有 JSON 导出（离线/CI）
"""

import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

OUT_PATH = Path(__file__).resolve().parent.parent / "backend/src/test/resources/ai/rules.json"

CONTAINER = "linggong-mysql"
DB = "linggong"
MYSQL_ARGS = ["-uroot", "-p123456", "--default-character-set=utf8mb4", "-N", "-B"]

SQL = (
    "SELECT JSON_ARRAYAGG(JSON_OBJECT("
    "'id',id,'title',title,'tags',tags,'content',content)) "
    "FROM (SELECT id,title,tags,content FROM tb_ai_rule ORDER BY id) t;"
)


def fetch_from_docker() -> str:
    cmd = ["docker", "exec", CONTAINER, "mysql", *MYSQL_ARGS, "-e", SQL, DB]
    proc = subprocess.run(cmd, capture_output=True)
    if proc.returncode != 0:
        sys.exit(f"docker exec 失败：{proc.stderr.decode('utf-8', 'replace')}")
    return proc.stdout.decode("utf-8")


def fingerprint(rules: list) -> str:
    """语料指纹：对「id / title / tags / content」四元组的规范串取 sha256 前 16 位。

    tags 参与指纹是必要的 —— B3（tags 加权）改的就是这一列，不纳入指纹就检测不到漂移。
    """
    canonical = "\n".join(
        f"{r['id']}\t{r['title']}\t{r['tags']}\t{r['content']}" for r in rules
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-file", help="改为从已有 JSON 文件读取，不连 docker")
    args = ap.parse_args()

    raw = Path(args.from_file).read_text(encoding="utf-8") if args.from_file else fetch_from_docker()
    rules = json.loads(raw.strip())
    rules.sort(key=lambda r: r["id"])

    for r in rules:
        assert set(r) == {"id", "title", "tags", "content"}, f"字段不符：{r.get('id')}"
        for field in ("title", "tags", "content"):
            if "\n" in r[field]:
                sys.exit(f"规则 {r['id']} 的 {field} 含换行，会破坏检索文本的字段结构")

    doc = {
        "meta": {
            "source": "tb_ai_rule",
            "corpusSize": len(rules),
            "fingerprint": fingerprint(rules),
            "generator": "tools/snapshot_rules.py",
            "note": (
                "评测语料快照。searchText = title + ' ' + tags + ' ' + content，"
                "与 Bm25ContentRetriever.IndexedRule 一致；三列任一变动都会改变指纹。"
            ),
        },
        "rules": rules,
    }
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"已写入 {OUT_PATH}")
    print(f"规则数：{len(rules)}    指纹：{doc['meta']['fingerprint']}")
    for r in rules:
        print(f"  {r['id']:>3}  {r['title']}")


if __name__ == "__main__":
    main()
