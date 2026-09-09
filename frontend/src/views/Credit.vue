<template>
  <div class="credit">
    <van-nav-bar title="信用详情" left-arrow @click-left="$router.back()" />

    <!-- 信用分卡：当前分数 + 等级说明 -->
    <div class="credit-card" :class="`credit-card--${levelKey}`">
      <div class="credit-card__label">当前信用分</div>
      <div class="credit-card__score">
        {{ credit }}<span class="credit-card__max"> / 100</span>
      </div>
      <div class="credit-card__tip">{{ levelTip }}</div>
    </div>

    <!-- 评分规则说明 -->
    <div class="rule-card">
      <div class="rule-card__title">信用分怎么变</div>
      <div class="rule-card__row">
        <van-icon name="good-job-o" color="#07c160" />
        <span>收到 4~5 星好评 <b>+1 / +2</b></span>
      </div>
      <div class="rule-card__row">
        <van-icon name="warning-o" color="var(--warning)" />
        <span>收到 1~2 星差评 <b class="is-down">−3</b></span>
      </div>
      <div class="rule-card__row">
        <van-icon name="clock-o" color="var(--danger)" />
        <span>已录用后退岗 / 取消录用（放鸽子）<b class="is-down">−10</b></span>
      </div>
      <div class="rule-card__row rule-card__row--muted">
        <van-icon name="info-o" />
        <span>信用分由系统按履约记录自动评定，本人不可修改</span>
      </div>
    </div>

    <!-- 变动流水 -->
    <div class="logs-head">
      <span>变动记录</span>
      <span class="logs-head__hint">正为加分 · 负为扣分</span>
    </div>
    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div v-for="log in logs" :key="log.id" class="log-item">
        <div class="log-item__left">
          <van-tag :type="typeColor(log.reasonType)" plain class="log-item__type">
            {{ typeText(log.reasonType) }}
          </van-tag>
          <span class="log-item__remark">{{ log.remark }}</span>
        </div>
        <div class="log-item__right">
          <span :class="['log-item__change', log.changeAmount >= 0 ? 'is-in' : 'is-out']">
            {{ log.changeAmount >= 0 ? '+' : '' }}{{ log.changeAmount }}
          </span>
          <span class="log-item__meta">
            变后 {{ log.afterCredit }} 分 · {{ formatDateTime(log.createTime) }}
          </span>
        </div>
      </div>

      <van-empty v-if="finished && logs.length === 0" description="还没有信用变动记录" />
    </van-list>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { getMyCredit, getCreditLogs } from '@/api/credit'
import { formatDateTime } from '@/utils/format'

const credit = ref(100)
const logs = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

// 分数 → 等级（决定卡片底色与说明文案）
const level = computed(() => {
  if (credit.value >= 90) return { key: 'good', tip: '信用极好，履约记录优秀' }
  if (credit.value >= 70) return { key: 'mid', tip: '信用良好，请继续保持按时履约' }
  if (credit.value >= 50) return { key: 'low', tip: '信用偏低，请避免放鸽子等违约行为' }
  return { key: 'bad', tip: '信用较低，频繁违约可能影响后续接单' }
})
const levelKey = computed(() => level.value.key)
const levelTip = computed(() => level.value.tip)

function typeText(t) {
  return { EVALUATION: '互评', BREAK_HIRE: '放鸽子' }[t] || t || '信用变动'
}
function typeColor(t) {
  return { EVALUATION: 'primary', BREAK_HIRE: 'danger' }[t] || 'default'
}

async function loadCredit() {
  try {
    const res = await getMyCredit()
    credit.value = res.data ?? 100
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}
loadCredit()

async function onLoad() {
  try {
    const res = await getCreditLogs(page.value, pageSize)
    const list = res.data || []
    logs.value.push(...list)
    const total = res.total
    const noMore = list.length < pageSize || (total != null && logs.value.length >= total)
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
</script>

<style scoped>
.credit {
  min-height: 100vh;
  padding-bottom: 30px;
  background: var(--bg-page);
}
.credit-card {
  margin: 12px;
  padding: 20px 20px 16px;
  border-radius: var(--radius-card);
  color: #fff;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}
.credit-card--good { background: linear-gradient(135deg, #34c98f, #07c160); }
.credit-card--mid { background: linear-gradient(135deg, var(--brand-primary-light), var(--brand-primary)); }
.credit-card--low { background: linear-gradient(135deg, #f2b04f, var(--warning)); }
.credit-card--bad { background: linear-gradient(135deg, #f06a6a, var(--danger)); }
.credit-card__label { font-size: 13px; opacity: 0.9; }
.credit-card__score { font-size: 42px; font-weight: 700; line-height: 1.2; margin: 6px 0; }
.credit-card__max { font-size: 16px; font-weight: 500; opacity: 0.85; }
.credit-card__tip { font-size: 13px; opacity: 0.9; }

.rule-card {
  margin: 0 12px;
  padding: 12px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.rule-card__title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; }
.rule-card__row { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); margin-top: 6px; }
.rule-card__row--muted { color: var(--text-tertiary); font-size: 12px; }
.rule-card__row .is-down { color: var(--danger); }

.logs-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.logs-head__hint { font-size: 12px; font-weight: 400; color: var(--text-tertiary); }

.log-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 12px 8px;
  padding: 12px 14px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.log-item__left { display: flex; align-items: center; gap: 8px; min-width: 0; }
.log-item__type { flex-shrink: 0; }
.log-item__remark { font-size: 14px; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.log-item__right { display: flex; flex-direction: column; align-items: flex-end; flex-shrink: 0; }
.log-item__change { font-size: 18px; font-weight: 700; }
.log-item__change.is-in { color: var(--success, #07c160); }
.log-item__change.is-out { color: var(--danger); }
.log-item__meta { margin-top: 2px; font-size: 12px; color: var(--text-tertiary); }
</style>
