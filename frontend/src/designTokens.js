// Centralized design tokens for values that would otherwise be hardcoded inline across
// multiple components (colors, borders). Prefer Mantine CSS variables where the value
// should follow the active theme; use raw values here only for things Mantine has no
// theme token for (e.g. the keyword-match highlight color).
export const COLORS = {
  BORDER: 'var(--mantine-color-gray-2)',
  BORDER_STRONG: 'var(--mantine-color-gray-3)',
  KEYWORD_HIGHLIGHT: 'rgba(255, 212, 59, 0.5)',
};
