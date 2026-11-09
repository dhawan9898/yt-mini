import { defineConfig } from 'vite';

export default defineConfig({
  root: 'web',
  server: {
    host: '0.0.0.0',
    port: 3000,
  },
  preview: {
    host: '0.0.0.0',
    port: 3000,
  },
  plugins: [
    {
      name: 'nexus-api-proxy',
      configureServer(server) {
        server.middlewares.use(async (req, res, next) => {
          if (req.url?.startsWith('/api/search')) {
            try {
              const parsed = new URL(req.url, 'http://localhost:3000');
              const q = parsed.searchParams.get('q') || '';
              if (!q.trim()) {
                res.setHeader('Content-Type', 'application/json');
                res.end(JSON.stringify({ query: '', suggestions: [] }));
                return;
              }
              const suggestUrl = `https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${encodeURIComponent(q)}`;
              const resp = await fetch(suggestUrl, {
                headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
                signal: AbortSignal.timeout(3000)
              });
              if (resp.ok) {
                const data = (await resp.json()) as [string, string[]];
                const suggestions = Array.isArray(data[1]) ? data[1].slice(0, 8) : [];
                res.setHeader('Content-Type', 'application/json');
                res.end(JSON.stringify({ query: q, suggestions }));
                return;
              }
            } catch (_) {}
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ query: '', suggestions: [] }));
            return;
          }
          if (req.url === '/api/status') {
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ status: 'ok', service: 'NexusStream Unified Web API', version: '2.2.0' }));
            return;
          }
          next();
        });
      }
    }
  ]
});

