import { useState, useEffect } from 'react';

const MOBILE_BREAKPOINT = 768;

/**
 * Hook to detect if the current device is mobile
 * Based on screen width and touch capability
 */
export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const checkMobile = () => {
      // Check screen width
      const isMobileScreen = window.innerWidth < MOBILE_BREAKPOINT;
      // Check touch capability
      const isTouchDevice =
        'ontouchstart' in window || navigator.maxTouchPoints > 0;

      // Consider mobile if screen is small OR it's a touch device with small screen
      setIsMobile(isMobileScreen && isTouchDevice);
    };

    // Initial check
    checkMobile();

    // Listen for resize events
    window.addEventListener('resize', checkMobile);

    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  return isMobile;
}
