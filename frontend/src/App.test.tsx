import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';

describe('App', () => {
  it('accepts a selected file', () => {
    render(<App />);
    const file = new File(['ghost'], 'report.pdf', { type: 'application/pdf' });

    fireEvent.change(screen.getByLabelText('Choose file'), { target: { files: [file] } });

    expect(screen.getByText('report.pdf')).toBeTruthy();
  });
});
