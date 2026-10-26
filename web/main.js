/**
 * NexusStream • Web Interactive Engine
 * Unified Client for YouTube and YouTube Music
 */

// =================================================================
// RICH DATA CATALOGS (Music, Videos, Shorts, Subscriptions)
// =================================================================
const MUSIC_CATALOG = [
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
      { startTimeMs: 5200, endTimeMs: 11000, text: "Lost beneath the chrome and wire below" }
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
      { startTimeMs: 6200, endTimeMs: 13000, text: "Dancing slow through golden amber rays" }
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
    plays: "3.1M"
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
    plays: "920K"
  }
];

const YOUTUBE_VIDEOS = [
  {
    id: "ytv_1",
    title: "Building an Autonomous Coding Agent from Scratch in 2026",
    channel: "Tech Vanguard Lab",
    channelAvatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800&q=80",
    duration: "18:24",
    views: "1.2M views",
    timeAgo: "2 days ago",
    verified: true,
    topic: "Coding"
  },
  {
    id: "ytv_2",
    title: "Liquid Glass UI Design in Jetpack Compose: Deep Dive & Physics Animations",
    channel: "Android Lead Architecture",
    channelAvatar: "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=800&q=80",
    duration: "24:15",
    views: "480K views",
    timeAgo: "5 days ago",
    verified: true,
    topic: "Coding"
  },
  {
    id: "ytv_3",
    title: "Synthwave Sunset 4K • 3 Hours Ambient Visual Odyssey (No Ads)",
    channel: "Cyberia Records",
    channelAvatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&q=80",
    duration: "3:00:00",
    views: "3.4M views",
    timeAgo: "3 weeks ago",
    verified: true,
    topic: "Synthwave"
  },
  {
    id: "ytv_4",
    title: "Interstellar Journey: Real 4K Space Exploration Drone Footage",
    channel: "Cosmo Vista",
    channelAvatar: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
    thumbUrl: "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&q=80",
    duration: "14:48",
    views: "890K views",
    timeAgo: "1 month ago",
    verified: false,
    topic: "Sci-Fi"
  }
];

const YOUTUBE_SHORTS = [
  {
    id: "short_1",
    title: "When your background media pipeline blocks ads automatically 🔥",
    channel: "NexusStream Dev",
    channelAvatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    likes: "248K",
    comments: "1.4K"
  },
  {
    id: "short_2",
    title: "Seamless Audio-to-Video toggle without rebuffering! 🎧⚡",
    channel: "Kotlin Architects",
    channelAvatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
    likes: "189K",
    comments: "892"
  }
];

