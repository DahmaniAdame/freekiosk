import AsyncStorage from '@react-native-async-storage/async-storage';
import { NativeModules } from 'react-native';

type BackupBuildResult = {
  success: boolean;
  json?: string;
  error?: string;
};

type BackupBuilder = () => Promise<BackupBuildResult>;
type BackupImporter = (json: string, fileName?: string) => Promise<{
  success: boolean;
  error?: string;
}>;

const { SettingsHistoryModule } = NativeModules;

let backupBuilder: BackupBuilder | null = null;
let backupImporter: BackupImporter | null = null;
let snapshotTimer: ReturnType<typeof setTimeout> | null = null;
let snapshotInFlight: Promise<void> | null = null;
const pendingReasons = new Set<string>();

export function initializeSettingsHistory(
  builder: BackupBuilder,
  importer: BackupImporter,
): void {
  backupBuilder = builder;
  backupImporter = importer;
}

/**
 * Coalesce related writes (for example, a settings form save) into one history
 * entry while still recording every completed logical change.
 */
export function scheduleSettingsSnapshot(reason = 'settings change'): void {
  pendingReasons.add(reason);
  if (!backupBuilder || !SettingsHistoryModule) return;

  if (snapshotTimer) clearTimeout(snapshotTimer);
  snapshotTimer = setTimeout(() => {
    snapshotTimer = null;
    void flushSettingsSnapshot();
  }, 800);
}

export async function flushSettingsSnapshot(reason?: string): Promise<void> {
  if (reason) pendingReasons.add(reason);
  if (!backupBuilder || !SettingsHistoryModule) return;

  if (snapshotTimer) {
    clearTimeout(snapshotTimer);
    snapshotTimer = null;
  }

  if (snapshotInFlight) await snapshotInFlight;

  const reasons = Array.from(pendingReasons);
  pendingReasons.clear();
  const snapshotReason = reasons.length > 0 ? reasons.slice(0, 4).join(', ') : 'settings change';

  snapshotInFlight = (async () => {
    const built = await backupBuilder!();
    if (!built.success || !built.json) {
      throw new Error(built.error || 'Could not build settings snapshot');
    }
    await SettingsHistoryModule.writeSnapshot(built.json, snapshotReason);
  })();

  try {
    await snapshotInFlight;
  } catch (error) {
    console.error('[SettingsHistory] Snapshot failed:', error);
  } finally {
    snapshotInFlight = null;
    if (pendingReasons.size > 0) scheduleSettingsSnapshot();
  }
}

/**
 * A normal Android update keeps app data. If an update marker exists, compare
 * the staged snapshot with AsyncStorage and import it only when one or more
 * previously saved settings disappeared.
 */
export async function restoreSettingsAfterUpdateIfNeeded(): Promise<boolean> {
  if (!SettingsHistoryModule || !backupImporter) return false;

  let json: string | null;
  try {
    json = await SettingsHistoryModule.getPendingUpdateSnapshot();
  } catch (error) {
    console.error('[SettingsHistory] Could not check update recovery state:', error);
    return false;
  }
  if (!json) return false;

  try {
    const parsed = JSON.parse(json) as { settings?: Record<string, string> };
    const settings = parsed.settings || {};
    const secureKeys = new Set([
      '@kiosk_rest_api_key',
      '@kiosk_mqtt_password',
      '@kiosk_http_basic_auth_password',
    ]);
    const regularKeys = Object.keys(settings).filter(key => !secureKeys.has(key));
    const current = regularKeys.length > 0 ? await AsyncStorage.multiGet(regularKeys) : [];
    let hasMissingSettings = current.some(([, value]) => value === null);

    // buildBackupJson also reads credentials from secure storage, allowing the
    // recovery check to detect a missing API/MQTT/basic-auth credential.
    if (!hasMissingSettings && backupBuilder) {
      const currentBackup = await backupBuilder();
      if (currentBackup.success && currentBackup.json) {
        const currentSettings = (JSON.parse(currentBackup.json) as {
          settings?: Record<string, string>;
        }).settings || {};
        hasMissingSettings = Object.keys(settings).some(key => currentSettings[key] === undefined);
      }
    }

    if (hasMissingSettings) {
      const restored = await backupImporter(json, 'automatic-update-recovery.json');
      if (!restored.success) {
        throw new Error(restored.error || 'Settings recovery failed');
      }
      console.warn('[SettingsHistory] Missing settings restored after app update');
    }

    await SettingsHistoryModule.completePendingRestore();
    return hasMissingSettings;
  } catch (error) {
    // Keep the staged snapshot and marker so recovery is retried next launch.
    console.error('[SettingsHistory] Update recovery failed:', error);
    return false;
  }
}

export default {
  initializeSettingsHistory,
  scheduleSettingsSnapshot,
  flushSettingsSnapshot,
  restoreSettingsAfterUpdateIfNeeded,
};
