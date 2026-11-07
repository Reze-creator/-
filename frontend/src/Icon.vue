<script setup>
defineProps({ name: String, size: { type: [Number, String], default: 20 } })
const paths = {
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  calendar:
    'M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2 M8 14h3 M8 18h6',
  car: 'M5 17H3v-5l2-7h14l2 7v5h-2 M5 17h14 M5 17v3 M19 17v3 M3 12h18 M7 15h1 M16 15h1 M7 5l-2 7 M17 5l2 7',
  bolt: 'M13 2 3 14h8l-1 8 11-13h-8l1-7z',
  pin: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0z M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  clock: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M12 6v6l4 2',
  arrow: 'M5 12h14 M13 6l6 6-6 6',
  chevron: 'm9 5 7 7-7 7',
  check: 'm5 12 4 4L19 6',
  close: 'm6 6 12 12 M18 6 6 18',
  shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3 M8 12l3 3 5-6',
  info: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M12 11v6 M12 7h.01',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-3a5 5 0 0 1 5-5h6a5 5 0 0 1 5 5v3',
  logout: 'M9 3H4v18h5 M9 12h12 M17 8l4 4-4 4',
  search: 'M19 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0 M15 15l6 6',
  refresh: 'M20 7V3l-3 3a9 9 0 1 0 3 11 M20 7h-5',
  wallet: 'M21 8H5a2 2 0 0 1 0-4h13v4 M3 6v13a2 2 0 0 0 2 2h16V8 M21 12h-5v5h5 M17 14.5h.01',
  settings: 'M4 7h16 M4 17h16 M8 4v6 M16 14v6',
  leaf: 'M20 3C8 2 2 8 5 15c3 7 14 5 15-12 M4 21 15 10',
}
</script>
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || paths.info" />
  </svg>
</template>
