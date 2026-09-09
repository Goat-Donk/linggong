<template>
  <div class="chat-list">
    <van-nav-bar title="消息" left-arrow @click-left="$router.back()" />

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div
        v-for="c in list"
        :key="c.id"
        class="chat-card"
        @click="openChat(c)"
      >
        <van-image v-if="c.peerIcon" round width="44" height="44" :src="c.peerIcon" />
        <van-icon v-else name="contact" size="44" color="#c8c9cc" />
        <div class="chat-card__meta">
          <div class="chat-card__top">
            <span class="chat-card__name">{{ c.peerName || '未知用户' }}</span>
            <span class="chat-card__time">{{ formatRelativeTime(c.lastMessageTime) }}</span>
          </div>
          <div class="chat-card__bottom">
            <span class="chat-card__last">{{ c.lastMessage || '开始聊天吧' }}</span>
            <van-badge v-if="c.unreadCount > 0" :content="c.unreadCount > 99 ? '99+' : c.unreadCount" />
          </div>
        </div>
      </div>

      <van-empty v-if="finished && list.length === 0" description="暂无会话" />
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getConversations } from '@/api/chat'
import { formatRelativeTime } from '@/utils/format'

const router = useRouter()

const list = ref([])
const loading = ref(false)
const finished = ref(false)

async function onLoad() {
  try {
    const res = await getConversations()
    list.value = res.data || []
  } catch (e) {
    // 失败提示已由 request.js Toast
  } finally {
    loading.value = false
    finished.value = true
  }
}

function openChat(c) {
  router.push(`/chat/${c.peerId}?jobId=${c.jobId}`)
}
</script>

<style scoped>
.chat-list {
  min-height: 100vh;
  padding-bottom: 24px;
}
.chat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.chat-card__meta {
  flex: 1;
  min-width: 0;
}
.chat-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.chat-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.chat-card__time {
  font-size: 12px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.chat-card__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.chat-card__last {
  flex: 1;
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
