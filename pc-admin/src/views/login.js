// pc-admin/src/views/login.js —— 登录页（含表单校验）
import { adminApi, setToken } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

export default {
  name: 'Login',
  mixins: [formatMixin],
  data() {
    return {
      form: { username: 'admin', password: 'admin123' },
      loading: false,
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' }
        ]
      }
    };
  },
  methods: {
    async submit() {
      const ok = await this.$refs.form.validate().catch(() => false);
      if (!ok) return;
      this.loading = true;
      try {
        const r = await adminApi.login(this.form.username, this.form.password);
        setToken(r.token);
        this.$message.success('登录成功');
        this.$emit('logged-in', r.user);
      } catch (e) {
        this.$message.error((e && e.msg) || '登录失败');
      } finally {
        this.loading = false;
      }
    }
  },
  template: `
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-brand"><span class="logo">闲</span> 闲置集 · 运营后台</div>
      <div class="login-sub">二手交易运营管理平台</div>
      <el-form ref="form" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" prefix-icon="User" clearable></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" prefix-icon="Lock" @keyup.enter="submit"></el-input>
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="login-tip">演示账号：admin / admin123（默认本地 Mock 模式）</div>
    </div>
  </div>`
};
