import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { meApi } from '@/api/me'
import { useAuthStore } from '@/store/auth'
import { Layout } from '@/components/Layout'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import { TIMEZONES } from '@/utils/timezones'

export function Profile() {
  const qc = useQueryClient()
  const { isGuest, setProfile } = useAuthStore()

  const { data, isLoading, error: loadError } = useQuery({
    queryKey: ['me'],
    queryFn: meApi.get,
  })

  const [displayName, setDisplayName] = useState('')
  const [tzId, setTzId] = useState('')

  useEffect(() => {
    if (data) {
      setDisplayName(data.displayName)
      setTzId(data.tzId)
    }
  }, [data])

  const update = useMutation({
    mutationFn: () => meApi.update({ displayName, tzId }),
    onSuccess: (updated) => {
      setProfile(updated.id, updated.email, updated.displayName, updated.role, updated.plan)
      qc.invalidateQueries({ queryKey: ['me'] })
    },
  })

  return (
    <Layout>
      <div className="max-w-lg">
        <div className="flex items-center gap-3 mb-6">
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">My Profile</h1>
          {isGuest && (
            <span className="text-xs bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400 px-2 py-0.5 rounded-full">
              Guest
            </span>
          )}
        </div>
        {isGuest && (
          <p className="mb-4 text-sm text-gray-500 dark:text-gray-400 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3">
            Guest account — your data is tied to this device session.
          </p>
        )}

        {loadError && <ErrorMessage error={loadError} />}

        {isLoading ? (
          <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 flex flex-col gap-4">
            <Skeleton className="h-5 w-24" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-5 w-20" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : data && (
          <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 flex flex-col gap-5">
            {/* Read-only info */}
            <div className="flex flex-col gap-1">
              <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">Email</span>
              <span className="text-sm text-gray-900 dark:text-gray-100">{data.email}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">Plan</span>
              <span className="text-sm text-gray-900 dark:text-gray-100">{data.plan}</span>
            </div>

            <div className="border-t border-gray-200 dark:border-gray-700" />

            {/* Editable fields */}
            <Input
              label="Display name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              minLength={2}
              maxLength={50}
              required
            />

            <div className="flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Timezone</label>
              <select
                value={tzId}
                onChange={(e) => setTzId(e.target.value)}
                className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                {TIMEZONES.map((tz) => (
                  <option key={tz.value} value={tz.value}>{tz.label}</option>
                ))}
              </select>
            </div>

            {update.error && <ErrorMessage error={update.error} />}
            {update.isSuccess && (
              <p className="text-sm text-green-600 dark:text-green-400">Saved.</p>
            )}

            <Button
              loading={update.isPending}
              disabled={!displayName.trim()}
              onClick={() => update.mutate()}
              className="w-full justify-center"
            >
              Save changes
            </Button>
          </div>
        )}
      </div>
    </Layout>
  )
}
