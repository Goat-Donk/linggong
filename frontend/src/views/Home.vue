<template>
  <div class="home">
    <van-nav-bar title="本地零工平台">
      <template #right>
        <van-icon name="fire-o" size="20" class="nav-icon" @click="$router.push('/feed')" />
        <van-icon name="user-o" size="20" @click="$router.push('/profile')" />
      </template>
    </van-nav-bar>

    <!-- 首页 hero：品牌区 -->
    <div class="hero">
      <div class="hero__text">
        <h2 class="hero__title">找个零工，就在附近</h2>
        <p class="hero__sub">真实岗位 · 工资日结 · 快速上手</p>
      </div>
    </div>

    <!-- 搜索框：回车/点搜索才生效，清除即重置为全量 -->
    <van-search
      v-model="keyword"
      placeholder="搜索岗位名称"
      shape="round"
      @search="onSearch"
      @clear="onSearchClear"
    />

    <!-- 分类 Tab（含「全部」） -->
    <van-tabs v-model:active="activeCategoryId" line-width="24">
      <van-tab v-for="cat in tabs" :key="cat.id" :name="cat.id" :title="cat.name" />
    </van-tabs>

    <!-- 排序 / 筛选 -->
    <van-dropdown-menu>
      <van-dropdown-item v-model="sort" :options="sortOptions" />
      <van-dropdown-item v-model="salaryKey" :options="salaryOptions" />
      <van-dropdown-item v-model="distanceValue" :options="distanceOptions" />
    </van-dropdown-menu>

    <!-- 岗位列表 -->
    <van-list
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
          <span v-if="job.distance != null" class="job-card__distance">
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
import { getCategories } from '@/api/category'
import { getJobList } from '@/api/job'
import { formatDistance, formatSalary, formatDateRange } from '@/utils/format'
import { userState } from '@/stores/user'

// 「全部」分类哨兵值：0 表示不按分类过滤（分类表 id 自增从 1 开始，不会冲突）
const ALL_CATEGORY_ID = 0

const categories = ref([])
const activeCategoryId = ref(ALL_CATEGORY_ID)

// 搜索：keyword 是输入框实时值，searchKeyword 是「已提交」的关键词，避免逐字触发查询
const keyword = ref('')
const searchKeyword = ref('')

// 排序 / 筛选状态
const sort = ref('latest') // latest 最新 / salary 薪资最高 / distance 距离最近
const salaryKey = ref('all') // all / lt200 / 200to400 / gt400
const distanceValue = ref(0) // 米，0 表示不限

const jobs = ref([])
const page = ref(1)
const finished = ref(false)
const loading = ref(false)
const pageSize = 10

// 过期请求序号：筛选条件变化时 ++，丢弃还在途中的旧请求结果，避免串数据
let requestSeq = 0

const sortOptions = [
  { text: '最新', value: 'latest' },
  { text: '薪资最高', value: 'salary' },
  { text: '距离最近', value: 'distance' }
]
const salaryOptions = [
  { text: '薪资不限', value: 'all' },
  { text: '200 以下', value: 'lt200' },
  { text: '200 - 400', value: '200to400' },
  { text: '400 以上', value: 'gt400' }
]
const distanceOptions = [
  { text: '距离不限', value: 0 },
  { text: '1 公里内', value: 1000 },
  { text: '3 公里内', value: 3000 },
  { text: '5 公里内', value: 5000 },
  { text: '10 公里内', value: 10000 }
]

// 分类 Tab：最前插入「全部」
const tabs = computed(() => [{ id: ALL_CATEGORY_ID, name: '全部' }, ...categories.value])

// 演示模式：种子数据集中在北京市区，距离排序/筛选固定以北京市中心为圆心，
// 保证任何位置打开都能看到按距离排序的结果。真实项目请改回 navigator.geolocation。
const DEMO_CENTER = { x: 116.4074, y: 39.9042 }

// 筛选条件变化 → 换 key 让 van-list 重挂载 → 自动重新触发首屏 onLoad
const listKey = computed(
  () => `${activeCategoryId.value}|${searchKeyword.value}|${sort.value}|${salaryKey.value}|${distanceValue.value}`
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
})

function onSearch() {
  searchKeyword.value = keyword.value.trim()
}

function onSearchClear() {
  searchKeyword.value = ''
}

// 清空搜索框（点 × 或手动删光）都重置为全量列表：
// van-search 的 @clear 只在点 × 时触发，手动逐字删光不会触发，这里兜底监听输入值。
watch(keyword, (val) => {
  if (val.trim() === '' && searchKeyword.value !== '') {
    searchKeyword.value = ''
  }
})

// 薪资筛选 key → 薪资区间
function salaryParams() {
  switch (salaryKey.value) {
    case 'lt200':
      return { maxSalary: 200 }
    case '200to400':
      return { minSalary: 200, maxSalary: 400 }
    case 'gt400':
      return { minSalary: 400 }
    default:
      return {}
  }
}

// van-list 触发的分页加载
async function onLoad() {
  const seq = ++requestSeq
  const needDistance = sort.value === 'distance' || distanceValue.value > 0
  const { minSalary, maxSalary } = salaryParams()
  const params = {
    keyword: searchKeyword.value || null,
    categoryId: activeCategoryId.value === ALL_CATEGORY_ID ? null : activeCategoryId.value,
    minSalary: minSalary ?? null,
    maxSalary: maxSalary ?? null,
    x: needDistance ? DEMO_CENTER.x : null,
    y: needDistance ? DEMO_CENTER.y : null,
    maxDistance: distanceValue.value > 0 ? distanceValue.value : null,
    sort: sort.value,
    page: page.value,
    pageSize
  }
  try {
    const res = await getJobList(params)
    if (seq !== requestSeq) return // 旧请求，丢弃
    const list = res.data || []
    jobs.value.push(...list)
    const total = res.total
    // 是否到底：返回不满一页，或有 total 且已累计到 total
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
.nav-icon {
  margin-right: 16px;
}
.hero {
  margin: 0 12px 12px;
  padding: 22px 18px;
  border-radius: var(--radius-card);
  background: linear-gradient(135deg, var(--brand-primary-light), var(--brand-primary));
  color: #fff;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}
.hero__title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.hero__sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.85;
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
  margin-bottom: 8px;
}
.job-card__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.job-card__salary {
  font-size: 16px;
  font-weight: 700;
  color: var(--danger);
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
.job-card__distance {
  color: var(--brand-primary);
}
.job-card__time {
  margin-left: auto;
}
.fab {
  position: fixed;
  right: 16px;
  bottom: 70px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 18px;
  background: var(--brand-primary);
  color: #fff;
  border-radius: 24px;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.4);
  z-index: 10;
}
.fab__text {
  font-size: 14px;
  font-weight: 600;
}
</style>
