import React, { useState } from 'react';
import { usePlayer } from '../../context/PlayerContext';
import { INITIAL_MUSIC_CATALOG, MediaTrack, Playlist } from '../../data/catalog';

const MOOD_CHIPS = ['All', 'Energize', 'Relax', 'Workout', 'Focus', 'Commute'];

export const YTMusicScreen: React.FC = () => {
  const [selectedMood, setSelectedMood] = useState('All');
  const {
    playTrack,
    setPlaybackMode,
    setIsMasterOpen,
    playlists
  } = usePlayer();

  const handlePlaySong = (song: MediaTrack, allTracks: MediaTrack[]) => {
    setPlaybackMode('SONG');
    playTrack(song, true, allTracks);
    setIsMasterOpen(true);
  };

  const handlePlayAllQuickPicks = () => {
    setPlaybackMode('SONG');
    playTrack(INITIAL_MUSIC_CATALOG[0], true, [...INITIAL_MUSIC_CATALOG]);
    setIsMasterOpen(true);
  };

  // Filter songs by mood
  const filteredCatalog = selectedMood === 'All'
    ? INITIAL_MUSIC_CATALOG
    : INITIAL_MUSIC_CATALOG.filter(s =>
        s.genre?.toLowerCase() === selectedMood.toLowerCase() ||
        (selectedMood === 'Energize' && (s.genre === 'Synthwave' || s.genre === 'Cyberpunk')) ||
        (selectedMood === 'Relax' && (s.genre === 'Ambient' || s.genre === 'Chillout')) ||
        (selectedMood === 'Focus' && (s.genre === 'Ambient' || s.genre === 'Focus'))
      );

  const displayList = filteredCatalog.length > 0 ? filteredCatalog : INITIAL_MUSIC_CATALOG;

  return (
    <section className="app-screen active">
      {/* Mood Filter Chips Bar */}
      <div className="mood-chips-scroll">
        {MOOD_CHIPS.map((mood) => (
          <button
            key={mood}
            className={`mood-chip ${selectedMood === mood ? 'active' : ''}`}
            onClick={() => setSelectedMood(mood)}
            type="button"
          >
            {mood}
          </button>
        ))}
      </div>

      {/* Scroller Content */}
      <div className="ytmusic-scroller">
        {/* Section: Listen Again */}
        <div className="ytm-section">
          <div className="ytm-section-header">
            <h2 className="ytm-section-title">Listen Again</h2>
            <button className="ytm-more-btn" type="button">More</button>
          </div>
          <div className="horizontal-cards-row">
            {displayList.map((item) => (
              <div
                key={item.id}
                className="ytm-album-card"
                onClick={() => handlePlaySong(item, displayList)}
              >
                <div className="ytm-album-art">
                  <img src={item.artworkUrl} alt={item.title} loading="lazy" />
                  <div className="ytm-album-play-overlay">
                    <span className="material-symbols-rounded">play_arrow</span>
                  </div>
                </div>
                <div className="ytm-album-title">{item.title}</div>
                <div className="ytm-album-sub">{item.artistOrChannel}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Section: Quick Picks */}
        <div className="ytm-section">
          <div className="ytm-section-header">
            <div>
              <span className="ytm-section-sub">START RADIO FROM A SONG</span>
              <h2 className="ytm-section-title">Quick Picks</h2>
            </div>
            <button
              className="ytm-play-all-btn"
              onClick={handlePlayAllQuickPicks}
              type="button"
            >
              <span className="material-symbols-rounded">play_arrow</span>
              Play all
            </button>
          </div>
          <div className="quick-picks-grid">
            {displayList.map((item) => (
              <div
                key={item.id}
                className="quick-pick-item"
                onClick={() => handlePlaySong(item, displayList)}
              >
                <img src={item.artworkUrl} alt={item.title} className="quick-pick-thumb" />
                <div className="quick-pick-info">
                  <div className="quick-pick-title">{item.title}</div>
                  <div className="quick-pick-meta">
                    {item.artistOrChannel} • {item.plays || '1.2M'} plays
                  </div>
                </div>
                <div className="quick-pick-action">
                  <span className="material-symbols-rounded">play_arrow</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Section: Mixed for You */}
        <div className="ytm-section">
          <div className="ytm-section-header">
            <h2 className="ytm-section-title">Mixed for You</h2>
          </div>
          <div className="horizontal-cards-row">
            {playlists.slice(0, 3).map((pl: Playlist) => (
              <div
                key={pl.id}
                className="ytm-album-card"
                onClick={() => {
                  const firstSong = INITIAL_MUSIC_CATALOG[0];
                  handlePlaySong(firstSong, INITIAL_MUSIC_CATALOG);
                }}
              >
                <div className="ytm-album-art">
                  <img src={pl.artworkUrl} alt={pl.title} loading="lazy" />
                  <div className="ytm-album-play-overlay">
                    <span className="material-symbols-rounded">play_arrow</span>
                  </div>
                </div>
                <div className="ytm-album-title">{pl.title}</div>
                <div className="ytm-album-sub">{pl.subtitle}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Section: Forgotten Favorites */}
        <div className="ytm-section">
          <div className="ytm-section-header">
            <h2 className="ytm-section-title">Forgotten Favorites</h2>
          </div>
          <div className="horizontal-cards-row">
            {[...displayList].reverse().map((item) => (
              <div
                key={`fav-${item.id}`}
                className="ytm-album-card"
                onClick={() => handlePlaySong(item, displayList)}
              >
                <div className="ytm-album-art">
                  <img src={item.artworkUrl} alt={item.title} loading="lazy" />
                  <div className="ytm-album-play-overlay">
                    <span className="material-symbols-rounded">play_arrow</span>
                  </div>
                </div>
                <div className="ytm-album-title">{item.title}</div>
                <div className="ytm-album-sub">{item.artistOrChannel}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};
