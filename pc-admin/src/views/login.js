// pc-admin/src/views/login.js
import { adminApi, setToken } from '../api.js';

export default {
  name: 'Login',
  emits: ['logged-in'],
  data() {
    return { form: { username: 'admin', password: 'admin123' }, loading: false, error: '' };
  },
  methods: {
    async submit() {
      this.loading = true; this.error = '';
      try {
        const r = await adminApi.login(this.form.username, this.form.password);
        setToken(r.token);
        this.$emit('logged-in', r.user);
      } catch (e) {
        this.error = '登录失败，请检查账号或后端连接';
      } finally { this.loading = false; }
    }
  },
  template: `
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-brand"><span class="logo">闲</span><span>闲置集 · 运营后台</span></div>
      <div class="login-sub">C2C 二手交易平台 · 管理控制台</div>
      <el-form @submit.prevent="submit" label-position="top">
        <el-form-item label="管理员账号">
          <el-input v-model="form.username" placeholder="请输入账号" size="large"></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password @keyup.enter="submit"></el-input>
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" style="margin-bottom:16px"></el-alert>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="login-tip">演示账号已预填（admin / admin123），当前为 Mock 模式，无需后端即可体验。</div>
    </div>
  </div>`
};
