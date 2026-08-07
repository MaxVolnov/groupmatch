/**
 * Shared header/footer markup for the standalone static entries
 * (/legal, /about). These pages are deliberately decoupled from the SPA:
 * no React, no router, no i18n runtime and no Zustand import.
 *
 * Auth state is therefore read straight out of the `groupmatch-auth`
 * localStorage key that zustand/persist writes — otherwise a signed-in user
 * lands on /legal and is invited to «Войти» in an account they already have.
 * Read-only and best-effort: a missing or malformed key just means anonymous.
 *
 * Text is copied verbatim from src/components/Footer.tsx (and its ru.json
 * keys) so the two footers cannot drift apart.
 */

const AUTH_STORAGE_KEY = 'groupmatch-auth'

/**
 * Форма ключа задаётся zustand/persist: { state: {...}, version }.
 * Сигналом считаем именно refreshToken: access живёт 15 минут и на
 * статической странице всё равно не обновляется, а refresh — это сессия.
 */
export function isAuthenticated(): boolean {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return false
    const state = JSON.parse(raw)?.state
    return state?.isAuthenticated === true && typeof state?.refreshToken === 'string'
      && state.refreshToken.length > 0
  } catch {
    return false
  }
}

/*
 * Знак — вариант для тёмного фона: статические страницы всегда тёмные
 * (`--gm-bg`), переключателя тем у них нет. В SPA, где темы две, знак
 * подменяется по классу — см. LogoMark в components/Layout.tsx.
 */
const BRAND_HTML = `
    <a href="/" class="flex min-h-[44px] items-center gap-2">
      <img src="/logo-on-dark.svg" alt="" width="32" height="32" onerror="this.remove()">
      <span class="text-lg font-bold text-[var(--gm-text)]">GroupMatch<span class="ml-1.5 align-top text-[11px] font-normal text-[var(--gm-muted)]">beta</span></span>
    </a>`

const ANONYMOUS_ACTIONS_HTML = `
      <a href="/signin" class="flex min-h-[44px] items-center text-sm text-[var(--gm-muted)] transition-colors hover:text-[var(--gm-text)]">Войти</a>
      <a href="/signup" class="inline-flex min-h-[44px] items-center rounded-lg bg-[var(--gm-accent)] px-4 text-sm font-semibold text-[var(--gm-text)] transition-colors hover:bg-[var(--gm-accent-hover)]">Начать бесплатно</a>`

const AUTHENTICATED_ACTIONS_HTML = `
      <a href="/" class="inline-flex min-h-[44px] items-center rounded-lg bg-[var(--gm-accent)] px-4 text-sm font-semibold text-[var(--gm-text)] transition-colors hover:bg-[var(--gm-accent-hover)]">Открыть приложение</a>`

export function headerHtml(authenticated = isAuthenticated()): string {
  return `
<header class="border-b border-[var(--gm-border)]">
  <div class="mx-auto flex max-w-3xl items-center justify-between gap-4 px-4 py-4 sm:px-6">${BRAND_HTML}
    <div class="flex items-center gap-3">${authenticated ? AUTHENTICATED_ACTIONS_HTML : ANONYMOUS_ACTIONS_HTML}
    </div>
  </div>
</header>
`.trim()
}

export const FOOTER_HTML = `
<footer class="border-t border-[var(--gm-border)]">
  <div class="mx-auto flex max-w-3xl flex-col items-center gap-3 px-4 py-10 text-center text-sm text-[var(--gm-muted)] sm:px-6">
    <p class="font-semibold text-[var(--gm-text)]">GroupMatch</p>
    <p>Планирование встреч для групп.</p>
    <p>ИНН: 771887947687</p>
    <a href="mailto:volnov.max@yandex.ru" class="flex min-h-[44px] items-center transition-colors hover:text-[var(--gm-text)]">volnov.max@yandex.ru</a>
    <nav class="flex items-center gap-4">
      <a href="/legal" class="flex min-h-[44px] items-center transition-colors hover:text-[var(--gm-text)]">Правовая информация</a>
      <a href="/about" class="flex min-h-[44px] items-center transition-colors hover:text-[var(--gm-text)]">О нас</a>
    </nav>
    <p>© 2026 Max Wave Studio</p>
  </div>
</footer>
`.trim()

/** Injects the shared chrome into #header-slot / #footer-slot, if present. */
export function mountSiteChrome(): void {
  const header = document.getElementById('header-slot')
  if (header) header.innerHTML = headerHtml()

  const footer = document.getElementById('footer-slot')
  if (footer) footer.innerHTML = FOOTER_HTML
}
