import { ReactNode } from 'react';
import { Header } from './Header';
import { ToastContainer } from '../ui/ToastContainer';

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  return (
    <div className="min-h-screen bg-white dark:bg-gray-900 transition-colors">
      <Header />
      <main className="relative pt-16">{children}</main>
      <ToastContainer />
    </div>
  );
}
