import { describe, expect, it } from 'vitest'
import {
  canSubmitDeletion,
  deletionBlockReason,
  groupsToDelete,
  groupsToTransfer,
  hasGroupConsequences,
  passwordToSend,
  type DeletionFormState,
} from './accountDeletion'
import type { AccountDeletionPreview } from '@/types'

const PREVIEW: AccountDeletionPreview = {
  graceDays: 30,
  ownedGroups: [
    { groupId: 'g1', title: 'Тимбилдинг', willBeDeleted: false, newOwnerName: 'Аня' },
    { groupId: 'g2', title: 'Заброшенная', willBeDeleted: true, newOwnerName: null },
    { groupId: 'g3', title: 'Настолки', willBeDeleted: false, newOwnerName: 'Борис' },
  ],
}

const base = (over: Partial<DeletionFormState> = {}): DeletionFormState => ({
  isGuest: false,
  password: '',
  acknowledged: false,
  preview: PREVIEW,
  ...over,
})

describe('когда можно отправлять удаление', () => {
  it('нельзя, пока последствия не загружены', () => {
    const state = base({ preview: null, acknowledged: true, password: 'MyPassword1!' })
    expect(deletionBlockReason(state)).toBe('preview-not-loaded')
    expect(canSubmitDeletion(state)).toBe(false)
  })

  /** Главная проверка: без явного согласия запрос не уходит. */
  it('нельзя без явного согласия, даже если пароль введён', () => {
    const state = base({ password: 'MyPassword1!' })
    expect(deletionBlockReason(state)).toBe('not-acknowledged')
    expect(canSubmitDeletion(state)).toBe(false)
  })

  it('нельзя без пароля', () => {
    expect(deletionBlockReason(base({ acknowledged: true }))).toBe('password-required')
  })

  it('пробелы паролем не считаются', () => {
    expect(deletionBlockReason(base({ acknowledged: true, password: '   ' })))
      .toBe('password-required')
  })

  it('можно, когда есть и согласие, и пароль', () => {
    expect(canSubmitDeletion(base({ acknowledged: true, password: 'MyPassword1!' }))).toBe(true)
  })

  /** У гостя пароля нет и требовать его не с чего. */
  it('гостю хватает согласия', () => {
    const guest = base({ isGuest: true, acknowledged: true })
    expect(deletionBlockReason(guest)).toBeNull()
    expect(canSubmitDeletion(guest)).toBe(true)
  })

  it('но и гостю согласие обязательно', () => {
    expect(canSubmitDeletion(base({ isGuest: true }))).toBe(false)
  })

  it('пароль гостя на сервер не уходит', () => {
    expect(passwordToSend(base({ isGuest: true, password: 'что-то' }))).toBeUndefined()
    expect(passwordToSend(base({ password: 'MyPassword1!' }))).toBe('MyPassword1!')
  })

  /**
   * Порядок причин — порядок прохождения экрана. Сообщать про пароль раньше,
   * чем показаны последствия, значит торопить решение.
   */
  it('сначала последствия, потом согласие, потом пароль', () => {
    expect(deletionBlockReason(base({ preview: null }))).toBe('preview-not-loaded')
    expect(deletionBlockReason(base({ acknowledged: false }))).toBe('not-acknowledged')
    expect(deletionBlockReason(base({ acknowledged: true }))).toBe('password-required')
  })
})

describe('что показывается в подтверждении', () => {
  it('группы, которые сменят владельца, названы поимённо', () => {
    expect(groupsToTransfer(PREVIEW).map((g) => g.title)).toEqual(['Тимбилдинг', 'Настолки'])
    expect(groupsToTransfer(PREVIEW).map((g) => g.newOwnerName)).toEqual(['Аня', 'Борис'])
  })

  it('группы, которые исчезнут, отделены от тех, что перейдут', () => {
    expect(groupsToDelete(PREVIEW).map((g) => g.title)).toEqual(['Заброшенная'])
  })

  it('вместе они дают весь список, где человек владелец', () => {
    expect(groupsToTransfer(PREVIEW).length + groupsToDelete(PREVIEW).length)
      .toBe(PREVIEW.ownedGroups.length)
  })

  /** Пустой заголовок «что станет с вашими группами» пугает на ровном месте. */
  it('без своих групп раздел про группы не показывается', () => {
    expect(hasGroupConsequences({ graceDays: 30, ownedGroups: [] })).toBe(false)
    expect(hasGroupConsequences(PREVIEW)).toBe(true)
  })

  it('до загрузки списки пустые, а не падают', () => {
    expect(groupsToTransfer(null)).toEqual([])
    expect(groupsToDelete(null)).toEqual([])
    expect(hasGroupConsequences(null)).toBe(false)
  })
})
