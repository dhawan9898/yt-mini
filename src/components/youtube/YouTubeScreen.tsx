import React, { useState } from 'react';
import { usePlayer, YouTubeTab } from '../../context/PlayerContext';
import { YouTubeVideo, YouTubeShort } from '../../data/catalog';
import { ShortsView } from './ShortsView';
import { CommentsDrawer } from './CommentsDrawer';
import { VideoMenuModal } from './VideoMenuModal';

const YT_TOPICS = [
  'All',
  'Coding',
  'Synthwave',
  'Sci-Fi',
  'Lo-Fi Beats',
  'Ambient Flow',
  'Live Sets'
];

export const YouTubeScreen: React.FC = () => {
  const {
    activeYtTab,
    setActiveYtTab,
    videos,
    subscriptions,
    history,
    playlists,
    downloadedIds,
    likedIds,
    playVideoAsTrack,
    setIsSettingsOpen
  } = usePlayer();

  const [selectedTopic, setSelectedTopic] = useState('All');
  const [menuVideo, setMenuVideo] = useState<YouTubeVideo | null>(null);
  const [activeCommentsShort, setActiveCommentsShort] = useState<YouTubeShort | null>(null);

  const filteredVideos = selectedTopic === 'All'
    ? videos
    : videos.filter(v => v.topic.toLowerCase() === selectedTopic.toLowerCase());

  return (
    <section className="app-screen active">
      {/* Category Topics Chips (Only on Home & Subs) */}
      {(activeYtTab === 'home' || activeYtTab === 'subs') && (
        <div className="yt-topics-scroll">
          {YT_TOPICS.map((topic) => (
            <button
              key={topic}
              className={`yt-topic-chip ${selectedTopic === topic ? 'active' : ''}`}
              onClick={() => setSelectedTopic(topic)}
              type="button"
            >
              {topic}
            </button>
          ))}
        </div>
      )}

      {/* Main Subview Container */}
      <div className="yt-content-wrapper">
        {/* ================= 1. HOME FEED (ZERO ADS, CLEAN FEED) ================= */}
        {activeYtTab === 'home' && (
          <div className="yt-subview active">
            <div className="yt-video-feed">
              {filteredVideos.map((vid) => (
                <div
                  key={vid.id}
                  className="yt-video-card"
                  onClick={() => playVideoAsTrack(vid, true)}
                >
                  <div className="yt-thumb-wrapper">
                    <img src={vid.thumbUrl} alt={vid.title} loading="lazy" />
                    <div className="yt-duration-badge">{vid.duration}</div>
                  </div>
                  <div className="yt-video-details">
                    <img
                      src={vid.channelAvatar}
                      alt={vid.channel}
                      className="yt-channel-avatar"
                    />
                    <div className="yt-meta-col">
                      <h3 className="yt-video-title">{vid.title}</h3>
                      <div className="yt-video-stats">
                        {vid.channel}
                        {vid.verified && (
                          <span className="material-symbols-rounded check-badge">
                            check_circle
                          </span>
                        )}
                        {' • '}{vid.views} • {vid.timeAgo}
                      </div>
                    </div>
                    <button
                      className="yt-more-dots"
                      onClick={(e) => {
                        e.stopPropagation();
                        setMenuVideo(vid);
                      }}
                      aria-label="More options"
                      type="button"
                    >
                      <span className="material-symbols-rounded">more_vert</span>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ================= 2. SHORTS FEED ================= */}
        {activeYtTab === 'shorts' && (
          <div className="yt-subview active">
            <ShortsView onOpenComments={(sh) => setActiveCommentsShort(sh)} />
          </div>
        )}

        {/* ================= 3. SUBSCRIPTIONS FEED ================= */}
        {activeYtTab === 'subs' && (
          <div className="yt-subview active">
            {/* Top Subscribed Channels Stories Bar */}
            <div className="subs-channels-bar">
              {subscriptions.map((sub) => (
                <div key={sub.id} className="sub-channel-pill">
                  <div className="sub-avatar-wrap">
                    <img src={sub.avatar} alt={sub.name} />
                    {sub.unread && <div className="sub-unread-dot" />}
                  </div>
                  <span className="sub-channel-name">{sub.name}</span>
                </div>
              ))}
            </div>

            {/* Subscriptions Feed List */}
            <div className="yt-video-feed">
              {videos.map((vid) => (
                <div
                  key={`sub-${vid.id}`}
                  className="yt-video-card"
                  onClick={() => playVideoAsTrack(vid, true)}
                >
                  <div className="yt-thumb-wrapper">
                    <img src={vid.thumbUrl} alt={vid.title} loading="lazy" />
                    <div className="yt-duration-badge">{vid.duration}</div>
                  </div>
                  <div className="yt-video-details">
                    <img
                      src={vid.channelAvatar}
                      alt={vid.channel}
                      className="yt-channel-avatar"
                    />
                    <div className="yt-meta-col">
                      <h3 className="yt-video-title">{vid.title}</h3>
                      <div className="yt-video-stats">
                        {vid.channel} • {vid.views} • {vid.timeAgo}
                      </div>
                    </div>
                    <button
                      className="yt-more-dots"
                      onClick={(e) => {
                        e.stopPropagation();
                        setMenuVideo(vid);
                      }}
                      aria-label="More options"
                      type="button"
                    >
                      <span className="material-symbols-rounded">more_vert</span>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ================= 4. YOU / ACCOUNT ================= */}
        {activeYtTab === 'you' && (
          <div className="yt-subview active">
            <div className="you-profile-card">
              <img
                src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&q=80"
                alt="User Profile"
                className="you-avatar"
              />
              <div className="you-meta">
                <h2 className="you-name">Alex Nexus</h2>
                <span className="you-handle">@alexnexus • NexusStream Pro</span>
              </div>
              <button
                className="you-switch-btn"
                onClick={() => setIsSettingsOpen(true)}
                type="button"
              >
                Settings
              </button>
            </div>

            {/* History Section */}
            <div className="you-section">
              <div className="you-section-header">
                <h3>History</h3>
                <button className="you-see-all-btn" type="button">See all</button>
              </div>
              <div className="you-history-row">
                {videos.slice(0, 4).map((vid, i) => (
                  <div
                    key={`hist-${vid.id}`}
                    className="you-history-card"
                    onClick={() => playVideoAsTrack(vid, true)}
                  >
                    <div className="you-hist-thumb">
                      <img src={vid.thumbUrl} alt={vid.title} />
                      <div
                        className="you-hist-progress"
                        style={{ width: `${40 + i * 18}%` }}
                      />
                    </div>
                    <div className="you-hist-title">{vid.title}</div>
                    <div className="you-hist-author">{vid.channel}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Playlists Section */}
            <div className="you-section">
              <div className="you-section-header">
                <h3>Playlists</h3>
                <button className="you-see-all-btn" type="button">See all</button>
              </div>
              <div className="you-playlists-row">
                {playlists.map((pl) => (
                  <div
                    key={pl.id}
                    className="you-history-card"
                    onClick={() => {
                      if (videos[0]) playVideoAsTrack(videos[0], true);
                    }}
                  >
                    <div className="you-hist-thumb">
                      <img src={pl.artworkUrl} alt={pl.title} />
                    </div>
                    <div className="you-hist-title">{pl.title}</div>
                    <div className="you-hist-author">{pl.subtitle}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Account Quick Options */}
            <div className="you-options-list">
              <div className="you-opt-item">
                <span className="material-symbols-rounded">download</span>
                <div className="opt-text">
                  <span className="opt-title">Offline Downloads</span>
                  <span className="opt-sub">{downloadedIds.size} media files stored offline</span>
                </div>
              </div>
              <div className="you-opt-item">
                <span className="material-symbols-rounded">thumb_up</span>
                <div className="opt-text">
                  <span className="opt-title">Liked Videos</span>
                  <span className="opt-sub">{likedIds.size} saved items</span>
                </div>
              </div>
              <div
                className="you-opt-item clickable"
                onClick={() => setIsSettingsOpen(true)}
              >
                <span className="material-symbols-rounded" style={{ color: '#00cec9' }}>
                  security
                </span>
                <div className="opt-text">
                  <span className="opt-title">Settings & Ad-Free Shield</span>
                  <span className="opt-sub">Zero Ads • SponsorBlock Active • PiP Ready</span>
                </div>
                <span className="material-symbols-rounded chevron">chevron_right</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Floating Pill Bottom Navigation (YouTube Mode) */}
      <nav className="yt-bottom-pill-bar">
        <button
          className={`yt-nav-pill-btn ${activeYtTab === 'home' ? 'active' : ''}`}
          onClick={() => setActiveYtTab('home')}
          type="button"
        >
          <span className="material-symbols-rounded">home</span>
          <span>Home</span>
        </button>

        <button
          className={`yt-nav-pill-btn ${activeYtTab === 'shorts' ? 'active' : ''}`}
          onClick={() => setActiveYtTab('shorts')}
          type="button"
        >
          <span className="material-symbols-rounded">bolt</span>
          <span>Shorts</span>
        </button>

        <button
          className={`yt-nav-pill-btn ${activeYtTab === 'subs' ? 'active' : ''}`}
          onClick={() => setActiveYtTab('subs')}
          type="button"
        >
          <span className="material-symbols-rounded">subscriptions</span>
          <span>Subscriptions</span>
        </button>

        <button
          className={`yt-nav-pill-btn ${activeYtTab === 'you' ? 'active' : ''}`}
          onClick={() => setActiveYtTab('you')}
          type="button"
        >
          <span className="material-symbols-rounded">account_circle</span>
          <span>You</span>
        </button>
      </nav>

      {/* Modals */}
      <VideoMenuModal video={menuVideo} onClose={() => setMenuVideo(null)} />
      <CommentsDrawer
        isOpen={Boolean(activeCommentsShort)}
        onClose={() => setActiveCommentsShort(null)}
      />
    </section>
  );
};
