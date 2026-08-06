import React, { useEffect, useState } from 'react';
import { StatusBar, View } from 'react-native';
import AppNavigator from './src/navigation/AppNavigator';
import { buildBackupJson, importBackupFromContent } from './src/utils/BackupService';
import {
  flushSettingsSnapshot,
  initializeSettingsHistory,
  restoreSettingsAfterUpdateIfNeeded,
} from './src/utils/SettingsHistoryService';

const App: React.FC = () => {
  const [settingsReady, setSettingsReady] = useState(false);

  useEffect(() => {
    let mounted = true;

    const prepareSettings = async () => {
      try {
        initializeSettingsHistory(buildBackupJson, importBackupFromContent);
        await restoreSettingsAfterUpdateIfNeeded();
        await flushSettingsSnapshot('app startup');
      } catch (error) {
        console.error('[App] Settings recovery initialization failed:', error);
      } finally {
        if (mounted) setSettingsReady(true);
      }
    };

    void prepareSettings();
    return () => {
      mounted = false;
    };
  }, []);

  if (!settingsReady) {
    return (
      <View style={{ flex: 1, backgroundColor: '#333333' }}>
        <StatusBar hidden={true} />
      </View>
    );
  }

  return (
    <>
      <StatusBar hidden={true} />
      <AppNavigator />
    </>
  );
};

export default App;
