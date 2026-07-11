import React, { useEffect, useState } from 'react';
import {
  Anchor,
  Badge,
  Button,
  Card,
  Center,
  Checkbox,
  Divider,
  Group,
  Loader,
  MultiSelect,
  Select,
  Stack,
  Switch,
  TagsInput,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getOutlookAvailableFolders,
  getOutlookFolderSettings,
  updateOutlookFolderSettings,
  getOutlookMoveSettings,
  updateOutlookMoveSettings,
  getReceiptSettings,
  updateReceiptSettings,
} from '../api/index.js';
import { queryKeys } from '../queryKeys.js';
import { getErrorMessage } from '../utils/errors.js';
import { useOutlookStatus } from '../hooks/useOutlookStatus.js';

function OutlookSection() {
  const queryClient = useQueryClient();
  const [availableFolders, setAvailableFolders] = useState([]);
  const [folderSettings, setFolderSettings] = useState([]);
  const [moveEnabled, setMoveEnabled] = useState(false);
  const [moveDestinationFolderId, setMoveDestinationFolderId] = useState(null);
  const [saving, setSaving] = useState(false);

  const statusQuery = useOutlookStatus();
  const connected = statusQuery.connected;

  const availableQuery = useQuery({
    queryKey: queryKeys.outlookAvailableFolders,
    queryFn: getOutlookAvailableFolders,
    enabled: connected,
  });
  const foldersQuery = useQuery({
    queryKey: queryKeys.outlookFolderSettings,
    queryFn: getOutlookFolderSettings,
    enabled: connected,
  });
  const moveQuery = useQuery({
    queryKey: queryKeys.outlookMoveSettings,
    queryFn: getOutlookMoveSettings,
    enabled: connected,
  });

  const loading =
    statusQuery.isChecking ||
    (connected && (availableQuery.isLoading || foldersQuery.isLoading || moveQuery.isLoading));

  useEffect(() => {
    const loadError =
      statusQuery.error || availableQuery.error || foldersQuery.error || moveQuery.error;
    if (!loadError) {
      return;
    }
    notifications.show({
      title: 'Failed to load Outlook settings',
      message: getErrorMessage(loadError, 'Could not load Outlook settings.'),
      color: 'red',
    });
  }, [statusQuery.error, availableQuery.error, foldersQuery.error, moveQuery.error]);

  useEffect(() => {
    if (availableQuery.data) {
      setAvailableFolders(availableQuery.data.map((f) => ({ value: f.id, label: f.displayPath })));
    }
  }, [availableQuery.data]);

  useEffect(() => {
    if (foldersQuery.data) {
      setFolderSettings(foldersQuery.data);
    }
  }, [foldersQuery.data]);

  useEffect(() => {
    if (moveQuery.data) {
      setMoveEnabled(moveQuery.data.enabled);
      setMoveDestinationFolderId(moveQuery.data.folderId || null);
    }
  }, [moveQuery.data]);

  const selectedFolderIds = folderSettings.map((fs) => fs.folderId);

  const handleFolderSelectionChange = (newIds) => {
    const existingMap = Object.fromEntries(folderSettings.map((fs) => [fs.folderId, fs]));
    setFolderSettings(
      newIds.map((id) => existingMap[id] ?? { folderId: id, expandSubfolders: false })
    );
  };

  const toggleExpandSubfolders = (folderId) => {
    setFolderSettings((prev) =>
      prev.map((fs) =>
        fs.folderId === folderId ? { ...fs, expandSubfolders: !fs.expandSubfolders } : fs
      )
    );
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await Promise.all([
        updateOutlookFolderSettings(folderSettings),
        updateOutlookMoveSettings(moveEnabled, moveDestinationFolderId),
      ]);
      queryClient.invalidateQueries({ queryKey: queryKeys.outlookFolderSettings });
      queryClient.invalidateQueries({ queryKey: queryKeys.outlookMoveSettings });
      queryClient.invalidateQueries({ queryKey: queryKeys.outlookRentalEmails });
      notifications.show({ title: 'Outlook settings saved', color: 'green' });
    } catch (err) {
      notifications.show({
        title: 'Failed to save settings',
        message: getErrorMessage(err, 'Could not save Outlook settings.'),
        color: 'red',
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card withBorder p="lg">
      <Group justify="space-between" mb="md">
        <Text fw={600}>Email Connection</Text>
        {statusQuery.status && (
          <Badge
            color={
              statusQuery.status === 'connected'
                ? 'green'
                : statusQuery.status === 'checking'
                  ? 'blue'
                  : 'gray'
            }
            variant="light"
          >
            {statusQuery.status === 'connected'
              ? 'Connected'
              : statusQuery.status === 'checking'
                ? 'Checking...'
                : 'Not connected'}
          </Badge>
        )}
      </Group>

      {loading ? (
        <Center py="xl">
          <Loader size="sm" />
        </Center>
      ) : statusQuery.isDisconnected ? (
        <Stack gap="sm">
          <Text size="sm" c="dimmed">
            Connect Outlook so rental emails can be imported into Inbox and moved after saving.
          </Text>
          <Anchor href="/api/outlook/connect" underline="never">
            <Button size="sm">Connect Outlook</Button>
          </Anchor>
        </Stack>
      ) : (
        <Stack gap="md">
          <div>
            <Text size="sm" fw={500} mb={4}>
              Watched folders
            </Text>
            <Text size="xs" c="dimmed" mb="xs">
              Select which Outlook folders to include when fetching rental emails. Leave empty to
              use the defaults (Inbox, Rent Expenses, Taxes).
            </Text>
            <MultiSelect
              data={availableFolders}
              value={selectedFolderIds}
              onChange={handleFolderSelectionChange}
              placeholder="Select folders…"
              searchable
              clearable
            />
          </div>

          {folderSettings.length > 0 && (
            <Stack gap="xs">
              {folderSettings.map((fs) => {
                const folder = availableFolders.find((f) => f.value === fs.folderId);
                return (
                  <Group key={fs.folderId} justify="space-between">
                    <Text size="sm">{folder?.label ?? fs.folderId}</Text>
                    <Checkbox
                      label="Include subfolders"
                      size="xs"
                      checked={fs.expandSubfolders}
                      onChange={() => toggleExpandSubfolders(fs.folderId)}
                    />
                  </Group>
                );
              })}
            </Stack>
          )}

          <Divider />

          <Stack gap="xs">
            <Text size="sm" fw={500}>
              Auto-move
            </Text>
            <Switch
              label="Move email to a folder after saving as an expense or income"
              checked={moveEnabled}
              onChange={(e) => {
                setMoveEnabled(e.currentTarget.checked);
                if (!e.currentTarget.checked) setMoveDestinationFolderId(null);
              }}
            />
            {moveEnabled && (
              <Select
                label="Destination folder"
                placeholder="Select a folder…"
                data={availableFolders}
                value={moveDestinationFolderId}
                onChange={setMoveDestinationFolderId}
                searchable
              />
            )}
          </Stack>

          <Group justify="flex-end">
            <Button
              loading={saving}
              disabled={moveEnabled && !moveDestinationFolderId}
              onClick={handleSave}
            >
              Save Email Settings
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  );
}

function ReceiptsSection() {
  const queryClient = useQueryClient();
  const { data: receiptSettings, isLoading } = useQuery({
    queryKey: queryKeys.receiptSettings,
    queryFn: getReceiptSettings,
  });
  const [folderBase, setFolderBase] = useState('');
  const [importFolders, setImportFolders] = useState([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (receiptSettings?.folderBase) setFolderBase(receiptSettings.folderBase);
    if (receiptSettings?.importFolders) setImportFolders(receiptSettings.importFolders);
  }, [receiptSettings]);

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateReceiptSettings(folderBase, importFolders);
      queryClient.invalidateQueries({ queryKey: queryKeys.receiptSettings });
      queryClient.invalidateQueries({ queryKey: queryKeys.receipts });
      notifications.show({ title: 'Receipt settings saved', color: 'green' });
    } catch (err) {
      notifications.show({
        title: 'Failed to save settings',
        message: getErrorMessage(err, 'Could not save receipt settings.'),
        color: 'red',
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card withBorder p="lg">
      <Text fw={600} mb="md">
        Receipt Storage
      </Text>
      {isLoading ? (
        <Center py="xl">
          <Loader size="sm" />
        </Center>
      ) : (
        <Stack gap="md">
          <div>
            <TextInput
              label="OneDrive folder path"
              description="Root OneDrive folder where receipts are stored. Files copied directly into this folder, or into its pending/ subfolder, show up in the Receipts tab ready to parse; saved entries move into a year subfolder."
              placeholder="e.g. Receipts/Rentals"
              value={folderBase}
              onChange={(e) => setFolderBase(e.target.value)}
            />
          </div>
          <div>
            <TagsInput
              label="Additional import folders"
              description="OneDrive folders to also scan for receipts already placed there by something other than Bookie — e.g. a phone scanning app. Files found here show up in the Receipts tab ready to parse, the same as uploads. Press Enter after each folder path."
              placeholder="e.g. Scans/Receipts"
              value={importFolders}
              onChange={setImportFolders}
            />
          </div>
          <Group justify="flex-end">
            <Button loading={saving} disabled={!folderBase.trim()} onClick={handleSave}>
              Save Receipt Storage
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  );
}

// Autostart toggle — Tauri only, silently skipped in browser.
let autostartEnable = null;
let autostartDisable = null;
let autostartIsEnabled = null;
try {
  const mod = await import('@tauri-apps/plugin-autostart');
  autostartEnable = mod.enable;
  autostartDisable = mod.disable;
  autostartIsEnabled = mod.isEnabled;
} catch {
  // Running in browser — autostart not available.
}

// Update check — Tauri only, silently skipped in browser.
let updaterCheck = null;
let processRelaunch = null;
try {
  updaterCheck = (await import('@tauri-apps/plugin-updater')).check;
  processRelaunch = (await import('@tauri-apps/plugin-process')).relaunch;
} catch {
  // Running in browser — the updater plugin is not available.
}

function AppSection() {
  const [launchAtLogin, setLaunchAtLogin] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!autostartIsEnabled) {
      setLoading(false);
      return;
    }
    autostartIsEnabled().then((v) => {
      setLaunchAtLogin(v);
      setLoading(false);
    });
  }, []);

  if (!autostartIsEnabled) return null;

  const handleToggle = async (checked) => {
    setLaunchAtLogin(checked);
    try {
      if (checked) {
        await autostartEnable();
      } else {
        await autostartDisable();
      }
    } catch {
      setLaunchAtLogin(!checked);
      notifications.show({ title: 'Could not update launch setting', color: 'red' });
    }
  };

  return (
    <Card withBorder>
      <Text fw={600} mb="md">
        Application
      </Text>
      {loading ? (
        <Loader size="xs" />
      ) : (
        <Group justify="space-between">
          <div>
            <Text size="sm">Launch at login</Text>
            <Text size="xs" c="dimmed">
              Start Bookie automatically when you log in.
            </Text>
          </div>
          <Switch checked={launchAtLogin} onChange={(e) => handleToggle(e.currentTarget.checked)} />
        </Group>
      )}
    </Card>
  );
}

function UpdatesSection() {
  const [status, setStatus] = useState('idle'); // idle | checking | up-to-date | available | installing | unavailable | error
  const [update, setUpdate] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  if (!updaterCheck) return null;

  const handleCheck = async () => {
    setStatus('checking');
    setErrorMessage(null);
    try {
      const result = await updaterCheck();
      if (result) {
        setUpdate(result);
        setStatus('available');
      } else {
        setStatus('up-to-date');
      }
    } catch (err) {
      // Most commonly hit when this build has no updater endpoint/signing key configured yet
      // (see ARCHITECTURE.md "Enabling Auto-Updates") rather than a real network failure.
      setErrorMessage(getErrorMessage(err, 'Update check is not available for this build.'));
      setStatus('unavailable');
    }
  };

  const handleInstall = async () => {
    if (!update) return;
    setStatus('installing');
    try {
      await update.downloadAndInstall();
      if (processRelaunch) {
        await processRelaunch();
      }
    } catch (err) {
      setErrorMessage(getErrorMessage(err, 'Could not install the update. Please try again.'));
      setStatus('error');
    }
  };

  return (
    <Card withBorder>
      <Text fw={600} mb="md">
        Updates
      </Text>
      <Stack gap="sm">
        <Group justify="space-between">
          <div>
            <Text size="sm">
              {update && (status === 'available' || status === 'installing' || status === 'error')
                ? `Version ${update.version} is available`
                : 'Check for the latest version of Bookie'}
            </Text>
            {status === 'up-to-date' && (
              <Text size="xs" c="dimmed">
                You're on the latest version.
              </Text>
            )}
            {status === 'unavailable' && (
              <Text size="xs" c="dimmed">
                {errorMessage}
              </Text>
            )}
            {status === 'error' && (
              <Text size="xs" c="red">
                {errorMessage}
              </Text>
            )}
          </div>
          {status === 'available' || status === 'installing' ? (
            <Button loading={status === 'installing'} onClick={handleInstall}>
              Install & Restart
            </Button>
          ) : (
            <Button variant="default" loading={status === 'checking'} onClick={handleCheck}>
              Check for Updates
            </Button>
          )}
        </Group>
      </Stack>
    </Card>
  );
}

export default function Settings() {
  return (
    <Stack gap="xl">
      <Title order={2}>Settings</Title>
      <AppSection />
      <UpdatesSection />
      <OutlookSection />
      <ReceiptsSection />
    </Stack>
  );
}
