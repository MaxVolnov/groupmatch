import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
import { EditGroupModal } from './group/EditGroupModal'
import { CreateMeetingModal } from './group/CreateMeetingModal'
import type { HeatmapSlot } from '@/types'
import { DateTime } from 'luxon'

type Tab = 'members' | 'availability' | 'heatmap' | 'meetings'

const TABS: { id: Tab; labelKey: string }[] = [
  { id: 'members', labelKey: 'group.tabs.members' },
  { id: 'availability', labelKey: 'group.tabs.availability' },
  { id: 'heatmap', labelKey: 'group.tabs.heatmap' },
  { id: 'meetings', labelKey: 'group.tabs.meetings' },
]

export function GroupPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { t } = useTranslation()
  const { userId, plan } = useAuthStore()
  const [tab, setTab] = useState<Tab>('heatmap')
  const [showEdit, setShowEdit] = useState(false)
  const [showCreateMeeting, setShowCreateMeeting] = useState(false)
  const [meetingPrefill, setMeetingPrefill] = useState<{ startsAt: string; endsAt: string } | undefined>(undefined)

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

  const openCreateMeeting = (prefill?: { startsAt: string; endsAt: string }) => {
    setMeetingPrefill(prefill)
    setShowCreateMeeting(true)
  }

  const closeCreateMeeting = () => {
    setShowCreateMeeting(false)
    setMeetingPrefill(undefined)
  }

  useEffect(() => {
    if (group?.title) {
      document.title = `${group.title} · GroupMatch`
    }
    return () => { document.title = 'GroupMatch' }
  }, [group?.title])

  const handleHeatmapSlotClick = (slot: HeatmapSlot) => {
    const start = DateTime.fromISO(slot.startsAt)
    openCreateMeeting({
      startsAt: slot.startsAt,
      endsAt: start.plus({ hours: 1 }).toUTC().toISO()!,
    })
  }

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
        <ErrorMessage error={error ?? new Error(t('group.notFound'))} />
      </Layout>
    )
  }

  const isOwner = group.ownerId === userId

  return (
    <Layout>
      {/* Header */}
      <div className="mb-6">
        <div className="mb-2">
          <Link to="/" className="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200">
            {t('group.backToGroups')}
          </Link>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 truncate">{group.title}</h1>
            {group.description && (
              <p className="mt-1 text-gray-500 dark:text-gray-400">{group.description}</p>
            )}
            <div className="mt-2 flex flex-wrap gap-3 text-xs text-gray-400 dark:text-gray-500">
              <span>{group.tzId}</span>
              {group.locked && <span>{t('group.locked')}</span>}
              {group.showParticipants && <span>{t('group.namesVisible')}</span>}
            </div>
          </div>
          {isOwner && (
            <div className="flex flex-wrap gap-2 sm:shrink-0">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setShowEdit(true)}
                className="flex-1 sm:flex-none justify-center"
              >
                {t('group.editGroup')}
              </Button>
              <Button
                variant="danger"
                size="sm"
                loading={deleteGroup.isPending}
                onClick={() => {
                  if (confirm(t('group.deleteConfirm'))) {
                    deleteGroup.mutate()
                  }
                }}
                className="flex-1 sm:flex-none justify-center"
              >
                {t('group.deleteGroup')}
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* Tabs — scrollable on mobile */}
      <div className="mb-6 border-b border-gray-200 dark:border-gray-700 overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
        <nav className="flex gap-1 min-w-max sm:min-w-0">
          {TABS.map((tabDef) => (
            <button
              key={tabDef.id}
              onClick={() => setTab(tabDef.id)}
              className={`whitespace-nowrap px-4 py-2.5 text-sm font-medium transition-colors border-b-2 min-h-[44px] ${
                tab === tabDef.id
                  ? 'border-gm-600 text-gm-600 dark:border-gm-400 dark:text-gm-400'
                  : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
              }`}
            >
              {t(tabDef.labelKey)}
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
      {tab === 'heatmap' && (
        <HeatmapTab groupId={group.id} isOwner={isOwner} onCreateMeeting={handleHeatmapSlotClick} />
      )}
      {tab === 'meetings' && userId && (
        <MeetingsTab group={group} currentUserId={userId} onScheduleClick={() => openCreateMeeting()} />
      )}

      <EditGroupModal group={group} open={showEdit} onClose={() => setShowEdit(false)} />
      <CreateMeetingModal
        groupId={group.id}
        open={showCreateMeeting}
        onClose={closeCreateMeeting}
        initialStartsAt={meetingPrefill?.startsAt}
        initialEndsAt={meetingPrefill?.endsAt}
      />
    </Layout>
  )
}
