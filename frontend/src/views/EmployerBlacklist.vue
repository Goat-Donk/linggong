<template>
  <div class="blacklist">
    <van-nav-bar title="黑名单管理" left-arrow @click-left="$router.back()" />

    <p class="hint">被拉黑的打工人将无法报名你发布的任何岗位（解除后可再次报名）</p>

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div v-for="item in list" :key="item.id" class="bl-item">
        <div class="bl-item__left">
          <van-image
            v-if="item.workerIcon"
            round
            width="42"
            height="42"
            :src="item.workerIcon"
          />
          <van-icon v-else name="contact" size="42" color="#c8c9cc" />
          <div class="bl-item__meta">
            <span class="bl-item__name">{{ item.workerName || '未知用户' }}</span>
            <span class="bl-item__reason">
              {{ item.reason ? '原因：' + item.reason : '未填写原因' }}
            </span>
            <span class="bl-item__time">{{ formatDateTime(item.createTime) }}</span>
          </div>
        </div>
        <van-button size="small" type="primary" plain @click="onRemove(item)">解除</van-button>
      </div>

      <van-empty v-if="finished && list.length === 0" description="暂无黑名单" />
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { getBlacklist, removeBlacklist } from '@/api/blacklist'
import { formatDateTime } from '@/utils/format'

const list = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

async function onLoad() {
  try {
    const res = await getBlacklist(page.value, pageSize)
    const rows = res.data || []
    list.value.push(...rows)
    const total = res.total
    const noMore = rows.length < pageSize || (total != null && list.value.length >= total)
    if (noMore) {
      finished.value = true
    } else {
      page.value++
    }
  } catch (e) {
    // 失败提示已由 request.js Toast
    finished.value = true
  } finally {
    loading.value = false
  }
}

async function onRemove(item) {
  try {
    await showConfirmDialog({
      title: '解除拉黑',
      message: `确定解除对「${item.workerName || '该打工人'}」的拉黑吗？解除后其可再次报名你的岗位。`
    })
  } catch (e) {
    return // 用户取消
  }
  try {
    await removeBlacklist(item.workerId)
    list.value = list.value.filter((i) => i.id !== item.id)
    showSuccessToast('已解除')
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}
</script>

<style scoped>
.blacklist {
  min-height: 100vh;
  padding-bottom: 24px;
}
.hint {
  margin: 10px 14px 0;
  font-size: 12px;
  color: var(--text-tertiary);
}
.bl-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 12px 0;
  padding: 12px 14px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.bl-item__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.bl-item__meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.bl-item__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.bl-item__reason {
  margin-top: 2px;
  font-size: 12px;
  color: var(--danger);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bl-item__time {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
