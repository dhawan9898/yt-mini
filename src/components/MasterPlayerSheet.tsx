import React, { useRef, useEffect, useState, useCallback } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { LyricLine } from '../data/catalog';

function formatDuration(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

export const MasterPlayerSheet: React.FC = () => {
  const {
    isMasterOpen,
    setIsMasterOpen,
    currentTrack,
    queue,
    queueIndex,
    isPlaying,
    playbackMode,
    setPlaybackMode,
    currentTime,
    duration,
    buffered,
    togglePlay,
    skipNext,
    skipPrev,
    seekTo,
    playbackRate,
    setPlaybackRate,
    isShuffle,
    toggleShuffle,
    repeatMode,
    toggleRepeat,
    showLyrics,
    setShowLyrics,
    showQueueDrawer,
    setShowQueueDrawer,
    removeFromQueue,
    playTrack,
    likedIds,
    toggleLike,
    downloadedIds,
    toggleDownload,
    sponsorBlockEnabled,
    skippedNotice,
    videoRef,
    sleepTimerSeconds,
    setSleepTimer
  } = usePlayer();

  const vinylCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const lyricsContainerRef = useRef<HTMLDivElement | null>(null);
  const [isScrubbing, setIsScrubbing] = useState(false);
  const [scrubValue, setScrubValue] = useState(0);
  const [isSpeedMenuOpen, setIsSpeedMenuOpen] = useState(false);
  const [isSleepMenuOpen, setIsSleepMenuOpen] = useState(false);

  // Draw procedural vinyl record grooves
  useEffect(() => {
    const canvas = vinylCanvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const size = 320;
    canvas.width = size;
    canvas.height = size;
    const center = size / 2;
    const outerRadius = size / 2 - 4;

    ctx.clearRect(0, 0, size, size);

    const bodyGrad = ctx.createRadialGradient(center, center, 0, center, center, outerRadius);
    bodyGrad.addColorStop(0, '#1a1a22');
    bodyGrad.addColorStop(0.7, '#111116');
    bodyGrad.addColorStop(1, '#08080c');
    ctx.fillStyle = bodyGrad;
    ctx.beginPath();
    ctx.arc(center, center, outerRadius, 0, Math.PI * 2);
    ctx.fill();

    for (let i = 1; i <= 8; i++) {
      const r = outerRadius * (0.42 + i * 0.065);
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(center, center, r, 0, Math.PI * 2);
      ctx.stroke();
    }

    const sweep = ctx.createConicGradient(0, center, center);
    sweep.addColorStop(0, 'transparent');
    sweep.addColorStop(0.25, 'rgba(255, 255, 255, 0.09)');
    sweep.addColorStop(0.5, 'transparent');
    sweep.addColorStop(0.75, 'rgba(255, 255, 255, 0.09)');
    sweep.addColorStop(1, 'transparent');
    ctx.fillStyle = sweep;
    ctx.beginPath();
    ctx.arc(center, center, outerRadius, 0, Math.PI * 2);
    ctx.fill();
  }, []);

  // Auto-scroll lyrics to current line
  useEffect(() => {
    if (!showLyrics || !lyricsContainerRef.current) return;
    const activeEl = lyricsContainerRef.current.querySelector('.lyric-line.active');
    if (activeEl) {
      activeEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }, [currentTime, showLyrics]);

  const handleScrubStart = (e: React.MouseEvent<HTMLDivElement> | React.TouchEvent<HTMLDivElement>) => {
    setIsScrubbing(true);
    handleScrubMove(e);
  };

  const handleScrubMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement> | React.TouchEvent<HTMLDivElement> | MouseEvent | TouchEvent) => {
      const track = document.getElementById('master-slider-track');
      if (!track) return;
      const rect = track.getBoundingClientRect();
      const clientX = 'touches' in e && e.touches[0] ? e.touches[0].clientX : (e as MouseEvent).clientX;
      const frac = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
      setScrubValue(frac * duration);
    },
    [duration]
  );

  const handleScrubEnd = useCallback(() => {
    if (isScrubbing) {
      seekTo(scrubValue);
      setIsScrubbing(false);
    }
  }, [isScrubbing, scrubValue, seekTo]);

  useEffect(() => {
    if (isScrubbing) {
      const onMove = (e: MouseEvent | TouchEvent) => handleScrubMove(e);
      const onUp = () => handleScrubEnd();
      window.addEventListener('mousemove', onMove);
      window.addEventListener('touchmove', onMove);
      window.addEventListener('mouseup', onUp);
      window.addEventListener('touchend', onUp);
      return () => {
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('touchmove', onMove);
        window.removeEventListener('mouseup', onUp);
        window.removeEventListener('touchend', onUp);
      };
    }
  }, [isScrubbing, handleScrubMove, handleScrubEnd]);

  // Request Picture-in-Picture on video element
  const handleTogglePiP = async () => {
    if (!videoRef.current) return;
    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
      } else if (document.pictureInPictureEnabled) {
        await videoRef.current.requestPictureInPicture();
      }
    } catch {
      // Fallback
    }
  };

  const displayTime = isScrubbing ? scrubValue : currentTime;
  const progressPct = duration > 0 ? (displayTime / duration) * 100 : 0;
  const bufferPct = buffered * 100;

  const isLiked = likedIds.has(currentTrack.id);
  const isDownloaded = downloadedIds.has(currentTrack.id);

  const playLeft = "M 22 15 L 48 30 L 48 70 L 22 85 Z";
  const playRight = "M 48 30 L 82 50 L 82 50 L 48 70 Z";
  const pauseLeft = "M 20 16 L 42 16 L 42 84 L 20 84 Z";
  const pauseRight = "M 58 16 L 80 16 L 80 84 L 58 84 Z";

  return (
    <div className={`player-screen ${isMasterOpen ? 'active' : ''}`}>
      {/* Top Header */}
      <div className="player-header">
        <button
          className="collapse-btn"
          onClick={() => {
            setIsMasterOpen(false);
            setShowQueueDrawer(false);
          }}
          aria-label="Collapse Player"
          type="button"
        >
          <span className="material-symbols-rounded">keyboard_arrow_down</span>
        </button>

        {/* Song vs Video Toggle Capsule */}
        <div className="playback-mode-capsule">
          <div
            className="capsule-indicator"
            style={{
              transform: playbackMode === 'SONG' ? 'translateX(0%)' : 'translateX(100%)'
            }}
          />
          <button
            className={`capsule-btn ${playbackMode === 'SONG' ? 'active' : ''}`}
            onClick={() => setPlaybackMode('SONG')}
            type="button"
          >
            Song
          </button>
          <button
            className={`capsule-btn ${playbackMode === 'VIDEO' ? 'active' : ''}`}
            onClick={() => setPlaybackMode('VIDEO')}
            type="button"
          >
            Video
          </button>
        </div>

        {/* Actions: Lyrics, Queue */}
        <div className="player-top-actions">
          <button
            className={`icon-glass-btn sm ${showLyrics ? 'active' : ''}`}
            onClick={() => setShowLyrics(prev => !prev)}
            aria-label="Toggle Lyrics"
            title="Synchronized Lyrics"
            type="button"
          >
            <span className="material-symbols-rounded">lyrics</span>
          </button>
          <button
            className={`icon-glass-btn sm ${showQueueDrawer ? 'active' : ''}`}
            onClick={() => setShowQueueDrawer(prev => !prev)}
            aria-label="Toggle Queue"
            title="Playing Queue"
            type="button"
          >
            <span className="material-symbols-rounded">queue_music</span>
          </button>
        </div>
      </div>

      {/* SponsorBlock Auto-Skip Notice Toast */}
      {skippedNotice && (
        <div className="sponsor-skip-badge">
          <span className="material-symbols-rounded" style={{ fontSize: '18px' }}>shield</span>
          <span>{skippedNotice}</span>
        </div>
      )}

      {/* Center Media Stage */}
      <div className="media-stage">
        {/* 1. Lyrics Display */}
        {showLyrics && (
          <div className="lyrics-container active" ref={lyricsContainerRef}>
            <div className="lyrics-scroller">
              {currentTrack.lyrics && currentTrack.lyrics.length > 0 ? (
                currentTrack.lyrics.map((line: LyricLine, idx: number) => {
                  const currentMs = displayTime * 1000;
                  const isActive = currentMs >= line.startTimeMs && currentMs <= line.endTimeMs + 600;
                  const isPast = currentMs > line.endTimeMs + 600;

                  return (
                    <div
                      key={idx}
                      className={`lyric-line ${isActive ? 'active' : ''} ${isPast ? 'past' : ''}`}
                      onClick={() => seekTo(line.startTimeMs / 1000)}
                    >
                      {line.words && line.words.length > 0 ? (
                        line.words.map((w, wIdx) => {
                          const isWordActive = currentMs >= w.start && currentMs <= w.end;
                          const isWordPast = currentMs > w.end;
                          return (
                            <span
                              key={wIdx}
                              className={`karaoke-word ${isWordActive ? 'active' : ''} ${isWordPast ? 'completed' : ''}`}
                            >
                              {w.word}{' '}
                            </span>
                          );
                        })
                      ) : (
                        line.text
                      )}
                    </div>
                  );
                })
              ) : (
                <div style={{ textAlign: 'center', color: 'rgba(255,255,255,0.4)', padding: '60px 20px' }}>
                  <span className="material-symbols-rounded" style={{ fontSize: '36px', opacity: 0.5, marginBottom: '8px' }}>music_note</span>
                  <p>Instrumental or no synced lyrics found</p>
                </div>
              )}
            </div>
          </div>
        )}

        {/* 2. Song Mode: Vinyl Turntable Disc */}
        {!showLyrics && playbackMode === 'SONG' && (
          <div className="vinyl-container active">
            <div className={`vinyl-disc ${isPlaying ? 'spinning' : 'paused'}`}>
              <canvas ref={vinylCanvasRef} className="vinyl-canvas" />
              <div className="vinyl-center-label">
                <img
                  src={currentTrack.artworkUrl}
                  alt={currentTrack.title}
                  className="vinyl-artwork"
                />
                <div className="vinyl-spindle-hole" />
              </div>
            </div>
          </div>
        )}

        {/* 3. Video Mode: HTML5 Video Surface */}
        {!showLyrics && playbackMode === 'VIDEO' && (
          <div className="video-container active">
            <video
              ref={videoRef}
              src={currentTrack.videoUrl}
              playsInline
              className="media-video-player"
              onClick={togglePlay}
              onEnded={() => {
                if (repeatMode === 'ONE') {
                  seekTo(0);
                } else {
                  skipNext();
                }
              }}
            />
            {/* PiP & Overlay Controls */}
            <div className="video-overlay-bar">
              <button
                className="video-pip-btn"
                onClick={handleTogglePiP}
                title="Picture-in-Picture"
                type="button"
              >
                <span className="material-symbols-rounded">picture_in_picture_alt</span>
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Track Meta Details */}
      <div className="player-meta-row">
        <div className="player-title-col">
          <h2 className="player-title">{currentTrack.title}</h2>
          <p className="player-artist">{currentTrack.artistOrChannel}</p>
        </div>
        <button
          className={`icon-heart-btn ${isLiked ? 'active' : ''}`}
          onClick={() => toggleLike(currentTrack.id)}
          aria-label={isLiked ? 'Unlike' : 'Like'}
          type="button"
        >
          <span className="material-symbols-rounded">
            {isLiked ? 'favorite' : 'favorite_border'}
          </span>
        </button>
      </div>

      {/* Liquid Slider Bar */}
      <div className="liquid-seek-container">
        <div
          id="master-slider-track"
          className="slider-track"
          onMouseDown={handleScrubStart}
          onTouchStart={handleScrubStart}
        >
          <div className="slider-buffer" style={{ width: `${Math.min(100, bufferPct)}%` }} />
          <div className="slider-progress" style={{ width: `${Math.min(100, progressPct)}%` }} />
          <div className="slider-thumb" style={{ left: `${Math.min(100, progressPct)}%` }} />
        </div>
        <div className="slider-time-row">
          <span className="time-text">{formatDuration(displayTime)}</span>
          <span className="time-text">{formatDuration(duration)}</span>
        </div>
      </div>

      {/* Primary Media Control Bar */}
      <div className="master-controls-row">
        <button
          className={`ctrl-btn ${isShuffle ? 'active' : ''}`}
          onClick={toggleShuffle}
          aria-label="Toggle Shuffle"
          type="button"
        >
          <span className="material-symbols-rounded">shuffle</span>
        </button>

        <button
          className="ctrl-btn"
          onClick={skipPrev}
          aria-label="Previous Track"
          type="button"
        >
          <span className="material-symbols-rounded">skip_previous</span>
        </button>

        <button
          className="master-play-btn"
          onClick={togglePlay}
          aria-label={isPlaying ? 'Pause' : 'Play'}
          type="button"
        >
          <svg viewBox="0 0 100 100" className="master-morph-svg">
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
          className="ctrl-btn"
          onClick={skipNext}
          aria-label="Next Track"
          type="button"
        >
          <span className="material-symbols-rounded">skip_next</span>
        </button>

        <button
          className={`ctrl-btn ${repeatMode !== 'OFF' ? 'active' : ''}`}
          onClick={toggleRepeat}
          aria-label="Toggle Repeat"
          type="button"
        >
          <span className="material-symbols-rounded">
            {repeatMode === 'ONE' ? 'repeat_one' : 'repeat'}
          </span>
        </button>
      </div>

      {/* Secondary Quick Utilities Bar */}
      <div className="secondary-utilities-row">
        {/* Speed Selector */}
        <div className="utility-menu-wrapper">
          <button
            className={`utility-chip ${playbackRate !== 1 ? 'active' : ''}`}
            onClick={() => setIsSpeedMenuOpen(!isSpeedMenuOpen)}
            type="button"
          >
            <span className="material-symbols-rounded" style={{ fontSize: '16px' }}>speed</span>
            <span>{playbackRate}x</span>
          </button>
          {isSpeedMenuOpen && (
            <div className="speed-dropdown-glass">
              {[0.5, 0.75, 1, 1.25, 1.5, 2].map((spd) => (
                <button
                  key={spd}
                  className={`speed-option ${playbackRate === spd ? 'active' : ''}`}
                  onClick={() => {
                    setPlaybackRate(spd);
                    setIsSpeedMenuOpen(false);
                  }}
                  type="button"
                >
                  {spd}x
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Sleep Timer */}
        <div className="utility-menu-wrapper">
          <button
            className={`utility-chip ${sleepTimerSeconds !== null ? 'active' : ''}`}
            onClick={() => setIsSleepMenuOpen(!isSleepMenuOpen)}
            type="button"
          >
            <span className="material-symbols-rounded" style={{ fontSize: '16px' }}>bedtime</span>
            <span>
              {sleepTimerSeconds !== null
                ? `${Math.ceil(sleepTimerSeconds / 60)}m`
                : 'Sleep'}
            </span>
          </button>
          {isSleepMenuOpen && (
            <div className="speed-dropdown-glass">
              {[15, 30, 45, 60].map((mins) => (
                <button
                  key={mins}
                  className="speed-option"
                  onClick={() => {
                    setSleepTimer(mins);
                    setIsSleepMenuOpen(false);
                  }}
                  type="button"
                >
                  {mins} min
                </button>
              ))}
              {sleepTimerSeconds !== null && (
                <button
                  className="speed-option text-rose-400"
                  onClick={() => {
                    setSleepTimer(null);
                    setIsSleepMenuOpen(false);
                  }}
                  type="button"
                >
                  Cancel
                </button>
              )}
            </div>
          )}
        </div>

        {/* Download Offline */}
        <button
          className={`utility-chip ${isDownloaded ? 'active' : ''}`}
          onClick={() => toggleDownload(currentTrack.id)}
          type="button"
        >
          <span className="material-symbols-rounded" style={{ fontSize: '16px' }}>
            {isDownloaded ? 'download_done' : 'download'}
          </span>
          <span>{isDownloaded ? 'Saved' : 'Save'}</span>
        </button>

        {/* SponsorBlock / AdBlock status chip */}
        <div className="utility-chip shield-status-chip" title="Ad-Free Shield & SponsorBlock active">
          <span className="material-symbols-rounded" style={{ fontSize: '16px', color: '#00cec9' }}>
            verified_user
          </span>
          <span>Ad-Free</span>
        </div>
      </div>

      {/* Queue Drawer Sheet */}
      <div className={`queue-drawer ${showQueueDrawer ? 'active' : 'hidden'}`}>
        <div className="queue-header">
          <div>
            <h3 className="queue-title">Playing Queue</h3>
            <span className="queue-count">{queue.length} streams queued</span>
          </div>
          <button
            className="icon-glass-btn sm"
            onClick={() => setShowQueueDrawer(false)}
            aria-label="Close Queue"
            type="button"
          >
            <span className="material-symbols-rounded">close</span>
          </button>
        </div>

        <div className="queue-list">
          {queue.map((item, idx) => {
            const isCurrent = idx === queueIndex;
            return (
              <div
                key={`${item.id}-${idx}`}
                className={`queue-item ${isCurrent ? 'current' : ''}`}
                onClick={() => {
                  playTrack(item, true);
                  setShowQueueDrawer(false);
                }}
              >
                <img src={item.artworkUrl} className="queue-thumb" alt={item.title} />
                <div className="queue-meta">
                  <div className="queue-item-title">{item.title}</div>
                  <div className="queue-item-artist">{item.artistOrChannel}</div>
                </div>
                {isCurrent ? (
                  <span className="queue-badge">PLAYING</span>
                ) : (
                  <button
                    className="queue-remove-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      removeFromQueue(idx);
                    }}
                    title="Remove from queue"
                    type="button"
                  >
                    <span className="material-symbols-rounded" style={{ fontSize: '16px' }}>close</span>
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
