export interface LyricWord {
  word: string;
  start: number;
  end: number;
}

export interface LyricLine {
  startTimeMs: number;
  endTimeMs: number;
  text: string;
  words?: LyricWord[];
}

export interface SponsorBlockSegment {
  category: 'sponsor' | 'intro' | 'outro' | 'selfpromo' | 'interaction';
  startMs: number;
  endMs: number;
  description: string;
}

export interface MediaTrack {
  id: string;
  title: string;
  artistOrChannel: string;
  audioUrl: string;
  videoUrl: string;
  artworkUrl: string;
  durationMs: number;
  dominantColor: string;
  secondaryColor: string;
  genre?: string;
  plays?: string;
  lyrics?: LyricLine[];
  sponsorSegments?: SponsorBlockSegment[];
}

export interface YouTubeVideo {
  id: string;
  title: string;
  channel: string;
  channelAvatar: string;
  videoUrl: string;
  thumbUrl: string;
  duration: string;
  durationMs: number;
  views: string;
  timeAgo: string;
  verified: boolean;
  topic: string;
  description?: string;
  likes?: string;
  sponsorSegments?: SponsorBlockSegment[];
}

export interface YouTubeShort {
  id: string;
  title: string;
  channel: string;
  channelAvatar: string;
  videoUrl: string;
  likes: string;
  comments: string;
  liked?: boolean;
}

export interface SubscriptionItem {
  id: string;
  name: string;
  avatar: string;
  unread: boolean;
  subscribers: string;
}

export interface CommentItem {
  id: string;
  author: string;
  avatar: string;
  text: string;
  timeAgo: string;
  likes: number;
  liked?: boolean;
}

export interface Playlist {
  id: string;
  title: string;
  subtitle: string;
  artworkUrl: string;
  trackCount: number;
  isCustom?: boolean;
  tracks: string[]; // ids
}

