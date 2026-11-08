import React, { createContext, useContext, useState, useRef, useEffect, useCallback } from 'react';
import {
  MediaTrack,
  YouTubeVideo,
  YouTubeShort,
  SubscriptionItem,
  Playlist,
  CommentItem,
  INITIAL_MUSIC_CATALOG,
  INITIAL_YOUTUBE_VIDEOS,
  INITIAL_SHORTS,
  INITIAL_SUBSCRIPTIONS,
  INITIAL_PLAYLISTS,
  INITIAL_COMMENTS
} from '../data/catalog';

export type AppMode = 'YTMUSIC' | 'YOUTUBE';
export type YouTubeTab = 'home' | 'shorts' | 'subs' | 'you';
export type PlaybackMode = 'SONG' | 'VIDEO';
export type RepeatMode = 'OFF' | 'ALL' | 'ONE';

interface PlayerContextType {
  // App navigation
  appMode: AppMode;
  setAppMode: (mode: AppMode) => void;
  activeYtTab: YouTubeTab;
  setActiveYtTab: (tab: YouTubeTab) => void;
  isSearchOpen: boolean;
  setIsSearchOpen: (open: boolean) => void;
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  isSettingsOpen: boolean;
  setIsSettingsOpen: (open: boolean) => void;

  // Media Playback State
  currentTrack: MediaTrack;
  queue: MediaTrack[];
  queueIndex: number;
  isPlaying: boolean;
  playbackMode: PlaybackMode;
  currentTime: number; // in seconds
  duration: number; // in seconds
  buffered: number; // 0 to 1
  volume: number;
  playbackRate: number;
  isShuffle: boolean;
  repeatMode: RepeatMode;
  showLyrics: boolean;
  showQueueDrawer: boolean;
  isMasterOpen: boolean;

  // Media Actions
  playTrack: (track: MediaTrack, autoplay?: boolean, newQueue?: MediaTrack[]) => void;
  playVideoAsTrack: (video: YouTubeVideo, autoplay?: boolean) => void;
  togglePlay: () => void;
  pause: () => void;
  play: () => void;
  skipNext: () => void;
  skipPrev: () => void;
  seekTo: (seconds: number) => void;
  setPlaybackMode: (mode: PlaybackMode) => void;
  setVolume: (v: number) => void;
  setPlaybackRate: (rate: number) => void;
  toggleShuffle: () => void;
  toggleRepeat: () => void;
  setShowLyrics: (show: boolean | ((prev: boolean) => boolean)) => void;
  setShowQueueDrawer: (show: boolean | ((prev: boolean) => boolean)) => void;
  setIsMasterOpen: (open: boolean) => void;
  removeFromQueue: (index: number) => void;
  addToQueueNext: (track: MediaTrack) => void;

  // Video and Audio References
  audioRef: React.RefObject<HTMLAudioElement>;
  videoRef: React.RefObject<HTMLVideoElement>;

  // Library & Data state
  videos: YouTubeVideo[];
  shorts: YouTubeShort[];
  subscriptions: SubscriptionItem[];
  playlists: Playlist[];
  comments: CommentItem[];
  likedIds: Set<string>;
  downloadedIds: Set<string>;
  watchLaterIds: Set<string>;
  history: MediaTrack[];
  toggleLike: (id: string) => void;
  toggleDownload: (id: string) => void;
  toggleWatchLater: (id: string) => void;
  addComment: (text: string) => void;
  toggleSubscribe: (id: string) => void;

  // Shields & Settings
  adBlockEnabled: boolean;
  setAdBlockEnabled: (enabled: boolean) => void;
  sponsorBlockEnabled: boolean;
  setSponsorBlockEnabled: (enabled: boolean) => void;
  skippedNotice: string | null;
  adsShieldCount: number;
  sponsorsSkippedCount: number;
  audioQuality: 'high' | 'medium' | 'low';
  setAudioQuality: (q: 'high' | 'medium' | 'low') => void;
  videoQuality: '1080p' | '720p' | '480p' | 'auto';
  setVideoQuality: (q: '1080p' | '720p' | '480p' | 'auto') => void;

  // Sleep / Bedtime Timer
  sleepTimerSeconds: number | null; // null if not active, or seconds remaining
  setSleepTimer: (minutes: number | null) => void;
}

const PlayerContext = createContext<PlayerContextType | null>(null);

