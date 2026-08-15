export type TransferStep =
  | 'IDLE'
  | 'INITIATED'
  | 'AWAITING_OTP'
  | 'SUBMITTING'
  | 'SUCCESS'
  | 'ERROR';

export interface TransferFormFields {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: string;
  currency: string;
  description: string;
}

export interface TransferChallenge {
  challengeId: string;
  expiresAt: string;
}

export interface TransferResult {
  transactionId: string;
  newBalance: number;
}

export interface TransferState {
  step: TransferStep;
  form: TransferFormFields;
  challenge: TransferChallenge | null;
  otpCode: string;
  result: TransferResult | null;
  error: string | null;
}

export const initialTransferState: TransferState = {
  step: 'IDLE',
  form: {
    sourceAccountId: '',
    destinationAccountId: '',
    amount: '',
    currency: 'USD',
    description: '',
  },
  challenge: null,
  otpCode: '',
  result: null,
  error: null,
};
