<template>

  <div>
    <h2 class="page-title">首页推荐 / 买家端预览</h2>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
      title="推荐算法：同城优先 + 热度半衰期衰减（7天）+ 行为加权（用户类目兴趣）+ 冷启动保量（3天内新品加分）"></el-alert>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="同城筛选"><el-input v-model="cityFilter" placeholder="留空=全国（如 上海）" clearable style="width:180px"></el-input></el-form-item>
        <el-form-item label="每页">
          <el-select v-model="size" style="width:110px" @change="onSize">
            <el-option label="10 条" :value="10"></el-option>
            <el-option label="20 条" :value="20"></el-option>
            <el-option label="50 条" :value="50"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onPage(1)">查询</el-button>
          <el-button v-if="cityFilter" @click="resetCity">清除同城</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="商品" min-width="240">
          <template #default="{row}">
            <div class="cell-goods">
              <img :src="row.cover || ('https://picsum.photos/seed/'+row.id+'/80/80')" class="thumb"/>
              <div><div class="g-title">{{ row.title }}</div><div class="g-sub">ID: {{ row.id }}</div></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="110"><template #default="{row}">{{ yuan(row.price) }}</template></el-table-column>
        <el-table-column label="类目" prop="categoryName" width="120"></el-table-column>
        <el-table-column label="成色" width="90"><template #default="{row}">{{ condText(row.conditionLevel) }}</template></el-table-column>
        <el-table-column label="城市" width="130">
          <template #default="{row}">
            <span>{{ row.city || '-' }}</span>
            <el-tag v-if="isSameCity(row)" type="success" size="small" style="margin-left:6px">同城</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="卖家" width="110"><template #default="{row}">{{ row.sellerId || '-' }}</template></el-table-column>
        <el-table-column label="上架时间" prop="createdAt" width="160"></el-table-column>
      </el-table>

      <div style="margin-top:12px; display:flex; justify-content:flex-end">
        <el-pagination
          :current-page="page" :page-size="size" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
          @current-change="onPage" @size-change="onSize"></el-pagination>
      </div>
    </el-card>
  </div>
</template>

<script>
import { recommendApi } from '@/api';
import { formatMixin, notifyError } from '@/utils/format';
// pc-admin/src/views/recommend.js —— 首页推荐预览（F-14.2 个性化推荐）
// 运营侧预览买家端推荐流：同城优先 + 热度半衰期衰减 + 行为加权 + 冷启动保量
// 对齐 SearchController /api/search/recommend 与 RecommendService.feed；ItemVO 字段直接渲染

const COND_TEXT = { 1: '全新', 2: '99新', 3: '95新', 4: '9成新', 5: '8成新' };


export default {
  name: 'Recommend',
  mixins: [formatMixin],
  data() {
    return {
      cityFilter: '', page: 1, size: 10, list: [], total: 0, loading: false
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      try {
        const r = await recommendApi.recommend(this.cityFilter || null, null, this.page, this.size);
        this.list = r.list || [];
        this.total = r.total || 0;
      } catch (e) {
        notifyError(this, e, '加载推荐流失败');
        this.list = []; this.total = 0;
      } finally { this.loading = false; }
    },
    onPage(p) { this.page = p; this.load(); },
    onSize(s) { this.size = s; this.page = 1; this.load(); },
    condText(l) { return COND_TEXT[l] || '未知'; },
    // 同城高亮：仅当用户指定城市且商品 city 匹配时标「同城」
    isSameCity(row) { return !!this.cityFilter && row.city === this.cityFilter; },
    resetCity() { this.cityFilter = ''; this.page = 1; this.load(); }
  }
}
</script>
