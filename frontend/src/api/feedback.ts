import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { FeedbackRequest, FeedbackResponse } from '@/types'

export const feedbackApi = {
  create: (data: FeedbackRequest): Promise<FeedbackResponse> =>
    IS_MOCK
      ? mockApi.feedback.create(data)
      : api.post<FeedbackResponse>('/feedback', data).then((r) => r.data),
}
