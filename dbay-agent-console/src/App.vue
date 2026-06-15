<template>
  <main class="shell">
    <section class="panel">
      <p class="eyebrow">DBay Agent</p>
      <h1>DataAgent 工作台</h1>
      <p>Sources、Knowledge、Memory 和 Datalake 将在这里接入 Lakebase。</p>
    </section>
  </main>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  background: #faf8f5;
  color: #2c3e50;
  display: grid;
  place-items: center;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.panel {
  width: min(720px, calc(100vw - 48px));
  border: 1px solid #e8e4df;
  background: #fff;
  padding: 32px;
}

.eyebrow {
  color: #9a5b25;
  font-size: 13px;
  font-weight: 700;
}

h1 {
  margin: 0 0 12px;
  font-size: 32px;
}
</style>

