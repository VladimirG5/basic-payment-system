export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
}

export interface RegisterResponse {
  userId: number;
  fullName: string;
  email: string;
  accountId: number;
  iban: string;
  balance: number;
  currency: string;
  accountStatus: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export interface AccountResponse {
  accountId: number;
  iban: string;
  balance: number;
  currency: string;
  status: 'ACTIVE' | 'FROZEN';
}

export type TransactionType = 'SENT' | 'RECEIVED';

export interface TransactionResponse {
  transactionId: number;
  type: TransactionType;
  amount: number;
  counterpartyName: string | null;
  counterpartyAccount: string | null;
  timestamp: string;
  status: string;
  referenceNote: string | null;
}

export interface TransferInitiateRequest {
  sourceAccountId: number;
  destinationAccountId: number;
  amount: number;
  currency: string;
  description?: string;
}

export interface TransferInitiateResponse {
  challengeId: string;
  expiresAt: string;
  status: string;
}

export interface TransferConfirmRequest {
  challengeId: string;
  otpCode: string;
}

export interface TransferConfirmResponse {
  status: string;
  transactionId: number;
  newBalance: number;
}
