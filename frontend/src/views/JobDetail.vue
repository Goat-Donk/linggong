<template>
  <div class="job-detail">
    <van-nav-bar title="岗位详情" left-arrow @click-left="$router.back()">
      <template #right>
        <van-icon
          v-if="userState.token && job"
          :name="favorited ? 'star' : 'star-o'"
          :color="favorited ? '#ffb62b' : '#969799'"
          size="22"
          @click="onToggleFavorite"
        />
      </template>
    </van-nav-bar>

    <div v-if="!job && !loadFailed" class="loading">
      <van-loading size="24">加载中...</van-loading>
    </div>

    <van-empty v-else-if="loadFailed" description="岗位不存在或已删除" />

    <template v-else>
      <div class="head-card">
        <div class="head-card__title">
          <span class="head-card__name">{{ job.name }}</span>
          <span class="head-card__salary">{{ formatSalary(job.salary) }}</span>
        </div>
        <div class="head-card__tags">
          <van-tag v-if="job.status !== 0" type="danger">已下架</van-tag>
          <van-tag type="warning" plain>招 {{ job.headcount }} 人</van-tag>
        </div>
      </div>

      <van-cell-group inset class="info-group">
        <van-cell title="地址" :value="job.address" icon="location-o" />
        <van-cell
          v-if="job.startTime || job.endTime"
          title="时间"
          :value="formatDateRange(job.startTime, job.endTime)"
          icon="clock-o"
        />
        <van-cell title="名额" :value="`${job.headcount} 人`" icon="friends-o" />
      </van-cell-group>

      <div v-if="publisher" class="publisher">
        <van-image v-if="publisher.icon" round width="40" height="40" :src="publisher.icon" />
        <van-icon v-else name="contact" size="40" color="#c8c9cc" />
        <div class="publisher__meta">
          <span class="publisher__name">{{ publisher.nickName }}</span>
          <span class="publisher__sub">发布者</span>
        </div>
      </div>

      <div class="desc">
        <h3 class="desc__title">岗位描述</h3>
        <p class="desc__content">{{ job.description || '暂无描述' }}</p>
      </div>

      <!-- 互评 -->
      <div class="eval-section">
        <div class="eval-section__head">
          <h3 class="eval-section__title">评价（{{ evalTotal }}）</h3>
          <van-button v-if="evalAction" size="mini" type="primary" plain @click="openEvaluate">
            {{ evalAction.text }}
          </van-button>
        </div>

        <div v-for="e in evaluations" :key="e.id" class="eval-item">
          <div class="eval-item__head">
            <van-image v-if="e.fromIcon" round width="28" height="28" :src="e.fromIcon" />
            <van-icon v-else name="contact" size="28" color="#c8c9cc" />
            <span class="eval-item__name">{{ e.fromNickName }}</span>
            <van-rate :model-value="e.rating" readonly :size="14" />
            <span class="eval-item__to">评 {{ e.toNickName }}</span>
          </div>
          <p v-if="e.content" class="eval-item__content">{{ e.content }}</p>
          <p class="eval-item__time">{{ formatRelativeTime(e.createTime) }}</p>
        </div>
        <van-empty v-if="evaluations.length === 0" description="暂无评价" />
      </div>
    </template>

    <div v-if="job" class="apply-bar">
      <van-button
        round
        block
        type="primary"
        :disabled="!canApply"
        :loading="applying"
        @click="onApply"
      >
        {{ applyText }}
      </van-button>
    </div>

    <!-- 评价表单弹层 -->
    <van-popup v-model:show="evalShow" position="bottom" round>
      <div class="eval-form">
        <h4 class="eval-form__title">{{ evalTargetName }}</h4>
        <div class="eval-form__rate">
          <van-rate v-model="evalForm.rating" :count="5" :size="28" />
        </div>
        <van-field
          v-model="evalForm.content"
          type="textarea"
          rows="3"
          placeholder="说说这次合作体验（选填）"
          maxlength="1024"
          show-word-limit
        />
        <van-button
          class="eval-form__submit"
          round
          block
          type="primary"
          :loading="evalSubmitting"
          @click="submitEval"
        >
          提交评价
        </van-button>
      </div>
    </van-popup>

    <!-- 雇主选工人弹层 -->
    <van-action-sheet
      v-model:show="workerSheetShow"
      :actions="workerActions"
      title="选择要评价的工人"
      @select="onPickWorker"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getJobById } from '@/api/job'
