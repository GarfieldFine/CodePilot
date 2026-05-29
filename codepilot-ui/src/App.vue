<script setup lang="ts">
</script>

<template>
  <router-view />
</template>

<style>
:root {
  --primary: #818cf8;
  --primary-dark: #6366f1;
  --primary-glow: rgba(99, 102, 241, 0.3);
  --accent: #a78bfa;
  --bg-root: #0a0a0f;
  --bg-primary: #0f0f18;
  --bg-secondary: #151520;
  --bg-card: #1a1a2a;
  --bg-card-hover: #1f1f32;
  --bg-elevated: #22223a;
  --border: #252540;
  --border-light: #2a2a45;
  --text-primary: #e8e8f0;
  --text-secondary: #a0a0b8;
  --text-muted: #6b6b80;
  --success: #34d399;
  --warning: #fbbf24;
  --danger: #f87171;
  --critical: #ef4444;
  --info: #60a5fa;
  --risk-low: #34d399;
  --risk-medium: #fbbf24;
  --risk-high: #f97316;
  --risk-critical: #ef4444;
  --code-bg: #0d0d1a;
  --diff-add-bg: rgba(52, 211, 153, 0.12);
  --diff-remove-bg: rgba(248, 113, 113, 0.12);
  --gradient-hero: linear-gradient(135deg, #6366f1 0%, #a78bfa 50%, #818cf8 100%);
  --font-mono: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
  --shadow-glow: 0 0 30px var(--primary-glow);
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 16px;
  --transition-fast: 150ms cubic-bezier(0.4, 0, 0.2, 1);
  --transition-smooth: 250ms cubic-bezier(0.4, 0, 0.2, 1);
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html {
  font-size: 16px;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  font-family: var(--font-sans);
  background: var(--bg-root);
  color: var(--text-primary);
  min-height: 100vh;
  line-height: 1.6;
}

#app {
  min-height: 100vh;
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: var(--border);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--text-muted);
}

::selection {
  background: rgba(99, 102, 241, 0.35);
  color: var(--text-primary);
}
</style>
