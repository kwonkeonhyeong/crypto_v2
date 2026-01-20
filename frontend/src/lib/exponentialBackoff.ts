export interface BackoffConfig {
  initialDelayMs: number;
  maxDelayMs: number;
  multiplier: number;
  jitterFactor: number;
}

const defaultConfig: BackoffConfig = {
  initialDelayMs: 1000,
  maxDelayMs: 30000,
  multiplier: 2,
  jitterFactor: 0.1,
};

export class ExponentialBackoff {
  private config: BackoffConfig;
  private attempt = 0;

  constructor(config: Partial<BackoffConfig> = {}) {
    this.config = { ...defaultConfig, ...config };
  }

  nextDelayMs(): number {
    const { initialDelayMs, maxDelayMs, multiplier, jitterFactor } = this.config;

    let delay = initialDelayMs * Math.pow(multiplier, this.attempt);
    delay = Math.min(delay, maxDelayMs);

    // Add jitter (+-jitterFactor%)
    const jitter = delay * jitterFactor * (Math.random() * 2 - 1);
    delay = Math.max(0, delay + jitter);

    this.attempt++;
    return delay;
  }

  reset(): void {
    this.attempt = 0;
  }

  getAttempt(): number {
    return this.attempt;
  }
}
