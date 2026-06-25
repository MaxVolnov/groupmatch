import { api } from './axios'
import type { CreatePaymentRequest, CreatePaymentResponse, SubscriptionResponse } from '@/types'

export const paymentsApi = {
  createPayment: (data: CreatePaymentRequest): Promise<CreatePaymentResponse> =>
    api.post<CreatePaymentResponse>('/payments/yookassa/create', data).then((r) => r.data),

  getSubscription: (): Promise<SubscriptionResponse | null> =>
    api.get<SubscriptionResponse>('/payments/subscription')
      .then((r) => r.data)
      .catch((e) => e?.response?.status === 204 ? null : Promise.reject(e)),
}
