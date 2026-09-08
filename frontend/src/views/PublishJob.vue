<template>
  <div class="publish">
    <van-nav-bar title="发布岗位" left-arrow @click-left="$router.back()" />

    <van-empty v-if="!isEmployer" description="仅雇主可发布岗位" />

    <van-form v-else @submit="onSubmit">
      <van-cell-group inset title="基本信息">
        <van-field
          v-model="form.name"
          name="name"
          label="岗位名称"
          placeholder="如：周末促销员"
          maxlength="64"
          :rules="[{ required: true, message: '请填写岗位名称' }]"
        />
        <van-field
          :model-value="categoryText"
          is-link
          readonly
          name="categoryId"
          label="岗位分类"
          placeholder="请选择分类"
          :rules="[{ required: true, message: '请选择分类' }]"
          @click="showCategoryPicker = true"
        />
        <van-field
          v-model="form.address"
          name="address"
          label="工作地址"
          placeholder="如：朝阳区望京 SOHO"
          maxlength="255"
        />
      </van-cell-group>

      <van-cell-group inset title="工作地点">
        <van-cell
          title="工作定位"
          :value="locationText"
          label="必填，报名者按距离找活"
          is-link
          @click="onLocate"
        />
      </van-cell-group>

      <van-cell-group inset title="薪资与名额">
        <van-field
          v-model="salaryText"
          name="salary"
          type="number"
          label="日薪"
          placeholder="元/天，必填"
          :rules="[{ required: true, message: '请填写日薪' }]"
        />
        <van-cell title="招聘名额" center>
          <van-stepper v-model="form.headcount" :min="1" :max="999" integer />
        </van-cell>
      </van-cell-group>

      <van-cell-group inset title="工作时间（可选）">
        <van-field
          v-model="startTimeText"
          is-link
          readonly
          name="startTime"
          label="开始时间"
          placeholder="点击选择"
          @click="openTimePicker('start')"
        />
        <van-field
          v-model="endTimeText"
          is-link
          readonly
          name="endTime"
          label="结束时间"
          placeholder="点击选择"
          @click="openTimePicker('end')"
        />
        <van-cell title="任务天数" :value="`${taskDays} 天`" label="起止跨自然日；未填则按 1 天" />
      </van-cell-group>

      <van-cell-group inset title="担保金冻结">
        <van-cell title="当前可用余额" :value="`¥${balance}`" label="点我去充值" is-link @click="$router.push('/wallet')" />
        <van-cell title="冻结担保金" :value="`¥${freezeAmount}`" :label="freezeFormula" />
        <van-cell
          :title="balance >= freezeAmount ? '余额充足' : '余额不足'"
          :value="balance >= freezeAmount ? '可直接发布' : '请先充值'"
          :class="balance >= freezeAmount ? 'fee-ok' : 'fee-lack'"
        />
        <van-cell title="说明" label="发岗时从余额冻结 日薪×名额×天数 作为担保；结算时给打工人发工资、剩余退回，另按结算额抽 10% 服务费（雇主承担）" />
      </van-cell-group>

      <van-cell-group inset title="岗位描述（可选）">
        <van-field
          v-model="form.description"
          name="description"
          type="textarea"
          rows="4"
          autosize
          maxlength="1024"
          show-word-limit
          placeholder="工作内容、要求等"
        />
      </van-cell-group>

      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="submitting">
          发布
        </van-button>
      </div>
    </van-form>

    <!-- 分类选择 -->
    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        :columns="categoryColumns"
        @confirm="onCategoryConfirm"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>

    <!-- 时间选择 -->
    <van-popup v-model:show="showTimePicker" position="bottom" round>
      <van-date-picker
        :title="timePickerTitle"
        :model-value="timePickerValue"
        :columns-type="['year', 'month', 'day', 'hour', 'minute']"
        @confirm="onTimeConfirm"
        @cancel="showTimePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showLoadingToast, closeToast } from 'vant'
import { getCategories } from '@/api/category'
import { publishJob } from '@/api/job'
import { regeo } from '@/api/map'
import { getMyWallet } from '@/api/wallet'
import { userState } from '@/stores/user'

const router = useRouter()

const isEmployer = computed(() => userState.info?.role === 1)

const form = reactive({
  name: '',
  categoryId: null,
  address: '',
  x: null, // 经度
  y: null, // 纬度
  headcount: 1,
  startTime: null, // ISO "2026-09-06T10:00:00"
  endTime: null,
  description: ''
})

const salaryText = ref('')
const startTimeText = ref('')
const endTimeText = ref('')
const submitting = ref(false)

// 担保冻结预览：余额 + 任务天数 + 需冻结金额（后端同口径：日薪×名额×自然日天数）
const balance = ref(0)
const taskDays = computed(() => {
  if (!form.startTime || !form.endTime) return 1
  const start = form.startTime.slice(0, 10)
  const end = form.endTime.slice(0, 10)
  const diff = Math.round((Date.parse(end) - Date.parse(start)) / 86400000) + 1
  return Math.max(1, diff)
})
const dailySalary = computed(() => (salaryText.value ? Number(salaryText.value) : 0))
const freezeAmount = computed(() => dailySalary.value * form.headcount * taskDays.value)
const freezeFormula = computed(() => {
  if (!dailySalary.value) return '填写日薪后计算'
  return `日薪 ¥${dailySalary.value} × ${form.headcount} 人 × ${taskDays.value} 天`
})

