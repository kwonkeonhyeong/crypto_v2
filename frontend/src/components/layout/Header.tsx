import { useTranslation } from 'react-i18next';
import { useAtomValue } from 'jotai';
import { connectionStatusAtom, websocketStateAtom } from '@/stores/websocketStore';
import { ThemeToggle } from './ThemeToggle';
import { LanguageToggle } from './LanguageToggle';
import { SoundControl } from '../sound/SoundControl';

export function Header() {
  const { t } = useTranslation();
  const status = useAtomValue(connectionStatusAtom);
  const { reconnectAttempt } = useAtomValue(websocketStateAtom);

  const statusColor = {
    connected: 'bg-green-500',
    connecting: 'bg-yellow-500',
    reconnecting: 'bg-yellow-500',
    disconnected: 'bg-red-500',
  }[status];

  const statusText =
    status === 'reconnecting'
      ? t('connection.reconnecting', { attempt: reconnectAttempt })
      : t(`connection.${status}`);

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm border-b border-gray-200 dark:border-gray-800">
      <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900 dark:text-white">
            {t('header.title')}
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {t('header.subtitle')}
          </p>
        </div>

        <div className="flex items-center gap-4">
          {/* Connection status indicator */}
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${statusColor}`} />
            <span className="text-xs text-gray-500 dark:text-gray-400 hidden sm:inline">
              {statusText}
            </span>
          </div>

          {/* Sound control */}
          <SoundControl />

          <LanguageToggle />
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
