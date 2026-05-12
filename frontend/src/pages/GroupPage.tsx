import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { groupsApi } from '@/api/groups'
import { useAuthStore } from '@/store/auth'
import { Layout } from '@/components/Layout'
import { Button } from '@/components/Button'
import { Spinner } from '@/components/Spinner'
import { ErrorMessage } from '@/components/ErrorMessage'
import { MembersTab } from './group/MembersTab'
import { AvailabilityTab } from './group/AvailabilityTab'
import { HeatmapTab } from './group/HeatmapTab'
import { MeetingsTab } from './group/MeetingsTab'

type Tab = 'members' | 'availability' | 'heatmap' | 'meetings'

const TABS: { id: Tab; label: string }[] = [
  { id: 'members', label: 'Members' },
  { id: 'availability', label: 'My Availability' },
  { id: 'heatmap', label: 'Heatmap' },
  { id: 'meetings', label: 'Meetings' },
]

export function GroupPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { userId, plan } = useAuthStore()
  const [tab, setTab] = useState<Tab>('heatmap')

  const { data: group, isLoading, error } = useQuery({
    queryKey: ['group', id],
    queryFn: () => groupsApi.get(id!),
    enabled: !!id,
  })

  const deleteGroup = useMutation({
    mutationFn: () => groupsApi.delete(id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['groups'] })
      navigate('/')
    },
  })

  if (isLoading) {
    return (
      <Layout>
        <div className="flex justify-center py-16"><Spinner size="lg" /></div>
      </Layout>
    )
  }

  if (error || !group) {
    return (
      <Layout>
        <ErrorMessage error={error ?? new Error('Group not found')} />
      </Layout>
    )
  }

  const isOwner = group.ownerId === userId

  return (
    <Layout>
      {/* Header */}
      <div className="mb-6">
        <div className="mb-2">
          <Link to="/" className="text-sm text-gray-500 hover:text-gray-700">
            ← Groups
          </Link>
        </div>
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{group.title}</h1>
            {group.description && (
              <p className="mt-1 text-gray-500">{group.description}</p>
            )}
            <div className="mt-2 flex gap-3 text-xs text-gray-400">
              <span>{group.tzId}</span>
              {group.locked && <span>🔒 Locked</span>}
              {group.showParticipants && <span>👥 Names visible</span>}
            </div>
          </div>
          {isOwner && (
            <Button
              variant="danger"
              size="sm"
              loading={deleteGroup.isPending}
              onClick={() => {
                if (confirm('Delete this group? This cannot be undone.')) {
                  deleteGroup.mutate()
                }
              }}
            >
              Delete group
            </Button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="mb-6 border-b">
        <nav className="flex gap-1">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`px-4 py-2.5 text-sm font-medium transition-colors border-b-2 ${
                tab === t.id
                  ? 'border-indigo-600 text-indigo-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab content */}
      {tab === 'members' && userId && (
        <MembersTab group={group} currentUserId={userId} />
      )}
      {tab === 'availability' && plan && (
        <AvailabilityTab groupId={group.id} callerPlan={plan} />
      )}
      {tab === 'heatmap' && <HeatmapTab groupId={group.id} />}
      {tab === 'meetings' && userId && (
        <MeetingsTab group={group} currentUserId={userId} />
      )}
    </Layout>
  )
}
