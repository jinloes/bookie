import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { modals } from '@mantine/modals';
import * as api from '../api/index.js';
import { renderWithProviders } from '../test/render.jsx';
import Backup from './Backup.jsx';

vi.mock('../api/index.js');
vi.mock('@tauri-apps/api/core');
vi.mock('@mantine/modals', () => ({ modals: { openConfirmModal: vi.fn() } }));

const h2Unconfirmed = { vendor: 'h2', frozen: false, keyConfirmed: false };

describe('Backup', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.listBackups).mockResolvedValue([]);
  });

  it('blocks the pre-migration backup until the key is shown and confirmed', async () => {
    let status = h2Unconfirmed;
    vi.mocked(api.getBackupStatus).mockImplementation(() => Promise.resolve(status));
    vi.mocked(api.revealBackupKey).mockResolvedValue({ key: 'bookie-secret-key' });
    vi.mocked(api.confirmBackupKey).mockImplementation(() => {
      status = { ...h2Unconfirmed, keyConfirmed: true };
      return Promise.resolve();
    });
    const { user } = renderWithProviders(<Backup />);

    const preMigration = await screen.findByRole('button', {
      name: 'Create pre-migration backup',
    });
    expect(preMigration).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Show backup key' }));
    expect(await screen.findByText('bookie-secret-key')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copy key' })).toBeInTheDocument();
    const confirm = screen.getByRole('button', { name: 'Confirm key saved' });
    expect(confirm).toBeDisabled();

    await user.click(screen.getByRole('checkbox', { name: /saved this key/ }));
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    expect(
      await screen.findByText('Backup key confirmed. Encrypted backups can now be uploaded.')
    ).toBeInTheDocument();
    expect(screen.queryByText('bookie-secret-key')).not.toBeInTheDocument();
    expect(
      await screen.findByRole('button', { name: 'Create pre-migration backup' })
    ).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Create pre-migration backup' }));
    expect(modals.openConfirmModal).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Create pre-migration backup' })
    );
  });

  it('blocks the first SQL Server backup until the key is confirmed', async () => {
    vi.mocked(api.getBackupStatus).mockResolvedValue({
      vendor: 'sqlserver',
      frozen: false,
      keyConfirmed: false,
    });
    renderWithProviders(<Backup />);

    expect(await screen.findByRole('button', { name: 'Prepare rollback' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Backup Now' })).toBeDisabled();
    expect(
      screen.queryByRole('button', { name: 'Create pre-migration backup' })
    ).not.toBeInTheDocument();
  });

  it('shows the freeze banner and lifts the freeze', async () => {
    vi.mocked(api.getBackupStatus).mockResolvedValue({
      vendor: 'sqlserver',
      frozen: true,
      keyConfirmed: true,
    });
    vi.mocked(api.liftWriteFreeze).mockResolvedValue(undefined);
    const { user } = renderWithProviders(<Backup />);

    expect(await screen.findByText('Writes are frozen')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Prepare rollback' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Lift freeze' }));

    expect(api.liftWriteFreeze).toHaveBeenCalled();
    expect(await screen.findByText('Writes are enabled again.')).toBeInTheDocument();
  });

  it('labels each backup with its category', async () => {
    vi.mocked(api.getBackupStatus).mockResolvedValue({
      vendor: 'sqlserver',
      frozen: false,
      keyConfirmed: true,
    });
    vi.mocked(api.listBackups).mockResolvedValue([
      {
        id: 'f1',
        name: 'pre-migration.bookiebak',
        category: 'PRE_MIGRATION',
        size: 10,
        createdDateTime: '2026-01-01T00:00:00Z',
      },
      {
        id: 'f2',
        name: 'rollback.sql.bookiebak',
        category: 'ROLLBACK',
        size: 10,
        createdDateTime: '2026-01-02T00:00:00Z',
      },
    ]);
    renderWithProviders(<Backup />);

    expect(await screen.findByText('Pre-migration')).toBeInTheDocument();
    expect(screen.getByText('Rollback')).toBeInTheDocument();
    expect(screen.queryByText('Save your backup encryption key')).not.toBeInTheDocument();
  });
});
