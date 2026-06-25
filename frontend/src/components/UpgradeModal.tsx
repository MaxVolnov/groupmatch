import { useNavigate } from 'react-router-dom'
import { Button } from './Button'
import { Modal } from './Modal'

const PRO_FEATURES = [
  'Unlimited groups as owner',
  'Unlimited group membership',
  'Everything in Free',
  'Priority support',
]

interface Props {
  open: boolean
  onClose: () => void
}

export function UpgradeModal({ open, onClose }: Props) {
  const navigate = useNavigate()

  return (
    <Modal
      title="Upgrade to Pro"
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Maybe later</Button>
          <Button
            onClick={() => {
              onClose()
              navigate('/pricing')
            }}
          >
            See pricing
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="text-sm text-gray-600 dark:text-gray-400">
          You've reached the limit of 3 groups on the Free plan. Upgrade to Pro for unlimited groups.
        </p>
        <ul className="flex flex-col gap-2">
          {PRO_FEATURES.map((f) => (
            <li key={f} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
              <span className="text-green-500 shrink-0">✓</span>
              {f}
            </li>
          ))}
        </ul>
      </div>
    </Modal>
  )
}
