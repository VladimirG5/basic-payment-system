// @vitest-environment jsdom
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as transfersApi from '../api/transfers';
import { useTransferStore } from './useTransferStore';

vi.mock('../api/transfers');

const mockedInitiate = vi.mocked(transfersApi.initiateTransfer);
const mockedConfirm = vi.mocked(transfersApi.confirmTransfer);

describe('useTransferStore', () => {
  beforeEach(() => {
    mockedInitiate.mockReset();
    mockedConfirm.mockReset();
  });

  it("seeds the form with the caller's account id and currency", () => {
    const { result } = renderHook(() => useTransferStore(42, 'EUR'));

    expect(result.current.state.form.sourceAccountId).toBe('42');
    expect(result.current.state.form.currency).toBe('EUR');
    expect(result.current.state.step).toBe('IDLE');
  });

  it('drives the full initiate -> OTP -> confirm -> success flow', async () => {
    mockedInitiate.mockResolvedValue({
      challengeId: 'chal-1',
      expiresAt: '2099-01-01T00:00:00Z',
      status: 'OTP_REQUIRED',
    });
    mockedConfirm.mockResolvedValue({ status: 'SUCCESS', transactionId: 99, newBalance: 60 });

    const { result } = renderHook(() => useTransferStore(1, 'USD'));

    act(() => {
      result.current.dispatch({ type: 'SET_FIELD', field: 'destinationAccountId', value: '2' });
      result.current.dispatch({ type: 'SET_FIELD', field: 'amount', value: '40' });
      result.current.dispatch({ type: 'INITIATE_TRANSFER' });
    });

    expect(result.current.state.step).toBe('INITIATED');
    expect(mockedInitiate).toHaveBeenCalledWith({
      sourceAccountId: 1,
      destinationAccountId: 2,
      amount: 40,
      currency: 'USD',
      description: undefined,
    });

    await waitFor(() => expect(result.current.state.step).toBe('AWAITING_OTP'));
    expect(result.current.state.challenge).toEqual({ challengeId: 'chal-1', expiresAt: '2099-01-01T00:00:00Z' });

    act(() => {
      result.current.dispatch({ type: 'SUBMIT_OTP', otpCode: '123456' });
    });

    expect(result.current.state.step).toBe('SUBMITTING');
    expect(mockedConfirm).toHaveBeenCalledWith({ challengeId: 'chal-1', otpCode: '123456' });

    await waitFor(() => expect(result.current.state.step).toBe('SUCCESS'));
    expect(result.current.state.result).toEqual({ transactionId: '99', newBalance: 60 });
  });

  it('moves to ERROR when initiate fails', async () => {
    mockedInitiate.mockRejectedValue(new Error('insufficient funds'));

    const { result } = renderHook(() => useTransferStore(1, 'USD'));

    act(() => {
      result.current.dispatch({ type: 'SET_FIELD', field: 'destinationAccountId', value: '2' });
      result.current.dispatch({ type: 'SET_FIELD', field: 'amount', value: '40' });
      result.current.dispatch({ type: 'INITIATE_TRANSFER' });
    });

    await waitFor(() => expect(result.current.state.step).toBe('ERROR'));
    expect(result.current.state.error).toBe('insufficient funds');
    expect(result.current.state.challenge).toBeNull();
  });

  it('retries OTP entry in place after a failed confirm, without a new initiate call', async () => {
    mockedInitiate.mockResolvedValue({
      challengeId: 'chal-1',
      expiresAt: '2099-01-01T00:00:00Z',
      status: 'OTP_REQUIRED',
    });
    mockedConfirm.mockRejectedValueOnce(new Error('Invalid OTP'));
    mockedConfirm.mockResolvedValueOnce({ status: 'SUCCESS', transactionId: 100, newBalance: 10 });

    const { result } = renderHook(() => useTransferStore(1, 'USD'));

    act(() => {
      result.current.dispatch({ type: 'SET_FIELD', field: 'destinationAccountId', value: '2' });
      result.current.dispatch({ type: 'SET_FIELD', field: 'amount', value: '40' });
      result.current.dispatch({ type: 'INITIATE_TRANSFER' });
    });
    await waitFor(() => expect(result.current.state.step).toBe('AWAITING_OTP'));

    act(() => {
      result.current.dispatch({ type: 'SUBMIT_OTP', otpCode: '000000' });
    });
    await waitFor(() => expect(result.current.state.step).toBe('ERROR'));
    expect(result.current.state.challenge).not.toBeNull();

    act(() => {
      result.current.dispatch({ type: 'SUBMIT_OTP', otpCode: '123456' });
    });
    expect(result.current.state.step).toBe('SUBMITTING');
    expect(mockedInitiate).toHaveBeenCalledTimes(1);

    await waitFor(() => expect(result.current.state.step).toBe('SUCCESS'));
    expect(result.current.state.result?.transactionId).toBe('100');
  });
});
