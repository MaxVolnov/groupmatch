import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { meetingsApi } from '@/api/meetings'
import { Button } from '@/components/Button'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { GroupResponse } from '@/types'
import { DateTime } from 'luxon'

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

function fmtRange(startsAt: string, endsAt: string): string {
  const s = DateTime.fromISO(startsAt).toLocal()
  const e = DateTime.fromISO(endsAt).toLocal()
  if (s.hasSame(e, 'day')) {
    return `${s.toFormat('dd MMM yyyy, HH:mm')} – ${e.toFormat('HH:mm')}`
  }
  return `${s.toFormat('dd MMM HH:mm')} – ${e.toFormat('dd MMM HH:mm')}`
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
  const isOwner = group.ownerId === currentUserId
  const qc = useQueryClient()

  const { data: meetings, isLoading, error } = useQuery({
    queryKey: ['meetings', group.id],
    queryFn: () => meetingsApi.list(group.id),
  })

  const del = useMutation({
    mutationFn: (meetingId: string) => meetingsApi.delete(group.id, meetingId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['meetings', group.id] }),
  })

  if (isLoading) return <MeetingSkeletonList />
  if (error) return <ErrorMessage error={error} />

  return (
    <div>
      {isOwner && (
        <div className="mb-4 flex justify-end">
          <Button size="sm" onClick={onScheduleClick}>
            + Schedule meeting
          </Button>
        </div>
      )}

      {meetings && meetings.length === 0 && (
        <div className="py-12 text-center">
          <p className="text-3xl mb-3">📅</p>
          <p className="text-sm text-gray-500 dark:text-gray-400">No meetings scheduled yet.</p>
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
                  Export .ics
                </Button>
                {isOwner && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => del.mutate(m.id)}
                    loading={del.isPending}
                    className="flex-1 sm:flex-none justify-center"
                  >
                    Delete
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
