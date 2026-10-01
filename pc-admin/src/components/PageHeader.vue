<template>
  <header class="ph">
    <div class="ph__main">
      <el-breadcrumb v-if="crumbs && crumbs.length" class="ph__crumbs" separator="/">
        <el-breadcrumb-item v-for="(c, i) in crumbs" :key="i">{{ c }}</el-breadcrumb-item>
      </el-breadcrumb>
      <h2 class="ph__title">{{ title }}</h2>
      <p v-if="desc" class="ph__desc">{{ desc }}</p>
    </div>
    <div class="ph__extra"><slot name="extra" /></div>
  </header>
</template>

<script>
export default {
  name: 'PageHeader',
  props: {
    title: { type: String, required: true },
    desc: { type: String, default: '' },
    crumbs: { type: Array, default: () => [] }
  }
};
</script>

<style scoped>
.ph { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--sp-4); margin-bottom: var(--sp-5); flex-wrap: wrap; }
.ph__main { min-width: 0; }
.ph__crumbs { margin-bottom: var(--sp-2); }
.ph__title { font-size: var(--fs-h1); font-weight: var(--fw-bold); line-height: var(--lh-tight); margin: 0; color: var(--text-1); }
.ph__desc { font-size: var(--fs-sm); color: var(--text-3); margin: var(--sp-1) 0 0; }
.ph__extra { display: flex; align-items: center; gap: var(--sp-2); flex-shrink: 0; }
</style>
