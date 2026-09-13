#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""C0 冒烟：验证 DashScope 兼容模式下的 /embeddings 到底能不能用、该用哪个模型。

## 为什么必须先跑这个

C1（向量召回）整个方案建立在「DashScope 的 compatible-mode 提供 /embeddings，
所以能复用已有的 langchain4j-open-ai 依赖、零新增依赖」这个假设上。
**这是假设，不是事实** —— DashScope 的兼容模式对 embedding 的支持与 chat 不同源，
模型名也和原生协议不一致。不先冒烟就去改 `AiModelConfig`、加 Bean、写 RRF，
一旦接口不通，整条 C1 的活全部返工。

所以这一步的产出不是代码，是**一个确定的事实**：能用哪个模型、维度多少、耗时多少。

## 用法

    python tools/check_embeddings.py

Key 的读取顺序（与后端一致，不打印 Key 本身）：
  1. 环境变量 DEEPSEEK_API_KEY
  2. backend/src/main/resources/application-local.yml 的 linggong.ai.api-key

脚本会依次探测多个候选模型名并打印结果表。**只要有一个 OK，C1 就可以零新增依赖地开工。**

退出码：0 = 至少一个模型可用；1 = 全部失败（把错误信息原样打出来，含 Arrearage 欠费）。

依赖：仅标准库。
"""

import json
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

# 兼容模式下的候选 embedding 模型名，按「大概率可用」排序。
# 腾讯/阿里的通义向量模型在原生协议下叫 text-embedding-v1/v2/v3/v4，
# 兼容模式是否全部开放需要实测 —— 这正是本脚本要回答的问题。
CANDIDATE_MODELS = [
    "text-embedding-v3",
    "text-embedding-v4",
    "text-embedding-v2",
    "text-embedding-v1",
]

DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
PROBE_TEXT = "服务费怎么算"  # 用真实业务短句，顺带看看中文的 token 计费与维度

ROOT = Path(__file__).resolve().parent.parent
LOCAL_YML = ROOT / "backend" / "src" / "main" / "resources" / "application-local.yml"


def load_config():
    """返回 (api_key, base_url)。读不到就明确报错，不要静默用空 Key 去试。"""
    import os

    key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    base_url = DEFAULT_BASE_URL

    if LOCAL_YML.exists():
        text = LOCAL_YML.read_text(encoding="utf-8")
        if not key:
            m = re.search(r'api-key:\s*"([^"]+)"', text)
            if m:
                key = m.group(1).strip()
        m = re.search(r'base-url:\s*"([^"]+)"', text)
        if m:
            base_url = m.group(1).strip()

    if not key:
        sys.exit(
            "找不到 API Key。请二选一：\n"
            "  - 设环境变量 DEEPSEEK_API_KEY\n"
            f"  - 确认 {LOCAL_YML} 里有 linggong.ai.api-key"
        )
    return key, base_url


def probe(base_url, api_key, model):
    """打一次 /embeddings。返回 (ok, 说明文字, 维度或 None, 耗时ms)。"""
    url = base_url.rstrip("/") + "/embeddings"
    body = json.dumps({"model": model, "input": PROBE_TEXT}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    started = time.time()
    try:
        # 本机 Python 的根证书偶尔不全，这里显式用默认 context 并在失败时给提示
        with urllib.request.urlopen(req, timeout=30, context=ssl.create_default_context()) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        elapsed = int((time.time() - started) * 1000)
        detail = e.read().decode("utf-8", errors="replace")[:400]
        return False, f"HTTP {e.code}: {detail}", None, elapsed
    except Exception as e:  # 网络/证书/超时
        elapsed = int((time.time() - started) * 1000)
        return False, f"{type(e).__name__}: {e}", None, elapsed

    elapsed = int((time.time() - started) * 1000)
    data = payload.get("data") or []
    if not data:
        return False, f"响应里没有 data 字段：{json.dumps(payload, ensure_ascii=False)[:300]}", None, elapsed
    dim = len(data[0].get("embedding") or [])
    return True, "OK", dim, elapsed


def main():
    api_key, base_url = load_config()
    print(f"base-url : {base_url}")
    print(f"api-key  : {api_key[:6]}***（长度 {len(api_key)}，不打印全文）")
    print(f"探测文本 : {PROBE_TEXT!r}")
    print()

    results = []
    for model in CANDIDATE_MODELS:
        ok, note, dim, ms = probe(base_url, api_key, model)
        results.append((model, ok, dim, ms, note))
        flag = "✅" if ok else "❌"
        dim_text = f"dim={dim}" if dim else "dim=-"
        print(f"{flag} {model:<22} {dim_text:<9} {ms:>5}ms  {note if not ok else ''}")

    print()
    good = [r for r in results if r[1]]
    if good:
        print(f"结论：{len(good)} 个模型可用，C1 可以零新增依赖开工。推荐用 {good[0][0]}（dim={good[0][2]}）。")
        return 0

    print("结论：没有一个模型探测成功。下面按错误类型分流，**注意区分「假设被证伪」与「根本没能测」**：")
    joined = " ".join(r[4] for r in results)
    if "Arrearage" in joined:
        print("  → 账号欠费（Arrearage）。**这是「没能测」，不是「不能用」** —— 假设依然悬着，")
        print("    充完值重跑本脚本才能得到结论。别在这个状态下就开始改 C1 的代码。")
        print("    附带一个弱信号（**不是证据**）：返回的是 HTTP 400 + DashScope 结构化错误体，")
        print("    而不是 404；但鉴权/欠费检查通常排在路由解析之前，所以这**不能证明** /embeddings 存在。")
    elif "HTTP 404" in joined:
        print("  → 兼容模式没有 /embeddings 端点。**假设被证伪**：C1 需退回自建 HTTP 调用（走 DashScope 原生协议）。")
    elif "Model" in joined and ("not exist" in joined or "does not exist" in joined):
        print("  → 模型名不对。假设未证伪，只是名字错：去百炼控制台确认兼容模式开放了哪些 embedding 模型，改 CANDIDATE_MODELS。")
    else:
        print("  → 需要人工看上面的原始报错再决定。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
