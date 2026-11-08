import React from 'react';
import { usePlayer } from '../context/PlayerContext';

export const Header: React.FC = () => {
  const {
    appMode,
    setAppMode,
    isSearchOpen,
    setIsSearchOpen,
    setIsSettingsOpen,
    adBlockEnabled,
    sponsorBlockEnabled,
    adsShieldCount
  } = usePlayer();

  return (
    <header className="master-header">
      {/* App Mode Switcher Capsule */}
      <div className="app-mode-capsule">
        <div
          className="mode-indicator"
          style={{
            transform: appMode === 'YTMUSIC' ? 'translateX(0%)' : 'translateX(100%)'
          }}
        />
        <button
          className={`mode-btn ${appMode === 'YTMUSIC' ? 'active' : ''}`}
          onClick={() => setAppMode('YTMUSIC')}
          type="button"
        >
          <span className="material-symbols-rounded">graphic_eq</span>
          <span>YT Music</span>
        </button>
        <button
          className={`mode-btn ${appMode === 'YOUTUBE' ? 'active' : ''}`}
          onClick={() => setAppMode('YOUTUBE')}
          type="button"
        >
          <span className="material-symbols-rounded">play_circle</span>
          <span>YouTube</span>
        </button>
      </div>

      {/* Header Actions: Search, Ad Shield Badge, Account */}
      <div className="header-actions">
        {/* Ad Shield Indicator */}
        <button
          className={`icon-glass-btn sm ${adBlockEnabled || sponsorBlockEnabled ? 'text-emerald-400' : ''}`}
          onClick={() => setIsSettingsOpen(true)}
          title="Ad-Free Shield Active"
          type="button"
          aria-label="Shield and Settings"
        >
          <span className="material-symbols-rounded" style={{ color: '#00cec9', fontSize: '20px' }}>
            security
          </span>
          <span className="shield-dot" />
        </button>

        <button
          className={`icon-glass-btn sm ${isSearchOpen ? 'active' : ''}`}
          onClick={() => setIsSearchOpen(!isSearchOpen)}
          aria-label="Search"
          type="button"
        >
          <span className="material-symbols-rounded">
            {isSearchOpen ? 'close' : 'search'}
          </span>
        </button>

        <button
          className="avatar-glass-btn"
          aria-label="Account Settings"
          onClick={() => setIsSettingsOpen(true)}
          type="button"
        >
          <img
            src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80"
            alt="Alex Nexus Avatar"
          />
        </button>
      </div>
    </header>
  );
};
