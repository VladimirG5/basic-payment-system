import type { TransferState } from '../types/transfer';
import { initialTransferState } from '../types/transfer';
import type { TransferAction } from './transferActions';
import type { TransferEffect } from './transferEffects';
import { noEffect } from './transferEffects';

export type TransferReducerResult = [TransferState, TransferEffect];

const unchanged = (state: TransferState): TransferReducerResult => [state, noEffect];

/**
 * Pure state machine for the transfer wizard. Illegal transitions (an action
 * arriving while the state isn't in the step it applies to — e.g. a stale
 * async response landing after the user already backed out) are no-ops
 * rather than thrown errors, since effects race with user/UI actions.
 */
export function transferReducer(state: TransferState, action: TransferAction): TransferReducerResult {
  switch (action.type) {
    case 'SET_FIELD': {
      if (state.step !== 'IDLE' && state.step !== 'ERROR') {
        return unchanged(state);
      }
      return [
        {
          ...state,
          step: 'IDLE',
          error: null,
          form: { ...state.form, [action.field]: action.value },
        },
        noEffect,
      ];
    }

    case 'INITIATE_TRANSFER': {
      if (state.step !== 'IDLE') {
        return unchanged(state);
      }
      return [{ ...state, step: 'INITIATED', error: null }, { type: 'CALL_INITIATE' }];
    }

    case 'OTP_SENT': {
      if (state.step !== 'INITIATED') {
        return unchanged(state);
      }
      return [
        {
          ...state,
          step: 'AWAITING_OTP',
          challenge: { challengeId: action.challengeId, expiresAt: action.expiresAt },
          otpCode: '',
        },
        noEffect,
      ];
    }

    case 'SUBMIT_OTP': {
      if (state.step !== 'AWAITING_OTP') {
        return unchanged(state);
      }
      return [
        { ...state, step: 'SUBMITTING', otpCode: action.otpCode, error: null },
        { type: 'CALL_CONFIRM' },
      ];
    }

    case 'TRANSFER_SUCCESS': {
      if (state.step !== 'SUBMITTING') {
        return unchanged(state);
      }
      return [
        {
          ...state,
          step: 'SUCCESS',
          result: { transactionId: action.transactionId, newBalance: action.newBalance },
          error: null,
        },
        noEffect,
      ];
    }

    case 'TRANSFER_FAILED': {
      if (state.step !== 'INITIATED' && state.step !== 'SUBMITTING') {
        return unchanged(state);
      }
      return [{ ...state, step: 'ERROR', error: action.error }, noEffect];
    }

    default: {
      action satisfies never;
      return unchanged(state);
    }
  }
}

export { initialTransferState };
