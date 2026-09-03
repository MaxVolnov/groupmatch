import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Modal } from '@/components/Modal'
import { ErrorMessage } from '@/components/ErrorMessage'
import {
  copyRuleFrom,
  needsScopeChoice,
  validateSlotEdit,
  type EditScope,
  type SlotEditorState,
} from '@/utils/slotEditor'
import type { SeriesRule } from '@/utils/series'
import { SeriesForm } from './SeriesForm'

/**
 * Модалка слота — один экран на два входа: строка списка и ячейка сетки.
 *
 * Решения живут в `slotEditor.ts`, здесь только разметка и локальное
 * состояние полей. Форма серии для «создать копию» — та же `SeriesForm`, что
 * во вкладке, с предзаполненным временем.
 */

interface Props {
  state: SlotEditorState | null
  busy?: boolean
  error?: unknown
  onClose: () => void
  onSave: (scope: EditScope, times: { startTime: string; endTime: string }) => void
  onDelete: (scope: EditScope) => void
  onCopy: (rule: SeriesRule) => void
  copySubmitting?: boolean
}

export function SlotModal({ state, busy, error, onClose, onSave, onDelete, onCopy, copySubmitting }: Props) {
  const { t } = useTranslation()
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [mode, setMode] = useState<'edit' | 'copy'>('edit')
  const [opened, setOpened] = useState<string | null>(null)

  // Поля наполняются при смене слота, а не эффектом на каждый рендер: эффект
  // затирал бы правку, если список успел обновиться, пока модалка открыта.
  if (state && opened !== state.slot.id) {
    setOpened(state.slot.id)
    setStartTime(state.startTime)
    setEndTime(state.endTime)
    setMode('edit')
  }

  if (!state) return null

  const issues = validateSlotEdit(startTime, endTime)
  const askScope = needsScopeChoice(state, startTime, endTime)

  return (
    <Modal
      title={t(mode === 'copy' ? 'group.slotModal.copyTitle' : 'group.slotModal.title')}
      open
      onClose={onClose}
    >
      {mode === 'copy' ? (
        <>
          <p className="mb-3 text-sm text-gray-600 dark:text-gray-400">
            {t('group.slotModal.copyHint')}
          </p>
          <SeriesForm
            initial={copyRuleFrom(state)}
            submitting={copySubmitting}
            submitLabel={t('group.slotModal.copySubmit')}
            onSubmit={onCopy}
            onCancel={() => setMode('edit')}
          />
        </>
      ) : (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-gray-600 dark:text-gray-400">{state.date}</p>

          <div className="grid grid-cols-2 gap-3">
            <Input
              label={t('group.availabilityTab.from')}
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
            />
            <Input
              label={t('group.availabilityTab.to')}
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
            />
          </div>

          {issues.map((issue) => (
            <p key={issue} className="text-sm text-red-600 dark:text-red-400">
              {t(`group.slotModal.issue.${issue}`)}
            </p>
          ))}

          {state.isSeries && (
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {t('group.slotModal.seriesNote', { count: state.seriesSize })}
            </p>
          )}

          {error ? <ErrorMessage error={error} /> : null}

          {/*
            Выбор области — после правки и только когда есть о чём спрашивать.
            Спрашивать заранее значит требовать решения раньше, чем человек
            понял, что он меняет.
          */}
          {askScope ? (
            <div className="flex flex-col gap-2 rounded-lg border border-gm-200 bg-gm-50 p-3 dark:border-gm-800 dark:bg-gm-900/30">
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                {t('group.slotModal.scopeQuestion')}
              </p>
              <p className="text-xs text-gray-600 dark:text-gray-400">
                {t('group.slotModal.scopeSingleWarning')}
              </p>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  loading={busy}
                  disabled={issues.length > 0}
                  className="min-h-[44px] justify-center"
                  onClick={() => onSave('single', { startTime, endTime })}
                >
                  {t('group.slotModal.scopeSingle')}
                </Button>
                <Button
                  size="sm"
                  loading={busy}
                  disabled={issues.length > 0}
                  className="min-h-[44px] justify-center"
                  onClick={() => onSave('series', { startTime, endTime })}
                >
                  {t('group.slotModal.scopeSeries', { count: state.seriesSize })}
                </Button>
              </div>
            </div>
          ) : (
            <Button
              loading={busy}
              disabled={issues.length > 0}
              className="min-h-[44px] justify-center"
              onClick={() => onSave('single', { startTime, endTime })}
            >
              {t('group.slotModal.save')}
            </Button>
          )}

          <div className="flex flex-wrap gap-2 border-t border-gray-200 pt-3 dark:border-gray-700">
            <Button variant="secondary" size="sm" className="min-h-[44px] justify-center" onClick={() => setMode('copy')}>
              {t('group.slotModal.copy')}
            </Button>
            <Button
              variant="danger"
              size="sm"
              loading={busy}
              className="min-h-[44px] justify-center"
              onClick={() => onDelete('single')}
            >
              {t('group.slotModal.delete')}
            </Button>
            {state.isSeries && (
              <Button
                variant="danger"
                size="sm"
                loading={busy}
                className="min-h-[44px] justify-center"
                onClick={() => onDelete('series')}
              >
                {t('group.slotModal.deleteSeries', { count: state.seriesSize })}
              </Button>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}