const SUBSCRIPTIONS = [
  { name: "Android Lead", avatar: "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100&q=80", unread: true },
  { name: "Cyberia", avatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&q=80", unread: true },
  { name: "Tech Vanguard", avatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80", unread: false },
  { name: "Cosmo Vista", avatar: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100&q=80", unread: true },
  { name: "Aetheria", avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80", unread: false }
];

// =================================================================
// APP STATE
// =================================================================
const state = {
  currentAppMode: "YTMUSIC", // "YTMUSIC" | "YOUTUBE"
  ytActiveView: "home", // "home" | "shorts" | "subs" | "you"
  currentTrackIndex: 0,
  isPlaying: false,
  playbackMode: "SONG", // "SONG" | "VIDEO"
  showLyrics: false,
  shuffle: false,
  repeatMode: "ALL",
  queue: [...MUSIC_CATALOG],
  currentScreen: "FEED",
  isScrubbing: false
};

// =================================================================
// DOM REFS
// =================================================================
const elements = {
  // Mode switcher
  modeIndicator: document.getElementById("modeIndicator"),
  ytMusicTabBtn: document.getElementById("ytMusicTabBtn"),
  ytVideoTabBtn: document.getElementById("ytVideoTabBtn"),
  musicAppScreen: document.getElementById("musicAppScreen"),
  youtubeAppScreen: document.getElementById("youtubeAppScreen"),

  // Search
  searchToggleBtn: document.getElementById("searchToggleBtn"),
  searchContainer: document.getElementById("searchContainer"),
  searchInput: document.getElementById("searchInput"),
  clearSearchBtn: document.getElementById("clearSearchBtn"),
  searchResultsSection: document.getElementById("searchResultsSection"),
  searchResultsList: document.getElementById("searchResultsList"),

  // YT Music Containers
  ytmListenAgainRow: document.getElementById("ytmListenAgainRow"),
  ytmQuickPicksList: document.getElementById("ytmQuickPicksList"),
  ytmMixedForYouRow: document.getElementById("ytmMixedForYouRow"),
  ytmFavoritesRow: document.getElementById("ytmFavoritesRow"),
  playAllQuickPicksBtn: document.getElementById("playAllQuickPicksBtn"),

  // YouTube Containers & Views
  ytHomeView: document.getElementById("ytHomeView"),
  ytShortsView: document.getElementById("ytShortsView"),
  ytSubscriptionsView: document.getElementById("ytSubscriptionsView"),
  ytYouView: document.getElementById("ytYouView"),
  ytHomeFeedList: document.getElementById("ytHomeFeedList"),
  shortsContainer: document.getElementById("shortsContainer"),
  subsChannelsBar: document.getElementById("subsChannelsBar"),
  subsFeedList: document.getElementById("subsFeedList"),
  youHistoryRow: document.getElementById("youHistoryRow"),
  youPlaylistsRow: document.getElementById("youPlaylistsRow"),

  // YouTube Bottom Pill Nav
  ytNavHomeBtn: document.getElementById("ytNavHomeBtn"),
  ytNavShortsBtn: document.getElementById("ytNavShortsBtn"),
  ytNavSubsBtn: document.getElementById("ytNavSubsBtn"),
  ytNavYouBtn: document.getElementById("ytNavYouBtn"),

  // Mini Player
  miniPlayer: document.getElementById("miniPlayer"),
  miniPlayerClickZone: document.getElementById("miniPlayerClickZone"),
  miniThumb: document.getElementById("miniThumb"),
  miniTitle: document.getElementById("miniTitle"),
  miniArtist: document.getElementById("miniArtist"),
  miniPlayPauseBtn: document.getElementById("miniPlayPauseBtn"),
  miniMorphLeft: document.getElementById("miniMorphLeft"),
  miniMorphRight: document.getElementById("miniMorphRight"),

  // Master Player Screen
  playerScreen: document.getElementById("playerScreen"),
  collapsePlayerBtn: document.getElementById("collapsePlayerBtn"),
  capsuleIndicator: document.getElementById("capsuleIndicator"),
  modeSongBtn: document.getElementById("modeSongBtn"),
  modeVideoBtn: document.getElementById("modeVideoBtn"),
  queueToggleBtn: document.getElementById("queueToggleBtn"),
  lyricsToggleBtn: document.getElementById("lyricsToggleBtn"),

  vinylContainer: document.getElementById("vinylContainer"),
  vinylDisc: document.getElementById("vinylDisc"),
  vinylCanvas: document.getElementById("vinylCanvas"),
  playerArtwork: document.getElementById("playerArtwork"),
  videoContainer: document.getElementById("videoContainer"),
  mediaVideo: document.getElementById("mediaVideo"),
  lyricsContainer: document.getElementById("lyricsContainer"),
  lyricsScroller: document.getElementById("lyricsScroller"),

  playerTitle: document.getElementById("playerTitle"),
  playerArtist: document.getElementById("playerArtist"),
  sliderTrack: document.getElementById("sliderTrack"),
  sliderBuffer: document.getElementById("sliderBuffer"),
  sliderProgress: document.getElementById("sliderProgress"),
  sliderThumb: document.getElementById("sliderThumb"),
  currentTime: document.getElementById("currentTime"),
  totalDuration: document.getElementById("totalDuration"),
  shuffleBtn: document.getElementById("shuffleBtn"),
  prevBtn: document.getElementById("prevBtn"),
  masterPlayPauseBtn: document.getElementById("masterPlayPauseBtn"),
  morphLeft: document.getElementById("morphLeft"),
  morphRight: document.getElementById("morphRight"),
  nextBtn: document.getElementById("nextBtn"),
  repeatBtn: document.getElementById("repeatBtn"),
  repeatIcon: document.getElementById("repeatIcon"),

  queueDrawer: document.getElementById("queueDrawer"),
  closeQueueBtn: document.getElementById("closeQueueBtn"),
  queueList: document.getElementById("queueList"),
  queueCount: document.getElementById("queueCount"),

  mediaAudio: document.getElementById("mediaAudio"),
  meshCanvas: document.getElementById("meshCanvas")
};

// =================================================================
// DYNAMIC MESH BACKGROUND
// =================================================================
class DynamicMeshEngine {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.phase = 0;
    this.dominantColor = MUSIC_CATALOG[0].dominantColor;
    this.secondaryColor = MUSIC_CATALOG[0].secondaryColor;
    this.resize();
    window.addEventListener("resize", () => this.resize());
    this.loop();
  }

  resize() {
    this.width = window.innerWidth;
    this.height = window.innerHeight;
    this.canvas.width = this.width;
    this.canvas.height = this.height;
  }

  setColors(dominant, secondary) {
    this.dominantColor = dominant;
    this.secondaryColor = secondary;
    document.documentElement.style.setProperty("--dominant-color", dominant);
    document.documentElement.style.setProperty("--secondary-color", secondary);
  }

  loop() {
    this.phase += 0.008;
    const w = this.width;
    const h = this.height;
    const ctx = this.ctx;

    ctx.fillStyle = "#07070a";
    ctx.fillRect(0, 0, w, h);

    const x1 = w * (0.35 + 0.18 * Math.cos(this.phase));
    const y1 = h * (0.28 + 0.15 * Math.sin(this.phase));
    const r1 = Math.min(w, h) * 0.75;

    const x2 = w * (0.68 + 0.14 * Math.sin(this.phase * 0.8));
    const y2 = h * (0.65 + 0.18 * Math.cos(this.phase * 0.8));
    const r2 = Math.min(w, h) * 0.85;

    const grad1 = ctx.createRadialGradient(x1, y1, 0, x1, y1, r1);
    grad1.addColorStop(0, hexToRgba(this.dominantColor, 0.45));
    grad1.addColorStop(0.5, hexToRgba(this.dominantColor, 0.18));
    grad1.addColorStop(1, "transparent");
    ctx.fillStyle = grad1;
    ctx.beginPath();
    ctx.arc(x1, y1, r1, 0, Math.PI * 2);
    ctx.fill();

    const grad2 = ctx.createRadialGradient(x2, y2, 0, x2, y2, r2);
    grad2.addColorStop(0, hexToRgba(this.secondaryColor, 0.4));
    grad2.addColorStop(0.5, hexToRgba(this.secondaryColor, 0.12));
    grad2.addColorStop(1, "transparent");
    ctx.fillStyle = grad2;
    ctx.beginPath();
    ctx.arc(x2, y2, r2, 0, Math.PI * 2);
    ctx.fill();

    const vignette = ctx.createLinearGradient(0, 0, 0, h);
    vignette.addColorStop(0, "rgba(0, 0, 0, 0.35)");
    vignette.addColorStop(0.5, "transparent");
    vignette.addColorStop(1, "rgba(0, 0, 0, 0.85)");
    ctx.fillStyle = vignette;
    ctx.fillRect(0, 0, w, h);

    requestAnimationFrame(() => this.loop());
  }
}

function hexToRgba(hex, alpha) {
  let c;
  if (/^#([A-Fa-f0-9]{3}){1,2}$/.test(hex)) {
    c = hex.substring(1).split("");
    if (c.length === 3) c = [c[0], c[0], c[1], c[1], c[2], c[2]];
    c = "0x" + c.join("");
    return `rgba(${(c >> 16) & 255}, ${(c >> 8) & 255}, ${c & 255}, ${alpha})`;
  }
  return `rgba(108,92,231,${alpha})`;
}

// Procedural Vinyl Canvas
function drawVinylGrooves(canvas) {
  const ctx = canvas.getContext("2d");
  const size = canvas.width;
  const center = size / 2;
  const outerRadius = size / 2 - 4;

  ctx.clearRect(0, 0, size, size);

  const bodyGrad = ctx.createRadialGradient(center, center, 0, center, center, outerRadius);
  bodyGrad.addColorStop(0, "#1a1a22");
  bodyGrad.addColorStop(0.7, "#111116");
  bodyGrad.addColorStop(1, "#08080c");
  ctx.fillStyle = bodyGrad;
  ctx.beginPath();
  ctx.arc(center, center, outerRadius, 0, Math.PI * 2);
  ctx.fill();

  for (let i = 1; i <= 8; i++) {
    const r = outerRadius * (0.42 + i * 0.065);
    ctx.strokeStyle = "rgba(255, 255, 255, 0.04)";
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.arc(center, center, r, 0, Math.PI * 2);
    ctx.stroke();
  }

  const sweep = ctx.createConicGradient(0, center, center);
  sweep.addColorStop(0, "transparent");
  sweep.addColorStop(0.25, "rgba(255, 255, 255, 0.08)");
  sweep.addColorStop(0.5, "transparent");
  sweep.addColorStop(0.75, "rgba(255, 255, 255, 0.08)");
  sweep.addColorStop(1, "transparent");
  ctx.fillStyle = sweep;
  ctx.beginPath();
  ctx.arc(center, center, outerRadius, 0, Math.PI * 2);
  ctx.fill();
}

function animateMorphButton(toPause) {
  const leftPaths = [elements.morphLeft, elements.miniMorphLeft];
  const rightPaths = [elements.morphRight, elements.miniMorphRight];

  const playLeft = "M 22 15 L 48 30 L 48 70 L 22 85 Z";
  const playRight = "M 48 30 L 82 50 L 82 50 L 48 70 Z";

  const pauseLeft = "M 20 16 L 42 16 L 42 84 L 20 84 Z";
  const pauseRight = "M 58 16 L 80 16 L 80 84 L 58 84 Z";

  leftPaths.forEach((p) => p && p.setAttribute("d", toPause ? pauseLeft : playLeft));
  rightPaths.forEach((p) => p && p.setAttribute("d", toPause ? pauseRight : playRight));
}

// =================================================================
// UNIFIED MASTER CONTROLLER
// =================================================================
class NexusMasterApp {
  constructor() {
    this.meshEngine = new DynamicMeshEngine(elements.meshCanvas);
    drawVinylGrooves(elements.vinylCanvas);
    this.currentTrack = state.queue[0];

    this.bindEvents();
    this.renderYTMusicSections();
    this.renderYouTubeFeed();
    this.renderShortsFeed();
    this.renderSubscriptionsView();
    this.renderYouView();
    this.loadTrack(this.currentTrack, false);
  }

  getCurrentMedia() {
    return state.playbackMode === "VIDEO" ? elements.mediaVideo : elements.mediaAudio;
  }

  bindEvents() {
    // Mode Switching: YT Music <-> YouTube
    elements.ytMusicTabBtn.addEventListener("click", () => this.switchAppMode("YTMUSIC"));
    elements.ytVideoTabBtn.addEventListener("click", () => this.switchAppMode("YOUTUBE"));

    // YouTube Bottom Navigation Pill
    const navButtons = [
      elements.ytNavHomeBtn,
      elements.ytNavShortsBtn,
      elements.ytNavSubsBtn,
      elements.ytNavYouBtn
    ];
    navButtons.forEach((btn) => {
      btn.addEventListener("click", () => {
        navButtons.forEach((b) => b.classList.remove("active"));
        btn.classList.add("active");
        this.switchYouTubeSubView(btn.dataset.view);
      });
    });

    // Audio & Video Event Listeners
    elements.mediaAudio.addEventListener("timeupdate", () => this.onTimeUpdate(elements.mediaAudio));
    elements.mediaAudio.addEventListener("ended", () => this.onTrackEnded());
    elements.mediaAudio.addEventListener("progress", () => this.onBufferProgress(elements.mediaAudio));

    elements.mediaVideo.addEventListener("timeupdate", () => this.onTimeUpdate(elements.mediaVideo));
    elements.mediaVideo.addEventListener("ended", () => this.onTrackEnded());
    elements.mediaVideo.addEventListener("progress", () => this.onBufferProgress(elements.mediaVideo));

    // Master Player Controls
    elements.masterPlayPauseBtn.addEventListener("click", () => this.togglePlayPause());
    elements.miniPlayPauseBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      this.togglePlayPause();
    });

    elements.nextBtn.addEventListener("click", () => this.skipNext());
    elements.prevBtn.addEventListener("click", () => this.skipPrev());
    elements.shuffleBtn.addEventListener("click", () => this.toggleShuffle());
    elements.repeatBtn.addEventListener("click", () => this.toggleRepeat());

    // Screen Switching
    elements.miniPlayerClickZone.addEventListener("click", () => this.showMasterPlayer(true));
    elements.collapsePlayerBtn.addEventListener("click", () => this.showMasterPlayer(false));

    // Song vs Video Toggle
    elements.modeSongBtn.addEventListener("click", () => this.setPlaybackMode("SONG"));
    elements.modeVideoBtn.addEventListener("click", () => this.setPlaybackMode("VIDEO"));

    // Lyrics & Queue Toggles
    elements.lyricsToggleBtn.addEventListener("click", () => this.toggleLyrics());
    elements.queueToggleBtn.addEventListener("click", () => this.toggleQueueDrawer());
    elements.closeQueueBtn.addEventListener("click", () => this.toggleQueueDrawer(false));

    // Search Toggle & Search Input
    elements.searchToggleBtn.addEventListener("click", () => this.toggleSearchOverlay());
    elements.clearSearchBtn.addEventListener("click", () => {
      elements.searchInput.value = "";
      this.handleSearch("");
    });
    elements.searchInput.addEventListener("input", (e) => this.handleSearch(e.target.value));

    // Quick Picks Play All
    elements.playAllQuickPicksBtn.addEventListener("click", () => {
      state.queue = [...MUSIC_CATALOG];
      state.currentTrackIndex = 0;
      this.loadTrack(state.queue[0], true);
      this.showMasterPlayer(true);
    });

    this.setupScrubber();
  }

  // =================================================================
  // APP MODE & VIEWS SWITCHING
  // =================================================================
  switchAppMode(mode) {
    state.currentAppMode = mode;
    if (mode === "YTMUSIC") {
      elements.modeIndicator.style.transform = "translateX(0%)";
      elements.ytMusicTabBtn.classList.add("active");
      elements.ytVideoTabBtn.classList.remove("active");
      elements.musicAppScreen.classList.add("active");
      elements.youtubeAppScreen.classList.remove("active");
    } else {
      elements.modeIndicator.style.transform = "translateX(100%)";
      elements.ytVideoTabBtn.classList.add("active");
      elements.ytMusicTabBtn.classList.remove("active");
      elements.youtubeAppScreen.classList.add("active");
      elements.musicAppScreen.classList.remove("active");
    }
  }

  switchYouTubeSubView(view) {
    state.ytActiveView = view;
    const views = {
      home: elements.ytHomeView,
      shorts: elements.ytShortsView,
      subs: elements.ytSubscriptionsView,
      you: elements.ytYouView
    };

    Object.values(views).forEach((v) => v.classList.remove("active"));
    if (views[view]) {
      views[view].classList.add("active");
    }
  }

  showMasterPlayer(show) {
    if (show) {
      elements.playerScreen.classList.add("active");
    } else {
      elements.playerScreen.classList.remove("active");
      this.toggleQueueDrawer(false);
    }
  }

  // =================================================================
  // YT MUSIC SECTIONS RENDERING
  // =================================================================
  renderYTMusicSections() {
    // 1. Listen Again Row
    elements.ytmListenAgainRow.innerHTML = "";
    MUSIC_CATALOG.forEach((item, idx) => {
      const card = document.createElement("div");
      card.className = "ytm-album-card";
      card.innerHTML = `
        <div class="ytm-album-art">
          <img src="${item.artworkUrl}" alt="${item.title}">
          <div class="ytm-album-play-overlay">
            <span class="material-symbols-rounded">play_arrow</span>
          </div>
        </div>
        <div class="ytm-album-title">${item.title}</div>
        <div class="ytm-album-sub">${item.artistOrChannel}</div>
      `;
      card.addEventListener("click", () => {
        state.queue = [...MUSIC_CATALOG];
        state.currentTrackIndex = idx;
        this.setPlaybackMode("SONG");
        this.loadTrack(item, true);
        this.showMasterPlayer(true);
      });
      elements.ytmListenAgainRow.appendChild(card);
    });

    // 2. Quick Picks 4-Row Grid
    elements.ytmQuickPicksList.innerHTML = "";
    MUSIC_CATALOG.forEach((item, idx) => {
      const itemEl = document.createElement("div");
      itemEl.className = "quick-pick-item";
      itemEl.innerHTML = `
        <img src="${item.artworkUrl}" alt="${item.title}" class="quick-pick-thumb">
        <div class="quick-pick-info">
          <div class="quick-pick-title">${item.title}</div>
          <div class="quick-pick-meta">${item.artistOrChannel} • ${item.plays} plays</div>
        </div>
        <div class="quick-pick-action">
          <span class="material-symbols-rounded">play_arrow</span>
        </div>
      `;
      itemEl.addEventListener("click", () => {
        state.queue = [...MUSIC_CATALOG];
        state.currentTrackIndex = idx;
        this.setPlaybackMode("SONG");
        this.loadTrack(item, true);
        this.showMasterPlayer(true);
      });
      elements.ytmQuickPicksList.appendChild(itemEl);
    });

    // 3. Mixed for You
    elements.ytmMixedForYouRow.innerHTML = "";
    const mixes = [
      { title: "My Supermix", sub: "Aetheria, Neon Vanguard, Solstice Duo", img: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&q=80" },
      { title: "Synthwave Mix", sub: "Hyperion Protocol, Kroma", img: "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=400&q=80" },
      { title: "Focus Deep State", sub: "Ambient flows without words", img: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400&q=80" }
    ];
    mixes.forEach((m, idx) => {
      const card = document.createElement("div");
      card.className = "ytm-album-card";
      card.innerHTML = `
        <div class="ytm-album-art">
          <img src="${m.img}" alt="${m.title}">
          <div class="ytm-album-play-overlay">
            <span class="material-symbols-rounded">play_arrow</span>
          </div>
        </div>
        <div class="ytm-album-title">${m.title}</div>
        <div class="ytm-album-sub">${m.sub}</div>
      `;
      card.addEventListener("click", () => {
        state.queue = [...MUSIC_CATALOG];
        state.currentTrackIndex = idx % MUSIC_CATALOG.length;
        this.setPlaybackMode("SONG");
        this.loadTrack(state.queue[state.currentTrackIndex], true);
        this.showMasterPlayer(true);
      });
      elements.ytmMixedForYouRow.appendChild(card);
    });

    // 4. Forgotten Favorites
    elements.ytmFavoritesRow.innerHTML = elements.ytmListenAgainRow.innerHTML;
  }

  // =================================================================
  // YOUTUBE FEED RENDERING (STRICTLY NO ADS, NO PLAYABLES, NO SHORTS)
  // =================================================================
  renderYouTubeFeed() {
    elements.ytHomeFeedList.innerHTML = "";
    YOUTUBE_VIDEOS.forEach((vid) => {
      const card = document.createElement("div");
      card.className = "yt-video-card";
      card.innerHTML = `
        <div class="yt-thumb-wrapper">
          <img src="${vid.thumbUrl}" alt="${vid.title}" loading="lazy">
          <div class="yt-duration-badge">${vid.duration}</div>
        </div>
        <div class="yt-video-details">
          <img src="${vid.channelAvatar}" alt="${vid.channel}" class="yt-channel-avatar">
          <div class="yt-meta-col">
            <h3 class="yt-video-title">${vid.title}</h3>
            <div class="yt-video-stats">${vid.channel} • ${vid.views} • ${vid.timeAgo}</div>
          </div>
          <div class="yt-more-dots">
            <span class="material-symbols-rounded">more_vert</span>
          </div>
        </div>
      `;

      card.addEventListener("click", () => {
        // Convert to playback model
        const mediaModel = {
          id: vid.id,
          title: vid.title,
          artistOrChannel: vid.channel,
          audioUrl: vid.videoUrl,
          videoUrl: vid.videoUrl,
          artworkUrl: vid.thumbUrl,
          durationMs: 180000,
          dominantColor: "#0984e3",
          secondaryColor: "#00cec9"
        };
        state.queue = [mediaModel, ...MUSIC_CATALOG];
        state.currentTrackIndex = 0;
        this.setPlaybackMode("VIDEO");
        this.loadTrack(mediaModel, true);
        this.showMasterPlayer(true);
      });

      elements.ytHomeFeedList.appendChild(card);
    });
  }

  // =================================================================
  // YOUTUBE SHORTS FEED (DEDICATED REEL)
  // =================================================================
  renderShortsFeed() {
    elements.shortsContainer.innerHTML = "";
    YOUTUBE_SHORTS.forEach((short) => {
      const card = document.createElement("div");
      card.className = "short-reel-card";
      card.innerHTML = `
        <video src="${short.videoUrl}" loop playsinline class="short-video-el" preload="auto"></video>
        <div class="short-overlay-bottom">
          <div class="short-channel-row">
            <img src="${short.channelAvatar}" alt="${short.channel}">
            <span class="short-channel-name">@${short.channel}</span>
            <button class="short-sub-btn">Subscribe</button>
          </div>
          <p class="short-caption">${short.title}</p>
        </div>
        <div class="short-actions-sidebar">
          <button class="short-action-btn">
            <div class="short-action-circle">
              <span class="material-symbols-rounded">thumb_up</span>
            </div>
            <span class="short-action-count">${short.likes}</span>
          </button>
          <button class="short-action-btn">
            <div class="short-action-circle">
              <span class="material-symbols-rounded">thumb_down</span>
            </div>
            <span class="short-action-count">Dislike</span>
          </button>
          <button class="short-action-btn">
            <div class="short-action-circle">
              <span class="material-symbols-rounded">comment</span>
            </div>
            <span class="short-action-count">${short.comments}</span>
          </button>
          <button class="short-action-btn">
            <div class="short-action-circle">
              <span class="material-symbols-rounded">share</span>
            </div>
            <span class="short-action-count">Share</span>
          </button>
          <button class="short-action-btn">
            <div class="short-action-circle">
              <span class="material-symbols-rounded">loop</span>
            </div>
            <span class="short-action-count">Remix</span>
          </button>
        </div>
      `;

      // Autoplay on view / click
      const videoEl = card.querySelector("video");
      card.addEventListener("click", () => {
        if (videoEl.paused) {
          videoEl.play();
        } else {
          videoEl.pause();
        }
      });

      elements.shortsContainer.appendChild(card);
    });
  }

  // =================================================================
  // YOUTUBE SUBSCRIPTIONS & ACCOUNT RENDERING
  // =================================================================
  renderSubscriptionsView() {
    elements.subsChannelsBar.innerHTML = "";
    SUBSCRIPTIONS.forEach((sub) => {
      const pill = document.createElement("div");
      pill.className = "sub-channel-pill";
      pill.innerHTML = `
        <div class="sub-avatar-wrap">
          <img src="${sub.avatar}" alt="${sub.name}">
          ${sub.unread ? '<div class="sub-unread-dot"></div>' : ""}
        </div>
        <span class="sub-channel-name">${sub.name}</span>
      `;
      elements.subsChannelsBar.appendChild(pill);
    });

    elements.subsFeedList.innerHTML = elements.ytHomeFeedList.innerHTML;
  }

  renderYouView() {
    elements.youHistoryRow.innerHTML = "";
    YOUTUBE_VIDEOS.slice(0, 3).forEach((vid, i) => {
      const card = document.createElement("div");
      card.className = "you-history-card";
      card.innerHTML = `
        <div class="you-hist-thumb">
          <img src="${vid.thumbUrl}" alt="${vid.title}">
          <div class="you-hist-progress" style="width: ${50 + i * 20}%"></div>
        </div>
        <div class="you-hist-title">${vid.title}</div>
        <div class="you-hist-author">${vid.channel}</div>
      `;
      elements.youHistoryRow.appendChild(card);
    });

    elements.youPlaylistsRow.innerHTML = `
      <div class="you-history-card">
        <div class="you-hist-thumb">
          <img src="https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400&q=80" alt="Liked">
        </div>
        <div class="you-hist-title">Liked Videos</div>
        <div class="you-hist-author">142 videos • Private</div>
      </div>
      <div class="you-history-card">
        <div class="you-hist-thumb">
          <img src="https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&q=80" alt="Watch Later">
        </div>
        <div class="you-hist-title">Watch Later</div>
        <div class="you-hist-author">28 videos • Private</div>
      </div>
    `;
  }

  // =================================================================
  // SEARCH HANDLER (Allows finding songs, videos, and shorts)
  // =================================================================
  toggleSearchOverlay() {
    const isHidden = elements.searchContainer.classList.contains("hidden");
    elements.searchContainer.classList.toggle("hidden", !isHidden);
    elements.searchResultsSection.classList.toggle("hidden", isHidden);
    if (isHidden) {
      elements.searchInput.focus();
    } else {
      elements.searchInput.value = "";
      this.handleSearch("");
    }
  }

  handleSearch(query) {
    if (!query.trim()) {
      elements.searchResultsSection.classList.add("hidden");
      return;
    }
    elements.searchResultsSection.classList.remove("hidden");
    elements.searchResultsList.innerHTML = "";

    const q = query.toLowerCase();
    const songMatches = MUSIC_CATALOG.filter((s) => s.title.toLowerCase().includes(q) || s.artistOrChannel.toLowerCase().includes(q));
    const videoMatches = YOUTUBE_VIDEOS.filter((v) => v.title.toLowerCase().includes(q) || v.channel.toLowerCase().includes(q));
    const shortsMatches = YOUTUBE_SHORTS.filter((sh) => sh.title.toLowerCase().includes(q) || sh.channel.toLowerCase().includes(q));

    if (songMatches.length === 0 && videoMatches.length === 0 && shortsMatches.length === 0) {
      elements.searchResultsList.innerHTML = '<p style="text-align: center; color: rgba(255,255,255,0.4); padding: 20px;">No matching results</p>';
      return;
    }

    // Render matches
    songMatches.forEach((song) => {
      const row = document.createElement("div");
      row.className = "quick-pick-item";
      row.innerHTML = `
        <img src="${song.artworkUrl}" class="quick-pick-thumb" alt="${song.title}">
        <div class="quick-pick-info">
          <div class="quick-pick-title">${song.title}</div>
          <div class="quick-pick-meta">Song • ${song.artistOrChannel}</div>
        </div>
        <span class="material-symbols-rounded">play_arrow</span>
      `;
      row.addEventListener("click", () => {
        state.queue = [song, ...MUSIC_CATALOG];
        state.currentTrackIndex = 0;
        this.setPlaybackMode("SONG");
        this.loadTrack(song, true);
        this.showMasterPlayer(true);
      });
      elements.searchResultsList.appendChild(row);
    });

    videoMatches.forEach((vid) => {
      const row = document.createElement("div");
      row.className = "quick-pick-item";
      row.innerHTML = `
        <img src="${vid.thumbUrl}" class="quick-pick-thumb" alt="${vid.title}">
        <div class="quick-pick-info">
          <div class="quick-pick-title">${vid.title}</div>
          <div class="quick-pick-meta">Video • ${vid.channel}</div>
        </div>
        <span class="material-symbols-rounded">play_circle</span>
      `;
      row.addEventListener("click", () => {
        const item = {
          id: vid.id,
          title: vid.title,
          artistOrChannel: vid.channel,
          audioUrl: vid.videoUrl,
          videoUrl: vid.videoUrl,
          artworkUrl: vid.thumbUrl,
          durationMs: 180000,
          dominantColor: "#0984e3",
          secondaryColor: "#00cec9"
        };
        state.queue = [item, ...MUSIC_CATALOG];
        state.currentTrackIndex = 0;
        this.setPlaybackMode("VIDEO");
        this.loadTrack(item, true);
        this.showMasterPlayer(true);
      });
      elements.searchResultsList.appendChild(row);
    });
  }

  // =================================================================
  // PLAYBACK SYSTEM
  // =================================================================
  loadTrack(track, autoplay = true) {
    this.currentTrack = track;
    this.meshEngine.setColors(track.dominantColor || "#6c5ce7", track.secondaryColor || "#ff7675");

    elements.playerTitle.textContent = track.title;
    elements.playerArtist.textContent = track.artistOrChannel;
    elements.miniTitle.textContent = track.title;
    elements.miniArtist.textContent = track.artistOrChannel;
    elements.totalDuration.textContent = formatDuration(track.durationMs);

    if (track.artworkUrl) {
      elements.playerArtwork.src = track.artworkUrl;
      elements.miniThumb.src = track.artworkUrl;
      elements.miniThumb.style.display = "block";
    }

    elements.mediaAudio.src = track.audioUrl;
    elements.mediaVideo.src = track.videoUrl;

    elements.sliderProgress.style.width = "0%";
    elements.sliderThumb.style.left = "0%";
    elements.currentTime.textContent = "00:00";

    this.renderLyrics(track.lyrics || []);
    this.renderQueue();

    elements.miniPlayer.classList.remove("hidden");

    if (autoplay) {
      this.play();
    } else {
      this.pause();
    }
  }

  play() {
    state.isPlaying = true;
    const media = this.getCurrentMedia();
    media.play().catch(() => {});
    animateMorphButton(true);
    elements.vinylDisc.classList.add("spinning");
    elements.vinylDisc.classList.remove("paused");
  }

  pause() {
    state.isPlaying = false;
    elements.mediaAudio.pause();
    elements.mediaVideo.pause();
    animateMorphButton(false);
    elements.vinylDisc.classList.add("paused");
  }

  togglePlayPause() {
    if (state.isPlaying) {
      this.pause();
    } else {
      this.play();
    }
  }

  skipNext() {
    if (state.queue.length === 0) return;
    let nextIdx;
    if (state.shuffle) {
      nextIdx = Math.floor(Math.random() * state.queue.length);
    } else {
      nextIdx = (state.currentTrackIndex + 1) % state.queue.length;
    }
    state.currentTrackIndex = nextIdx;
    this.loadTrack(state.queue[nextIdx], true);
  }

  skipPrev() {
    const media = this.getCurrentMedia();
    if (media.currentTime > 3) {
      media.currentTime = 0;
      return;
    }
    if (state.queue.length === 0) return;
    const prevIdx = (state.currentTrackIndex - 1 + state.queue.length) % state.queue.length;
    state.currentTrackIndex = prevIdx;
    this.loadTrack(state.queue[prevIdx], true);
  }

  toggleShuffle() {
    state.shuffle = !state.shuffle;
    elements.shuffleBtn.classList.toggle("active", state.shuffle);
  }

  toggleRepeat() {
    const modes = ["OFF", "ALL", "ONE"];
    const nextIdx = (modes.indexOf(state.repeatMode) + 1) % modes.length;
    state.repeatMode = modes[nextIdx];
    elements.repeatBtn.classList.toggle("active", state.repeatMode !== "OFF");
    elements.repeatIcon.textContent = state.repeatMode === "ONE" ? "repeat_one" : "repeat";
  }

  onTrackEnded() {
    if (state.repeatMode === "ONE") {
      const media = this.getCurrentMedia();
      media.currentTime = 0;
      media.play();
    } else {
      this.skipNext();
    }
  }

  setPlaybackMode(mode) {
    if (state.playbackMode === mode) return;
    const currentMedia = this.getCurrentMedia();
    const currentPos = currentMedia.currentTime;
    const wasPlaying = state.isPlaying;

    state.playbackMode = mode;

    if (mode === "SONG") {
      elements.capsuleIndicator.style.transform = "translateX(0%)";
      elements.modeSongBtn.classList.add("active");
      elements.modeVideoBtn.classList.remove("active");
      elements.vinylContainer.classList.add("active");
      elements.videoContainer.classList.remove("active");
      elements.lyricsContainer.classList.remove("active");
      state.showLyrics = false;
      elements.lyricsToggleBtn.classList.remove("active");

      elements.mediaVideo.pause();
      elements.mediaAudio.currentTime = currentPos;
      if (wasPlaying) elements.mediaAudio.play();
    } else {
      elements.capsuleIndicator.style.transform = "translateX(100%)";
      elements.modeVideoBtn.classList.add("active");
      elements.modeSongBtn.classList.remove("active");
      elements.videoContainer.classList.add("active");
      elements.vinylContainer.classList.remove("active");
      elements.lyricsContainer.classList.remove("active");
      state.showLyrics = false;
      elements.lyricsToggleBtn.classList.remove("active");

      elements.mediaAudio.pause();
      elements.mediaVideo.currentTime = currentPos;
      if (wasPlaying) elements.mediaVideo.play();
    }
  }

  toggleLyrics() {
    state.showLyrics = !state.showLyrics;
    elements.lyricsToggleBtn.classList.toggle("active", state.showLyrics);

    if (state.showLyrics) {
      elements.lyricsContainer.classList.add("active");
      elements.vinylContainer.classList.remove("active");
      elements.videoContainer.classList.remove("active");
    } else {
      elements.lyricsContainer.classList.remove("active");
      if (state.playbackMode === "VIDEO") {
        elements.videoContainer.classList.add("active");
      } else {
        elements.vinylContainer.classList.add("active");
      }
    }
  }

  toggleQueueDrawer(show) {
    const isVisible = show !== undefined ? show : elements.queueDrawer.classList.contains("hidden");
    elements.queueDrawer.classList.toggle("hidden", !isVisible);
  }

  onTimeUpdate(media) {
    if (state.isScrubbing) return;
    const currentMs = media.currentTime * 1000;
    const durMs = media.duration && !isNaN(media.duration) ? media.duration * 1000 : this.currentTrack.durationMs;

    const progressFrac = Math.min(1, Math.max(0, currentMs / durMs));
    elements.sliderProgress.style.width = `${progressFrac * 100}%`;
    elements.sliderThumb.style.left = `${progressFrac * 100}%`;
    elements.currentTime.textContent = formatDuration(currentMs);

    this.updateLyricsHighlight(currentMs);
  }

  onBufferProgress(media) {
    if (media.buffered && media.buffered.length > 0) {
      const end = media.buffered.end(media.buffered.length - 1);
      const dur = media.duration || 1;
      elements.sliderBuffer.style.width = `${(end / dur) * 100}%`;
    }
  }

  setupScrubber() {
    const track = elements.sliderTrack;
    const seek = (e) => {
      const rect = track.getBoundingClientRect();
      const clickX = e.clientX || (e.touches && e.touches[0].clientX);
      const frac = Math.min(1, Math.max(0, (clickX - rect.left) / rect.width));

      elements.sliderProgress.style.width = `${frac * 100}%`;
      elements.sliderThumb.style.left = `${frac * 100}%`;

      const media = this.getCurrentMedia();
      const dur = media.duration && !isNaN(media.duration) ? media.duration : this.currentTrack.durationMs / 1000;
      media.currentTime = frac * dur;
    };

    track.addEventListener("mousedown", (e) => {
      state.isScrubbing = true;
      seek(e);
      const onMove = (me) => seek(me);
      const onUp = () => {
        state.isScrubbing = false;
        window.removeEventListener("mousemove", onMove);
        window.removeEventListener("mouseup", onUp);
      };
      window.addEventListener("mousemove", onMove);
      window.addEventListener("mouseup", onUp);
    });

    track.addEventListener("touchstart", (e) => {
      state.isScrubbing = true;
      seek(e);
    }, { passive: true });
    track.addEventListener("touchmove", (e) => {
      if (state.isScrubbing) seek(e);
    }, { passive: true });
    track.addEventListener("touchend", () => {
      state.isScrubbing = false;
    });
  }

  renderLyrics(lyrics) {
    elements.lyricsScroller.innerHTML = "";
    if (!lyrics || lyrics.length === 0) {
      elements.lyricsScroller.innerHTML = '<p class="lyric-line" style="text-align: center; color: rgba(255,255,255,0.4);">No synced lyrics available</p>';
      return;
    }

    lyrics.forEach((line, lineIdx) => {
      const lineDiv = document.createElement("div");
      lineDiv.className = "lyric-line";
      lineDiv.dataset.index = lineIdx;
      lineDiv.dataset.start = line.startTimeMs;
      lineDiv.dataset.end = line.endTimeMs;

      if (line.words && line.words.length > 0) {
        line.words.forEach((w) => {
          const span = document.createElement("span");
          span.className = "karaoke-word";
          span.textContent = w.word + " ";
          span.dataset.start = w.start;
          span.dataset.end = w.end;
          lineDiv.appendChild(span);
        });
      } else {
        lineDiv.textContent = line.text;
      }

      lineDiv.addEventListener("click", () => {
        const media = this.getCurrentMedia();
        media.currentTime = line.startTimeMs / 1000;
      });

      elements.lyricsScroller.appendChild(lineDiv);
    });
  }

  updateLyricsHighlight(currentMs) {
    const lines = elements.lyricsScroller.querySelectorAll(".lyric-line");
    let activeLine = null;

    lines.forEach((lineEl) => {
      const start = Number(lineEl.dataset.start);
      const end = Number(lineEl.dataset.end);

      if (currentMs >= start && currentMs <= end + 800) {
        lineEl.classList.add("active");
        lineEl.classList.remove("past");
        activeLine = lineEl;

        const words = lineEl.querySelectorAll(".karaoke-word");
        words.forEach((w) => {
          const wStart = Number(w.dataset.start);
          const wEnd = Number(w.dataset.end);
          if (currentMs >= wStart && currentMs <= wEnd) {
            w.classList.add("active");
            w.classList.remove("completed");
          } else if (currentMs > wEnd) {
            w.classList.add("completed");
            w.classList.remove("active");
          } else {
            w.classList.remove("active", "completed");
          }
        });
      } else if (currentMs > end + 800) {
        lineEl.classList.remove("active");
        lineEl.classList.add("past");
      } else {
        lineEl.classList.remove("active", "past");
      }
    });

    if (activeLine && !state.isScrubbing) {
      activeLine.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  }

  renderQueue() {
    elements.queueList.innerHTML = "";
    elements.queueCount.textContent = `${state.queue.length} streams queued`;

    state.queue.forEach((item, idx) => {
      const isCurrent = idx === state.currentTrackIndex;
      const el = document.createElement("div");
      el.className = `queue-item ${isCurrent ? "current" : ""}`;
      el.innerHTML = `
        <img src="${item.artworkUrl}" class="queue-thumb" alt="${item.title}">
        <div class="queue-meta">
          <div class="queue-item-title">${item.title}</div>
          <div class="queue-item-artist">${item.artistOrChannel}</div>
        </div>
        ${isCurrent ? '<span class="queue-badge">PLAYING</span>' : `<span style="font-size: 11px; color: rgba(255,255,255,0.4);">${formatDuration(item.durationMs)}</span>`}
      `;

      el.addEventListener("click", () => {
        state.currentTrackIndex = idx;
        this.loadTrack(item, true);
        this.toggleQueueDrawer(false);
      });

      elements.queueList.appendChild(el);
    });
  }
}

function formatDuration(ms) {
  const totalSeconds = Math.floor(Math.max(0, ms) / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
}

// Bootstrapping
window.addEventListener("DOMContentLoaded", () => {
  new NexusMasterApp();
});
