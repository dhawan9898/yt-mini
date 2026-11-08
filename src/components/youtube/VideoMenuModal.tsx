import React from 'react';
import { usePlayer } from '../../context/PlayerContext';
import { YouTubeVideo } from '../../data/catalog';

interface VideoMenuModalProps {
  video: YouTubeVideo | null;
  onClose: () => void;
}

export const VideoMenuModal: React.FC<VideoMenuModalProps> = ({ video, onClose }) => {
  const {
    watchLaterIds,
    toggleWatchLater,
    downloadedIds,
    toggleDownload,
    addToQueueNext,
    playlists
  } = usePlayer();

  if (!video) return null;

  const isSavedWatchLater = watchLaterIds.has(video.id);
  const isDownloaded = downloadedIds.has(video.id);

  const handleShare = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: video.title,
          text: `Watch ${video.title} by ${video.channel} on NexusStream`,
          url: window.location.href
        });
      } catch {
        // Ignored
      }
    } else {
      navigator.clipboard?.writeText(window.location.href);
      alert('Link copied to clipboard!');
    }
    onClose();
  };

  const handleQueueNext = () => {
    addToQueueNext({
      id: video.id,
      title: video.title,
      artistOrChannel: video.channel,
      audioUrl: video.videoUrl,
      videoUrl: video.videoUrl,
      artworkUrl: video.thumbUrl,
      durationMs: video.durationMs,
      dominantColor: '#0984e3',
      secondaryColor: '#00cec9',
      genre: video.topic
    });
    alert(`Queued "${video.title}" to play next`);
    onClose();
  };

  return (
    <div className="video-menu-overlay" onClick={onClose}>
      <div className="video-menu-sheet" onClick={(e) => e.stopPropagation()}>
        {/* Video Thumbnail Header */}
        <div className="menu-video-preview">
          <img src={video.thumbUrl} alt={video.title} className="menu-preview-thumb" />
          <div className="menu-preview-meta">
            <h4 className="menu-preview-title">{video.title}</h4>
            <span className="menu-preview-channel">{video.channel}</span>
          </div>
        </div>

        <div className="menu-actions-list">
          {/* Play Next in Queue */}
          <button className="menu-action-item" onClick={handleQueueNext} type="button">
            <span className="material-symbols-rounded">queue_play_next</span>
            <span>Play Next in Queue</span>
          </button>

          {/* Save to Watch Later */}
          <button
            className="menu-action-item"
            onClick={() => {
              toggleWatchLater(video.id);
              onClose();
            }}
            type="button"
          >
            <span className="material-symbols-rounded">
              {isSavedWatchLater ? 'check_circle' : 'schedule'}
            </span>
            <span>{isSavedWatchLater ? 'Remove from Watch Later' : 'Save to Watch Later'}</span>
          </button>

          {/* Download offline */}
          <button
            className="menu-action-item"
            onClick={() => {
              toggleDownload(video.id);
              onClose();
            }}
            type="button"
          >
            <span className="material-symbols-rounded">
              {isDownloaded ? 'download_done' : 'download'}
            </span>
            <span>{isDownloaded ? 'Downloaded (Offline Available)' : 'Download Video (Ad-Free)'}</span>
          </button>

          {/* Share */}
          <button className="menu-action-item" onClick={handleShare} type="button">
            <span className="material-symbols-rounded">share</span>
            <span>Share Video</span>
          </button>

          {/* Not Interested */}
          <button className="menu-action-item text-tertiary" onClick={onClose} type="button">
            <span className="material-symbols-rounded">not_interested</span>
            <span>Not Interested</span>
          </button>
        </div>
      </div>
    </div>
  );
};
