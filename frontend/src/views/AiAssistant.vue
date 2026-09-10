<template>
  <div class="assistant-page">
    <van-nav-bar title="小灵 AI 助手" left-arrow @click-left="$router.back()" />

    <!-- 顶部简介条 -->
    <div class="assistant-hero">
      <div class="assistant-avatar">灵</div>
      <div class="assistant-hero-text">
        <div class="assistant-name">平台 AI 问答助手</div>
        <div class="assistant-sub">平台规则我懂，钱包 / 报名 / 考勤 / 工资我帮你查</div>
      </div>
    </div>

    <div class="assistant-body" ref="bodyRef">
      <div
        v-for="(m, i) in messages"
        :key="i"
        class="msg"
        :class="m.role === 'user' ? 'msg--mine' : 'msg--peer'"
      >
        <div class="msg__bubble">
          <div v-if="m.loading" class="typing"><span></span><span></span><span></span></div>
          <div v-else class="msg__text">{{ m.content }}<span v-if="m.streaming" class="msg__cursor">|</span></div>
        </div>
      </div>
      <van-empty v-if="messages.length === 1 && messages[0].greet" description="试着问我：服务费怎么算？我的钱包多少钱？" />
    </div>

    <div class="assistant-input">
      <van-field
        v-model="text"
        placeholder="输入问题，回车发送"
        @keyup.enter="onSend"
      />
      <van-button
        size="small"
        :type="loading ? 'danger' : 'primary'"
        :disabled="!loading && !text.trim()"
        @click="loading ? onStop() : onSend()"
      >
        {{ loading ? '停止' : '发送' }}
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted, nextTick } from 'vue'
import { showToast } from 'vant'
import { chatAiAssistant } from '@/api/ai'

const messages = ref([])
const text = ref('')
const loading = ref(false)
const bodyRef = ref(null)
let controller = null

// 欢迎语：作为 assistant 首条消息展示，不参与会话记忆（记忆在服务端按 userId 隔离）
messages.value.push({
  role: 'assistant',
  content: '你好呀～我是小灵，平台规则、你的钱包、报名、考勤和工资我都能帮你查。有什么想问的？',
  greet: true,
  loading: false,
  streaming: false
})

async function onSend() {
  const question = text.value.trim()
  if (!question || loading.value) return

  // 清掉欢迎语占位
  if (messages.value.length && messages.value[0].greet) {
    messages.value.splice(0, 1)
  }

  messages.value.push({ role: 'user', content: question, loading: false, streaming: false })
  messages.value.push({ role: 'assistant', content: '', loading: true, streaming: false })
  text.value = ''
  loading.value = true
  scrollToBottom()

  controller = new AbortController()
  try {
    const response = await chatAiAssistant(question, controller.signal)
    const lastIndex = messages.value.length - 1

    // 后端限流 / 未登录返回的是 Result JSON（HTTP 200），按 content-type 分流，避免把 JSON 当流渲染
    const contentType = response.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      const body = await response.json()
      messages.value[lastIndex].loading = false
      messages.value[lastIndex].content = body.errorMsg || '请求失败，请稍后再试'
      return
    }
    if (!response.ok) {
      if (response.status === 401) {
        localStorage.removeItem('token')
        showToast('请先登录')
        window.location.href = '/login'
        return
      }
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      messages.value[lastIndex].content = await response.text()
      return
    }

    // 流式逐段上屏（参考 AI-dianping ai-assistant.js 的 reader 写法）
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    messages.value[lastIndex].loading = false
    messages.value[lastIndex].streaming = true
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      messages.value[lastIndex].content = buffer
      scrollToBottom()
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      const last = messages.value[messages.value.length - 1]
      if (last) {
        last.content = last.content || '抱歉，请求出错了，请稍后再试'
      }
    }
  } finally {
    loading.value = false
    controller = null
    const last = messages.value[messages.value.length - 1]
    if (last) {
      last.loading = false
      last.streaming = false
    }
    scrollToBottom()
  }
}

function onStop() {
  if (controller) controller.abort()
  controller = null
  loading.value = false
}

onUnmounted(() => {
  if (controller) controller.abort()
})

async function scrollToBottom() {
  await nextTick()
  if (bodyRef.value) {
    bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  }
}
</script>

<style scoped>
.assistant-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.assistant-hero {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
}
.assistant-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--brand-primary);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.assistant-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.assistant-sub {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}
.assistant-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
}
.msg {
  display: flex;
  margin: 8px 12px;
}
.msg--mine {
  justify-content: flex-end;
}
.msg--peer {
  justify-content: flex-start;
}
.msg__bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 12px;
  word-break: break-word;
}
.msg--mine .msg__bubble {
  background: var(--brand-primary);
  color: #fff;
  border-bottom-right-radius: 2px;
}
.msg--peer .msg__bubble {
  background: var(--bg-card);
  color: var(--text-primary);
  border-bottom-left-radius: 2px;
}
.msg__text {
  font-size: 15px;
  line-height: 1.55;
  white-space: pre-wrap;
}
.msg__cursor {
  animation: blink 0.8s step-start infinite;
  margin-left: 2px;
}
@keyframes blink {
  50% { opacity: 0; }
}
.typing {
  display: flex;
  gap: 4px;
  padding: 4px 2px;
}
.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
  animation: bounce 1s infinite;
}
.typing span:nth-child(2) { animation-delay: 0.15s; }
.typing span:nth-child(3) { animation-delay: 0.3s; }
@keyframes bounce {
  0%, 100% { transform: translateY(0); opacity: 0.4; }
  50% { transform: translateY(-4px); opacity: 1; }
}
.assistant-input {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
}
.assistant-input :deep(.van-field) {
  flex: 1;
  background: var(--bg-page);
  border-radius: 18px;
  padding: 6px 12px;
}
</style>
