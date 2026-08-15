import { apiClient } from './client';
import type { TransferConfirmRequest, TransferConfirmResponse, TransferInitiateRequest, TransferInitiateResponse } from './types';

export async function initiateTransfer(request: TransferInitiateRequest): Promise<TransferInitiateResponse> {
  const response = await apiClient.post<TransferInitiateResponse>('/api/v1/transfers/initiate', request);
  return response.data;
}

export async function confirmTransfer(request: TransferConfirmRequest): Promise<TransferConfirmResponse> {
  const response = await apiClient.post<TransferConfirmResponse>('/api/v1/transfers/confirm', request);
  return response.data;
}
