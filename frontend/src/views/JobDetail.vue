<template>
  <div class="job-detail">
    <van-nav-bar title="岗位详情" left-arrow @click-left="$router.back()" />

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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { getJobById } from '@/api/job'
import { getUserById } from '@/api/user'
import { applyJob } from '@/api/application'
import { userState } from '@/stores/user'
import { formatSalary, formatDateRange } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const job = ref(null)
const publisher = ref(null)
const applying = ref(false)
const applied = ref(false)
const loadFailed = ref(false)

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
})

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
  background: #fff;
  border-radius: 8px;
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
  color: #323233;
}
.head-card__salary {
  font-size: 18px;
  font-weight: 600;
  color: #ee0a24;
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
  background: #fff;
  border-radius: 8px;
}
.publisher__meta {
  display: flex;
  flex-direction: column;
}
.publisher__name {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}
.publisher__sub {
  font-size: 12px;
  color: #c8c9cc;
}
.desc {
  margin: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
}
.desc__title {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 10px;
}
.desc__content {
  font-size: 14px;
  line-height: 1.6;
  color: #646566;
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
</style>
