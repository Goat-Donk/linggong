<template>
  <div class="attendance-my">
    <van-nav-bar title="我的考勤" left-arrow @click-left="$router.back()" />

    <van-empty v-if="!isWorker" description="仅打工人可查看考勤" />

    <template v-else-if="view">
      <!-- 岗位信息 -->
      <div class="job-card">
        <div class="job-card__name">{{ view.jobName }}</div>
        <div class="job-card__meta">
          <span>{{ formatSalary(view.salary) }}</span>
          <span v-if="view.startTime || view.endTime">
            {{ formatDateRange(view.startTime, view.endTime) }}
          </span>
        </div>
      </div>

      <!-- 今日打卡 -->
      <van-cell-group inset title="今日">
        <van-cell title="出勤日期" :value="view.today" />
        <van-cell
          v-if="!view.hired"
          title="录用的岗位才能打卡"
          :label="view.hint"
        />
        <template v-else>
          <van-cell
            title="状态"
            :value="view.todayRow?.dayText || view.hint"
            :class="todayStateClass"
          />
          <van-cell title="操作">
            <div class="punch-actions">
              <van-button
                size="small"
                round
                type="primary"
                :disabled="!view.canOn"
                :loading="onLoading"
                @click="onPunch('on')"
              >
                我已到岗
              </van-button>
              <van-button
                size="small"
                round
                type="success"
                :disabled="!view.canOff"
                :loading="offLoading"
                @click="onPunch('off')"
              >
                请求下工
              </van-button>
              <span v-if="view.hint && view.hint !== '今天还未打卡'" class="punch-hint">
                {{ view.hint }}
              </span>
            </div>
          </van-cell>
        </template>
      </van-cell-group>

      <!-- 历史逐日记录 -->
      <van-cell-group inset title="逐日打卡记录">
        <van-cell
          v-for="row in view.records"
          :key="row.workDate"
          :title="row.workDate"
          :value="row.dayText"
          :value-class="valueClass(row)"
        >
          <template #label>
            <span v-if="row.onTime">到岗 {{ formatDateTime(row.onTime) }}</span>
            <span v-if="row.offTime"> · 下工 {{ formatDateTime(row.offTime) }}</span>
            <span v-if="!row.onTime && !row.offTime">当天未打卡</span>
          </template>
        </van-cell>
        <van-empty
          v-if="!view.records.length"
          description="暂无打卡记录"
          image-size="80"
        />
      </van-cell-group>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { showSuccessToast } from 'vant'
import { getMyAttendance, punchOn, punchOff } from '@/api/attendance'
import { userState } from '@/stores/user'
import { formatSalary, formatDateRange, formatDateTime } from '@/utils/format'

const route = useRoute()
const isWorker = computed(() => userState.info?.role === 0)

const view = ref(null)
const onLoading = ref(false)
const offLoading = ref(false)

const todayStateClass = computed(() => {
  const s = view.value?.todayRow
  if (!s) return ''
  if (s.onStatus === 3) return 'state-reject'
  if (s.onStatus === 2 && s.offStatus === 2) return 'state-done'
  return ''
})

function valueClass(row) {
  return row.onStatus === 2 && row.offStatus === 2 ? 'cell-value-ok' : ''
}

async function load() {
  const jobId = route.query.jobId
  if (!jobId) return
  try {
    const res = await getMyAttendance(jobId)
    view.value = res.data
  } catch (e) {
    // 失败已 Toast
  }
}

async function onPunch(kind) {
  const jobId = route.query.jobId
  if (kind === 'on') {
    onLoading.value = true
  } else {
    offLoading.value = true
  }
  try {
    if (kind === 'on') {
      await punchOn(jobId)
      showSuccessToast('已申请到岗，等待雇主核销')
    } else {
      await punchOff(jobId)
      showSuccessToast('已申请下工，等待雇主核销')
    }
    await load()
  } catch (e) {
    // 失败已 Toast（未录用 / 不在任务期 / 到岗未通过等）
  } finally {
    onLoading.value = false
    offLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.attendance-my {
  min-height: 100vh;
  padding-bottom: 32px;
}
.job-card {
  margin: 12px;
  padding: 16px 18px;
  background: linear-gradient(135deg, var(--brand-primary-light), var(--brand-primary));
  border-radius: var(--radius-card);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}
.job-card__name {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 6px;
}
.job-card__meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}
.punch-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.punch-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}
.state-reject :deep(.van-cell__value) {
  color: var(--danger);
}
.state-done :deep(.van-cell__value) {
  color: var(--success);
}
.cell-value-ok {
  color: var(--success);
}
</style>
