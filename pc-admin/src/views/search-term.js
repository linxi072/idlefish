// pc-admin/src/views/search-term.js —— 搜索词运营（F-14.3，对齐 SearchTermController /api/search/term/* 与 SearchTermAdminController /api/admin/search-term/*）
// 运营后台：热搜词运营（ENABLED 榜预览+屏蔽/恢复）、全部热搜词（含 ENABLED/BLOCKED 状态查看）、屏蔽词管理（列表拉取+按词增删）、同义词词典（列表+新增）。
// F-14.3 补全：后端新增 GET /api/admin/search-term/block-words（屏蔽词列表）与 /api/admin/search-term/hot-words（全量热搜词含状态），本页据此拉取真实列表。
import { searchTermApi } from '../api.js';
import { formatMixin, notifyError } from '../utils/format.js';

export default {
  name: 'SearchTerm',
  mixins: [formatMixin],
  data() {
    return {
      // 热搜榜（ENABLED）
      hotWords: [], hotLoading: false,
      restoreWord: '',
      // 全部热搜词（含 ENABLED/BLOCKED 状态）
      allHotWords: [], allHotLoading: false,
      // 屏蔽词列表（真实拉取；mock 由接口返回）
      blockWords: [], blockLoading: false, blockInput: '', blockAdding: false,
      // 同义词词典
      synonyms: [], synLoading: false,
      synWord: '', synTo: '', synLoadingAdd: false
    };
  },
  mounted() { this.load(); },
  methods: {
    async load() {
      await Promise.all([
        this.fetchHot(), this.fetchAllHot(), this.fetchBlockWords(), this.fetchSynonyms()
      ]);
    },
    async fetchHot() {
      this.hotLoading = true;
      try {
        const r = await searchTermApi.hot(20);
        this.hotWords = (r || []).map((w, i) => ({ word: w, rank: i + 1 }));
      } catch (e) { notifyError(this, e, '加载热搜榜失败'); }
      finally { this.hotLoading = false; }
    },
    async fetchAllHot() {
      this.allHotLoading = true;
      try {
        const r = await searchTermApi.hotWords();
        this.allHotWords = (r || []).map((x, i) => ({
          word: x.word, heat: x.heat || 0, status: x.status || 'ENABLED', rank: i + 1
        }));
      } catch (e) { notifyError(this, e, '加载全量热搜词失败'); }
      finally { this.allHotLoading = false; }
    },
    async fetchBlockWords() {
      this.blockLoading = true;
      try {
        const r = await searchTermApi.blockWords();
        this.blockWords = (r || []).map(w => ({ word: w }));
      } catch (e) { notifyError(this, e, '加载屏蔽词列表失败'); }
      finally { this.blockLoading = false; }
    },
    async fetchSynonyms() {
      this.synLoading = true;
      try {
        const r = await searchTermApi.synonyms();
        this.synonyms = (r || []).map(x => ({ word: x.word, synonym: x.synonym }));
      } catch (e) { notifyError(this, e, '加载同义词失败'); }
      finally { this.synLoading = false; }
    },
    statusTag(status) {
      return status === 'BLOCKED' ? 'danger' : 'success';
    },
    // 热搜词：屏蔽（置 BLOCKED，移出热搜榜）
    async blockHot(word) {
      try {
        await searchTermApi.setHotWordStatus(word, 'BLOCKED');
        this.$message.success('已屏蔽热搜词：' + word);
        this.fetchHot(); this.fetchAllHot();
      } catch (e) { notifyError(this, e, '屏蔽失败'); }
    },
    // 热搜词：恢复（置 ENABLED，重新进入热搜榜）；可选传入行内词或顶部输入框
    async restoreHot(wordArg) {
      const word = (wordArg || this.restoreWord || '').trim();
      if (!word) { this.$message.warning('请输入或选择要恢复的热搜词'); return; }
      try {
        await searchTermApi.setHotWordStatus(word, 'ENABLED');
        this.$message.success('已恢复热搜词：' + word);
        this.restoreWord = '';
        this.fetchHot(); this.fetchAllHot();
      } catch (e) { notifyError(this, e, '恢复失败'); }
    },
    // 屏蔽词：新增
    async addBlock() {
      const word = (this.blockInput || '').trim();
      if (!word) { this.$message.warning('请输入屏蔽词'); return; }
      this.blockAdding = true;
      try {
        await searchTermApi.blockWord(word);
        this.$message.success('已添加屏蔽词：' + word);
        this.blockInput = '';
        this.fetchBlockWords();
      } catch (e) { notifyError(this, e, '添加失败'); }
      finally { this.blockAdding = false; }
    },
    // 屏蔽词：移除
    async removeBlock(word) {
      try {
        await searchTermApi.unblockWord(word);
        this.$message.success('已移除屏蔽词：' + word);
        this.fetchBlockWords();
      } catch (e) { notifyError(this, e, '移除失败'); }
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
        <el-form-item><el-button type="success" @click="restoreHot()">恢复</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-bottom:16px">
      <el-divider content-position="left">全部热搜词（含状态）</el-divider>
      <el-table :data="allHotWords" v-loading="allHotLoading" border stripe max-height="360">
        <el-table-column label="排名" prop="rank" width="90"></el-table-column>
        <el-table-column label="热搜词" prop="word" min-width="160"></el-table-column>
        <el-table-column label="热度" prop="heat" width="110"></el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{row}">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{row}">
            <el-button v-if="row.status!=='BLOCKED'" size="small" type="danger" plain @click="blockHot(row.word)">屏蔽</el-button>
            <el-button v-else size="small" type="success" plain @click="restoreHot(row.word)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-bottom:16px">
      <el-divider content-position="left">屏蔽词管理</el-divider>
      <el-form inline>
        <el-form-item label="屏蔽词">
          <el-input v-model="blockInput" placeholder="输入要屏蔽的词" style="width:220px" clearable></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="blockAdding" @click="addBlock">添加屏蔽</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="blockWords" v-loading="blockLoading" border stripe max-height="300" style="margin-top:8px">
        <el-table-column label="屏蔽词" prop="word" min-width="200"></el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button size="small" type="info" plain @click="removeBlock(row.word)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="tip">屏蔽词命中后不再入库、不进入热搜榜；支持按词增删与列表查看。</div>
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
