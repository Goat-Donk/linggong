<template>
  <div class="profile-edit">
    <van-nav-bar title="求职登记" left-arrow @click-left="$router.back()" />

    <van-form @submit="onSubmit">
      <van-cell-group inset title="求职意向">
        <van-field
          v-model="form.title"
          name="title"
          label="意向标题"
          placeholder="如：可做传单/导购，周末全天"
          maxlength="64"
          :rules="[{ required: true, message: '请填写求职意向' }]"
        />
        <van-field label="期望分类" required>
          <template #input>
            <van-checkbox-group v-model="form.categoryIds" direction="horizontal">
              <van-checkbox
                v-for="c in categories"
                :key="c.id"
                :name="c.id"
                icon-size="16px"
                shape="square"
                class="cat-checkbox"
              >
                {{ c.name }}
              </van-checkbox>
            </van-checkbox-group>
          </template>
        </van-field>
      </van-cell-group>

      <van-cell-group inset title="技能与期望">
        <van-field
          v-model="skillTagsText"
          name="skillTags"
          label="技能标签"
          type="textarea"
          rows="2"
          autosize
          maxlength="255"
          show-word-limit
          placeholder="用逗号分隔，如：吃苦耐劳,会骑电动车,普通话标准"
        />
        <van-field label="期望日薪">
          <template #input>
            <div class="salary-row">
              <van-field
                v-model="form.salaryMin"
                class="salary-field"
                type="number"
                placeholder="最低"
              />
              <span class="salary-sep">~</span>
              <van-field
                v-model="form.salaryMax"
                class="salary-field"
                type="number"
                placeholder="最高"
              />
              <span class="salary-unit">元/天</span>
            </div>
          </template>
        </van-field>
        <van-field label="可出勤时段" required>
          <template #input>
            <van-checkbox-group v-model="form.workTime" direction="horizontal">
              <van-checkbox
                v-for="t in WORK_TIME_OPTIONS"
                :key="t"
                :name="t"
                icon-size="16px"
                shape="square"
                class="cat-checkbox"
              >
                {{ t }}
              </van-checkbox>
            </van-checkbox-group>
          </template>
        </van-field>
        <van-field
          v-model="form.location"
          name="location"
          label="常驻区域"
          placeholder="如：海淀区中关村"
          maxlength="255"
        />
      </van-cell-group>

      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="saving">
          保存登记
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getCategories } from '@/api/category'
import { getWorkerProfile, saveWorkerProfile } from '@/api/workerProfile'
import { userState } from '@/stores/user'

const router = useRouter()

// 可出勤时段固定选项（与展示端约定一致）
const WORK_TIME_OPTIONS = ['工作日白天', '工作日晚上', '周末', '节假日/全天']

const form = reactive({
  title: '',
  categoryIds: [],
  salaryMin: '',
  salaryMax: '',
  workTime: [],
  location: ''
})
const skillTagsText = ref('')
const categories = ref([])
const saving = ref(false)

onMounted(async () => {
  // 分类列表（供期望分类勾选）
  try {
    const res = await getCategories()
    categories.value = res.data || []
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
  // 预填：自己已填过的登记内容（view/self 返回的字段正好是编辑表单形状）
  try {
    const res = await getWorkerProfile(userState.info.id)
    const d = res.data
    if (d && d.hasProfile) {
      form.title = d.title || ''
      form.categoryIds = d.categoryIds || []
      form.salaryMin = d.salaryMin ?? ''
      form.salaryMax = d.salaryMax ?? ''
      form.workTime = d.workTime || []
      form.location = d.location || ''
      skillTagsText.value = (d.skillTags || []).join(',')
    }
  } catch (e) {
    // 拉取失败不阻断编辑
  }
})

// 技能标签文本 → 数组（按中英文逗号拆）
function parseTags(text) {
  return String(text || '')
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function onSubmit() {
  if (form.categoryIds.length === 0) {
    showToast('请至少选择一类期望岗位')
    return
  }
  if (form.workTime.length === 0) {
    showToast('请至少选择一段可出勤时段')
    return
  }
  const min = form.salaryMin === '' || form.salaryMin == null ? null : Number(form.salaryMin)
  const max = form.salaryMax === '' || form.salaryMax == null ? null : Number(form.salaryMax)
  if (min != null && max != null && min > max) {
    showToast('期望日薪区间不合法')
    return
  }
  saving.value = true
  try {
    await saveWorkerProfile({
      title: form.title.trim(),
      categoryIds: form.categoryIds,
      skillTags: parseTags(skillTagsText.value),
      salaryMin: min,
      salaryMax: max,
      workTime: form.workTime,
      location: form.location.trim()
    })
    showSuccessToast('保存成功')
    router.back()
  } catch (e) {
    // 失败提示已由 request.js Toast
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-edit {
  min-height: 100vh;
  padding-bottom: 32px;
}
.cat-checkbox {
  width: 50%;
  margin-bottom: 8px;
}
.salary-row {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 4px;
}
.salary-field {
  flex: 1;
  padding: 0;
  background: #f7f8fa;
  border-radius: 6px;
}
.salary-sep {
  color: #969799;
}
.salary-unit {
  font-size: 13px;
  color: #969799;
  margin-left: 2px;
}
.submit {
  margin: 24px 16px;
}
</style>
