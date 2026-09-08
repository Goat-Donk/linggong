<template>
  <div class="employer-applications">
    <van-nav-bar title="审核报名" left-arrow @click-left="$router.back()" />

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div v-for="app in applications" :key="app.id" class="app-card">
        <div class="app-card__head" @click="goWorker(app.workerId)">
          <van-image
            v-if="app.workerIcon"
            round
            width="40"
            height="40"
            :src="app.workerIcon"
          />
          <van-icon v-else name="contact" size="40" color="#c8c9cc" />
          <div class="app-card__worker">
            <span class="app-card__name">
              {{ app.workerName || '未知用户' }}
              <span class="app-card__hint">查看主页 ›</span>
            </span>
            <span class="app-card__job">{{ app.jobName }}</span>
          </div>
          <van-tag :type="applyStatusType(app.status)">{{ formatApplyStatus(app.status) }}</van-tag>
        </div>

        <div class="app-card__foot">
          <span class="app-card__time">{{ formatDateTime(app.createTime) }}</span>
          <div v-if="app.status === 0" class="app-card__actions">
            <van-button size="small" type="danger" plain @click="onReject(app)">拒绝</van-button>
            <van-button size="small" type="primary" @click="onApprove(app)">通过</van-button>
          </div>
        </div>
      </div>

      <van-empty v-if="finished && applications.length === 0" description="暂无待审核报名" />
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import {
  getEmployerApplications,
  approveApplication,
  rejectApplication
} from '@/api/application'
import { formatApplyStatus, applyStatusType, formatDateTime } from '@/utils/format'

const router = useRouter()

const applications = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

async function onLoad() {
  try {
    const res = await getEmployerApplications(page.value, pageSize)
    const list = res.data || []
    applications.value.push(...list)
    const total = res.total
    const noMore = list.length < pageSize || (total != null && applications.value.length >= total)
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

// 查看报名人的求职主页（打工人简历）
function goWorker(workerId) {
  if (workerId) {
    router.push(`/worker-profile/view/${workerId}`)
  }
}

async function onApprove(app) {
  try {
    await approveApplication(app.id)
    app.status = 1
    showSuccessToast('已通过')
  } catch (e) {
    // 失败提示已由 request.js Toast（非本人岗位 / 已处理 / 越权等）
  }
}

async function onReject(app) {
  try {
    await rejectApplication(app.id)
    app.status = 3
    showSuccessToast('已拒绝')
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}
</script>

<style scoped>
.employer-applications {
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
  gap: 10px;
  cursor: pointer;
}
.app-card__head:active {
  opacity: 0.7;
}
.app-card__worker {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.app-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.app-card__hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--brand-primary);
  margin-left: 4px;
}
.app-card__job {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}
.app-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.app-card__time {
  font-size: 12px;
  color: var(--text-tertiary);
}
.app-card__actions {
  display: flex;
  gap: 8px;
}
</style>
