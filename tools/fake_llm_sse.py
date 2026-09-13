#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""本地假 LLM（OpenAI 兼容流式协议）—— 不消耗真实 token 就能测 RAG 外层链路。

## 它解决什么问题

RAG 链路的正确性分两层，两层的测法完全不同：

  1. **内层（检索、指标、trace 拼装）** 是纯逻辑，JUnit 够了；
  2. **外层（SSE 流式输出、回答分片跨线程累积、首字延迟、客户端断开）**
     必须在**真实的 HTTP 客户端**上跑，单测里用 `Flux.just("好")` 合成的流不算数 ——
     它绕过了 langchain4j 的 SSE 解析、okhttp/JDK 派发线程、以及真实的分片时序。

第 2 层如果只能靠真实模型来测，就会有两个后果：**每次回归都烧 token**，
以及**账号欠费/断网时整条链路没法验证**。后一条不是假设 —— 2026-09-13 就真发生了
（DashScope 返回 `{"type":"Arrearage"}`，所有问答静默降级成兜底话术，trace 表里
一排 `status=ERROR`）。有了这个桩，那种情况下链路照样可测。

## 用法

    # 终端 A：起桩
    python tools/fake_llm_sse.py

    # 终端 B：后端把 base-url 指过来（只影响本次启动，不改配置）
    cd backend
    mvn spring-boot:run -Dspring-boot.run.profiles=local \
        "-Dspring-boot.run.arguments=--linggong.ai.base-url=http://127.0.0.1:18999/v1"

之后正常走 `/ai/assistant/chat`，回答就是本桩吐的那串固定文本。
回答内容写死在 `CHUNKS`，**故意用中文多分片**：顺带验证跨线程累积与 UTF-8 编码。

## 用它验证过的三件事（对应 `tb_ai_trace.status`）

| 状态 | 怎么触发 |
| --- | --- |
| `OK` | 正常请求，答完 |
| `INCOMPLETE` | `curl --max-time 0.9` 中途掐断（`CHUNK_DELAY_SECONDS` 就是为这个留的） |
| `ERROR` | 不启桩 / 指向错误端口，模型调用失败 |

## 端口为什么是 18999

Windows 有一批**保留端口段**，`bind` 上去会直接 `WinError 10013`（权限错误，
不是端口占用，报错信息很误导）。查：

    netsh int ipv4 show excludedportrange protocol=tcp

9099 就落在保留段 9093-9192 里，所以换到了 18999。**换端口前先查一次。**

依赖：仅标准库，无需 pip 安装。
"""

import json
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = 18999
# 每个分片之间停一下，好让 curl 有机会在中途掐断（验证 CANCEL → INCOMPLETE）。
# 设 0 就是全速吐完，只测 OK 路径。
CHUNK_DELAY_SECONDS = 0.2

# 故意用中文多分片：跨线程累积 + UTF-8 编码 + 截断逻辑都能顺带覆盖
CHUNKS = ["平台", "从", "每笔", "已完成", "订单", "中", "抽取", "10%", "作为", "服务费", "。"]


class Stub(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        self.rfile.read(length)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        # 不加 Connection: close 的话，浏览器/客户端可能复用连接等下一个响应，卡住不结束
        self.send_header("Connection", "close")
        self.end_headers()

        def emit(payload):
            body = "data: " + json.dumps(payload, ensure_ascii=False) + "\n\n"
            self.wfile.write(body.encode("utf-8"))
            self.wfile.flush()

        def chunk(delta, finish=None):
            return {
                "id": "stub-1",
                "object": "chat.completion.chunk",
                "created": 0,
                "model": "stub",
                "choices": [{"index": 0, "delta": delta, "finish_reason": finish}],
            }

        emit(chunk({"role": "assistant", "content": ""}))
        for piece in CHUNKS:
            emit(chunk({"content": piece}))
            time.sleep(CHUNK_DELAY_SECONDS)
        emit(chunk({}, finish="stop"))
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()

    def log_message(self, *args):
        pass  # 默认每条请求打一行日志，刷屏；这里静音


if __name__ == "__main__":
    print(f"fake LLM listening on http://127.0.0.1:{PORT}/v1", flush=True)
    try:
        HTTPServer(("127.0.0.1", PORT), Stub).serve_forever()
    except KeyboardInterrupt:
        sys.exit(0)