export const PlayerProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Navigation
  const [appMode, setAppMode] = useState<AppMode>('YTMUSIC');
  const [activeYtTab, setActiveYtTab] = useState<YouTubeTab>('home');
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  // Data
  const [videos] = useState<YouTubeVideo[]>(INITIAL_YOUTUBE_VIDEOS);
  const [shorts, setShorts] = useState<YouTubeShort[]>(INITIAL_SHORTS);
  const [subscriptions, setSubscriptions] = useState<SubscriptionItem[]>(INITIAL_SUBSCRIPTIONS);
  const [playlists, setPlaylists] = useState<Playlist[]>(INITIAL_PLAYLISTS);
  const [comments, setComments] = useState<CommentItem[]>(INITIAL_COMMENTS);
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set(['ytm_1', 'short_3']));
  const [downloadedIds, setDownloadedIds] = useState<Set<string>>(new Set(['ytm_1', 'ytm_2']));
  const [watchLaterIds, setWatchLaterIds] = useState<Set<string>>(new Set(['ytv_1']));
  const [history, setHistory] = useState<MediaTrack[]>([]);

  // Playback
  const [queue, setQueue] = useState<MediaTrack[]>(INITIAL_MUSIC_CATALOG);
  const [queueIndex, setQueueIndex] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackMode, setPlaybackModeState] = useState<PlaybackMode>('SONG');
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(INITIAL_MUSIC_CATALOG[0].durationMs / 1000);
  const [buffered, setBuffered] = useState<number>(0);
  const [volume, setVolumeState] = useState<number>(1);
  const [playbackRate, setPlaybackRateState] = useState<number>(1);
  const [isShuffle, setIsShuffle] = useState<boolean>(false);
  const [repeatMode, setRepeatMode] = useState<RepeatMode>('ALL');
  const [showLyrics, setShowLyrics] = useState<boolean>(false);
  const [showQueueDrawer, setShowQueueDrawer] = useState<boolean>(false);
  const [isMasterOpen, setIsMasterOpen] = useState<boolean>(false);

  // Settings & Shields
  const [adBlockEnabled, setAdBlockEnabled] = useState<boolean>(true);
  const [sponsorBlockEnabled, setSponsorBlockEnabled] = useState<boolean>(true);
  const [skippedNotice, setSkippedNotice] = useState<string | null>(null);
  const [adsShieldCount, setAdsShieldCount] = useState<number>(42);
  const [sponsorsSkippedCount, setSponsorsSkippedCount] = useState<number>(18);
  const [audioQuality, setAudioQuality] = useState<'high' | 'medium' | 'low'>('high');
  const [videoQuality, setVideoQuality] = useState<'1080p' | '720p' | '480p' | 'auto'>('1080p');

  // Sleep Timer
  const [sleepTimerSeconds, setSleepTimerSeconds] = useState<number | null>(null);

  // Media DOM refs
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);

  const currentTrack = queue[queueIndex] || INITIAL_MUSIC_CATALOG[0];

  // Sync media time & play states
  const getActiveMediaEl = useCallback(() => {
    return playbackMode === 'VIDEO' ? videoRef.current : audioRef.current;
  }, [playbackMode]);

  const play = useCallback(() => {
    const el = getActiveMediaEl();
    if (el) {
      el.play().then(() => setIsPlaying(true)).catch(() => {});
    } else {
      setIsPlaying(true);
    }
  }, [getActiveMediaEl]);

  const pause = useCallback(() => {
    if (audioRef.current) audioRef.current.pause();
    if (videoRef.current) videoRef.current.pause();
    setIsPlaying(false);
  }, []);

  const togglePlay = useCallback(() => {
    if (isPlaying) {
      pause();
    } else {
      play();
    }
  }, [isPlaying, pause, play]);

  const seekTo = useCallback((seconds: number) => {
    const clamped = Math.max(0, Math.min(seconds, duration));
    if (audioRef.current) audioRef.current.currentTime = clamped;
    if (videoRef.current) videoRef.current.currentTime = clamped;
    setCurrentTime(clamped);
  }, [duration]);

  const setPlaybackRate = useCallback((rate: number) => {
    setPlaybackRateState(rate);
    if (audioRef.current) audioRef.current.playbackRate = rate;
    if (videoRef.current) videoRef.current.playbackRate = rate;
  }, []);

  const setVolume = useCallback((v: number) => {
    setVolumeState(v);
    if (audioRef.current) audioRef.current.volume = v;
    if (videoRef.current) videoRef.current.volume = v;
  }, []);

  const playTrack = useCallback((track: MediaTrack, autoplay = true, newQueue?: MediaTrack[]) => {
    if (newQueue) {
      setQueue(newQueue);
      const idx = newQueue.findIndex(t => t.id === track.id);
      setQueueIndex(idx >= 0 ? idx : 0);
    } else {
      const idx = queue.findIndex(t => t.id === track.id);
      if (idx >= 0) {
        setQueueIndex(idx);
      } else {
        setQueue(prev => [track, ...prev]);
        setQueueIndex(0);
      }
    }

    setCurrentTime(0);
    setDuration(track.durationMs / 1000);

    // Update history
    setHistory(prev => [track, ...prev.filter(t => t.id !== track.id)].slice(0, 50));

    // Media element src reload
    setTimeout(() => {
      if (audioRef.current) {
        audioRef.current.src = track.audioUrl;
        audioRef.current.currentTime = 0;
      }
      if (videoRef.current) {
        videoRef.current.src = track.videoUrl;
        videoRef.current.currentTime = 0;
      }
      if (autoplay) {
        const active = playbackMode === 'VIDEO' ? videoRef.current : audioRef.current;
        if (active) {
          active.play().then(() => setIsPlaying(true)).catch(() => {});
        }
      }
    }, 20);
  }, [queue, playbackMode]);

  const playVideoAsTrack = useCallback((video: YouTubeVideo, autoplay = true) => {
    const asTrack: MediaTrack = {
      id: video.id,
      title: video.title,
      artistOrChannel: video.channel,
      audioUrl: video.videoUrl,
      videoUrl: video.videoUrl,
      artworkUrl: video.thumbUrl,
      durationMs: video.durationMs,
      dominantColor: "#0984e3",
      secondaryColor: "#00cec9",
      genre: video.topic
    };
    setPlaybackModeState('VIDEO');
    playTrack(asTrack, autoplay, [asTrack, ...queue]);
    setIsMasterOpen(true);
  }, [playTrack, queue]);

  const skipNext = useCallback(() => {
    if (queue.length === 0) return;
    let nextIdx = (queueIndex + 1) % queue.length;
    if (isShuffle) {
      nextIdx = Math.floor(Math.random() * queue.length);
    }
    setQueueIndex(nextIdx);
    playTrack(queue[nextIdx], true);
  }, [queue, queueIndex, isShuffle, playTrack]);

  const skipPrev = useCallback(() => {
    if (currentTime > 3) {
      seekTo(0);
      return;
    }
    if (queue.length === 0) return;
    const prevIdx = (queueIndex - 1 + queue.length) % queue.length;
    setQueueIndex(prevIdx);
    playTrack(queue[prevIdx], true);
  }, [currentTime, queue, queueIndex, seekTo, playTrack]);

  const setPlaybackMode = useCallback((mode: PlaybackMode) => {
    if (mode === playbackMode) return;
    const fromEl = playbackMode === 'VIDEO' ? videoRef.current : audioRef.current;
    const toEl = mode === 'VIDEO' ? videoRef.current : audioRef.current;
    const pos = fromEl ? fromEl.currentTime : currentTime;
    const wasPlaying = isPlaying;

    if (fromEl) fromEl.pause();
    setPlaybackModeState(mode);

    setTimeout(() => {
      if (toEl) {
        toEl.currentTime = pos;
        if (wasPlaying) {
          toEl.play().catch(() => {});
        }
      }
    }, 20);
  }, [playbackMode, currentTime, isPlaying]);

  const toggleShuffle = useCallback(() => {
    setIsShuffle(prev => !prev);
  }, []);

  const toggleRepeat = useCallback(() => {
    setRepeatMode(prev => {
      if (prev === 'OFF') return 'ALL';
      if (prev === 'ALL') return 'ONE';
      return 'OFF';
    });
  }, []);

  const removeFromQueue = useCallback((index: number) => {
    setQueue(prev => {
      const next = [...prev];
      next.splice(index, 1);
      return next;
    });
    if (index === queueIndex && queue.length > 1) {
      skipNext();
    } else if (index < queueIndex) {
      setQueueIndex(i => i - 1);
    }
  }, [queueIndex, queue.length, skipNext]);

  const addToQueueNext = useCallback((track: MediaTrack) => {
    setQueue(prev => {
      const next = [...prev];
      next.splice(queueIndex + 1, 0, track);
      return next;
    });
  }, [queueIndex]);

  const toggleLike = useCallback((id: string) => {
    setLikedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  const toggleDownload = useCallback((id: string) => {
    setDownloadedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  const toggleWatchLater = useCallback((id: string) => {
    setWatchLaterIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  const addComment = useCallback((text: string) => {
    const newComment: CommentItem = {
      id: `c_${Date.now()}`,
      author: 'You (Alex)',
      avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80',
      text,
      timeAgo: 'Just now',
      likes: 0,
      liked: false
    };
    setComments(prev => [newComment, ...prev]);
  }, []);

  const toggleSubscribe = useCallback((id: string) => {
    setSubscriptions(prev =>
      prev.map(s => (s.id === id ? { ...s, unread: false } : s))
    );
  }, []);

  const setSleepTimer = useCallback((minutes: number | null) => {
    if (minutes === null) {
      setSleepTimerSeconds(null);
    } else {
      setSleepTimerSeconds(minutes * 60);
    }
  }, []);

  // Sleep timer interval countdown
  useEffect(() => {
    if (sleepTimerSeconds === null) return;
    if (sleepTimerSeconds <= 0) {
      pause();
      setSleepTimerSeconds(null);
      return;
    }
    const timer = setInterval(() => {
      setSleepTimerSeconds(prev => {
        if (prev === null || prev <= 1) {
          pause();
          return null;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [sleepTimerSeconds, pause]);

  // SponsorBlock segment detection & skip
  useEffect(() => {
    if (!sponsorBlockEnabled || !currentTrack.sponsorSegments) return;
    const currentMs = currentTime * 1000;
    for (const segment of currentTrack.sponsorSegments) {
      if (currentMs >= segment.startMs && currentMs < segment.endMs - 300) {
        seekTo(segment.endMs / 1000);
        setSponsorsSkippedCount(c => c + 1);
        setSkippedNotice(`SponsorBlock: Skipped ${segment.description}`);
        const t = setTimeout(() => setSkippedNotice(null), 3000);
        return () => clearTimeout(t);
      }
    }
  }, [currentTime, sponsorBlockEnabled, currentTrack, seekTo]);

  // Sync active CSS custom properties with track dominant colors
  useEffect(() => {
    const root = document.documentElement;
    root.style.setProperty('--dominant-color', currentTrack.dominantColor || '#6c5ce7');
    root.style.setProperty('--secondary-color', currentTrack.secondaryColor || '#ff7675');
  }, [currentTrack]);

  return (
    <PlayerContext.Provider
      value={{
        appMode,
        setAppMode,
        activeYtTab,
        setActiveYtTab,
        isSearchOpen,
        setIsSearchOpen,
        searchQuery,
        setSearchQuery,
        isSettingsOpen,
        setIsSettingsOpen,

        currentTrack,
        queue,
        queueIndex,
        isPlaying,
        playbackMode,
        currentTime,
        duration,
        buffered,
        volume,
        playbackRate,
        isShuffle,
        repeatMode,
        showLyrics,
        showQueueDrawer,
        isMasterOpen,

        playTrack,
        playVideoAsTrack,
        togglePlay,
        pause,
        play,
        skipNext,
        skipPrev,
        seekTo,
        setPlaybackMode,
        setVolume,
        setPlaybackRate,
        toggleShuffle,
        toggleRepeat,
        setShowLyrics,
        setShowQueueDrawer,
        setIsMasterOpen,
        removeFromQueue,
        addToQueueNext,

        audioRef,
        videoRef,

        videos,
        shorts,
        subscriptions,
        playlists,
        comments,
        likedIds,
        downloadedIds,
        watchLaterIds,
        history,
        toggleLike,
        toggleDownload,
        toggleWatchLater,
        addComment,
        toggleSubscribe,

        adBlockEnabled,
        setAdBlockEnabled,
        sponsorBlockEnabled,
        setSponsorBlockEnabled,
        skippedNotice,
        adsShieldCount,
        sponsorsSkippedCount,
        audioQuality,
        setAudioQuality,
        videoQuality,
        setVideoQuality,

        sleepTimerSeconds,
        setSleepTimer
      }}
    >
      {/* Background HTML5 audio & video streams */}
      <audio
        ref={audioRef}
        src={currentTrack.audioUrl}
        preload="auto"
        onTimeUpdate={(e) => setCurrentTime((e.target as HTMLAudioElement).currentTime)}
        onDurationChange={(e) => {
          const d = (e.target as HTMLAudioElement).duration;
          if (d && !isNaN(d)) setDuration(d);
        }}
        onProgress={(e) => {
          const el = e.target as HTMLAudioElement;
          if (el.buffered.length > 0) {
            setBuffered(el.buffered.end(el.buffered.length - 1) / (el.duration || 1));
          }
        }}
        onEnded={() => {
          if (repeatMode === 'ONE') {
            seekTo(0);
            play();
          } else {
            skipNext();
          }
        }}
      />
      {children}
    </PlayerContext.Provider>
  );
};

export const usePlayer = () => {
  const context = useContext(PlayerContext);
  if (!context) throw new Error('usePlayer must be used within PlayerProvider');
  return context;
};
