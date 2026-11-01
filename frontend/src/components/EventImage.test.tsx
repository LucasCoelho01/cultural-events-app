import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EventImage } from './EventImage';

describe('EventImage', () => {
  it('renders a lazy image with the title as alt', () => {
    render(<EventImage src="https://img.example.com/a.jpg" title="Show X" />);
    const img = screen.getByRole('img', { name: 'Show X' });
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(img).toHaveAttribute('src', 'https://img.example.com/a.jpg');
    expect(screen.queryByTestId('image-placeholder')).not.toBeInTheDocument();
  });

  it.each([null, undefined, ''])('shows the placeholder for src=%s', (src) => {
    render(<EventImage src={src} title="Show X" />);
    expect(screen.getByTestId('image-placeholder')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Show X' })).toBeNull();
  });

  it('swaps to the placeholder when the image fails and resets on a new src', () => {
    const { rerender } = render(
      <EventImage src="https://img.example.com/broken.jpg" title="Show X" />,
    );
    fireEvent.error(screen.getByRole('img', { name: 'Show X' }));
    expect(screen.getByTestId('image-placeholder')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Show X' })).toBeNull();

    rerender(
      <EventImage src="https://img.example.com/ok.jpg" title="Show X" />,
    );
    expect(screen.getByRole('img', { name: 'Show X' })).toBeInTheDocument();
  });
});
