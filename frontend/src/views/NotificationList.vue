<template>
  <div class="notification-list">
    <van-nav-bar
      title="消息通知"
      left-arrow
      right-text="全部已读"
      @click-left="$router.back()"
      @click-right="onReadAll"
    />

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div
        v-for="n in list"
        :key="n.id"
        class="notif-card"
        :class="{ 'notif-card--unread': n.readFlag === 0 }"
        @click="onClick(n)"
      >
        <div class="notif-card__head">
          <span class="notif-card__title">{{ n.title }}</span>
          <span v-if="n.readFlag === 0" class="notif-card__dot" />
        </div>
        <p class="notif-card__content">{{ n.content }}</p>
        <p class="notif-card__time">{{ formatRelativeTime(n.createTime) }}</p>
      </div>

      <van-empty v-if="finished && list.length === 0" description="暂无通知" />
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { getNotifications, markAllRead } from '@/api/notification'
import { formatRelativeTime } from '@/utils/format'

const router = useRouter()

const list = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

async function onLoad() {
  try {
    const res = await getNotifications(page.value, pageSize)
    const data = res.data || []
    list.value.push(...data)
    const total = res.total
    const noMore = data.length < pageSize || (total != null && list.value.length >= total)
    if (noMore) {
      finished.value = true
    } else {
      page.value++
    }
  } catch (e) {
    // 失败提示已由 request.js Toast；结束加载避免 van-list 无限重试
    finished.value = true
  } finally {
    loading.value = false
  }
}

// 点击通知：有关联岗位则跳岗位详情，未读标记本地置已读
function onClick(n) {
  if (n.bizId) {
    router.push(`/job/${n.bizId}`)
  }
}

async function onReadAll() {
  try {
    await markAllRead()
    list.value.forEach((n) => {
      n.readFlag = 1
    })
    showSuccessToast('已全部读')
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}
</script>

<style scoped>
.notification-list {
  min-height: 100vh;
  padding-bottom: 24px;
}
.notif-card {
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.notif-card--unread {
  background: var(--brand-primary-light, #ecf3ff);
}
.notif-card__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.notif-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.notif-card__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--danger, #ee0a24);
}
.notif-card__content {
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-secondary);
  margin: 0 0 6px;
  word-break: break-word;
}
.notif-card__time {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0;
}
</style>
