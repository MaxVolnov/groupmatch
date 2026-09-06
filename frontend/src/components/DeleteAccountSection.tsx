import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { meApi } from '@/api/me'
import { useAuthStore } from '@/store/auth'
import { Button } from './Button'
import { Input } from './Input'
import { Modal } from './Modal'
import { ErrorMessage } from './ErrorMessage'
import {
  canSubmitDeletion,
  groupsToDelete,
  groupsToTransfer,
  hasGroupConsequences,
  passwordToSend,
  type DeletionFormState,
} from '@/utils/accountDeletion'

/**
 * Удаление аккаунта в профиле.
 *
 * Отдельный компонент, а не блок в Profile.tsx: у него своё состояние, свой
 * запрос за последствиями и своя модалка, и в общем файле профиля это заняло бы
 * больше места, чем всё остальное вместе.
 *
 * Решения о том, можно ли отправлять запрос и что показывать, живут в
 * `utils/accountDeletion.ts` и проверяются тестами. Здесь только разметка.
 */
export function DeleteAccountSection({ isGuest }: { isGuest: boolean }) {
  const { t } = useTranslation()
  const logout = useAuthStore((s) => s.logout)

  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [acknowledged, setAcknowledged] = useState(false)

  // Последствия тянутся только когда модалка открыта: на самом экране профиля
  // они не нужны, а лишний запрос при каждом заходе — нужен ещё меньше.
  const preview = useQuery({
    queryKey: ['deletion-preview'],
    queryFn: meApi.deletionPreview,
    enabled: open,
  })

  const state: DeletionFormState = {
    isGuest,
    password,
    acknowledged,
    preview: preview.data ?? null,
  }

  const remove = useMutation({
    mutationFn: () => meApi.deleteAccount(passwordToSend(state)),
    // Выход сразу: токены на сервере уже отозваны, и оставаться на экране
    // профиля удалённого аккаунта не в чем.
    onSuccess: () => logout(),
  })

  const close = () => {
    setOpen(false)
    setPassword('')
    setAcknowledged(false)
    remove.reset()
  }

  const transferred = groupsToTransfer(state.preview)
  const removed = groupsToDelete(state.preview)

  return (
    <section className="mt-10 rounded-xl border border-red-200 bg-red-50/50 p-5 dark:border-red-900 dark:bg-red-950/20">
      <h2 className="text-sm font-semibold text-red-800 dark:text-red-300">
        {t('profile.dangerZone')}
      </h2>
      <p className="mt-1 text-sm text-gray-700 dark:text-gray-300">
        {t('profile.deleteAccountHint')}
      </p>
      <Button
        variant="danger"
        size="sm"
        className="mt-4 min-h-[44px]"
        onClick={() => setOpen(true)}
      >
        {t('profile.deleteAccount')}
      </Button>

      <Modal title={t('profile.deleteAccountTitle')} open={open} onClose={close}>
        {preview.isLoading && (
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('common.loading')}</p>
        )}

        {preview.isError && <ErrorMessage error={preview.error} />}

        {preview.data && (
          <div className="flex flex-col gap-4">
            {/*
              Конкретика вместо «вы уверены?»: человек должен увидеть, что
              станет с каждой его группой, до того как согласится.
            */}
            {hasGroupConsequences(state.preview) && (
              <div className="flex flex-col gap-3">
                {transferred.length > 0 && (
                  <div>
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {t('profile.deleteGroupsTransferred')}
                    </p>
                    <ul className="mt-1 list-disc pl-5 text-sm text-gray-700 dark:text-gray-300">
                      {transferred.map((g) => (
                        <li key={g.groupId}>
                          {t('profile.deleteGroupTransferredItem', {
                            title: g.title,
                            name: g.newOwnerName,
                          })}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {removed.length > 0 && (
                  <div>
                    <p className="text-sm font-medium text-red-800 dark:text-red-300">
                      {t('profile.deleteGroupsRemoved')}
                    </p>
                    <ul className="mt-1 list-disc pl-5 text-sm text-gray-700 dark:text-gray-300">
                      {removed.map((g) => (
                        <li key={g.groupId}>{g.title}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}

            <ul className="list-disc pl-5 text-sm text-gray-700 dark:text-gray-300">
              <li>{t('profile.deleteConsequenceSignin')}</li>
              <li>{t('profile.deleteConsequenceGrace', { days: preview.data.graceDays })}</li>
            </ul>

            <label className="flex cursor-pointer items-start gap-2 text-sm text-gray-800 dark:text-gray-200">
              <input
                type="checkbox"
                checked={acknowledged}
                onChange={(e) => setAcknowledged(e.target.checked)}
                className="mt-0.5 h-4 w-4"
              />
              <span>{t('profile.deleteAcknowledge')}</span>
            </label>

            {!isGuest && (
              <Input
                label={t('profile.deleteConfirmPassword')}
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            )}

            {remove.error ? <ErrorMessage error={remove.error} /> : null}

            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="secondary" className="min-h-[44px]" onClick={close}>
                {t('common.cancel')}
              </Button>
              <Button
                variant="danger"
                className="min-h-[44px]"
                loading={remove.isPending}
                disabled={!canSubmitDeletion(state)}
                onClick={() => remove.mutate()}
              >
                {t('profile.deleteAccountConfirm')}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </section>
  )
}
