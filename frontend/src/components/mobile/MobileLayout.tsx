import { ReactNode } from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';
import { MobilePrayerButtons } from './MobilePrayerButtons';
import type { Side } from '@/types/prayer';

interface MobileLayoutProps {
  children: ReactNode;
  onPray: (side: Side) => void;
}

/**
 * Mobile layout wrapper
 * Renders fixed bottom prayer buttons on mobile devices
 * On desktop, just renders children without modification
 */
export function MobileLayout({ children, onPray }: MobileLayoutProps) {
  const isMobile = useIsMobile();

  if (!isMobile) {
    return <>{children}</>;
  }

  return (
    <div className="min-h-screen flex flex-col">
      {/* Main content area - 2/3 of screen */}
      <div className="flex-1 overflow-auto pb-[33vh]">{children}</div>

      {/* Fixed bottom prayer buttons - 1/3 of screen */}
      <MobilePrayerButtons onPray={onPray} />
    </div>
  );
}
