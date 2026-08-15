import type { TransferFormFields } from '../types/transfer';

export type TransferAction =
  | { type: 'SET_FIELD'; field: keyof TransferFormFields; value: string }
  | { type: 'INITIATE_TRANSFER' }
  | { type: 'OTP_SENT'; challengeId: string; expiresAt: string }
  | { type: 'SUBMIT_OTP'; otpCode: string }
  | { type: 'TRANSFER_SUCCESS'; transactionId: string; newBalance: number }
  | { type: 'TRANSFER_FAILED'; error: string };