interface BgmToggleProps {
  enabled: boolean;
  onToggle: () => void;
}

/**
 * BGM toggle button
 * Shows music note icon with on/off state
 */
export function BgmToggle({ enabled, onToggle }: BgmToggleProps) {
  return (
    <button
      onClick={onToggle}
      className={`
        p-2 rounded-lg transition-colors
        ${
          enabled
            ? 'bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400'
            : 'bg-gray-100 dark:bg-gray-800 text-gray-400'
        }
      `}
      aria-label={enabled ? 'Stop BGM' : 'Play BGM'}
      title={enabled ? 'BGM ON' : 'BGM OFF'}
    >
      {enabled ? (
        <MusicOnIcon className="w-5 h-5" />
      ) : (
        <MusicOffIcon className="w-5 h-5" />
      )}
    </button>
  );
}

function MusicOnIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3"
      />
    </svg>
  );
}

function MusicOffIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2z"
      />
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M3 3l18 18"
      />
    </svg>
  );
}
