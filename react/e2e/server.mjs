import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join } from "node:path";
import { fileURLToPath } from "node:url";

/**
 * The smallest static server the e2e needs: the fixture page, the bundled demo
 * app, and the stub widget served at /widget.js (the path the SDK injects). No
 * dependencies, so it starts in milliseconds under Playwright's webServer.
 */
const port = Number(process.argv[2] ?? 3111);
const dir = fileURLToPath(new URL("./", import.meta.url));

const ROUTES = {
  "/": "fixtures/index.html",
  "/app.js": ".artifacts/app.js",
  "/widget.js": "fixtures/widget-stub.js",
};

const CONTENT_TYPE = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
};

createServer(async (req, res) => {
  const path = ROUTES[(req.url ?? "/").split("?")[0]];
  if (!path) {
    res.writeHead(404).end("not found");
    return;
  }
  try {
    const body = await readFile(join(dir, path));
    res.writeHead(200, { "content-type": CONTENT_TYPE[extname(path)] ?? "application/octet-stream" });
    res.end(body);
  } catch {
    res.writeHead(500).end("build the package first (npm run build)");
  }
}).listen(port, () => console.log(`e2e fixtures on http://localhost:${port}`));
