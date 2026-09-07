// Local-only helper: serves the built SPA and proxies /api to the Floci-emulated
// API Gateway. Floci keys API Gateway routing by Host header, and the emulated
// gateway hostname is not resolvable on this host, so the proxy pins that header.
import { createServer, request } from 'node:http';
import { readFileSync, existsSync, statSync } from 'node:fs';
import { join, extname, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(root, 'frontend', 'dist');
const state = join(root, 'infrastructure', 'terraform.tfstate');
const port = Number(process.env.PORT || 5173);

function gatewayHost() {
  if (process.env.GHOST_GATEWAY) return process.env.GHOST_GATEWAY;
  if (existsSync(state)) {
    try {
      const url = JSON.parse(readFileSync(state, 'utf8')).outputs.api_url.value;
      return new URL(url).hostname;
    } catch { /* fall through */ }
  }
  throw new Error('Gateway host unknown: set GHOST_GATEWAY or run terraform apply first.');
}

const GATEWAY_HOST = gatewayHost();
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };

createServer((req, res) => {
  if (req.url.startsWith('/api/')) {
    const upstream = new URL(req.url, 'http://127.0.0.1');
    const proxy = request({ host: '127.0.0.1', port: 4566, path: upstream.pathname + upstream.search, method: req.method,
      headers: { ...req.headers, host: GATEWAY_HOST, connection: 'close' } }, up => { res.writeHead(up.statusCode, up.headers); up.pipe(res); });
    proxy.on('error', () => { res.writeHead(502); res.end('bad gateway'); });
    req.pipe(proxy);
    return;
  }
  const requested = join(dist, req.url === '/' ? 'index.html' : req.url);
  if (requested.startsWith(dist) && existsSync(requested) && statSync(requested).isFile()) {
    res.writeHead(200, { 'content-type': types[extname(requested)] || 'application/octet-stream' });
    res.end(readFileSync(requested));
  } else {
    res.writeHead(200, { 'content-type': 'text/html' });
    res.end(readFileSync(join(dist, 'index.html')));
  }
}).listen(port, '127.0.0.1', () => console.log(`GhostDrop dev server on http://localhost:${port}`));