export const INITIAL_MUSIC_CATALOG: MediaTrack[] = [
  {
    id: "ytm_1",
    title: "Starfall Horizons (Liquid Edit)",
    artistOrChannel: "Aetheria & Kroma",
    audioUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    artworkUrl: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&q=80",
    durationMs: 214000,
    dominantColor: "#6c5ce7",
    secondaryColor: "#ff7675",
    genre: "Synthwave",
    plays: "4.8M",
    sponsorSegments: [
      { category: 'intro', startMs: 0, endMs: 3500, description: 'Track Intro' }
    ],
    lyrics: [
      {
        startTimeMs: 0,
        endTimeMs: 4000,
        text: "Light dissolves through frosted crystal skies",
        words: [
          { word: "Light", start: 0, end: 800 },
          { word: "dissolves", start: 800, end: 1600 },
          { word: "through", start: 1600, end: 2200 },
          { word: "frosted", start: 2200, end: 2900 },
          { word: "crystal", start: 2900, end: 3500 },
          { word: "skies", start: 3500, end: 4000 }
        ]
      },
      {
        startTimeMs: 4200,
        endTimeMs: 9500,
        text: "We drift beyond the edge of sound and time",
        words: [
          { word: "We", start: 4200, end: 4800 },
          { word: "drift", start: 4800, end: 5700 },
          { word: "beyond", start: 5700, end: 6600 },
          { word: "the", start: 6600, end: 7000 },
          { word: "edge", start: 7000, end: 7800 },
          { word: "of", start: 7800, end: 8200 },
          { word: "sound", start: 8200, end: 8900 },
          { word: "and", start: 8900, end: 9100 },
          { word: "time", start: 9100, end: 9500 }
        ]
      },
      {
        startTimeMs: 9800,
        endTimeMs: 15000,
        text: "Echoes colliding in a liquid glass cascade",
        words: [
          { word: "Echoes", start: 9800, end: 11000 },
          { word: "colliding", start: 11000, end: 12200 },
          { word: "in", start: 12200, end: 12600 },
          { word: "a", start: 12600, end: 12900 },
          { word: "liquid", start: 12900, end: 13700 },
          { word: "glass", start: 13700, end: 14300 },
          { word: "cascade", start: 14300, end: 15000 }
        ]
      },
      {
        startTimeMs: 15200,
        endTimeMs: 21000,
        text: "No boundaries left when horizons fade away",
        words: [
          { word: "No", start: 15200, end: 15800 },
          { word: "boundaries", start: 15800, end: 17200 },
          { word: "left", start: 17200, end: 18000 },
          { word: "when", start: 18000, end: 18600 },
          { word: "horizons", start: 18600, end: 19800 },
          { word: "fade", start: 19800, end: 20400 },
          { word: "away", start: 20400, end: 21000 }
        ]
      },
      {
        startTimeMs: 21500,
        endTimeMs: 28000,
        text: "Floating through the ultraviolet glow of memory",
        words: [
          { word: "Floating", start: 21500, end: 22800 },
          { word: "through", start: 22800, end: 23400 },
          { word: "the", start: 23400, end: 23800 },
          { word: "ultraviolet", start: 23800, end: 25200 },
          { word: "glow", start: 25200, end: 26100 },
          { word: "of", start: 26100, end: 26500 },
          { word: "memory", start: 26500, end: 28000 }
        ]
      }
    ]
  },
  {
    id: "ytm_2",
    title: "Midnight Cyberia",
    artistOrChannel: "Neon Vanguard",
    audioUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
    artworkUrl: "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&q=80",
    durationMs: 188000,
    dominantColor: "#00cec9",
    secondaryColor: "#e84393",
    genre: "Cyberpunk",
    plays: "8.2M",
    lyrics: [
      { startTimeMs: 0, endTimeMs: 5000, text: "Signals in the neon rainfall glow" },
      { startTimeMs: 5200, endTimeMs: 11000, text: "Lost beneath the chrome and wire below" },
      { startTimeMs: 11200, endTimeMs: 17500, text: "Circuits pulse with digital desire" },
      { startTimeMs: 17800, endTimeMs: 24000, text: "Electric soul consumed by cyber fire" }
    ]
  },
  {
    id: "ytm_3",
    title: "Velvet Pulse",
    artistOrChannel: "Solstice Duo",
    audioUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    artworkUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
    durationMs: 245000,
    dominantColor: "#fd79a8",
    secondaryColor: "#fdcb6e",
    genre: "Ambient",
    plays: "1.9M",
    lyrics: [
      { startTimeMs: 0, endTimeMs: 6000, text: "Soft murmurs breathe in twilight haze" },
      { startTimeMs: 6200, endTimeMs: 13000, text: "Dancing slow through golden amber rays" },
      { startTimeMs: 13200, endTimeMs: 20000, text: "Gravity softens into gentle sound" },
      { startTimeMs: 20200, endTimeMs: 28000, text: "Where peace and timeless quiet are found" }
    ]
  },
  {
    id: "ytm_4",
    title: "Chromium Dreams",
    artistOrChannel: "Hyperion Protocol",
    audioUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
    artworkUrl: "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80",
    durationMs: 302000,
    dominantColor: "#0984e3",
    secondaryColor: "#00cec9",
    genre: "Synthwave",
    plays: "3.1M",
    lyrics: [
      { startTimeMs: 0, endTimeMs: 7000, text: "Speeding down the synthetic gridway" },
      { startTimeMs: 7200, endTimeMs: 14000, text: "Leaving yesterday behind in the rear-view mirror" }
    ]
  },
  {
    id: "ytm_5",
    title: "Solaris Resonance",
    artistOrChannel: "Aura Borealis",
    audioUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
    artworkUrl: "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800&q=80",
    durationMs: 260000,
    dominantColor: "#e17055",
    secondaryColor: "#fab1a0",
    genre: "Chillout",
    plays: "920K",
    lyrics: [
      { startTimeMs: 0, endTimeMs: 6500, text: "Sunlight cascades through ocean deep" },
      { startTimeMs: 6800, endTimeMs: 13000, text: "Awakening thoughts that were fast asleep" }
    ]
  },
  {
    id: "ytm_6",
    title: "Quantum Drift",
    artistOrChannel: "Nova Spectra",
    audioUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    artworkUrl: "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&q=80",
    durationMs: 220000,
    dominantColor: "#9b59b6",
    secondaryColor: "#3498db",
    genre: "Focus",
    plays: "1.4M",
    lyrics: [
      { startTimeMs: 0, endTimeMs: 5500, text: "Harmonics aligning in state zero" },
      { startTimeMs: 5800, endTimeMs: 12000, text: "The universe breathes between the strings" }
    ]
  }
];

