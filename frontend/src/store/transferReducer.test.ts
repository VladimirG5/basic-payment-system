import { describe, expect, it } from 'vitest';
import { initialTransferState } from '../types/transfer';
import type { TransferState } from '../types/transfer';
import { transferReducer } from './transferReducer';

describe('transferReducer', () => {
  it('SET_FIELD updates the named form field while IDLE, with no effect', () => {
    const [state, effect] = transferReducer(initialTransferState, {
      type: 'SET_FIELD',
      field: 'amount',
      value: '100.00',
    });

    expect(state.form.amount).toBe('100.00');
    expect(state.step).toBe('IDLE');
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('SET_FIELD from ERROR clears the error and returns to IDLE', () => {
    const errorState: TransferState = { ...initialTransferState, step: 'ERROR', error: 'boom' };

    const [state] = transferReducer(errorState, {
      type: 'SET_FIELD',
      field: 'description',
      value: 'rent',
    });

    expect(state.step).toBe('IDLE');
    expect(state.error).toBeNull();
    expect(state.form.description).toBe('rent');
  });

  it('SET_FIELD is a no-op once a transfer is in flight', () => {
    const inFlight: TransferState = { ...initialTransferState, step: 'SUBMITTING' };

    const [state, effect] = transferReducer(inFlight, {
      type: 'SET_FIELD',
      field: 'amount',
      value: '999',
    });

    expect(state).toBe(inFlight);
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('INITIATE_TRANSFER moves IDLE -> INITIATED and requests the initiate effect', () => {
    const [state, effect] = transferReducer(initialTransferState, { type: 'INITIATE_TRANSFER' });

    expect(state.step).toBe('INITIATED');
    expect(effect).toEqual({ type: 'CALL_INITIATE' });
  });

  it('INITIATE_TRANSFER is a no-op outside of IDLE', () => {
    const awaitingOtp: TransferState = { ...initialTransferState, step: 'AWAITING_OTP' };

    const [state, effect] = transferReducer(awaitingOtp, { type: 'INITIATE_TRANSFER' });

    expect(state).toBe(awaitingOtp);
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('OTP_SENT moves INITIATED -> AWAITING_OTP and stores the challenge', () => {
    const initiated: TransferState = { ...initialTransferState, step: 'INITIATED' };

    const [state, effect] = transferReducer(initiated, {
      type: 'OTP_SENT',
      challengeId: 'chal-1',
      expiresAt: '2026-08-15T12:03:00Z',
    });

    expect(state.step).toBe('AWAITING_OTP');
    expect(state.challenge).toEqual({ challengeId: 'chal-1', expiresAt: '2026-08-15T12:03:00Z' });
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('OTP_SENT is a no-op outside of INITIATED', () => {
    const [state, effect] = transferReducer(initialTransferState, {
      type: 'OTP_SENT',
      challengeId: 'chal-1',
      expiresAt: '2026-08-15T12:03:00Z',
    });

    expect(state).toBe(initialTransferState);
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('SUBMIT_OTP moves AWAITING_OTP -> SUBMITTING and requests the confirm effect', () => {
    const awaitingOtp: TransferState = {
      ...initialTransferState,
      step: 'AWAITING_OTP',
      challenge: { challengeId: 'chal-1', expiresAt: '2026-08-15T12:03:00Z' },
    };

    const [state, effect] = transferReducer(awaitingOtp, { type: 'SUBMIT_OTP', otpCode: '123456' });

    expect(state.step).toBe('SUBMITTING');
    expect(state.otpCode).toBe('123456');
    expect(effect).toEqual({ type: 'CALL_CONFIRM' });
  });

  it('SUBMIT_OTP is a no-op outside of AWAITING_OTP', () => {
    const [state, effect] = transferReducer(initialTransferState, {
      type: 'SUBMIT_OTP',
      otpCode: '123456',
    });

    expect(state).toBe(initialTransferState);
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('TRANSFER_SUCCESS moves SUBMITTING -> SUCCESS and stores the result', () => {
    const submitting: TransferState = { ...initialTransferState, step: 'SUBMITTING' };

    const [state, effect] = transferReducer(submitting, {
      type: 'TRANSFER_SUCCESS',
      transactionId: 'txn-1',
      newBalance: 42.5,
    });

    expect(state.step).toBe('SUCCESS');
    expect(state.result).toEqual({ transactionId: 'txn-1', newBalance: 42.5 });
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('TRANSFER_SUCCESS is a no-op outside of SUBMITTING', () => {
    const [state, effect] = transferReducer(initialTransferState, {
      type: 'TRANSFER_SUCCESS',
      transactionId: 'txn-1',
      newBalance: 42.5,
    });

    expect(state).toBe(initialTransferState);
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('TRANSFER_FAILED moves INITIATED -> ERROR and stores the error', () => {
    const initiated: TransferState = { ...initialTransferState, step: 'INITIATED' };

    const [state, effect] = transferReducer(initiated, {
      type: 'TRANSFER_FAILED',
      error: 'insufficient funds',
    });

    expect(state.step).toBe('ERROR');
    expect(state.error).toBe('insufficient funds');
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('TRANSFER_FAILED moves SUBMITTING -> ERROR and stores the error', () => {
    const submitting: TransferState = { ...initialTransferState, step: 'SUBMITTING' };

    const [state, effect] = transferReducer(submitting, {
      type: 'TRANSFER_FAILED',
      error: 'otp expired',
    });

    expect(state.step).toBe('ERROR');
    expect(state.error).toBe('otp expired');
    expect(effect).toEqual({ type: 'NONE' });
  });

  it('TRANSFER_FAILED is a no-op outside of INITIATED/SUBMITTING', () => {
    const [state, effect] = transferReducer(initialTransferState, {
      type: 'TRANSFER_FAILED',
      error: 'should not apply',
    });

    expect(state).toBe(initialTransferState);
    expect(effect).toEqual({ type: 'NONE' });
  });
});
