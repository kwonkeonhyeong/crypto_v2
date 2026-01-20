import { useEffect } from 'react';
import { useAtom } from 'jotai';
import { soundEnabledAtom, bgmEnabledAtom } from '@/stores/soundStore';
import { soundManager, initializeSounds } from '@/lib/soundManager';

/**
 * Hook for managing sound settings
 * Synchronizes Jotai state with soundManager
 */
export function useSound() {
  const [soundEnabled, setSoundEnabled] = useAtom(soundEnabledAtom);
  const [bgmEnabled, setBgmEnabled] = useAtom(bgmEnabledAtom);

  // Initialize sounds on mount
  useEffect(() => {
    initializeSounds();

    // Sync initial state
    soundManager.setEnabled(soundEnabled);
    soundManager.setBgmEnabled(bgmEnabled);

    return () => {
      soundManager.unloadAll();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Sync sound enabled state
  useEffect(() => {
    soundManager.setEnabled(soundEnabled);
  }, [soundEnabled]);

  // Sync BGM enabled state
  useEffect(() => {
    soundManager.setBgmEnabled(bgmEnabled);
  }, [bgmEnabled]);

  const toggleSound = () => {
    setSoundEnabled((prev) => !prev);
  };

  const toggleBgm = () => {
    setBgmEnabled((prev) => !prev);
  };

  return {
    soundEnabled,
    bgmEnabled,
    toggleSound,
    toggleBgm,
    setSoundEnabled,
    setBgmEnabled,
  };
}
