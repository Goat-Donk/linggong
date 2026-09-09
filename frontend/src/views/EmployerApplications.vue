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
          <div class="app-card__actions">
            <van-tag v-if="app.blacklisted" type="danger" plain class="bl-tag">已拉黑</van-tag>
            <van-button v-else size="small" type="default" plain @click="onBlacklist(app)">拉黑</van-button>
            <van-button size="small" type="default" plain @click="goChat(app)">联系TA</van-button>
            <van-button v-if="app.status === 0" size="small" type="danger" plain @click="onReject(app)">拒绝</van-button>
            <van-button v-if="app.status === 0" size="small" type="primary" @click="onApprove(app)">通过</van-button>
            <van-button
              v-if="app.status === 1"
              size="small"
              type="warning"
              plain
              :loading="dismissingId === app.id"
              @click="onDismiss(app)"
            >
              取消录用
            </van-button>
          </div>
        </div>
      </div>

      <van-empty v-if="finished && applications.length === 0" description="暂无待审核报名" />
    </van-list>

    <!-- 拉黑原因选择（选择后立即拉黑该报名工人） -->
    <van-action-sheet
      v-model:show="actionShow"
      :title="actionTitle"
      :actions="reasonActions"
      cancel-text="取消"
      close-on-click-action
      @select="onReasonSelect"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import {
  getEmployerApplications,
  approveApplication,
  rejectApplication,
  dismissApplication
} from '@/api/application'
import { addBlacklist } from '@/api/blacklist'
import { formatApplyStatus, applyStatusType, formatDateTime } from '@/utils/format'

const router = useRouter()

const applications = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const dismissingId = ref(null)

// 拉黑原因选择（ActionSheet 组件版）
const BLACKLIST_REASONS = ['恶意报名后取消', '录用后放鸽子', '骚扰 / 不当言行', '其他']
const reasonActions = BLACKLIST_REASONS.map((name) => ({ name }))
const actionShow = ref(false)
const actionTitle = ref('')
let blacklistTarget = null // 当前待拉黑的报名条目（仅事件触发时引用，无需响应式）

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

// 联系该报名工人：进入围绕该岗位的聊天会话
function goChat(app) {
  router.push(`/chat/${app.workerId}?jobId=${app.jobId}`)
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

// 取消已录用(1)的录用：名额释放可补招。仅当该工人无已核销到岗时才允许，
// 避免对已做工的工人赖账（后端兜底校验，前端仅二次确认）。
async function onDismiss(app) {
  try {
    await showConfirmDialog({
      title: '取消录用',
      message: `确定取消对「${app.workerName}」的录用吗？名额会释放可重新招人，该工人会收到通知，且你的信用分将 −10。`
    })
  } catch (e) {
    return // 用户取消
  }
  dismissingId.value = app.id
  try {
    await dismissApplication(app.id)
    app.status = 3
    showSuccessToast('已取消录用')
  } catch (e) {
    // 失败提示已由 request.js Toast（已有核销考勤不允许取消等）
  } finally {
    dismissingId.value = null
  }
}

// 打开拉黑原因选择面板：先选中条目，再让用户挑原因
function onBlacklist(app) {
  blacklistTarget = app
  actionTitle.value = `拉黑「${app.workerName || '该工人'}」？拉黑后其无法再报名你的岗位`
  actionShow.value = true
}

// 用户点选某个原因 → 立即拉黑该工人（自动取消其待确认报名、静默不通知）
async function onReasonSelect(action) {
  const app = blacklistTarget
  blacklistTarget = null
  if (!app || !action?.name) return
  try {
    await addBlacklist(app.workerId, action.name)
    app.blacklisted = true
    showSuccessToast('已拉黑')
  } catch (e) {
    // 失败提示已由 request.js Toast（已达上限 / 重复拉黑 / 目标不是打工人等）
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
  align-items: center;
}
.bl-tag {
  align-self: center;
}
</style>
