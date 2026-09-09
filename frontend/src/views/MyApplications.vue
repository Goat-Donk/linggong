<template>
  <div class="my-applications">
    <van-nav-bar title="我的报名" left-arrow @click-left="$router.back()" />

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div
        v-for="app in applications"
        :key="app.id"
        class="app-card"
        @click="$router.push(`/job/${app.jobId}`)"
      >
        <div class="app-card__head">
          <span class="app-card__name">{{ app.jobName }}</span>
          <van-tag :type="applyStatusType(app.status)">{{ formatApplyStatus(app.status) }}</van-tag>
        </div>
        <div class="app-card__row">
          <van-icon name="gold-coin-o" />
          <span>{{ formatSalary(app.salary) }}</span>
        </div>
        <div class="app-card__row">
          <van-icon name="location-o" />
          <span class="app-card__address">{{ app.address || '地址待定' }}</span>
          <span class="app-card__time">{{ formatDateTime(app.createTime) }}</span>
        </div>
        <div
          v-if="app.status === 1"
          class="app-card__att"
          @click.stop="$router.push(`/attendance/my?jobId=${app.jobId}`)"
        >
          <van-icon name="clock-o" />
          <span>今日考勤 ›</span>
        </div>
        <div
          v-if="app.status === 0"
          class="app-card__actions"
        >
          <van-button
            size="small"
            type="danger"
            plain
            round
            :loading="cancelingId === app.id"
            @click.stop="onCancel(app)"
          >
            撤销报名
          </van-button>
        </div>
      </div>

      <van-empty v-if="finished && applications.length === 0" description="还没有报名记录" />
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { getMyApplications, cancelApplication } from '@/api/application'
import { formatApplyStatus, applyStatusType, formatSalary, formatDateTime } from '@/utils/format'

const applications = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const cancelingId = ref(null)

async function onLoad() {
  try {
    const res = await getMyApplications(page.value, pageSize)
    const list = res.data || []
    applications.value.push(...list)
    const total = res.total
    // 到底判断：不满一页，或有 total 且已累计到 total
    const noMore = list.length < pageSize || (total != null && applications.value.length >= total)
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

// 撤销待确认(0)的报名：二次确认后置为已取消(3)，名额随之释放
async function onCancel(app) {
  try {
    await showConfirmDialog({
      title: '撤销报名',
      message: `确定撤销「${app.jobName}」的报名吗？撤销后名额会释放。`
    })
  } catch (e) {
    return // 用户取消
  }
  cancelingId.value = app.id
  try {
    await cancelApplication(app.id)
    app.status = 3 // 本地直接置为已取消，避免整页刷新
    showSuccessToast('已撤销报名')
  } catch (e) {
    // 失败提示已由 request.js Toast
  } finally {
    cancelingId.value = null
  }
}
</script>

<style scoped>
.my-applications {
  min-height: 100vh;
  padding-bottom: 24px;
}
.app-card {
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.app-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.app-card__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.app-card__row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.app-card__address {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.app-card__time {
  margin-left: auto;
  color: var(--text-tertiary);
}
.app-card__att {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line, #ebedf0);
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--brand-primary);
}
.app-card__actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line, #ebedf0);
  display: flex;
  justify-content: flex-end;
}
</style>
