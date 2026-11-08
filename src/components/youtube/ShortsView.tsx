import React, { useState, useRef } from 'react';
import { usePlayer } from '../../context/PlayerContext';
import { YouTubeShort } from '../../data/catalog';

interface ShortsViewProps {
  onOpenComments: (short: YouTubeShort) => void;
}

export const ShortsView: React.FC<ShortsViewProps> = ({ onOpenComments }) => {
  const { shorts } = usePlayer();
  const [activeShortIndex, setActiveShortIndex] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [likedShorts, setLikedShorts] = useState<Set<string>>(new Set(['short_3']));
  const [subscribedChannels, setSubscribedChannels] = useState<Set<string>>(new Set());
  const videoRefs = useRef<(HTMLVideoElement | null)[]>([]);

  const handleVideoClick = (idx: number) => {
    const v = videoRefs.current[idx];
    if (v) {
      if (v.paused) {
        v.play().catch(() => {});
      } else {
        v.pause();
      }
    }
  };

  const toggleShortLike = (id: string) => {
    setLikedShorts(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const toggleSubscribe = (channel: string) => {
    setSubscribedChannels(prev => {
      const next = new Set(prev);
      if (next.has(channel)) {
        next.delete(channel);
      } else {
        next.add(channel);
      }
      return next;
    });
  };

  const handleShare = async (short: YouTubeShort) => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: short.title,
          text: `Check out this short on NexusStream: ${short.title}`,
          url: window.location.href
        });
      } catch {
        // Ignored
      }
    } else {
      navigator.clipboard?.writeText(window.location.href);
      alert('Short link copied to clipboard!');
    }
  };

  return (
    <div className="shorts-feed-container">
      {shorts.map((short, idx) => {
        const isLiked = likedShorts.has(short.id);
        const isSubscribed = subscribedChannels.has(short.channel);

        return (
          <div key={short.id} className="short-reel-card">
            <video
              ref={el => (videoRefs.current[idx] = el)}
              src={short.videoUrl}
              loop
              playsInline
              muted={isMuted}
              autoPlay={idx === 0}
              className="short-video-el"
              onClick={() => handleVideoClick(idx)}
            />

            {/* Mute toggle button floating top */}
            <button
              className="short-mute-btn"
              onClick={() => setIsMuted(!isMuted)}
              type="button"
              aria-label={isMuted ? 'Unmute' : 'Mute'}
            >
              <span className="material-symbols-rounded">
                {isMuted ? 'volume_off' : 'volume_up'}
              </span>
            </button>

            {/* Bottom Caption & Channel Meta */}
            <div className="short-overlay-bottom">
              <div className="short-channel-row">
                <img
                  src={short.channelAvatar}
                  alt={short.channel}
                  className="short-avatar-img"
                />
                <span className="short-channel-name">@{short.channel}</span>
                <button
                  className={`short-sub-btn ${isSubscribed ? 'subscribed' : ''}`}
                  onClick={() => toggleSubscribe(short.channel)}
                  type="button"
                >
                  {isSubscribed ? 'Subscribed' : 'Subscribe'}
                </button>
              </div>
              <p className="short-caption">{short.title}</p>
            </div>

            {/* Right Action Sidebar: Like, Dislike, Comment, Share */}
            <div className="short-actions-sidebar">
              <button
                className="short-action-btn"
                onClick={() => toggleShortLike(short.id)}
                type="button"
              >
                <div className={`short-action-circle ${isLiked ? 'active text-rose-500' : ''}`}>
                  <span className="material-symbols-rounded">
                    {isLiked ? 'favorite' : 'thumb_up'}
                  </span>
                </div>
                <span className="short-action-count">{short.likes}</span>
              </button>

              <button className="short-action-btn" type="button">
                <div className="short-action-circle">
                  <span className="material-symbols-rounded">thumb_down</span>
                </div>
                <span className="short-action-count">Dislike</span>
              </button>

              <button
                className="short-action-btn"
                onClick={() => onOpenComments(short)}
                type="button"
              >
                <div className="short-action-circle">
                  <span className="material-symbols-rounded">comment</span>
                </div>
                <span className="short-action-count">{short.comments}</span>
              </button>

              <button
                className="short-action-btn"
                onClick={() => handleShare(short)}
                type="button"
              >
                <div className="short-action-circle">
                  <span className="material-symbols-rounded">share</span>
                </div>
                <span className="short-action-count">Share</span>
              </button>

              <button className="short-action-btn" type="button">
                <div className="short-action-circle">
                  <span className="material-symbols-rounded">loop</span>
                </div>
                <span className="short-action-count">Remix</span>
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
};
