<template>
  <div class="wallet">
    <van-nav-bar title="我的钱包" left-arrow @click-left="$router.back()" />

    <!-- 余额卡 -->
    <div class="balance-card">
      <div class="balance-card__label">可用余额（元）</div>
      <div class="balance-card__amount">
        <span class="balance-card__yen">¥</span>{{ balance }}
      </div>
      <div class="balance-card__tip">工资到账后想提现，点右上「提现」</div>
      <div class="balance-card__actions">
        <van-button
          class="balance-card__btn"
          round
          size="small"
          plain
          color="#fff"
          @click="openDialog('recharge')"
        >
          充值
        </van-button>
        <van-button
          class="balance-card__btn"
          round
          size="small"
          plain
          color="#fff"
          @click="openDialog('withdraw')"
        >
          提现
        </van-button>
      </div>
    </div>

    <!-- 流水列表 -->
    <div class="logs-head">
      <span>资金流水</span>
      <span class="logs-head__hint">入账为正 · 出账为负</span>
    </div>
    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div v-for="log in logs" :key="log.id" class="log-item">
        <div class="log-item__left">
          <van-tag :type="typeColor(log.type)" plain class="log-item__type">
            {{ log.type }}
          </van-tag>
          <span class="log-item__remark">{{ log.remark || log.type }}</span>
        </div>
        <div class="log-item__right">
          <span :class="['log-item__amount', log.amount >= 0 ? 'is-in' : 'is-out']">
            {{ log.amount >= 0 ? '+' : '-' }}¥{{ Math.abs(log.amount) }}
          </span>
          <span class="log-item__meta">{{ formatDateTime(log.createTime) }}</span>
        </div>
      </div>
      <van-empty v-if="finished && logs.length === 0" description="还没有资金流水" />
    </van-list>

    <!-- 充值 / 提现弹层（共用一套表单，按 mode 区分） -->
    <van-popup v-model:show="dialogShow" position="bottom" round>
      <div class="recharge-form">
        <h4 class="recharge-form__title">{{ dialogTitle }}</h4>
        <div v-if="mode === 'withdraw' && balance > 0" class="recharge-form__max" @click="amountInput = balance">
          可提现余额 ¥{{ balance }}，点此填入
        </div>
        <div class="recharge-form__presets">
          <span
            v-for="v in presets"
            :key="v"
            :class="['recharge-form__chip', amountInput === v ? 'is-active' : '']"
            @click="amountInput = v"
          >
            ¥{{ v }}
          </span>
        </div>
        <van-field
          v-model="amountInput"
          type="number"
          :placeholder="mode === 'withdraw' ? '输入提现金额' : '输入充值金额'"
          label="金额"
          :formatter="(v) => v.replace(/\D/g, '')"
        />
        <van-button
          class="recharge-form__submit"
          round
          block
          :type="mode === 'withdraw' ? 'danger' : 'primary'"
          :loading="submitting"
          @click="submitDialog"
        >
          {{ dialogSubmitText }}
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import { getMyWallet, rechargeWallet, withdrawWallet, getWalletLogs } from '@/api/wallet'
import { formatDateTime } from '@/utils/format'

const balance = ref(0)
const logs = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

const dialogShow = ref(false)
const mode = ref('recharge') // 'recharge' | 'withdraw'
const submitting = ref(false)
const presets = ref([])
const amountInput = ref(100)

const presetsByMode = {
  recharge: [50, 100, 200, 500],
  withdraw: [100, 200, 500, 1000]
}

const dialogTitle = computed(() => (mode.value === 'withdraw' ? '模拟提现' : '模拟充值'))
const dialogSubmitText = computed(() => (mode.value === 'withdraw' ? '确认提现' : '确认充值'))

// 流水类型 → Vant Tag 颜色
function typeColor(type) {
  return { 充值: 'primary', 冻结: 'warning', 解冻: 'success', 工资: 'success', 服务费: 'danger', 提现: 'danger' }[type] || 'default'
}

async function loadBalance() {
  try {
    const res = await getMyWallet()
    balance.value = res.data?.balance ?? 0
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}

function openDialog(m) {
  mode.value = m
  presets.value = presetsByMode[m]
  amountInput.value = m === 'withdraw' ? Math.min(100, balance.value) : 100
  dialogShow.value = true
}

async function submitDialog() {
  const amount = Number(amountInput.value)
  if (!amount || amount <= 0) {
    return showToast('请输入有效金额')
  }
  submitting.value = true
  try {
    const api = mode.value === 'withdraw' ? withdrawWallet : rechargeWallet
    const res = await api(amount)
    balance.value = res.data?.balance ?? balance.value
    showSuccessToast(mode.value === 'withdraw' ? '提现成功' : '充值成功')
    dialogShow.value = false
    // 流水回到第一页重新拉
    logs.value = []
    page.value = 1
    finished.value = false
    loading.value = false
    onLoad()
  } catch (e) {
    // 失败提示已由 request.js Toast（余额不足/超上限/金额非正数等）
  } finally {
    submitting.value = false
  }
}

async function onLoad() {
  try {
    const res = await getWalletLogs(page.value, pageSize)
    const list = res.data || []
    logs.value.push(...list)
    const total = res.total
    if (list.length < pageSize || (total != null && logs.value.length >= total)) {
      finished.value = true
    } else {
      page.value++
    }
  } catch (e) {
    finished.value = true // 拉取失败不再反复重试
  }
}

loadBalance()
</script>

<style scoped>
.wallet {
  min-height: 100vh;
  padding-bottom: 30px;
  background: var(--bg-page);
}
.balance-card {
  position: relative;
  margin: 12px;
  padding: 24px 20px 20px;
  background: linear-gradient(135deg, var(--brand-primary-light), var(--brand-primary));
  border-radius: var(--radius-card);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
  color: #fff;
}
.balance-card__label {
  font-size: 13px;
  opacity: 0.85;
}
.balance-card__amount {
  margin: 10px 0 6px;
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
}
.balance-card__yen {
  font-size: 22px;
  font-weight: 600;
  margin-right: 2px;
}
.balance-card__tip {
  font-size: 12px;
  opacity: 0.75;
}
.balance-card__actions {
  position: absolute;
  right: 16px;
  top: 18px;
  display: flex;
  gap: 8px;
}
.balance-card__btn {
  border-color: rgba(255, 255, 255, 0.6);
}
.logs-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 16px 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.logs-head__hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary);
}
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
.log-item__left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.log-item__type {
  flex-shrink: 0;
}
.log-item__remark {
  font-size: 14px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-item__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}
.log-item__amount {
  font-size: 16px;
  font-weight: 700;
}
.log-item__amount.is-in {
  color: var(--success, #07c160);
}
.log-item__amount.is-out {
  color: var(--danger);
}
.log-item__meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.recharge-form {
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
}
.recharge-form__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 14px;
}
.recharge-form__max {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--brand-primary);
}
.recharge-form__presets {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.recharge-form__chip {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 14px;
  color: var(--text-secondary);
  background: var(--bg-page);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}
.recharge-form__chip.is-active {
  color: var(--brand-primary);
  border-color: var(--brand-primary);
  background: rgba(37, 99, 235, 0.06);
}
.recharge-form__submit {
  margin-top: 16px;
}
</style>
