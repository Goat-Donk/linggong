<template>
  <div class="login">
    <div class="brand">
      <div class="logo"><van-icon name="fire-o" size="40" color="#ffffff" /></div>
      <h1>本地零工平台</h1>
      <p class="slogan">接零工 · 找活干 · 就在指尖</p>
    </div>

    <van-form @submit="onLogin">
      <van-cell-group inset>
        <van-field
          v-model="phone"
          name="phone"
          type="tel"
          maxlength="11"
          placeholder="请输入手机号"
          :rules="phoneRules"
          left-icon="phone-o"
        />
        <van-field
          v-model="code"
          name="code"
          type="digit"
          maxlength="6"
          placeholder="请输入 6 位验证码"
          :rules="codeRules"
          left-icon="shield-o"
        >
          <template #button>
            <van-button
              size="small"
              type="primary"
              plain
              native-type="button"
              :disabled="countdown > 0"
              @click="onSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </van-button>
          </template>
        </van-field>
      </van-cell-group>

      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          登录 / 注册
        </van-button>
      </div>
    </van-form>

    <p class="tip">未注册的手机号验证通过后自动注册</p>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { sendCode, login, getMe } from '@/api/user'
import { setToken, setInfo } from '@/stores/user'

const router = useRouter()
const route = useRoute()

const phone = ref('')
const code = ref('')
const loading = ref(false)
const countdown = ref(0)
let timer = null

const phoneRules = [
  { required: true, message: '请输入手机号' },
  { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }
]
const codeRules = [
  { required: true, message: '请输入验证码' },
  { pattern: /^\d{6}$/, message: '验证码为 6 位数字' }
]

// 发送验证码（成功后 60s 倒计时，防止重复发送）
async function onSendCode() {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    showToast('请先输入正确的手机号')
    return
  }
  try {
    await sendCode(phone.value)
    showSuccessToast('验证码已发送')
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    // 失败提示已由 request.js 统一 Toast
  }
}

// 登录成功：存 token + 拉用户信息，跳回来源页（被守卫拦截时的 redirect），默认首页
async function onLogin() {
  loading.value = true
  try {
    const res = await login(phone.value, code.value)
    setToken(res.data)
    const me = await getMe()
    setInfo(me.data)
    showSuccessToast('登录成功')
    router.replace(route.query.redirect || '/home')
  } catch (e) {
    // 失败提示已由 request.js 统一 Toast
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.login {
  min-height: 100vh;
  padding: 64px 0 40px;
}
.brand {
  text-align: center;
  margin-bottom: 40px;
}
.logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--brand-primary-light), var(--brand-primary));
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.3);
  margin-bottom: 16px;
}
.brand h1 {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}
.slogan {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}
.submit {
  margin: 24px 16px 0;
}
.tip {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
