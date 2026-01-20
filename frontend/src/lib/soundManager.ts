import { Howl, Howler } from 'howler';

interface SoundConfig {
  src: string;
  volume?: number;
  loop?: boolean;
}

/**
 * Sound Manager - Howler.js wrapper for managing game sounds
 *
 * Handles:
 * - Sound effects (click, liquidation)
 * - BGM playback
 * - Volume control
 * - Enable/disable functionality
 */
class SoundManager {
  private sounds: Map<string, Howl> = new Map();
  private enabled = true;
  private bgmEnabled = false;
  private initialized = false;

  constructor() {
    // Set global volume
    Howler.volume(0.7);
  }

  /**
   * Register a sound
   */
  register(name: string, config: SoundConfig): void {
    // Avoid duplicate registration
    if (this.sounds.has(name)) {
      return;
    }

    const sound = new Howl({
      src: [config.src],
      volume: config.volume ?? 1.0,
      loop: config.loop ?? false,
      html5: config.loop, // Use HTML5 audio for BGM (memory efficiency)
      preload: true,
    });

    this.sounds.set(name, sound);
  }

  /**
   * Play a sound effect
   */
  play(name: string): number | undefined {
    if (!this.enabled) return undefined;

    const sound = this.sounds.get(name);
    if (!sound) {
      console.warn(`Sound not found: ${name}`);
      return undefined;
    }

    return sound.play();
  }

  /**
   * Play BGM
   */
  playBgm(): void {
    if (!this.bgmEnabled) return;

    const bgm = this.sounds.get('bgm');
    if (bgm && !bgm.playing()) {
      bgm.play();
    }
  }

  /**
   * Stop BGM
   */
  stopBgm(): void {
    const bgm = this.sounds.get('bgm');
    if (bgm) {
      bgm.stop();
    }
  }

  /**
   * Stop a specific sound
   */
  stop(name: string): void {
    const sound = this.sounds.get(name);
    if (sound) {
      sound.stop();
    }
  }

  /**
   * Set volume for a specific sound
   */
  setVolume(name: string, volume: number): void {
    const sound = this.sounds.get(name);
    if (sound) {
      sound.volume(volume);
    }
  }

  /**
   * Set global volume
   */
  setGlobalVolume(volume: number): void {
    Howler.volume(volume);
  }

  /**
   * Enable/disable sound effects
   */
  setEnabled(enabled: boolean): void {
    this.enabled = enabled;
    if (!enabled) {
      this.stopAllEffects();
    }
  }

  /**
   * Enable/disable BGM
   */
  setBgmEnabled(enabled: boolean): void {
    this.bgmEnabled = enabled;
    if (enabled) {
      this.playBgm();
    } else {
      this.stopBgm();
    }
  }

  /**
   * Stop all sound effects (not BGM)
   */
  stopAllEffects(): void {
    this.sounds.forEach((sound, name) => {
      if (name !== 'bgm') {
        sound.stop();
      }
    });
  }

  /**
   * Stop all sounds including BGM
   */
  stopAll(): void {
    this.sounds.forEach((sound) => sound.stop());
  }

  /**
   * Unload a specific sound
   */
  unload(name: string): void {
    const sound = this.sounds.get(name);
    if (sound) {
      sound.unload();
      this.sounds.delete(name);
    }
  }

  /**
   * Unload all sounds
   */
  unloadAll(): void {
    this.sounds.forEach((sound) => sound.unload());
    this.sounds.clear();
    this.initialized = false;
  }

  isEnabled(): boolean {
    return this.enabled;
  }

  isBgmEnabled(): boolean {
    return this.bgmEnabled;
  }

  isInitialized(): boolean {
    return this.initialized;
  }

  setInitialized(value: boolean): void {
    this.initialized = value;
  }
}

// Singleton instance
export const soundManager = new SoundManager();

/**
 * Initialize all sounds
 * Called once when the app starts
 */
export function initializeSounds(): void {
  if (soundManager.isInitialized()) {
    return;
  }

  soundManager.register('bgm', {
    src: '/sounds/bgm.mp3',
    volume: 0.3,
    loop: true,
  });

  soundManager.register('click', {
    src: '/sounds/click.mp3',
    volume: 0.5,
  });

  soundManager.register('liquidation', {
    src: '/sounds/liquidation.mp3',
    volume: 0.4,
  });

  soundManager.register('large-liquidation', {
    src: '/sounds/large-liquidation.mp3',
    volume: 0.8,
  });

  soundManager.setInitialized(true);
}
