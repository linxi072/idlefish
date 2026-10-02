<template>
  <div class="tree-flat-table">
    <el-table :data="rows" v-loading="loading" border stripe size="small"
      :max-height="maxHeight" :empty-text="emptyText" row-key="__rk">
      <el-table-column
        v-for="col in columns" :key="col.prop"
        :prop="col.prop" :label="col.label"
        :width="col.width" :min-width="col.minWidth"
        :align="col.align || 'left'" :fixed="col.fixed || false"
      >
        <template #default="{ row }">
          <!-- 名称列：按层级缩进 + 树线 + 分支/叶子节点标记，体现父子从属 -->
          <span v-if="col.prop === nameProp" class="tree-name-cell">
            <i v-for="n in (row._depth - 1)" :key="'g' + n" class="tree-guide"></i>
            <span class="tree-dot" :class="row._hasChild ? 'branch' : 'leaf'"></span>
            <span class="tree-name-text">{{ row[col.prop] }}</span>
          </span>
          <!-- 标签列：col.tag(v,row) 返回 {text,type} -->
          <el-tag v-else-if="col.tag" :type="asTag(col.tag(row[col.prop], row)).type" size="small">
            {{ asTag(col.tag(row[col.prop], row)).text }}
          </el-tag>
          <!-- 普通列：col.format(v,row) 可选格式化 -->
          <span v-else class="cell-text">
            {{ col.format ? col.format(row[col.prop], row) : (row[col.prop] == null || row[col.prop] === '' ? '—' : row[col.prop]) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="$slots.actions" label="操作" :width="actionWidth" align="center" fixed="right">
        <template #default="{ row }">
          <slot name="actions" :row="row" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
// 树状数据 → 扁平表格：递归展开为有序行，保留每个节点的全部原始字段，
// 并写入 _depth（层级，从 1 起）/ _hasChild（是否含子类）/ _childCount（直属子类数），
// 由名称列按 _depth 缩进渲染、并以「层级」列给出数字，确保嵌套结构扁平化后依然清晰可辨。
export default {
  name: 'TreeFlatTable',
  props: {
    data: { type: Array, default: () => [] },
    columns: { type: Array, default: () => [] },
    nameProp: { type: String, default: 'name' },
    childrenProp: { type: String, default: 'children' },
    loading: { type: Boolean, default: false },
    actionWidth: { type: [Number, String], default: 220 },
    maxHeight: { type: [Number, String], default: 560 },
    emptyText: { type: String, default: '暂无数据' }
  },
  computed: {
    rows() {
      const out = [];
      let seq = 0;
      const walk = (list, depth) => {
        (list || []).forEach(node => {
          const children = node[this.childrenProp];
          const hasChild = !!(children && children.length);
          out.push(Object.assign({}, node, {
            __rk: node.id != null ? node.id + '_' + seq++ : seq++,
            _depth: depth,
            _hasChild: hasChild,
            _childCount: hasChild ? children.length : 0
          }));
          if (hasChild) walk(children, depth + 1);
        });
      };
      walk(this.data, 1);
      return out;
    }
  },
  methods: {
    asTag(t) {
      if (t && typeof t === 'object') return t;
      return { text: t, type: 'info' };
    }
  }
};
</script>

<style scoped>
.tree-name-cell { display: inline-flex; align-items: center; line-height: 1; }
.tree-guide { display: inline-block; width: 14px; height: 12px; border-left: 1px dashed #dcdfe6; margin-left: 2px; }
.tree-dot { width: 7px; height: 7px; border-radius: 50%; margin: 0 6px 0 2px; flex: 0 0 auto; }
.tree-dot.branch { background: var(--el-color-primary, #FF8C1A); }
.tree-dot.leaf { background: #c0c4cc; }
.cell-text { white-space: nowrap; }
</style>
