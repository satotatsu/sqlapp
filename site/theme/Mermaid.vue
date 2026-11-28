<script setup>
import { ref, onMounted, watch } from 'vue';
import { useData } from 'vitepress';

const props = defineProps({ code: { type: String, required: true } });
const { isDark, lang } = useData();
const svg = ref('');
const error = ref('');
const source = decodeURIComponent(props.code);
let revision = 0;
async function render() {
  const current = ++revision;
  try {
    const { default: mermaid } = await import('mermaid');
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: isDark.value ? 'dark' : 'default' });
    const result = await mermaid.render('diagram-' + crypto.randomUUID(), source);
    if (current === revision) { svg.value = result.svg; error.value = ''; }
  } catch (failure) {
    error.value = String(failure);
  }
}
onMounted(render);
watch(isDark, render);
</script>

<template>
  <figure class="sqlapp-mermaid">
    <div v-if="svg" role="img" :aria-label="lang === 'ja' ? 'ER図・ワークフロー図' : 'ER / workflow diagram'" v-html="svg" />
    <p v-if="error" role="alert">{{ error }}</p>
    <details><summary>{{ lang === 'ja' ? '図のソース' : 'Diagram source' }}</summary><pre>{{ source }}</pre></details>
  </figure>
</template>

<style scoped>
.sqlapp-mermaid { margin: 1.5rem 0; overflow-x: auto; }
summary { cursor: pointer; }
pre { white-space: pre; }
</style>
