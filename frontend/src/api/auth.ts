import { apiClient } from './client';
import type { ChangePasswordRequest, LoginRequest, LoginResponse, RegisterRequest, RegisterResponse } from './types';

export async function register(request: RegisterRequest): Promise<RegisterResponse> {
  const response = await apiClient.post<RegisterResponse>('/api/v1/auth/register', request);
  return response.data;
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/api/v1/auth/login', request);
  return response.data;
}

export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await apiClient.post<void>('/api/v1/auth/change-password', request);
}
