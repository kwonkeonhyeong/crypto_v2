import { useSound } from '@/hooks/useSound';
import { SoundToggle } from './SoundToggle';
import { BgmToggle } from './BgmToggle';

/**
 * Sound control panel
 * Contains both sound effects and BGM toggles
 */
export function SoundControl() {
  const { soundEnabled, bgmEnabled, toggleSound, toggleBgm } = useSound();

  return (
    <div className="flex items-center gap-2">
      <SoundToggle enabled={soundEnabled} onToggle={toggleSound} />
      <BgmToggle enabled={bgmEnabled} onToggle={toggleBgm} />
    </div>
  );
}