const categories = ref([])
const showCategoryPicker = ref(false)
const showTimePicker = ref(false)
const timeField = ref('start')
const timePickerValue = ref([])

const categoryColumns = computed(() =>
  categories.value.map((c) => ({ text: c.name, value: c.id }))
)
const categoryText = computed(() => {
  const c = categories.value.find((c) => c.id === form.categoryId)
  return c ? c.name : ''
})
const timePickerTitle = computed(() => (timeField.value === 'start' ? '开始时间' : '结束时间'))
// 逆地理编码解析出的文字地址（与手动「工作地址」分开记，定位栏优先显示它）
const locatedAddress = ref('')
const locationText = computed(() => {
  if (form.x == null || form.y == null) return '未定位'
  return locatedAddress.value || '已定位'
})

onMounted(async () => {
  const res = await getCategories()
  categories.value = res.data || []
  if (isEmployer.value) {
    try {
      const w = await getMyWallet()
      balance.value = w.data?.balance ?? 0
    } catch (e) {
      // 拉不到余额不影响填写，发布时后端仍会兜底校验
    }
  }
})

function onLocate() {
  if (!navigator.geolocation) {
    showToast('当前浏览器不支持定位')
    return
  }
  showLoadingToast({ message: '定位中...', forbidClick: true })
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      form.x = pos.coords.longitude
      form.y = pos.coords.latitude
      closeToast()
      showSuccessToast('定位成功')
      // 拿到坐标即定位成功；逆地理编码异步补文字地址，失败不影响「已定位」状态
      regeo(form.x, form.y)
        .then((res) => {
          if (res.data) {
            locatedAddress.value = res.data
            form.address = res.data
          }
        })
        .catch(() => {
          // 地址解析失败：request.js 已 Toast，地址留空可手动填
        })
    },
    () => {
      closeToast()
      showToast('定位失败，请检查定位权限')
    },
    { enableHighAccuracy: false, timeout: 8000, maximumAge: 30000 }
  )
}

function onCategoryConfirm({ selectedValues }) {
  form.categoryId = selectedValues[0]
  showCategoryPicker.value = false
}

function openTimePicker(field) {
  timeField.value = field
  const iso = field === 'start' ? form.startTime : form.endTime
  if (iso) {
    const [d, t] = iso.split('T')
    const [y, m, dd] = d.split('-').map(Number)
    const [h, min] = (t || '').split(':').map(Number)
    timePickerValue.value = [y, m, dd, h, min]
  } else {
    const now = new Date()
    timePickerValue.value = [
      now.getFullYear(),
      now.getMonth() + 1,
      now.getDate(),
      now.getHours(),
      now.getMinutes()
    ]
  }
  showTimePicker.value = true
}

function onTimeConfirm({ selectedValues }) {
  const iso = formatPickerDate(selectedValues)
  if (timeField.value === 'start') {
    form.startTime = iso
    startTimeText.value = iso.replace('T', ' ').slice(0, 16)
  } else {
    form.endTime = iso
    endTimeText.value = iso.replace('T', ' ').slice(0, 16)
  }
  showTimePicker.value = false
}

// [年, 月, 日, 时, 分] → "2026-09-06T10:30:00"（后端 LocalDateTime 默认 ISO-8601 解析）
function formatPickerDate(arr) {
  const [y, m, d, h, min] = arr
  const pad = (n) => String(n).padStart(2, '0')
  return `${y}-${pad(m)}-${pad(d)}T${pad(h)}:${pad(min)}:00`
}

async function onSubmit() {
  if (form.x == null || form.y == null) {
    showToast('请先获取工作定位')
    return
  }
  const salaryNum = Number(salaryText.value)
  if (!Number.isInteger(salaryNum) || salaryNum <= 0) {
    showToast('日薪需为正整数（元/天）')
    return
  }
  if (balance.value < freezeAmount.value) {
    showToast(`可用余额不足：需冻结 ¥${freezeAmount.value}，当前 ¥${balance.value}`)
    return
  }
  submitting.value = true
  try {
    const payload = {
      categoryId: form.categoryId,
      name: form.name.trim(),
      address: form.address.trim() || null,
      x: form.x,
      y: form.y,
      salary: salaryNum,
      headcount: form.headcount,
      startTime: form.startTime,
      endTime: form.endTime,
      description: form.description.trim() || null
    }
    const res = await publishJob(payload)
    showSuccessToast('发布成功')
    router.replace(`/job/${res.data}`)
  } catch (e) {
    // 失败提示已由 request.js Toast（非雇主 / 时间非法 / 余额不足 / 校验失败等）
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.publish {
  min-height: 100vh;
  padding-bottom: 32px;
}
.submit {
  margin: 24px 16px;
}
.fee-ok :deep(.van-cell__title) {
  color: var(--success, #07c160);
}
.fee-lack :deep(.van-cell__title) {
  color: var(--danger);
}
</style>
