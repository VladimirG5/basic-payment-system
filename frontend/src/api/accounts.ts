import { apiClient } from './client';
import type { AccountResponse, TransactionResponse } from './types';

export async function getMyAccount(): Promise<AccountResponse> {
  const response = await apiClient.get<AccountResponse>('/api/v1/accounts/me');
  return response.data;
}

export async function getAccount(accountId: number): Promise<AccountResponse> {
  const response = await apiClient.get<AccountResponse>(`/api/v1/accounts/${accountId}`);
  return response.data;
}

export async function getTransactions(accountId: number): Promise<TransactionResponse[]> {
  const response = await apiClient.get<TransactionResponse[]>(`/api/v1/accounts/${accountId}/transactions`);
  return response.data;
}
