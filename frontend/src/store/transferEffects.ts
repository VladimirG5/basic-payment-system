export type TransferEffect =
  | { type: 'NONE' }
  | { type: 'CALL_INITIATE' }
  | { type: 'CALL_CONFIRM' };

export const noEffect: TransferEffect = { type: 'NONE' };
