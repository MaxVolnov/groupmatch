import type { AccountDeletionPreview, OwnedGroupFate } from '@/types'

/**
 * Решения экрана удаления аккаунта, без React.
 *
 * Здесь то, что нельзя проверять глазами: можно ли уже отправлять запрос и что
 * именно человек увидит в подтверждении. Удаление необратимо через срок
 * отсрочки, поэтому «случайно отправилось» — не косметическая ошибка, и
 * условие отправки должно быть проверяемым отдельно от разметки.
 */

export interface DeletionFormState {
  /** У гостя пароля нет: он его никогда не задавал. */
  isGuest: boolean
  password: string
  /** Явное согласие — отдельным действием, а не «нажал кнопку, значит согласен». */
  acknowledged: boolean
  /** Пока не загружен, последствия неизвестны и показывать нечего. */
  preview: AccountDeletionPreview | null
}

export type DeletionBlockReason =
  | 'preview-not-loaded'
  | 'not-acknowledged'
  | 'password-required'

/**
 * Почему отправлять ещё нельзя. {@code null} — можно.
 *
 * Порядок веток — порядок, в котором человек проходит экран: сначала он видит
 * последствия, потом соглашается, потом подтверждает пароль. Сообщать про
 * пароль раньше, чем показаны последствия, значит торопить решение.
 */
export function deletionBlockReason(state: DeletionFormState): DeletionBlockReason | null {
  if (!state.preview) return 'preview-not-loaded'
  if (!state.acknowledged) return 'not-acknowledged'
  if (!state.isGuest && state.password.trim() === '') return 'password-required'
  return null
}

/** Единственное место, где решается, отправлять ли запрос. */
export function canSubmitDeletion(state: DeletionFormState): boolean {
  return deletionBlockReason(state) === null
}

/** Группы, которые переживут удаление и сменят владельца. */
export function groupsToTransfer(preview: AccountDeletionPreview | null): OwnedGroupFate[] {
  return (preview?.ownedGroups ?? []).filter((g) => !g.willBeDeleted)
}

/** Группы, которые исчезнут вместе с аккаунтом. */
export function groupsToDelete(preview: AccountDeletionPreview | null): OwnedGroupFate[] {
  return (preview?.ownedGroups ?? []).filter((g) => g.willBeDeleted)
}

/**
 * Есть ли вообще о чём предупреждать по группам.
 *
 * Если человек не владелец ни одной группы, раздел про группы показывать не
 * нужно: пустой заголовок «что станет с вашими группами» пугает на ровном
 * месте.
 */
export function hasGroupConsequences(preview: AccountDeletionPreview | null): boolean {
  return (preview?.ownedGroups.length ?? 0) > 0
}

/** Пароль уходит на сервер только когда он есть; у гостя его нет. */
export function passwordToSend(state: DeletionFormState): string | undefined {
  return state.isGuest ? undefined : state.password
}
