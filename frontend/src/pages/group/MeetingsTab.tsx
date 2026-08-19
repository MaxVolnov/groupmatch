import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { meetingsApi } from '@/api/meetings'
import { Button } from '@/components/Button'
import { CalendarSubscribeButton } from '@/components/CalendarSubscribeButton'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { GroupResponse } from '@/types'
import { fmtRange } from '@/utils/datetime'

interface Props {
  group: GroupResponse
  currentUserId: string
  onScheduleClick: () => void
}

async function downloadIcs(groupId: string, meetingId: string) {
  const data = await meetingsApi.exportIcs(groupId, meetingId)
  const blob = new Blob([data], { type: 'text/calendar' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'meeting.ics'
  a.click()
  URL.revokeObjectURL(url)
}

function MeetingSkeletonList() {
  return (
    <div className="flex flex-col gap-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-5 py-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0 flex-1">
              <Skeleton className="h-5 w-1/2 mb-2" />
              <Skeleton className="h-4 w-2/3" />
            </div>
            <div className="flex items-center gap-2 sm:shrink-0">
              <Skeleton className="h-9 w-24" />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

export function MeetingsTab({ group, currentUserId, onScheduleClick }: Props) {
  const { t } = useTranslation()
  const isOwner = group.ownerId === currentUserId
  const qc = useQueryClient()
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const { data: meetings, isLoading, error } = useQuery({
    queryKey: ['meetings', group.id],
    queryFn: () => meetingsApi.list(group.id),
  })

  const del = useMutation({
    mutationFn: (meetingId: string) => meetingsApi.delete(group.id, meetingId),
    onMutate: (meetingId) => setDeletingId(meetingId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['meetings', group.id] }),
    onSettled: () => setDeletingId(null),
  })

  if (isLoading) return <MeetingSkeletonList />
  if (error) return <ErrorMessage error={error} />

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-end gap-2">
        <CalendarSubscribeButton groupId={group.id} />
        {isOwner && (
          <Button size="sm" onClick={onScheduleClick}>
            {t('group.meetingsTab.schedule')}
          </Button>
        )}
      </div>

      {meetings && meetings.length === 0 && (
        <div className="py-12 text-center">
          <p className="text-3xl mb-3">📅</p>
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('group.meetingsTab.noMeetings')}</p>
        </div>
      )}

      <div className="flex flex-col gap-3">
        {meetings?.map((m) => (
          <div key={m.id} className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-5 py-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <p className="font-medium text-gray-900 dark:text-gray-100">{m.title}</p>
                <p className="text-sm text-gray-500 dark:text-gray-400">{fmtRange(m.startsAt, m.endsAt)}</p>
                {m.description && (
                  <p className="mt-1 text-sm text-gray-400 dark:text-gray-500">{m.description}</p>
                )}
              </div>
              <div className="flex items-center gap-2 sm:shrink-0">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => downloadIcs(group.id, m.id)}
                  className="flex-1 sm:flex-none justify-center"
                >
                  {t('group.meetingsTab.exportIcs')}
                </Button>
                {isOwner && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => del.mutate(m.id)}
                    loading={deletingId === m.id}
                    className="flex-1 sm:flex-none justify-center"
                  >
                    {t('group.meetingsTab.delete')}
                  </Button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
