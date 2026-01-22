import { Provider } from 'jotai';
import { Layout } from '@/components/layout/Layout';
import { PrayerPage } from '@/pages/PrayerPage';
import { useTheme } from '@/hooks/useTheme';

function AppContent() {
  // Initialize theme system
  useTheme();

  return (
    <Layout>
      <PrayerPage />
    </Layout>
  );
}

function App() {
  return (
    <Provider>
      <AppContent />
    </Provider>
  );
}

export default App;
