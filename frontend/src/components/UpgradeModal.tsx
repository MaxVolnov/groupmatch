import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { Modal } from './Modal'

const PRO_FEATURES = [
  'upgradeModal.features.groups',
  'upgradeModal.features.membership',
  'upgradeModal.features.everything',
  'upgradeModal.features.support',
]

interface Props {
  open: boolean
  onClose: () => void
}

export function UpgradeModal({ open, onClose }: Props) {
  const navigate = useNavigate()
  const { t } = useTranslation()

  return (
    <Modal
      title={t('upgradeModal.title')}
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>{t('upgradeModal.maybeLater')}</Button>
          <Button
            onClick={() => {
              onClose()
              navigate('/pricing')
            }}
          >
            {t('upgradeModal.seePricing')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="text-sm text-gray-600 dark:text-gray-400">
          {t('upgradeModal.body')}
        </p>
        <ul className="flex flex-col gap-2">
          {PRO_FEATURES.map((f) => (
            <li key={f} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
              <span className="text-green-500 shrink-0">✓</span>
              {t(f)}
            </li>
          ))}
        </ul>
      </div>
    </Modal>
  )
}
