import { useCallback, useRef, useState } from 'react';
import { getErrorMessage } from '../api/errors';
import { confirmTransfer, initiateTransfer } from '../api/transfers';
import { initialTransferState, type TransferState } from '../types/transfer';
import type { TransferAction } from './transferActions';
import type { TransferEffect } from './transferEffects';
import { transferReducer } from './transferReducer';

function runEffect(effect: TransferEffect, state: TransferState, dispatch: (action: TransferAction) => void): void {
  switch (effect.type) {
    case 'NONE':
      return;

    case 'CALL_INITIATE':
      initiateTransfer({
        sourceAccountId: Number(state.form.sourceAccountId),
        destinationAccountId: Number(state.form.destinationAccountId),
        amount: Number(state.form.amount),
        currency: state.form.currency,
        description: state.form.description || undefined,
      })
        .then((response) => {
          dispatch({ type: 'OTP_SENT', challengeId: response.challengeId, expiresAt: response.expiresAt });
        })
        .catch((error: unknown) => {
          dispatch({ type: 'TRANSFER_FAILED', error: getErrorMessage(error) });
        });
      return;

    case 'CALL_CONFIRM':
      // state.challenge is always set here - CALL_CONFIRM is only ever produced by
      // SUBMIT_OTP, which the reducer only accepts from AWAITING_OTP or an ERROR that
      // already carries a challenge (see transferReducer.ts).
      confirmTransfer({ challengeId: state.challenge!.challengeId, otpCode: state.otpCode })
        .then((response) => {
          dispatch({
            type: 'TRANSFER_SUCCESS',
            transactionId: String(response.transactionId),
            newBalance: response.newBalance,
          });
        })
        .catch((error: unknown) => {
          dispatch({ type: 'TRANSFER_FAILED', error: getErrorMessage(error) });
        });
      return;
  }
}

export interface TransferStore {
  state: TransferState;
  dispatch: (action: TransferAction) => void;
}

/**
 * TCA-style store: transferReducer is pure and returns [state, effect], which
 * useReducer alone can't express, so this drives it manually. Effects read
 * from a ref (not the `state` closure) because they run from async API
 * callbacks that can fire well after the render that scheduled them.
 */
export function useTransferStore(sourceAccountId: number, currency: string): TransferStore {
  const [state, setState] = useState<TransferState>(() => ({
    ...initialTransferState,
    form: { ...initialTransferState.form, sourceAccountId: String(sourceAccountId), currency },
  }));
  const stateRef = useRef(state);
  stateRef.current = state;

  const dispatch = useCallback((action: TransferAction) => {
    const [nextState, effect] = transferReducer(stateRef.current, action);
    stateRef.current = nextState;
    setState(nextState);
    runEffect(effect, nextState, dispatch);
  }, []);

  return { state, dispatch };
}
