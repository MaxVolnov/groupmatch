import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { DateTime } from 'luxon'
import { availabilityApi } from '@/api/availability'
import type { AvailabilityResponse } from '@/types'
import { toSeriesRequest, type SeriesRule } from '@/utils/series'
import { applySlotEdit, openSlotEditor, type EditScope, type SlotEditorState } from '@/utils/slotEditor'

/**
 * Обвязка модалки слота: состояние и мутации.
 *
 * Хук общий на оба входа — строку списка и ячейку сетки. Иначе «одна и та же
 * модалка» превратилась бы в две копии обвязки, которые однажды разойдутся в
 * мелочи вроде того, что инвалидируется после сохранения.
 *
 * Решения (что открылось, что спросить, куда отправить) живут в
 * `slotEditor.ts` и проверяются тестами; здесь только React и сеть.
 */
export function useSlotEditor(groupId: string, slots: AvailabilityResponse[], timeZone: string) {
  const qc = useQueryClient()
  const [state, setState] = useState<SlotEditorState | null>(null)

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['availability', groupId] })
    qc.invalidateQueries({ queryKey: ['heatmap', groupId] })
  }

  const close = () => setState(null)

  const save = useMutation({
    mutationFn: ({ scope, times }: { scope: EditScope; times: { startTime: string; endTime: string } }) =>
      applySlotEdit(state!, scope, times, timeZone, availabilityApi),
    onSuccess: () => { invalidate(); close() },
  })

  const remove = useMutation({
    mutationFn: (scope: EditScope) => availabilityApi.deleteSlotScoped(state!.slot.id, scope),
    onSuccess: () => { invalidate(); close() },
  })

  const copy = useMutation({
    mutationFn: (rule: SeriesRule) =>
      availabilityApi.addSeries(groupId, toSeriesRequest(rule, timeZone)),
    onSuccess: () => { invalidate(); close() },
  })

  return {
    state,
    open: (slot: AvailabilityResponse) => setState(openSlotEditor(slot, slots, timeZone)),
    close,
    busy: save.isPending || remove.isPending,
    copySubmitting: copy.isPending,
    error: save.error ?? remove.error ?? copy.error,
    onSave: (scope: EditScope, times: { startTime: string; endTime: string }) =>
      save.mutate({ scope, times }),
    onDelete: (scope: EditScope) => remove.mutate(scope),
    onCopy: (rule: SeriesRule) => copy.mutate(rule),
  }
}

/** Зона, в которой считаются слоты, серии и очистка. Одна на весь экран группы. */
export const userZone = () => DateTime.local().zoneName
