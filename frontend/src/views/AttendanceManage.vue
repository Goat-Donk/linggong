<template>
  <div class="attendance-manage">
    <van-nav-bar
      :title="inBoard ? '考勤核销' : '考勤核销'"
      left-arrow
      @click-left="onBack"
    />

    <van-empty v-if="!isEmployer" description="仅雇主可核销考勤" />

    <!-- 岗位选择模式 -->
    <template v-else-if="!inBoard">
      <div v-for="job in jobs" :key="job.id" class="job-item" @click="enterJob(job)">
        <div class="job-item__main">
          <span class="job-item__name">{{ job.name }}</span>
          <span class="job-item__count">{{ job.hiredCount }} 人已录用</span>
        </div>
        <div class="job-item__sub">
          <span>{{ formatSalary(job.salary) }}</span>
          <span v-if="job.startTime || job.endTime">{{ formatDateRange(job.startTime, job.endTime) }}</span>
          <span class="job-item__go">去核销 ›</span>
        </div>
      </div>
      <van-empty v-if="jobsLoadedOnce && !jobs.length" description="暂无已录用工人的岗位" />
    </template>

    <!-- 按日核销模式 -->
    <template v-else-if="day">
      <van-cell-group inset class="block">
        <van-cell title="岗位" :value="day.jobName" />
        <van-cell
          title="核销日期"
          :value="day.date"
          is-link
          @click="pickerShow = true"
        />
        <van-cell title="是否任务期内" :value="day.inPeriod ? '是' : '否'" :value-class="day.inPeriod ? '' : 'cell-off'" />
      </van-cell-group>

      <div class="worker-list">
        <div v-for="row in day.rows" :key="row.workerId" class="worker-card">
          <div class="worker-card__head">
            <van-image v-if="row.workerIcon" round width="38" height="38" :src="row.workerIcon" />
            <van-icon v-else name="contact" size="38" color="#c8c9cc" />
            <span class="worker-card__name">{{ row.workerName || '未知用户' }}</span>
            <van-tag :type="dayTagType(row)" round>{{ row.dayText }}</van-tag>
          </div>
          <div class="worker-card__sub">
            <span>到岗：{{ punchLabel(row.onStatus) }}</span>
            <span>下工：{{ punchLabel(row.offStatus) }}</span>
            <template v-if="row.onTime"> · {{ formatDateTime(row.onTime) }}</template>
            <template v-if="row.offTime"> · {{ formatDateTime(row.offTime) }}</template>
          </div>
          <div class="worker-card__actions">
            <!-- 到岗待核销 -->
            <template v-if="row.onStatus === 1">
              <van-button size="small" type="success" plain :loading="auditing" @click="audit(row, 'on', true)">到岗通过</van-button>
              <van-button size="small" type="danger" plain :loading="auditing" @click="audit(row, 'on', false)">到岗驳回</van-button>
            </template>
            <!-- 下工待核销 -->
            <template v-else-if="row.onStatus === 2 && row.offStatus === 1">
              <van-button size="small" type="success" plain :loading="auditing" @click="audit(row, 'off', true)">下工通过</van-button>
              <van-button size="small" type="danger" plain :loading="auditing" @click="audit(row, 'off', false)">下工驳回</van-button>
            </template>
            <!-- 已到岗未申请下工 → 补记完工 -->
            <template v-else-if="row.onStatus === 2 && row.offStatus === 0">
              <van-button size="small" type="primary" plain :loading="auditing" @click="confirm(row)">补记完工</van-button>
              <span class="action-hint">工人忘了申请下工，可补记</span>
            </template>
            <span v-else class="action-hint">今日该工人无需操作</span>
          </div>
        </div>
      </div>
    </template>

    <!-- 日期选择 -->
    <van-popup v-model:show="pickerShow" position="bottom" round>
      <van-date-picker
        :model-value="pickerValue"
        :min-date="minDate"
        :max-date="maxDate"
        title="选择核销日期"
        @confirm="onPickDate"
        @cancel="pickerShow = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import {
  getAttendanceJobs,
  getJobAttendance,
  auditAttendance,
  confirmAttendanceOff
} from '@/api/attendance'
import { userState } from '@/stores/user'
import { formatSalary, formatDateRange, formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const isEmployer = computed(() => userState.info?.role === 1)

const inBoard = computed(() => !!route.query.jobId)

// 岗位列表
const jobs = ref([])
const jobsLoadedOnce = ref(false)

// 核销看板
const day = ref(null)
const date = ref('') // 当前核销日期
const pickerShow = ref(false)
const pickerValue = ref([])
const minDate = ref(new Date(2020, 0, 1))
const maxDate = ref(new Date(2035, 11, 31))
const auditing = ref(false)

const onBack = () => {
  if (inBoard.value) {
    router.replace({ path: '/attendance/manage' })
  } else {
    router.back()
  }
}

function enterJob(job) {
  router.push({ path: '/attendance/manage', query: { jobId: job.id } })
}

async function loadJobs() {
  try {
    const res = await getAttendanceJobs()
    jobs.value = res.data || []
  } catch (e) {
    // 失败已 Toast
  } finally {
    jobsLoadedOnce.value = true
  }
}

async function loadDay(jobId) {
  day.value = null
  try {
    const res = await getJobAttendance(jobId, date.value || undefined)
    const data = res.data
    day.value = data
    date.value = data.date
  } catch (e) {
    // 失败已 Toast（非本人岗位等）
  }
}

function onPickDate({ selectedValues }) {
  const [y, m, d] = selectedValues
  const pad = (n) => String(n).padStart(2, '0')
  date.value = `${y}-${pad(m)}-${pad(d)}`
  pickerShow.value = false
  loadDay(route.query.jobId)
}

async function audit(row, punch, pass) {
  auditing.value = true
  try {
    await auditAttendance({
      jobId: Number(route.query.jobId),
      workerId: row.workerId,
      workDate: day.value.date,
      punch,
      pass
    })
    showSuccessToast(pass ? '已通过' : '已驳回')
    await loadDay(route.query.jobId)
  } catch (e) {
    // 失败已 Toast
  } finally {
    auditing.value = false
  }
}

async function confirm(row) {
  auditing.value = true
  try {
    await confirmAttendanceOff({
      jobId: Number(route.query.jobId),
      workerId: row.workerId,
      workDate: day.value.date
    })
    showSuccessToast('已补记完工（满勤 1 天）')
    await loadDay(route.query.jobId)
  } catch (e) {
    // 失败已 Toast
  } finally {
    auditing.value = false
  }
}

// 状态文案
const punchLabel = (s) => ({ 0: '未申请', 1: '待核销', 2: '已通过', 3: '已驳回' }[s] ?? '—')

function dayTagType(row) {
  if (row.onStatus === 3) return 'danger'
  if (row.onStatus === 2 && row.offStatus === 2) return 'success'
  if (row.onStatus === 2) return 'warning'
  if (row.onStatus === 1) return 'primary'
  return 'default'
}

watch(
  () => route.query.jobId,
  (jobId) => {
    if (jobId) {
      date.value = ''
      loadDay(jobId)
    } else if (!jobsLoadedOnce.value) {
      loadJobs()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.attendance-manage {
  min-height: 100vh;
  padding-bottom: 40px;
}
.block {
  margin-top: 8px;
}
.cell-off {
  color: var(--danger);
}
.job-item {
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.job-item__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.job-item__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.job-item__count {
  font-size: 12px;
  color: var(--text-tertiary);
}
.job-item__sub {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}
.job-item__go {
  margin-left: auto;
  color: var(--brand-primary);
}
.worker-list {
  padding: 0 12px;
}
.worker-card {
  margin-top: 12px;
  padding: 14px 14px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.worker-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.worker-card__name {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.worker-card__sub {
  margin: 8px 0 10px;
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.worker-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.action-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
