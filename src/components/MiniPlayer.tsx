import React from 'react';
import { usePlayer } from '../context/PlayerContext';

export const MiniPlayer: React.FC = () => {
  const {
    currentTrack,
    isPlaying,
    togglePlay,
    skipNext,
    currentTime,
    duration,
    setIsMasterOpen,
    appMode,
    activeYtTab
  } = usePlayer();

  if (!currentTrack) return null;

  const progressPct = duration > 0 ? (currentTime / duration) * 100 : 0;
  const isVideoInShorts = appMode === 'YOUTUBE' && activeYtTab === 'shorts';

  // In full-screen shorts view we hide miniplayer so it doesn't overlap shorts actions
  if (isVideoInShorts) return null;

  const playLeft = "M 22 15 L 48 30 L 48 70 L 22 85 Z";
  const playRight = "M 48 30 L 82 50 L 82 50 L 48 70 Z";
  const pauseLeft = "M 20 16 L 42 16 L 42 84 L 20 84 Z";
  const pauseRight = "M 58 16 L 80 16 L 80 84 L 58 84 Z";

  return (
    <div
      className={`mini-player ${appMode === 'YOUTUBE' ? 'with-bottom-nav' : ''}`}
      onClick={() => setIsMasterOpen(true)}
      role="button"
      tabIndex={0}
      aria-label={`Now playing: ${currentTrack.title} by ${currentTrack.artistOrChannel}. Tap to open player.`}
    >
      <div className="mini-player-content">
        <div className="mini-thumb-wrap">
          <img
            src={currentTrack.artworkUrl}
            alt={currentTrack.title}
            className="mini-thumb"
          />
          {isPlaying && (
            <div className="mini-equalizer">
              <span className="bar bar-1" />
              <span className="bar bar-2" />
              <span className="bar bar-3" />
            </div>
          )}
        </div>

        <div className="mini-meta">
          <span className="mini-title">{currentTrack.title}</span>
          <span className="mini-artist">{currentTrack.artistOrChannel}</span>
        </div>

        <div className="mini-controls" onClick={(e) => e.stopPropagation()}>
          <button
            className="mini-btn morph-play"
            onClick={togglePlay}
            aria-label={isPlaying ? 'Pause' : 'Play'}
            type="button"
          >
            <svg viewBox="0 0 100 100" className="morph-svg">
              <path
                d={isPlaying ? pauseLeft : playLeft}
                className="morph-path"
              />
              <path
                d={isPlaying ? pauseRight : playRight}
                className="morph-path"
              />
            </svg>
          </button>

          <button
            className="mini-btn"
            onClick={skipNext}
            aria-label="Skip next track"
            type="button"
          >
            <span className="material-symbols-rounded">skip_next</span>
          </button>
        </div>
      </div>

      {/* Mini Progress Bar */}
      <div className="mini-progress-track">
        <div
          className="mini-progress-fill"
          style={{ width: `${Math.min(100, Math.max(0, progressPct))}%` }}
        />
      </div>
    </div>
  );
};
