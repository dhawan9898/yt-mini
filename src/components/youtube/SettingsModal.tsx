import React from 'react';
import { usePlayer } from '../../context/PlayerContext';

export const SettingsModal: React.FC = () => {
  const {
    isSettingsOpen,
    setIsSettingsOpen,
    adBlockEnabled,
    setAdBlockEnabled,
    sponsorBlockEnabled,
    setSponsorBlockEnabled,
    adsShieldCount,
    sponsorsSkippedCount,
    audioQuality,
    setAudioQuality,
    videoQuality,
    setVideoQuality,
    sleepTimerSeconds,
    setSleepTimer,
    downloadedIds
  } = usePlayer();

  if (!isSettingsOpen) return null;

  return (
    <div className="settings-modal-overlay" onClick={() => setIsSettingsOpen(false)}>
      <div className="settings-modal-sheet" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="settings-header">
          <div className="settings-title-group">
            <span className="material-symbols-rounded" style={{ color: '#00cec9', fontSize: '24px' }}>
              security
            </span>
            <h3 className="settings-heading">Settings & Shield</h3>
          </div>
          <button
            className="icon-glass-btn sm"
            onClick={() => setIsSettingsOpen(false)}
            aria-label="Close Settings"
            type="button"
          >
            <span className="material-symbols-rounded">close</span>
          </button>
        </div>

        <div className="settings-content-scroll">
          {/* Ad-Free Shield Card */}
          <div className="settings-card-glass">
            <div className="settings-card-top">
              <div className="shield-icon-badge">
                <span className="material-symbols-rounded" style={{ color: '#00cec9' }}>
                  shield_with_heart
                </span>
              </div>
              <div className="settings-card-meta">
                <h4 className="settings-card-title">Nexus AdBlock Media Pipeline</h4>
                <p className="settings-card-sub">
                  Blocks preroll, midroll, banner ads, and tracking scripts natively.
                </p>
              </div>
              <label className="switch-toggle">
                <input
                  type="checkbox"
                  checked={adBlockEnabled}
                  onChange={(e) => setAdBlockEnabled(e.target.checked)}
                />
                <span className="switch-slider" />
              </label>
            </div>
            <div className="shield-stats-row">
              <div className="shield-stat-col">
                <span className="shield-stat-val">{adsShieldCount}</span>
                <span className="shield-stat-lbl">Ads Blocked</span>
              </div>
              <div className="shield-stat-col">
                <span className="shield-stat-val">100%</span>
                <span className="shield-stat-lbl">Zero Ad Break</span>
              </div>
              <div className="shield-stat-col">
                <span className="shield-stat-val">240 MB</span>
                <span className="shield-stat-lbl">Bandwidth Saved</span>
              </div>
            </div>
          </div>

          {/* SponsorBlock Card */}
          <div className="settings-card-glass">
            <div className="settings-card-top">
              <div className="shield-icon-badge">
                <span className="material-symbols-rounded" style={{ color: '#fdcb6e' }}>
                  fast_forward
                </span>
              </div>
              <div className="settings-card-meta">
                <h4 className="settings-card-title">SponsorBlock Auto-Skip</h4>
                <p className="settings-card-sub">
                  Automatically jumps over sponsor pitches, intros, and filler segments.
                </p>
              </div>
              <label className="switch-toggle">
                <input
                  type="checkbox"
                  checked={sponsorBlockEnabled}
                  onChange={(e) => setSponsorBlockEnabled(e.target.checked)}
                />
                <span className="switch-slider" />
              </label>
            </div>
            <div className="shield-stats-row">
              <div className="shield-stat-col">
                <span className="shield-stat-val">{sponsorsSkippedCount}</span>
                <span className="shield-stat-lbl">Segments Skipped</span>
              </div>
              <div className="shield-stat-col">
                <span className="shield-stat-val">14m 20s</span>
                <span className="shield-stat-lbl">Time Saved</span>
              </div>
            </div>
          </div>

          {/* Audio Quality Settings */}
          <div className="settings-section-title">STREAM QUALITY</div>
          <div className="settings-options-group">
            <div className="settings-option-item">
              <div className="option-label-col">
                <span className="option-name">Audio Quality</span>
                <span className="option-desc">Optimal bitrate for music playback</span>
              </div>
              <select
                className="glass-select"
                value={audioQuality}
                onChange={(e) => setAudioQuality(e.target.value as any)}
              >
                <option value="high">High (256 kbps AAC)</option>
                <option value="medium">Normal (128 kbps)</option>
                <option value="low">Data Saver (64 kbps)</option>
              </select>
            </div>

            <div className="settings-option-item">
              <div className="option-label-col">
                <span className="option-name">Video Resolution</span>
                <span className="option-desc">Target playback video stream</span>
              </div>
              <select
                className="glass-select"
                value={videoQuality}
                onChange={(e) => setVideoQuality(e.target.value as any)}
              >
                <option value="1080p">1080p FHD (High Bitrate)</option>
                <option value="720p">720p HD</option>
                <option value="480p">480p SD</option>
                <option value="auto">Auto (Adaptive)</option>
              </select>
            </div>
          </div>

          {/* Sleep / Bedtime Timer */}
          <div className="settings-section-title">BEDTIME & SLEEP REMINDER</div>
          <div className="settings-options-group">
            <div className="settings-option-item">
              <div className="option-label-col">
                <span className="option-name">Sleep Timer</span>
                <span className="option-desc">
                  {sleepTimerSeconds !== null
                    ? `Playback will pause in ${Math.ceil(sleepTimerSeconds / 60)} minutes`
                    : 'Pause media automatically when you fall asleep'}
                </span>
              </div>
              <div className="sleep-timer-chips">
                {[15, 30, 45, 60].map((mins) => (
                  <button
                    key={mins}
                    className={`time-chip ${sleepTimerSeconds && Math.ceil(sleepTimerSeconds / 60) === mins ? 'active' : ''}`}
                    onClick={() => setSleepTimer(mins)}
                    type="button"
                  >
                    {mins}m
                  </button>
                ))}
                {sleepTimerSeconds !== null && (
                  <button
                    className="time-chip cancel"
                    onClick={() => setSleepTimer(null)}
                    type="button"
                  >
                    Off
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Offline Downloads */}
          <div className="settings-section-title">OFFLINE STORAGE</div>
          <div className="settings-options-group">
            <div className="settings-option-item">
              <div className="option-label-col">
                <span className="option-name">Saved Streams</span>
                <span className="option-desc">{downloadedIds.size} files cached for offline playback</span>
              </div>
              <span className="download-badge">Offline Ready</span>
            </div>
          </div>

          {/* Version Info */}
          <div className="settings-footer-info">
            NexusStream Unified Client • Version 2.1.0<br />
            Official Data API v3 with Guarded Scraping Fallback Architecture
          </div>
        </div>
      </div>
    </div>
  );
};
