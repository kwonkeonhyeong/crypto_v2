export type LiquidationSide = 'LONG' | 'SHORT';

export interface Liquidation {
  id: string;
  symbol: string;
  side: LiquidationSide;
  quantity: number;
  price: number;
  usdValue: number;
  isLarge: boolean;
  timestamp: number;
}