import { getUserById } from '@/api/user'
import { applyJob, getMyApplications, getEmployerApplications } from '@/api/application'
import { getEvaluationsByJob, publishEvaluation } from '@/api/evaluation'
import { favoriteJob, isFavorited } from '@/api/favorite'
import { userState } from '@/stores/user'
import { formatSalary, formatDateRange, formatRelativeTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const job = ref(null)
const publisher = ref(null)
const applying = ref(false)
const applied = ref(false)
const loadFailed = ref(false)

// 收藏状态
const favorited = ref(false)

// 互评相关状态
const evaluations = ref([])
const evalTotal = ref(0)
const evalShow = ref(false)
const evalSubmitting = ref(false)
const evalForm = reactive({ toUserId: null, rating: 5, content: '' })
const evalTargetName = ref('')
const workerSheetShow = ref(false)
const workerActions = ref([])

const canApply = computed(() => {
  if (!job.value) return false
  if (applied.value) return false
  if (job.value.status !== 0) return false
  if (userState.info && userState.info.id === job.value.employerId) return false
  return true
})

const applyText = computed(() => {
  if (!job.value) return '报名'
  if (job.value.status !== 0) return '已下架'
  if (applied.value) return '已报名'
  if (userState.info && userState.info.id === job.value.employerId) return '自己发布的岗位'
  return '立即报名'
})

// 谁能评价：雇主 → 选工人评；已报名工人 → 评雇主；其余不显示
const evalAction = computed(() => {
  if (!job.value || !userState.token) return null
  if (userState.info?.id === job.value.employerId) return { text: '评价工人', kind: 'worker' }
  if (applied.value) return { text: '评价雇主', kind: 'employer' }
  return null
})

onMounted(async () => {
  try {
    const res = await getJobById(route.params.id)
    job.value = res.data
  } catch (e) {
    loadFailed.value = true
    return
  }
  // 发布者信息需登录（/user/{id} 需登录），匿名跳过，不影响详情展示
  if (userState.token) {
    try {
      const p = await getUserById(job.value.employerId)
      publisher.value = p.data
    } catch (e) {
      // 拉不到发布者不阻断详情
    }
  }
  // 评价列表公开可看（后端已放行 GET /evaluation）
  loadEvaluations()
  // 已登录且非雇主：查是否已报名，决定「评价雇主」按钮 + 报名按钮状态
  if (userState.token && userState.info?.id !== job.value.employerId) {
    checkApplied()
  }
  // 已登录：查是否已收藏（自己发布的岗位也允许收藏）
  if (userState.token) {
    checkFavorited()
  }
})

// 查收藏状态：星标点亮
async function checkFavorited() {
  try {
    const res = await isFavorited(job.value.id)
    favorited.value = !!res.data
  } catch (e) {
    // 查不到收藏状态不影响浏览
  }
}

// 切换收藏 / 取消收藏
async function onToggleFavorite() {
  const next = !favorited.value
  try {
    await favoriteJob(job.value.id, next)
    favorited.value = next
    showSuccessToast(next ? '已收藏' : '已取消收藏')
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}

async function onApply() {
  if (!userState.token) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  applying.value = true
  try {
    await applyJob(job.value.id)
    applied.value = true
    showSuccessToast('报名成功，等待雇主确认')
  } catch (e) {
    // 失败提示已由 request.js Toast（名额满/重复报名/不能报自己岗位等）
  } finally {
    applying.value = false
  }
}

async function loadEvaluations() {
  try {
    const res = await getEvaluationsByJob(job.value.id, 1, 10)
    evaluations.value = res.data || []
    evalTotal.value = res.total || 0
  } catch (e) {
    // 拉不到评价不阻断详情
  }
}

// 查历史报名状态：让「已报名」与「评价雇主」按钮在刷新后依然正确
async function checkApplied() {
  try {
    const res = await getMyApplications(1, 50)
    const list = res.data || []
    applied.value = list.some((a) => a.jobId === job.value.id)
  } catch (e) {
    // 查不到报名状态不影响浏览
  }
}

async function openEvaluate() {
  if (!userState.token) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const action = evalAction.value
  if (!action) return
  if (action.kind === 'worker') {
    await loadApplicants() // 雇主：先选要评价的工人
  } else {
    startEval(job.value.employerId, publisher.value?.nickName || '雇主')
  }
}

async function loadApplicants() {
  try {
    const res = await getEmployerApplications(1, 100)
    const list = (res.data || []).filter((a) => a.jobId === job.value.id)
    if (list.length === 0) {
      showToast('该岗位还没有报名的工人')
      return
    }
    workerActions.value = list.map((a) => ({
      name: a.workerName || `用户${a.workerId}`,
      workerId: a.workerId,
      workerName: a.workerName
    }))
    workerSheetShow.value = true
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}

function onPickWorker(action) {
  workerSheetShow.value = false
  startEval(action.workerId, action.name)
}

function startEval(toUserId, name) {
  evalForm.toUserId = toUserId
  evalForm.rating = 5
  evalForm.content = ''
  evalTargetName.value = `评价 ${name}`
  evalShow.value = true
}

async function submitEval() {
  evalSubmitting.value = true
  try {
    await publishEvaluation({
      jobId: job.value.id,
      toUserId: evalForm.toUserId,
      rating: evalForm.rating,
      content: evalForm.content
    })
    showSuccessToast('评价成功')
    evalShow.value = false
    loadEvaluations() // 刷新列表
  } catch (e) {
    // 失败提示已由 request.js Toast（不能评自己/未报名/已评价过等）
  } finally {
    evalSubmitting.value = false
  }
}
</script>

<style scoped>
.job-detail {
  min-height: 100vh;
  padding-bottom: 80px;
}
.loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}
.head-card {
  margin: 12px;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.head-card__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.head-card__name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}
.head-card__salary {
  font-size: 18px;
  font-weight: 700;
  color: var(--danger);
}
.head-card__tags {
  display: flex;
  gap: 8px;
}
.info-group {
  margin: 0 12px;
}
.publisher {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px;
  padding: 12px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.publisher__meta {
  display: flex;
  flex-direction: column;
}
.publisher__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.publisher__sub {
  font-size: 12px;
  color: var(--text-tertiary);
}
.desc {
  margin: 12px;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.desc__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 10px;
}
.desc__content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
}
.apply-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
}
.eval-section {
  margin: 12px;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.eval-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.eval-section__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.eval-item {
  padding: 12px 0;
  border-top: 1px solid var(--border-color);
}
.eval-item:first-of-type {
  border-top: none;
  padding-top: 0;
}
.eval-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.eval-item__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.eval-item__to {
  font-size: 12px;
  color: var(--text-tertiary);
}
.eval-item__content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary);
  margin-top: 8px;
  word-break: break-word;
}
.eval-item__time {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 6px;
}
.eval-form {
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
}
.eval-form__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}
.eval-form__rate {
  margin-bottom: 12px;
}
.eval-form__submit {
  margin-top: 16px;
}
</style>
