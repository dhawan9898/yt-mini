import React, { useState, useEffect, useRef } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { INITIAL_MUSIC_CATALOG, MediaTrack, YouTubeVideo, YouTubeShort } from '../data/catalog';

type SearchFilter = 'all' | 'songs' | 'videos' | 'shorts';

export const SearchOverlay: React.FC = () => {
  const {
    isSearchOpen,
    setIsSearchOpen,
    searchQuery,
    setSearchQuery,
    videos,
    shorts,
    playTrack,
    playVideoAsTrack,
    setPlaybackMode,
    setIsMasterOpen
  } = usePlayer();

  const [activeFilter, setActiveFilter] = useState<SearchFilter>('all');
  const [liveResults, setLiveResults] = useState<MediaTrack[]>([]);
  const [isSearchingOnline, setIsSearchingOnline] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (isSearchOpen) {
      setTimeout(() => inputRef.current?.focus(), 150);
    }
  }, [isSearchOpen]);

  // Online fallback search via invidious / mock
  useEffect(() => {
    if (!searchQuery.trim() || searchQuery.length < 2) {
      setLiveResults([]);
      setIsSearchingOnline(false);
      return;
    }

    const timer = setTimeout(async () => {
      setIsSearchingOnline(true);
      try {
        const res = await fetch(
          `https://inv.nadeko.net/api/v1/search?q=${encodeURIComponent(searchQuery)}&type=video`,
          { signal: AbortSignal.timeout(2500) }
        );
        if (res.ok) {
          const data = await res.json();
          const mapped: MediaTrack[] = data.slice(0, 6).map((item: any) => ({
            id: item.videoId || `live_${Math.random()}`,
            title: item.title,
            artistOrChannel: item.author || 'YouTube Channel',
            audioUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            artworkUrl: item.videoThumbnails?.[0]?.url || `https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg`,
            durationMs: (item.lengthSeconds || 180) * 1000,
            dominantColor: '#0984e3',
            secondaryColor: '#00cec9',
            genre: 'YouTube'
          }));
          setLiveResults(mapped);
        }
      } catch {
        // Fallback gracefully without throwing
      } finally {
        setIsSearchingOnline(false);
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [searchQuery]);

  if (!isSearchOpen) return null;

  const q = searchQuery.toLowerCase().trim();

  const filteredSongs = INITIAL_MUSIC_CATALOG.filter(
    s => !q || s.title.toLowerCase().includes(q) || s.artistOrChannel.toLowerCase().includes(q)
  );

  const filteredVideos = videos.filter(
    v => !q || v.title.toLowerCase().includes(q) || v.channel.toLowerCase().includes(q)
  );

  const filteredShorts = shorts.filter(
    sh => !q || sh.title.toLowerCase().includes(q) || sh.channel.toLowerCase().includes(q)
  );

  const handleSelectSong = (song: MediaTrack) => {
    setPlaybackMode('SONG');
    playTrack(song, true);
    setIsMasterOpen(true);
    setIsSearchOpen(false);
  };

  const handleSelectVideo = (video: YouTubeVideo) => {
    playVideoAsTrack(video, true);
    setIsSearchOpen(false);
  };

  const handleSelectLive = (track: MediaTrack) => {
    setPlaybackMode('VIDEO');
    playTrack(track, true);
    setIsMasterOpen(true);
    setIsSearchOpen(false);
  };

  const popularKeywords = ['Synthwave', 'Coding Beats', 'Liquid UI', 'Cyberpunk', 'Lo-Fi Rain', 'Space 4K'];

  return (
    <>
      <div className="search-container">
        <div className="search-glass-bar">
          <span className="material-symbols-rounded search-icon">search</span>
          <input
            ref={inputRef}
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search songs, videos, channels..."
            autoComplete="off"
          />
          {searchQuery && (
            <button
              className="clear-btn"
              onClick={() => setSearchQuery('')}
              aria-label="Clear Search"
              type="button"
            >
              <span className="material-symbols-rounded">close</span>
            </button>
          )}
        </div>
      </div>

      <div className="search-results-overlay">
        {/* Quick Suggestions when empty */}
        {!searchQuery && (
          <div style={{ padding: '0 16px 14px' }}>
            <div style={{ fontSize: '11px', fontWeight: 600, color: 'rgba(255,255,255,0.4)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: '8px' }}>
              Suggested Searches
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              {popularKeywords.map((kw) => (
                <button
                  key={kw}
                  className="search-chip"
                  onClick={() => setSearchQuery(kw)}
                  type="button"
                >
                  <span className="material-symbols-rounded" style={{ fontSize: '14px', marginRight: '4px', verticalAlign: 'middle' }}>trending_up</span>
                  {kw}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Filter Chips */}
        <div className="search-filter-chips">
          {(['all', 'songs', 'videos', 'shorts'] as SearchFilter[]).map((f) => (
            <button
              key={f}
              className={`search-chip ${activeFilter === f ? 'active' : ''}`}
              onClick={() => setActiveFilter(f)}
              type="button"
            >
              {f.charAt(0).toUpperCase() + f.slice(1)}
            </button>
          ))}
        </div>

        {/* Results List */}
        <div className="search-results-list">
          {isSearchingOnline && (
            <div style={{ textAlign: 'center', color: 'rgba(255,255,255,0.6)', padding: '16px' }}>
              <span className="material-symbols-rounded" style={{ animation: 'spin 1s linear infinite', display: 'inline-block', verticalAlign: 'middle', marginRight: '6px' }}>
                sync
              </span>
              Searching unified feed...
            </div>
          )}

          {/* Online Live Results */}
          {(activeFilter === 'all' || activeFilter === 'videos') && liveResults.length > 0 && (
            <>
              <div style={{ fontSize: '11px', fontWeight: 700, color: '#00cec9', textTransform: 'uppercase', letterSpacing: '0.05em', padding: '8px 12px 4px' }}>
                Online Feed
              </div>
              {liveResults.map((live) => (
                <div
                  key={live.id}
                  className="quick-pick-item"
                  onClick={() => handleSelectLive(live)}
                >
                  <img src={live.artworkUrl} className="quick-pick-thumb" alt={live.title} />
                  <div className="quick-pick-info">
                    <div className="quick-pick-title">{live.title}</div>
                    <div className="quick-pick-meta">YouTube • {live.artistOrChannel}</div>
                  </div>
                  <span className="material-symbols-rounded" style={{ color: '#0984e3' }}>play_circle</span>
                </div>
              ))}
            </>
          )}

          {/* Songs matches */}
          {(activeFilter === 'all' || activeFilter === 'songs') && (
            <>
              {filteredSongs.length > 0 && activeFilter === 'all' && (
                <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--dominant-color)', textTransform: 'uppercase', letterSpacing: '0.05em', padding: '8px 12px 4px' }}>
                  YT Music Songs
                </div>
              )}
              {filteredSongs.map((song) => (
                <div
                  key={song.id}
                  className="quick-pick-item"
                  onClick={() => handleSelectSong(song)}
                >
                  <img src={song.artworkUrl} className="quick-pick-thumb" alt={song.title} />
                  <div className="quick-pick-info">
                    <div className="quick-pick-title">{song.title}</div>
                    <div className="quick-pick-meta">{song.artistOrChannel} • {song.plays || 'Track'}</div>
                  </div>
                  <div className="quick-pick-action">
                    <span className="material-symbols-rounded">play_arrow</span>
                  </div>
                </div>
              ))}
            </>
          )}

          {/* Video matches */}
          {(activeFilter === 'all' || activeFilter === 'videos') && (
            <>
              {filteredVideos.length > 0 && activeFilter === 'all' && (
                <div style={{ fontSize: '11px', fontWeight: 700, color: '#ff7675', textTransform: 'uppercase', letterSpacing: '0.05em', padding: '12px 12px 4px' }}>
                  YouTube Videos
                </div>
              )}
              {filteredVideos.map((vid) => (
                <div
                  key={vid.id}
                  className="quick-pick-item"
                  onClick={() => handleSelectVideo(vid)}
                >
                  <img src={vid.thumbUrl} className="quick-pick-thumb" alt={vid.title} />
                  <div className="quick-pick-info">
                    <div className="quick-pick-title">{vid.title}</div>
                    <div className="quick-pick-meta">{vid.channel} • {vid.views} • {vid.duration}</div>
                  </div>
                  <span className="material-symbols-rounded">play_circle</span>
                </div>
              ))}
            </>
          )}

          {/* Shorts matches */}
          {(activeFilter === 'all' || activeFilter === 'shorts') && filteredShorts.length > 0 && (
            <>
              {activeFilter === 'all' && (
                <div style={{ fontSize: '11px', fontWeight: 700, color: '#ff0033', textTransform: 'uppercase', letterSpacing: '0.05em', padding: '12px 12px 4px' }}>
                  YouTube Shorts
                </div>
              )}
              {filteredShorts.map((sh) => (
                <div
                  key={sh.id}
                  className="quick-pick-item"
                  onClick={() => {
                    const asTrack: MediaTrack = {
                      id: sh.id,
                      title: sh.title,
                      artistOrChannel: sh.channel,
                      audioUrl: sh.videoUrl,
                      videoUrl: sh.videoUrl,
                      artworkUrl: sh.channelAvatar,
                      durationMs: 30000,
                      dominantColor: '#ff0033',
                      secondaryColor: '#ff7675'
                    };
                    setPlaybackMode('VIDEO');
                    playTrack(asTrack, true);
                    setIsMasterOpen(true);
                    setIsSearchOpen(false);
                  }}
                >
                  <img src={sh.channelAvatar} className="quick-pick-thumb" style={{ borderRadius: '50%' }} alt={sh.channel} />
                  <div className="quick-pick-info">
                    <div className="quick-pick-title">{sh.title}</div>
                    <div className="quick-pick-meta">@{sh.channel} • {sh.likes} likes</div>
                  </div>
                  <span className="material-symbols-rounded">bolt</span>
                </div>
              ))}
            </>
          )}

          {filteredSongs.length === 0 && filteredVideos.length === 0 && filteredShorts.length === 0 && liveResults.length === 0 && !isSearchingOnline && (
            <div style={{ textAlign: 'center', color: 'rgba(255,255,255,0.4)', padding: '32px 16px' }}>
              <span className="material-symbols-rounded" style={{ fontSize: '36px', marginBottom: '8px', opacity: 0.5 }}>search_off</span>
              <p>No results found for "{searchQuery}"</p>
            </div>
          )}
        </div>
      </div>
    </>
  );
};
