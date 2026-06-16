import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { groupsApi } from '@/api/groups'
import { invitesApi } from '@/api/invites'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Modal } from '@/components/Modal'
import { Spinner } from '@/components/Spinner'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { GroupResponse } from '@/types'

interface Props {
  group: GroupResponse
  currentUserId: string
}

function InviteSection({ group }: { group: GroupResponse }) {
  const qc = useQueryClient()
  const [copiedId, setCopiedId] = useState<string | null>(null)

  const { data: invites } = useQuery({
    queryKey: ['invites', group.id],
    queryFn: () => invitesApi.list(group.id),
  })

  const create = useMutation({
    mutationFn: () => invitesApi.create(group.id, { maxUses: 0 }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['invites', group.id] }),
  })

  const revoke = useMutation({
    mutationFn: (inviteId: string) => invitesApi.revoke(group.id, inviteId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['invites', group.id] }),
  })

  const baseUrl = `${window.location.origin}${import.meta.env.BASE_URL}`.replace(/\/$/, '')

  return (
    <div className="mt-6 border-t border-gray-200 dark:border-gray-700 pt-6">
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-medium text-gray-900 dark:text-gray-100">Invite links</h3>
        <Button size="sm" loading={create.isPending} onClick={() => create.mutate()}>
          + New link
        </Button>
      </div>
      {create.error && <ErrorMessage error={create.error} />}
      {invites && invites.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-gray-400">No active invite links.</p>
      )}
      <div className="flex flex-col gap-2">
        {invites?.map((inv) => (
          <div key={inv.id} className="flex items-center gap-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700 p-3">
            <code className="flex-1 min-w-0 truncate text-xs text-gray-700 dark:text-gray-300">
              {baseUrl}/join/{inv.token}
            </code>
            <span className="shrink-0 text-xs text-gray-400 dark:text-gray-500">
              {inv.maxUses === 0 ? '∞' : `${inv.currentUses}/${inv.maxUses}`}
            </span>
            <button
              className="flex items-center justify-center min-h-[44px] min-w-[44px] text-gray-400 dark:text-gray-500 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
              onClick={() => {
                navigator.clipboard.writeText(`${baseUrl}/join/${inv.token}`)
                setCopiedId(inv.id)
                setTimeout(() => setCopiedId(null), 2000)
              }}
              title="Copy link"
            >
              {copiedId === inv.id ? '✓' : '📋'}
            </button>
            <button
              className="flex items-center justify-center min-h-[44px] min-w-[44px] text-gray-400 dark:text-gray-500 hover:text-red-600 dark:hover:text-red-400 transition-colors"
              onClick={() => revoke.mutate(inv.id)}
              title="Revoke"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

export function MembersTab({ group, currentUserId }: Props) {
  const qc = useQueryClient()
  const isOwner = group.ownerId === currentUserId
  const [addOpen, setAddOpen] = useState(false)
  const [addUserId, setAddUserId] = useState('')

  const { data: members, isLoading, error } = useQuery({
    queryKey: ['members', group.id],
    queryFn: () => groupsApi.members(group.id),
  })

  const addMember = useMutation({
    mutationFn: () => groupsApi.addMember(group.id, addUserId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['members', group.id] })
      setAddUserId('')
      setAddOpen(false)
    },
  })

  const removeMember = useMutation({
    mutationFn: (userId: string) => groupsApi.removeMember(group.id, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', group.id] }),
  })

  if (isLoading) return <div className="flex justify-center py-8"><Spinner /></div>
  if (error) return <ErrorMessage error={error} />

  return (
    <div>
      {isOwner && (
        <div className="mb-4 flex justify-end">
          <Button size="sm" onClick={() => setAddOpen(true)}>
            + Add member
          </Button>
        </div>
      )}

      <div className="divide-y divide-gray-200 dark:divide-gray-700 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        {members?.map((m) => (
          <div key={m.userId} className="flex items-center justify-between px-4 py-3">
            <div>
              <span className="font-medium text-gray-900 dark:text-gray-100">{m.displayName}</span>
              <span
                className={`ml-2 rounded-full px-2 py-0.5 text-xs font-medium ${
                  m.role === 'OWNER'
                    ? 'bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300'
                    : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300'
                }`}
              >
                {m.role}
              </span>
            </div>
            {isOwner && m.userId !== currentUserId && m.role !== 'OWNER' && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => removeMember.mutate(m.userId)}
                loading={removeMember.isPending}
              >
                Ban
              </Button>
            )}
            {!isOwner && m.userId === currentUserId && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => removeMember.mutate(m.userId)}
                loading={removeMember.isPending}
              >
                Leave
              </Button>
            )}
          </div>
        ))}
      </div>

      {isOwner && <InviteSection group={group} />}

      <Modal
        title="Add member"
        open={addOpen}
        onClose={() => setAddOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setAddOpen(false)}>Cancel</Button>
            <Button
              loading={addMember.isPending}
              disabled={!addUserId.trim()}
              onClick={() => addMember.mutate()}
            >
              Add
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <Input
            label="User ID"
            value={addUserId}
            onChange={(e) => setAddUserId(e.target.value)}
            placeholder="UUID of the user"
          />
          {addMember.error && <ErrorMessage error={addMember.error} />}
        </div>
      </Modal>
    </div>
  )
}
