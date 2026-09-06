<template>
  <div class="edit-profile">
    <van-nav-bar title="编辑资料" left-arrow @click-left="$router.back()" />

    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field label="头像" center>
          <template #input>
            <van-uploader v-model="fileList" :max-count="1" :after-read="onAvatarRead" />
          </template>
        </van-field>

        <van-field
          v-model="form.nickName"
          label="昵称"
          placeholder="请输入昵称"
          maxlength="32"
          :rules="[{ required: true, message: '请填写昵称' }]"
        />

        <van-field label="性别">
          <template #input>
            <van-radio-group v-model="form.gender" direction="horizontal">
              <van-radio :name="1">男</van-radio>
              <van-radio :name="2">女</van-radio>
              <van-radio :name="0">保密</van-radio>
            </van-radio-group>
          </template>
        </van-field>

        <van-field v-model="form.age" type="number" label="年龄" placeholder="请输入年龄" />

        <van-field
          v-model="form.introduce"
          type="textarea"
          rows="3"
          label="简介"
          placeholder="介绍一下自己"
          maxlength="255"
          show-word-limit
        />
      </van-cell-group>

      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="saving">
          保存
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { getUserInfo, updateProfile, getMe } from '@/api/user'
import { uploadImage } from '@/api/upload'
import { userState, setInfo } from '@/stores/user'

const router = useRouter()
const form = reactive({ nickName: '', icon: '', gender: 0, age: null, introduce: '' })
const fileList = ref([])
const saving = ref(false)

onMounted(async () => {
  // 昵称/头像从登录态取，简介/年龄/性别从资料表取（两者分属不同接口）
  form.nickName = userState.info?.nickName || ''
  form.icon = userState.info?.icon || ''
  if (form.icon) {
    fileList.value = [{ url: form.icon }]
  }
  try {
    const res = await getUserInfo(userState.info.id)
    const info = res.data
    if (info) {
      form.gender = info.gender ?? 0
      form.age = info.age ?? null
      form.introduce = info.introduce || ''
    }
  } catch (e) {
    // 资料拉取失败不阻断编辑
  }
})

// 头像上传：成功后回填 URL 到表单，供保存时提交
async function onAvatarRead(item) {
  item.status = 'uploading'
  item.message = '上传中...'
  try {
    const res = await uploadImage(item.file)
    item.status = 'done'
    item.message = ''
    item.url = res.data
    form.icon = res.data
  } catch (e) {
    item.status = 'failed'
    item.message = '上传失败'
  }
}

async function onSubmit() {
  if (fileList.value.some((f) => f.status === 'uploading')) {
    return
  }
  saving.value = true
  try {
    await updateProfile({
      nickName: form.nickName,
      icon: form.icon,
      gender: form.gender,
      age: form.age ? Number(form.age) : null,
      introduce: form.introduce
    })
    // 刷新本地登录态（昵称/头像），个人中心立即生效
    const me = await getMe()
    if (me.data) setInfo(me.data)
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
.submit {
  margin: 24px 16px;
}
</style>
