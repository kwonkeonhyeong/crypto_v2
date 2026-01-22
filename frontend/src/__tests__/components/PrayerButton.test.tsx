import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PrayerButton } from '../../components/prayer/PrayerButton';

// Mock framer-motion
vi.mock('framer-motion', () => ({
  motion: {
    button: ({ children, onClick, disabled, className }: React.ComponentProps<'button'>) => (
      <button
        onClick={onClick}
        disabled={disabled}
        className={className}
        data-testid="prayer-button"
      >
        {children}
      </button>
    ),
  },
  useAnimation: () => ({
    start: vi.fn(),
  }),
}));

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'prayer.up': 'UP',
        'prayer.down': 'DOWN',
      };
      return translations[key] || key;
    },
  }),
}));

describe('PrayerButton', () => {
  const mockOnPray = vi.fn();

  beforeEach(() => {
    mockOnPray.mockClear();
  });

  it('UP_버튼을_렌더링한다', () => {
    render(<PrayerButton side="up" count={100} onPray={mockOnPray} />);

    expect(screen.getByText('UP')).toBeInTheDocument();
    expect(screen.getByText('100')).toBeInTheDocument();
  });

  it('DOWN_버튼을_렌더링한다', () => {
    render(<PrayerButton side="down" count={50} onPray={mockOnPray} />);

    expect(screen.getByText('DOWN')).toBeInTheDocument();
    expect(screen.getByText('50')).toBeInTheDocument();
  });

  it('카운트를_천단위_구분자로_표시한다', () => {
    render(<PrayerButton side="up" count={1234567} onPray={mockOnPray} />);

    expect(screen.getByText('1,234,567')).toBeInTheDocument();
  });

  it('클릭_시_onPray_콜백을_호출한다', () => {
    render(<PrayerButton side="up" count={100} onPray={mockOnPray} />);

    const button = screen.getByTestId('prayer-button');
    fireEvent.click(button);

    expect(mockOnPray).toHaveBeenCalledTimes(1);
    expect(mockOnPray).toHaveBeenCalledWith('up', expect.any(Object));
  });

  it('DOWN_버튼_클릭_시_down_side로_콜백을_호출한다', () => {
    render(<PrayerButton side="down" count={100} onPray={mockOnPray} />);

    const button = screen.getByTestId('prayer-button');
    fireEvent.click(button);

    expect(mockOnPray).toHaveBeenCalledWith('down', expect.any(Object));
  });

  it('disabled_상태에서는_클릭해도_콜백을_호출하지_않는다', () => {
    render(<PrayerButton side="up" count={100} disabled onPray={mockOnPray} />);

    const button = screen.getByTestId('prayer-button');
    fireEvent.click(button);

    expect(mockOnPray).not.toHaveBeenCalled();
  });

  it('disabled_상태에서_버튼이_비활성화된다', () => {
    render(<PrayerButton side="up" count={100} disabled onPray={mockOnPray} />);

    const button = screen.getByTestId('prayer-button');
    expect(button).toBeDisabled();
  });

  it('UP_버튼에_로켓_이모지가_표시된다', () => {
    render(<PrayerButton side="up" count={100} onPray={mockOnPray} />);

    // Rocket emoji: U+1F680 (🚀)
    expect(screen.getByText('\u{1F680}')).toBeInTheDocument();
  });

  it('DOWN_버튼에_차트_이모지가_표시된다', () => {
    render(<PrayerButton side="down" count={100} onPray={mockOnPray} />);

    // Chart emoji: U+1F4C9 (📉)
    expect(screen.getByText('\u{1F4C9}')).toBeInTheDocument();
  });
});
