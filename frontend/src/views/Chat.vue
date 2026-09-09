<template>
  <div class="chat-page">
    <van-nav-bar :title="peerName || '聊天'" left-arrow @click-left="$router.back()" />

    <div class="chat-body" ref="bodyRef">
      <div
        v-for="m in list"
        :key="m.id"
        class="msg"
        :class="m.fromUserId === me ? 'msg--mine' : 'msg--peer'"
      >
        <div class="msg__bubble">
          <div class="msg__text">{{ m.content }}</div>
        </div>
        <div class="msg__time">{{ formatRelativeTime(m.createTime) }}</div>
      </div>
      <van-empty v-if="list.length === 0" description="发条消息打个招呼吧" />
    </div>

    <div class="chat-input">
      <van-field
        v-model="text"
        placeholder="输入消息..."
        @keyup.enter="onSend"
      />
      <van-button size="small" type="primary" :disabled="!text.trim()" @click="onSend">
        发送
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getConversation, getMessages, sendMessage } from '@/api/chat'
import { getUserById } from '@/api/user'
import { userState } from '@/stores/user'
import { formatRelativeTime } from '@/utils/format'

const route = useRoute()

const me = ref(userState.info?.id)
const peerId = ref(Number(route.params.peerId))
const jobId = ref(Number(route.query.jobId))
const peerName = ref('')
const conversationId = ref(null)

const list = ref([])
const text = ref('')
const bodyRef = ref(null)

let timer = null

onMounted(async () => {
  // 拿对方昵称显示在导航栏
  try {
    const u = await getUserById(peerId.value)
    peerName.value = u.data?.nickName || ''
  } catch (e) {
    // 拿不到昵称不影响聊天
  }
  // 定位会话（可能为空，首次发消息时自动建）
  try {
    const res = await getConversation(jobId.value, peerId.value)
    if (res.data) {
      conversationId.value = res.data.id
      await loadMessages()
    }
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
  // 轮询新消息（约 3s）
  timer = setInterval(() => {
    if (conversationId.value) loadMessages()
  }, 3000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function loadMessages() {
  if (!conversationId.value) return
  try {
    const res = await getMessages(conversationId.value, 1, 20)
    list.value = (res.data || []).slice().reverse()
    scrollToBottom()
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}

async function onSend() {
  const content = text.value.trim()
  if (!content) return
  try {
    const res = await sendMessage({ jobId: jobId.value, peerId: peerId.value, content })
    if (res.data && !conversationId.value) {
      conversationId.value = res.data
    }
    text.value = ''
    await loadMessages()
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}

async function scrollToBottom() {
  await nextTick()
  if (bodyRef.value) {
    bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
}
.msg {
  display: flex;
  flex-direction: column;
  margin: 8px 12px;
}
.msg--mine {
  align-items: flex-end;
}
.msg--peer {
  align-items: flex-start;
}
.msg__bubble {
  max-width: 72%;
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
  line-height: 1.5;
}
.msg__time {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 4px;
}
.chat-input {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
}
.chat-input :deep(.van-field) {
  flex: 1;
  background: var(--bg-page);
  border-radius: 18px;
  padding: 6px 12px;
}
</style>
