/**
 * Открыта ли страница во встроенном браузере мессенджера.
 *
 * Зачем это знать. Гостевой аккаунт живёт refresh-токеном в localStorage
 * конкретного браузера — ни email, ни пароля у него нет. Встроенный браузер
 * Telegram или Instagram — это отдельное хранилище, к которому у человека нет
 * доступа ни с компьютера, ни из Safari на том же телефоне. То есть гость,
 * зашедший по ссылке прямо из переписки, в свой аккаунт больше не вернётся
 * никогда, и узнает об этом через неделю, когда попробует.
 *
 * Предупредить дешевле, чем потом объяснять.
 *
 * Определение намеренно грубое: перечисленные приложения представляются в
 * User-Agent честно и своими именами. Ложное срабатывание стоит одной лишней
 * строки подсказки, пропуск — потерянного аккаунта, так что перекос в сторону
 * срабатывания здесь правильный.
 */

const IN_APP_MARKERS = [
  'Telegram',      // Telegram, все платформы
  'WhatsApp',      // WhatsApp
  'Instagram',     // Instagram
  'FBAN',          // Facebook для iOS
  'FBAV',          // Facebook для Android
  'FB_IAB',        // встроенный браузер Facebook
  'Line/',         // LINE
  'MicroMessenger',// WeChat
  'VKAndroidApp',  // VK
  'OKApp',         // Одноклассники
] as const

export function isInAppBrowser(userAgent: string = navigator.userAgent): boolean {
  if (!userAgent) return false
  return IN_APP_MARKERS.some((marker) => userAgent.includes(marker))
}
