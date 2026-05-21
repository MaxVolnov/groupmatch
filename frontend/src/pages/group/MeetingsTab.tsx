import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { meetingsApi } from '@/api/meetings'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Modal } from '@/components/Modal'
import { Spinner } from '@/components/Spinner'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { GroupResponse } from '@/types'
import { DateTime } from 'luxon'

interface Props {
  group: GroupResponse
  currentUserId: string
}

function toIso(local: string): string {
  return DateTime.fromISO(local, { zone: 'local' }).toUTC().toISO()!
}

function defaultDatetime(offsetHours: number): string {
  return DateTime.now().plus({ hours: offsetHours }).toFormat("yyyy-MM-dd'T'HH:mm")
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

function CreateMeetingModal({
  groupId,
  open,
  onClose,
}: {
  groupId: string
  open: boolean
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [startsAt, setStartsAt] = useState(defaultDatetime(24))
  const [endsAt, setEndsAt] = useState(defaultDatetime(25))

  const create = useMutation({
    mutationFn: () =>
      meetingsApi.create(groupId, {
        title,
        description: description || undefined,
        startsAt: toIso(startsAt),
        endsAt: toIso(endsAt),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['meetings', groupId] })
      setTitle('')
      setDescription('')
      onClose()
    },
  })

  return (
    <Modal
      title="Schedule meeting"
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!title.trim()} onClick={() => create.mutate()}>
            Schedule
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <Input
          label="Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Sprint planning"
          minLength={3}
          maxLength={100}
          required
        />
        <Input
          label="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={2000}
        />
        <Input label="Starts at" type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} />
        <Input label="Ends at" type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} />
        {create.error && <ErrorMessage error={create.error} />}
      </div>
    </Modal>
  )
}

export function MeetingsTab({ group, currentUserId }: Props) {
  const isOwner = group.ownerId === currentUserId
  const [showCreate, setShowCreate] = useState(false)
  const qc = useQueryClient()

  const { data: meetings, isLoading, error } = useQuery({
    queryKey: ['meetings', group.id],
    queryFn: () => meetingsApi.list(group.id),
  })

  const del = useMutation({
    mutationFn: (meetingId: string) => meetingsApi.delete(group.id, meetingId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['meetings', group.id] }),
  })

  if (isLoading) return <div className="flex justify-center py-8"><Spinner /></div>
  if (error) return <ErrorMessage error={error} />

  return (
    <div>
      {isOwner && (
        <div className="mb-4 flex justify-end">
          <Button size="sm" onClick={() => setShowCreate(true)}>
            + Schedule meeting
          </Button>
        </div>
      )}

      {meetings && meetings.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-gray-400">No meetings scheduled yet.</p>
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

      <CreateMeetingModal
        groupId={group.id}
        open={showCreate}
        onClose={() => setShowCreate(false)}
      />
    </div>
  )
}