export const INITIAL_YOUTUBE_VIDEOS: YouTubeVideo[] = [
  {
    id: "ytv_1",
    title: "Building an Autonomous Coding Agent from Scratch in 2026",
    channel: "Tech Vanguard Lab",
    channelAvatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800&q=80",
    duration: "18:24",
    durationMs: 1104000,
    views: "1.2M views",
    timeAgo: "2 days ago",
    verified: true,
    topic: "Coding",
    likes: "68K",
    description: "In this comprehensive masterclass, explore the architectural foundations of modern coding agents: context pruning, speculative execution, AST tooling, and self-verifying feedback loops."
  },
  {
    id: "ytv_2",
    title: "Liquid Glass UI Design in Jetpack Compose: Deep Dive & Physics Animations",
    channel: "Android Lead Architecture",
    channelAvatar: "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=800&q=80",
    duration: "24:15",
    durationMs: 1455000,
    views: "480K views",
    timeAgo: "5 days ago",
    verified: true,
    topic: "Coding",
    likes: "32K",
    description: "Learn how to build glassy, fluid surfaces with real-time blur shaders, spring damping, dynamic mesh backdrops, and seamless morphing transitions."
  },
  {
    id: "ytv_3",
    title: "Synthwave Sunset 4K • 3 Hours Ambient Visual Odyssey (No Ads)",
    channel: "Cyberia Records",
    channelAvatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&q=80",
    duration: "3:00:00",
    durationMs: 10800000,
    views: "3.4M views",
    timeAgo: "3 weeks ago",
    verified: true,
    topic: "Synthwave",
    likes: "154K",
    description: "Immerse yourself in retrofuturistic neon landscapes with continuous synth, chillwave, and outrun melodies. Clean ad-free audio pipeline enabled."
  },
  {
    id: "ytv_4",
    title: "Interstellar Journey: Real 4K Space Exploration Drone Footage",
    channel: "Cosmo Vista",
    channelAvatar: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&q=80",
    duration: "14:48",
    durationMs: 888000,
    views: "890K views",
    timeAgo: "1 month ago",
    verified: false,
    topic: "Sci-Fi",
    likes: "45K",
    description: "Deep space photography captured from deep survey observatories and rendered at high frame rates with celestial sound design."
  },
  {
    id: "ytv_5",
    title: "Lo-Fi Beats to Relax / Study to • Midnight Tokyo Rain [Stream]",
    channel: "Lo-Fi Sanctuary",
    channelAvatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&q=80",
    duration: "1:20:10",
    durationMs: 4810000,
    views: "2.1M views",
    timeAgo: "2 weeks ago",
    verified: true,
    topic: "Lo-Fi Beats",
    likes: "98K",
    description: "Calm dusty vinyl piano chops, gentle ambient rain, and warm basslines designed for extended productivity and deep rest."
  },
  {
    id: "ytv_6",
    title: "Ambient Flow: Healing Frequencies 432Hz Sound Bath Meditation",
    channel: "Zenith Soundscapes",
    channelAvatar: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800&q=80",
    duration: "45:00",
    durationMs: 2700000,
    views: "640K views",
    timeAgo: "3 days ago",
    verified: false,
    topic: "Ambient Flow",
    likes: "29K",
    description: "Gentle crystal singing bowls and binaural drones tuned to harmonic resonance for stress release and mental clarity."
  }
];

