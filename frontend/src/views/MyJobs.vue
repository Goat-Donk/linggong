<template>
  <div class="my-jobs">
    <van-nav-bar title="我的岗位" left-arrow @click-left="$router.back()" />

    <van-empty v-if="!isEmployer" description="仅雇主可查看岗位" />

    <template v-else>
      <!-- 经营汇总条 -->
      <div class="summary-card">
        <div class="summary-card__title">
          <span>经营汇总</span>
          <span class="summary-card__hint">担保冻结中 = 未结算岗位的保证金</span>
        </div>
        <div class="summary-grid">
          <div class="summary-item">
            <span class="summary-item__label">累计发岗</span>
            <span class="summary-item__value">{{ summary.totalJobs ?? 0 }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">招聘中</span>
            <span class="summary-item__value">{{ summary.hiringJobs ?? 0 }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">冻结担保</span>
            <span class="summary-item__value">{{ formatMoney(summary.frozenAmount) }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">已结算岗位</span>
            <span class="summary-item__value">{{ summary.settledJobs ?? 0 }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">累计服务费</span>
            <span class="summary-item__value">{{ formatMoney(summary.totalServiceFee) }}</span>
          </div>
        </div>
      </div>

      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div v-for="job in jobs" :key="job.id" class="job-card">
          <div class="job-card__head">
            <span class="job-card__name">{{ job.name }}</span>
            <van-tag :type="job.status === 0 ? 'success' : 'default'">
              {{ job.status === 0 ? '招聘中' : '已下架' }}
            </van-tag>
          </div>
          <div class="job-card__row">
            <van-icon name="gold-coin-o" />
            <span>{{ formatSalary(job.salary) }}</span>
            <span class="job-card__count">招 {{ job.headcount }} 人</span>
          </div>
          <div class="job-card__row">
            <van-icon name="location-o" />
            <span class="job-card__address">{{ job.address || '地址待定' }}</span>
          </div>
          <div v-if="job.startTime || job.endTime" class="job-card__row">
            <van-icon name="clock-o" />
            <span>{{ formatDateRange(job.startTime, job.endTime) }}</span>
          </div>
          <div class="job-card__stats">
            <span v-if="job.settled" class="stat stat--done">已结算</span>
            <span v-else class="stat">已录用 {{ job.hiredCount }} 人</span>
            <span v-if="job.pendingCount" class="stat stat--warn">待确认 {{ job.pendingCount }} 人</span>
            <span class="stat">冻结 {{ formatMoney(job.frozenAmount) }}</span>
          </div>
          <div v-if="!job.settled && job.status === 0" class="job-card__actions">
            <van-button size="small" plain round @click="onEdit(job)">编辑</van-button>
            <van-button
              size="small"
              type="danger"
              plain
              round
              :loading="offingId === job.id"
              @click="onOffShelf(job)"
            >
              下架
            </van-button>
          </div>
        </div>

        <van-empty v-if="finished && jobs.length === 0" description="还没有发布岗位" />
      </van-list>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { getMyJobs, offShelfJob, getMyJobSummary } from '@/api/job'
import { userState } from '@/stores/user'
import { formatSalary, formatDateRange, formatMoney } from '@/utils/format'

const router = useRouter()
const isEmployer = computed(() => userState.info?.role === 1)

const summary = ref({})
const jobs = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const offingId = ref(null)

async function onLoad() {
  if (!isEmployer.value) {
    finished.value = true
    return
  }
  try {
    const res = await getMyJobs(page.value, pageSize)
    const list = res.data || []
    jobs.value.push(...list)
    const total = res.total
    const noMore = list.length < pageSize || (total != null && jobs.value.length >= total)
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

// 经营汇总条：进页拉一次（仅雇主）
async function loadSummary() {
  if (!isEmployer.value) return
  try {
    const res = await getMyJobSummary()
    summary.value = res.data || {}
  } catch (e) {
    // 失败静默：汇总条为空不影响列表
  }
}
loadSummary()

function onEdit(job) {
  router.push({ path: '/publish', query: { edit: job.id } })
}

async function onOffShelf(job) {
  try {
    await showConfirmDialog({
      title: '下架岗位',
      message: `确定下架「${job.name}」吗？下架后不再展示给打工人。`
    })
  } catch (e) {
    return // 用户取消
  }
  offingId.value = job.id
  try {
    await offShelfJob(job.id)
    job.status = 1 // 本地直接置为已下架，避免整页刷新
    showSuccessToast('已下架')
  } catch (e) {
    // 失败提示已由 request.js Toast（已有录用工人在岗等）
  } finally {
    offingId.value = null
  }
}
</script>

<style scoped>
.my-jobs {
  min-height: 100vh;
  padding-bottom: 24px;
}
.summary-card {
  margin: 12px 12px 0;
  padding: 14px 16px 12px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.summary-card__title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.summary-card__hint {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-tertiary);
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px 8px;
  margin-top: 12px;
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-item__label {
  font-size: 12px;
  color: var(--text-tertiary);
}
.summary-item__value {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.job-card {
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.job-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.job-card__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.job-card__row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.job-card__address {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.job-card__count {
  margin-left: 12px;
  color: var(--text-tertiary);
}
.job-card__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.stat {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-secondary, #f7f8fa);
  padding: 2px 8px;
  border-radius: 10px;
}
.stat--done {
  color: var(--brand-primary);
}
.stat--warn {
  color: var(--warning, #ff976a);
}
.job-card__actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line, #ebedf0);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
