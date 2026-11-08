import React from 'react';
import { usePlayer } from './context/PlayerContext';
import { DynamicMeshCanvas } from './components/DynamicMeshCanvas';
import { Header } from './components/Header';
import { SearchOverlay } from './components/SearchOverlay';
import { YTMusicScreen } from './components/ytmusic/YTMusicScreen';
import { YouTubeScreen } from './components/youtube/YouTubeScreen';
import { MiniPlayer } from './components/MiniPlayer';
import { MasterPlayerSheet } from './components/MasterPlayerSheet';
import { SettingsModal } from './components/youtube/SettingsModal';

export const App: React.FC = () => {
  const { appMode } = usePlayer();

  return (
    <>
      {/* Background Dynamic Fluid Mesh Canvas */}
      <DynamicMeshCanvas />

      {/* Main Viewport Shell */}
      <div className="app-viewport">
        {/* Top Header & App Mode Selector */}
        <Header />

        {/* Search Overlay */}
        <SearchOverlay />

        {/* Current Active Mode Screen */}
        <main className="main-content-area">
          {appMode === 'YTMUSIC' ? <YTMusicScreen /> : <YouTubeScreen />}
        </main>

        {/* Bottom Floating Mini Player */}
        <MiniPlayer />

        {/* Fullscreen Master Player Sheet */}
        <MasterPlayerSheet />

        {/* Shield & Settings Modal */}
        <SettingsModal />
      </div>
    </>
  );
};
