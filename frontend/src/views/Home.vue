<template>
  <div class="home">
    <van-nav-bar title="本地零工平台">
      <template #right>
        <van-icon name="user-o" size="20" @click="$router.push('/profile')" />
      </template>
    </van-nav-bar>

    <!-- 附近模式开关 -->
    <div class="nearby-bar">
      <span class="nearby-bar__label">
        <van-icon name="location-o" /> 只看附近
      </span>
      <van-switch :model-value="nearbyMode" size="20" @update:model-value="onNearbyToggle" />
    </div>

    <!-- 分类 Tab -->
    <van-tabs v-model:active="activeCategoryId" line-width="24">
      <van-tab v-for="cat in categories" :key="cat.id" :name="cat.id" :title="cat.name" />
    </van-tabs>

    <!-- 岗位列表 -->
    <van-list
      v-if="categories.length"
      :key="listKey"
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多岗位了"
      @load="onLoad"
    >
      <div
        v-for="job in jobs"
        :key="job.id"
        class="job-card"
        @click="$router.push(`/job/${job.id}`)"
      >
        <div class="job-card__head">
          <span class="job-card__name">{{ job.name }}</span>
          <span class="job-card__salary">{{ formatSalary(job.salary) }}</span>
        </div>
        <div class="job-card__row">
          <van-icon name="location-o" />
          <span class="job-card__address">{{ job.address }}</span>
          <span v-if="nearbyMode && job.distance != null" class="job-card__distance">
            {{ formatDistance(job.distance) }}
          </span>
        </div>
        <div class="job-card__row">
          <van-icon name="friends-o" />
          <span>招 {{ job.headcount }} 人</span>
          <span v-if="job.startTime || job.endTime" class="job-card__time">
            {{ formatDateRange(job.startTime, job.endTime) }}
          </span>
        </div>
      </div>

      <van-empty v-if="finished && jobs.length === 0" description="暂无岗位" />
    </van-list>

    <!-- 雇主发布入口：悬浮按钮，仅雇主可见 -->
    <div v-if="userState.info?.role === 1" class="fab" @click="$router.push('/publish')">
      <van-icon name="plus" size="20" color="#fff" />
      <span class="fab__text">发布</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { showToast, showLoadingToast, closeToast } from 'vant'
import { getCategories } from '@/api/category'
import { getJobsByCategory, getNearbyJobs } from '@/api/job'
import { formatDistance, formatSalary, formatDateRange } from '@/utils/format'
import { userState } from '@/stores/user'

const categories = ref([])
const activeCategoryId = ref(null)
const nearbyMode = ref(false)
const coords = ref(null) // { x: 经度, y: 纬度 }

const jobs = ref([])
const page = ref(1)
const finished = ref(false)
const loading = ref(false)
const pageSize = 10

// 过期请求序号：筛选条件变化时 ++，丢弃还在途中的旧请求结果，避免串数据
let requestSeq = 0

// 筛选条件变化 → 换 key 让 van-list 重挂载 → 自动重新触发首屏 onLoad
const listKey = computed(
  () => `${activeCategoryId.value}|${nearbyMode.value}|${coords.value?.x}|${coords.value?.y}`
)

watch(listKey, () => {
  requestSeq++
  jobs.value = []
  page.value = 1
  finished.value = false
  loading.value = false
})

onMounted(async () => {
  const res = await getCategories()
  categories.value = res.data || []
  if (categories.value.length) {
    activeCategoryId.value = categories.value[0].id
  }
})

// 切换「只看附近」：先定位成功再真正切换，避免附近模式没坐标就发请求
function onNearbyToggle(val) {
  if (!val) {
    coords.value = null
    nearbyMode.value = false
    return
  }
  if (!navigator.geolocation) {
    showToast('当前浏览器不支持定位')
    return
  }
  showLoadingToast({ message: '定位中...', forbidClick: true })
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      closeToast()
      coords.value = { x: pos.coords.longitude, y: pos.coords.latitude }
      nearbyMode.value = true
    },
    () => {
      closeToast()
      showToast('定位失败，请检查定位权限')
    },
    { enableHighAccuracy: false, timeout: 8000, maximumAge: 30000 }
  )
}

// van-list 触发的分页加载
async function onLoad() {
  const categoryId = activeCategoryId.value
  if (categoryId == null) {
    loading.value = false
    return
  }
  const seq = ++requestSeq
  try {
    const res = nearbyMode.value
      ? await getNearbyJobs({
          categoryId,
          x: coords.value.x,
          y: coords.value.y,
          page: page.value,
          pageSize
        })
      : await getJobsByCategory(categoryId, page.value, pageSize)
    if (seq !== requestSeq) return // 旧请求，丢弃
    const list = res.data || []
    jobs.value.push(...list)
    const total = res.total
    // 是否到底：返回不满一页，或有 total 且已累计到 total。
    // 附近搜索后端不返回 total（为 null），此时只靠「不满一页」判断。
    const noMore = list.length < pageSize || (total != null && jobs.value.length >= total)
    if (noMore) {
      finished.value = true
    } else {
      page.value++
    }
  } catch (e) {
    // 失败提示已由 request.js Toast
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}
</script>

<style scoped>
.home {
  padding-bottom: 80px;
}
.nearby-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f1f2;
}
.nearby-bar__label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #646566;
}
.job-card {
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: #fff;
  border-radius: 8px;
}
.job-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.job-card__name {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}
.job-card__salary {
  font-size: 16px;
  font-weight: 600;
  color: #ee0a24;
}
.job-card__row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 13px;
  color: #969799;
}
.job-card__address {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.job-card__distance {
  color: #1989fa;
}
.job-card__time {
  margin-left: auto;
}
.fab {
  position: fixed;
  right: 16px;
  bottom: 40px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 18px;
  background: #1989fa;
  color: #fff;
  border-radius: 24px;
  box-shadow: 0 4px 12px rgba(25, 137, 250, 0.4);
  z-index: 10;
}
.fab__text {
  font-size: 14px;
  font-weight: 600;
}
</style>
