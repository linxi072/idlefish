// pc-admin/src/views/search-term.js —— 搜索词运营（F-14.3，对齐 SearchTermController /api/search/term/* 与 SearchTermAdminController /api/admin/search-term/*）
// 运营后台：热搜词运营（预览+屏蔽/恢复）、屏蔽词管理（按词增删）、同义词词典（列表+新增）。
// 注：后端未提供「屏蔽词列表」与「全量热搜词」GET 接口，故屏蔽词以「按词输入增删」形式操作；热搜词预览取自 ENABLED 热搜榜。
import { searchTermApi, USE_MOCK } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

export default {
  name: 'SearchTerm',
  mixins: [formatMixin],
  data() {
    return {
      // 热搜词（ENABLED）
      hotWords: [], hotLoading: false,
      restoreWord: '',
      // 屏蔽词（无列表 GET：mock 演示用本地数组，真实模式不展示列表）
      blockInput: '', blockLoading: false,
      blockWordsDemo: USE_MOCK ? ['赌博', '发票', '代开发票'] : [],
      // 同义词词典
      synonyms: [], synLoading: false,
      synWord: '', synTo: '', synLoadingAdd: false
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      await Promise.all([this.fetchHot(), this.fetchSynonyms()]);
    },
    async fetchHot() {
      this.hotLoading = true;
      try {
        const r = await searchTermApi.hot(20);
        this.hotWords = (r || []).map((w, i) => ({ word: w, rank: i + 1 }));
      } catch (e) {
        notifyError(this, e, '加载热搜榜失败');
      } finally { this.hotLoading = false; }
    },
    async fetchSynonyms() {
      this.synLoading = true;
      try {
        const r = await searchTermApi.synonyms();
        this.synonyms = (r || []).map(x => ({ word: x.word, synonym: x.synonym }));
      } catch (e) {
        notifyError(this, e, '加载同义词失败');
      } finally { this.synLoading = false; }
    },
    // 热搜词：屏蔽（置 BLOCKED，移出热搜榜）
    async blockHot(word) {
      try {
        await searchTermApi.setHotWordStatus(word, 'BLOCKED');
        this.$message.success('已屏蔽热搜词：' + word);
        this.fetchHot();
      } catch (e) { notifyError(this, e, '屏蔽失败'); }
    },
    // 热搜词：恢复（置 ENABLED，重新进入热搜榜）
    async restoreHot() {
      const word = (this.restoreWord || '').trim();
      if (!word) { this.$message.warning('请输入要恢复的热搜词'); return; }
      try {
        await searchTermApi.setHotWordStatus(word, 'ENABLED');
        this.$message.success('已恢复热搜词：' + word);
        this.restoreWord = '';
        this.fetchHot();
      } catch (e) { notifyError(this, e, '恢复失败'); }
    },
    // 屏蔽词：新增
    async addBlock() {
      const word = (this.blockInput || '').trim();
      if (!word) { this.$message.warning('请输入屏蔽词'); return; }
      this.blockLoading = true;
      try {
        await searchTermApi.blockWord(word);
        this.$message.success('已添加屏蔽词：' + word);
        if (USE_MOCK) this.blockWordsDemo.push(word);
        this.blockInput = '';
      } catch (e) { notifyError(this, e, '添加失败'); }
      finally { this.blockLoading = false; }
    },
    // 屏蔽词：移除
    async removeBlock(word) {
      this.blockLoading = true;
      try {
        await searchTermApi.unblockWord(word);
        this.$message.success('已移除屏蔽词：' + word);
        if (USE_MOCK) this.blockWordsDemo = this.blockWordsDemo.filter(w => w !== word);
      } catch (e) { notifyError(this, e, '移除失败'); }
      finally { this.blockLoading = false; }
    },
    // 同义词：新增
    async addSynonym() {
      const word = (this.synWord || '').trim();
      const to = (this.synTo || '').trim();
      if (!word || !to) { this.$message.warning('请输入原词与同义词'); return; }
      this.synLoadingAdd = true;
      try {
        await searchTermApi.addSynonym(word, to);
        this.$message.success('已添加同义词：' + word + ' → ' + to);
        this.synWord = ''; this.synTo = '';
        this.fetchSynonyms();
      } catch (e) { notifyError(this, e, '添加失败'); }
      finally { this.synLoadingAdd = false; }
    }
  },
  template: `
  <div>
    <h2 class="page-title">搜索词运营</h2>

    <el-card shadow="never" style="margin-bottom:16px">
      <el-divider content-position="left">热搜词运营（ENABLED 热搜榜）</el-divider>
      <el-table :data="hotWords" v-loading="hotLoading" border stripe max-height="360">
        <el-table-column label="排名" prop="rank" width="90"></el-table-column>
        <el-table-column label="热搜词" prop="word" min-width="160"></el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{row}">
            <el-button size="small" type="danger" plain @click="blockHot(row.word)">屏蔽</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-form inline style="margin-top:12px">
        <el-form-item label="恢复热搜词">
          <el-input v-model="restoreWord" placeholder="输入被屏蔽的词以恢复" style="width:220px" clearable></el-input>
        </el-form-item>
        <el-form-item><el-button type="success" @click="restoreHot">恢复</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-bottom:16px">
      <el-divider content-position="left">屏蔽词管理</el-divider>
      <el-form inline>
        <el-form-item label="屏蔽词">
          <el-input v-model="blockInput" placeholder="输入要屏蔽的词" style="width:220px" clearable></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="blockLoading" @click="addBlock">添加屏蔽</el-button>
        </el-form-item>
      </el-form>
      <div v-if="blockWordsDemo.length" style="margin:8px 0 4px">
        <el-tag v-for="w in blockWordsDemo" :key="w" closable type="info"
          style="margin:0 8px 8px 0" @close="removeBlock(w)">{{ w }}</el-tag>
      </div>
      <div class="tip">屏蔽词命中后不再入库、不进入热搜榜；按词输入增删即可生效。</div>
    </el-card>

    <el-card shadow="never">
      <el-divider content-position="left">同义词词典</el-divider>
      <el-table :data="synonyms" v-loading="synLoading" border stripe max-height="320">
        <el-table-column label="原词" prop="word" min-width="160"></el-table-column>
        <el-table-column label="同义词" prop="synonym" min-width="160"></el-table-column>
      </el-table>
      <el-form inline style="margin-top:12px">
        <el-form-item label="原词"><el-input v-model="synWord" placeholder="如 手机" style="width:160px" clearable></el-input></el-form-item>
        <el-form-item label="同义词"><el-input v-model="synTo" placeholder="如 移动电话" style="width:160px" clearable></el-input></el-form-item>
        <el-form-item><el-button type="primary" :loading="synLoadingAdd" @click="addSynonym">添加同义词</el-button></el-form-item>
      </el-form>
    </el-card>
  </div>`
};
