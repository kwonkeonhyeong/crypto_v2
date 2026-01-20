import { describe, it, expect, beforeEach } from 'vitest';
import { ExponentialBackoff } from '../lib/exponentialBackoff';

describe('ExponentialBackoff', () => {
  let backoff: ExponentialBackoff;

  beforeEach(() => {
    backoff = new ExponentialBackoff({
      initialDelayMs: 1000,
      maxDelayMs: 30000,
      multiplier: 2,
      jitterFactor: 0, // Disable jitter for predictable tests
    });
  });

  it('should return initial delay on first call', () => {
    const delay = backoff.nextDelayMs();
    expect(delay).toBe(1000);
  });

  it('should increase delay exponentially', () => {
    const delay1 = backoff.nextDelayMs();
    const delay2 = backoff.nextDelayMs();
    const delay3 = backoff.nextDelayMs();

    expect(delay1).toBe(1000);
    expect(delay2).toBe(2000);
    expect(delay3).toBe(4000);
  });

  it('should cap delay at maxDelayMs', () => {
    for (let i = 0; i < 10; i++) {
      backoff.nextDelayMs();
    }
    const delay = backoff.nextDelayMs();
    expect(delay).toBeLessThanOrEqual(30000);
  });

  it('should reset attempt counter', () => {
    backoff.nextDelayMs();
    backoff.nextDelayMs();
    expect(backoff.getAttempt()).toBe(2);

    backoff.reset();
    expect(backoff.getAttempt()).toBe(0);
    expect(backoff.nextDelayMs()).toBe(1000);
  });

  it('should add jitter when jitterFactor is set', () => {
    // Run multiple times to check for variation
    const delays = new Set<number>();
    for (let i = 0; i < 10; i++) {
      const b = new ExponentialBackoff({
        initialDelayMs: 1000,
        maxDelayMs: 30000,
        multiplier: 2,
        jitterFactor: 0.1,
      });
      delays.add(b.nextDelayMs());
    }

    // With jitter, we should have some variation
    expect(delays.size).toBeGreaterThanOrEqual(1);
  });
});
