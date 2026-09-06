<template>
  <div class="publish-blog">
    <van-nav-bar title="发布动态" left-arrow @click-left="$router.back()" />

    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.title"
          label="标题"
          placeholder="给动态起个标题（选填）"
          maxlength="64"
        />
        <van-field
          v-model="form.content"
          type="textarea"
          rows="4"
          label="内容"
          placeholder="晒晒今天打的零工..."
          maxlength="2048"
          show-word-limit
          :rules="[{ required: true, message: '请填写内容' }]"
        />
        <van-field label="图片">
          <template #input>
            <van-uploader v-model="fileList" multiple :max-count="9" :after-read="onAfterRead" />
          </template>
        </van-field>
      </van-cell-group>

      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="submitting">
          发布
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { publishBlog } from '@/api/blog'
import { uploadImage } from '@/api/upload'

const router = useRouter()
const form = reactive({ title: '', content: '' })
const fileList = ref([])
const submitting = ref(false)

// 选图后逐张上传，成功后把后端 URL 回填到 item.url 作为预览
async function onAfterRead(item) {
  item.status = 'uploading'
  item.message = '上传中...'
  try {
    const res = await uploadImage(item.file)
    item.status = 'done'
    item.message = ''
    item.url = res.data
  } catch (e) {
    item.status = 'failed'
    item.message = '上传失败'
  }
}

async function onSubmit() {
  // 有图片还在上传时拦截，避免提交丢图
  if (fileList.value.some((f) => f.status === 'uploading')) {
    showToast('图片上传中，请稍候')
    return
  }
  // 只收集上传成功的图片 URL，逗号分隔（删除的图片自动不在 fileList 里）
  const images = fileList.value
    .filter((f) => f.status === 'done' && f.url)
    .map((f) => f.url)
    .join(',')

  submitting.value = true
  try {
    await publishBlog({ title: form.title, content: form.content, images })
    showSuccessToast('发布成功')
    router.back()
  } catch (e) {
    // 失败提示已由 request.js Toast
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.submit {
  margin: 24px 16px;
}
</style>
