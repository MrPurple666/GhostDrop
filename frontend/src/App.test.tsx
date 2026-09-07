import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi, afterEach } from 'vitest';
import { App } from './App';
import { DropPage } from './DropPage';

afterEach(() => vi.unstubAllGlobals());

describe('App', () => {
  it('accepts a selected file', () => {
    render(<App />);
    const file = new File(['ghost'], 'report.pdf', { type: 'application/pdf' });
    fireEvent.change(screen.getByLabelText('Choose file'), { target: { files: [file] } });
    expect(screen.getByText(/report\.pdf/)).toBeTruthy();
  });
});

describe('DropPage', () => {
  const copy = { download: 'Download file', unavailable: 'This GhostDrop is no longer available.', downloading: 'Preparing download…', size: 'Size', passwordProtected: '', wrongPassword: '', remainingDownloads: '' } as never;

  it('shows unavailable when lookup fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404 })));
    render(<DropPage id="missing" copy={copy} />);
    expect(await screen.findByText(/no longer available/)).toBeTruthy();
  });
});