export const INITIAL_SHORTS: YouTubeShort[] = [
  {
    id: "short_1",
    title: "When your background media pipeline blocks ads automatically 🔥",
    channel: "NexusStream Dev",
    channelAvatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    likes: "248K",
    comments: "1.4K",
    liked: false
  },
  {
    id: "short_2",
    title: "Seamless Audio-to-Video toggle without rebuffering! 🎧⚡",
    channel: "Kotlin Architects",
    channelAvatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
    likes: "189K",
    comments: "892",
    liked: false
  },
  {
    id: "short_3",
    title: "Vinyl disc procedural canvas physics in dark glass UI ✨",
    channel: "UI Alchemy",
    channelAvatar: "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
    likes: "312K",
    comments: "2.1K",
    liked: true
  },
  {
    id: "short_4",
    title: "Zero-bloat ad-free experience that respects your battery life 🔋🚀",
    channel: "Tech Vanguard Lab",
    channelAvatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    likes: "95K",
    comments: "540",
    liked: false
  }
];

export const INITIAL_SUBSCRIPTIONS: SubscriptionItem[] = [
  { id: "sub_1", name: "Android Lead", avatar: "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100&q=80", unread: true, subscribers: "1.8M" },
  { id: "sub_2", name: "Cyberia", avatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&q=80", unread: true, subscribers: "3.4M" },
  { id: "sub_3", name: "Tech Vanguard", avatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80", unread: false, subscribers: "920K" },
  { id: "sub_4", name: "Cosmo Vista", avatar: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100&q=80", unread: true, subscribers: "2.1M" },
  { id: "sub_5", name: "Aetheria", avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80", unread: false, subscribers: "4.5M" },
  { id: "sub_6", name: "Lo-Fi Sanctuary", avatar: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=100&q=80", unread: true, subscribers: "1.1M" }
];

export const INITIAL_PLAYLISTS: Playlist[] = [
  {
    id: "pl_supermix",
    title: "My Supermix",
    subtitle: "Aetheria, Neon Vanguard, Solstice Duo",
    artworkUrl: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&q=80",
    trackCount: 48,
    tracks: ["ytm_1", "ytm_2", "ytm_3", "ytm_4"]
  },
  {
    id: "pl_synthwave",
    title: "Synthwave Mix",
    subtitle: "Hyperion Protocol, Kroma, Neon Vanguard",
    artworkUrl: "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=400&q=80",
    trackCount: 32,
    tracks: ["ytm_2", "ytm_4"]
  },
  {
    id: "pl_focus",
    title: "Focus Deep State",
    subtitle: "Ambient flows without words",
    artworkUrl: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400&q=80",
    trackCount: 26,
    tracks: ["ytm_3", "ytm_5", "ytm_6"]
  },
  {
    id: "pl_watchlater",
    title: "Watch Later",
    subtitle: "28 videos • Private",
    artworkUrl: "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=400&q=80",
    trackCount: 28,
    isCustom: true,
    tracks: ["ytv_1", "ytv_2"]
  },
  {
    id: "pl_liked",
    title: "Liked Videos",
    subtitle: "142 items • Private",
    artworkUrl: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400&q=80",
    trackCount: 142,
    isCustom: true,
    tracks: ["ytv_3", "ytm_1"]
  }
];

export const INITIAL_COMMENTS: CommentItem[] = [
  {
    id: "c_1",
    author: "Elena Rostova",
    avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80",
    text: "The audio-to-video seamless switch without dropping a single frame or re-buffering is unbelievably smooth! Best player ever.",
    timeAgo: "2 hours ago",
    likes: 124,
    liked: false
  },
  {
    id: "c_2",
    author: "Marcus Vance",
    avatar: "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100&q=80",
    text: "Finally an app that separates YouTube videos and Shorts cleanly into dedicated tabs without injecting playable ads into the feed.",
    timeAgo: "5 hours ago",
    likes: 89,
    liked: true
  },
  {
    id: "c_3",
    author: "Aiden Reed",
    avatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80",
    text: "The synced word-by-word karaoke lyrics with the glowing liquid glass design is absolute art.",
    timeAgo: "1 day ago",
    likes: 42,
    liked: false
  }
];
