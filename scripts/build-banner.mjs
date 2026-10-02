/* ==========================================================================
   The Leanback banner, drawn rather than kept.

     node scripts/build-banner.mjs        then open http://127.0.0.1:8765

   ⚠️ `app/src/main/res/drawable-xhdpi/banner.png` IS AN OUTPUT OF THIS FILE. It was a hand-made
   raster before, and it cost exactly what a hand-made raster costs: the mark inside it was the OLD
   folded drawing for weeks after the product stopped using it, and nothing could say so — the
   launcher is the one surface nobody looks at from inside the application.

   ⚠️ IT RUNS IN A BROWSER, AND THAT IS THE WHOLE REASON THIS IS NOT A JAVA PROGRAM. The wordmark is
   the one place in this product where type is used as a picture, and it is Inter at 900 with the
   tracking pulled to −0.055em. Inter ships as ONE variable file; Java2D renders a variable font's
   default instance and has no way to reach the weight axis, so a Java2D banner would either be
   Inter Regular or a synthesised bold — a different set of letterforms from the lockup on the
   splash, on the gate and in the browser. A browser can reach the axis. So this script is a server
   that hands a page the font and the geometry, and writes back the PNG the page draws.

   ⚠️ IT SERVES THE FONT ITSELF rather than borrowing the interface's dev server. The banner has to
   be rebuildable with nothing else running — and a `file://` page cannot load a `file://` font,
   which is the first thing anybody tries.

   ⚠️ THE MARK IS `Html/KitsuUI/logo.svg`'s, READ AT RUN TIME. Not copied into this file: a fourth
   hand-kept copy of the three paths is precisely what every note around this mark warns against.
   ========================================================================== */

import { createServer } from "node:http";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const tvRoot = join(here, "..");

const FONT = join(tvRoot, "app", "src", "main", "res", "font", "inter.ttf");
const LOGO = join(tvRoot, "..", "..", "Html", "KitsuUI", "logo.svg");
const OUTPUT = join(tvRoot, "app", "src", "main", "res", "drawable-xhdpi", "banner.png");

const PORT = 8765;

/**
 * The mark at rest, lifted from the design system's own file.
 *
 * ⚠️ PARSED, NOT TRUSTED TO A SHAPE. It reads the `viewBox` and every `fill`/`d` pair, so a change
 * to the drawing arrives here by itself and a change to the FILE's structure fails loudly instead
 * of drawing something almost right.
 */
function markFromLogo() {
  const svg = readFileSync(LOGO, "utf8");

  const box = svg.match(/viewBox="([\d.\s-]+)"/);
  if (!box) {
    throw new Error(`${LOGO} has no viewBox — the banner cannot place the mark without one.`);
  }
  const [x, y, width, height] = box[1].trim().split(/\s+/).map(Number);

  const segments = [...svg.matchAll(/<path\s+fill="([^"]+)"\s+d="([^"]+)"\s*\/>/g)]
    .map(([, colour, data]) => ({ colour, data }));

  if (segments.length !== 3) {
    throw new Error(`${LOGO} has ${segments.length} paths; the mark is three segments.`);
  }
  return { box: { x, y, width, height }, segments };
}

const mark = markFromLogo();

const page = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Kitsu — Leanback banner</title>
<style>
  @font-face { font-family: "Inter"; font-style: normal; font-weight: 100 900; src: url("/inter.ttf") format("truetype"); }
  body { margin: 0; background: #1b1b1f; color: #ddd; font: 14px system-ui; display: grid; place-items: center; height: 100vh; gap: 16px; }
  canvas { width: 640px; height: 360px; outline: 1px solid #444; }
  #said { font: 13px ui-monospace, monospace; }
</style>
</head>
<body>
  <canvas id="banner" width="640" height="360"></canvas>
  <div id="said">drawing…</div>
<script>
const MARK = ${JSON.stringify(mark)};

/* ⚠️ 320×180 AT xhdpi, which is the only size Leanback states. Drawn at 2× so it survives a
   launcher that scales it up rather than down. */
const WIDTH = 640;
const HEIGHT = 360;

const WORDMARK = "kitsu";
/* ⚠️ THE LOCKUP'S ONE PIECE OF COLOUR. The word is a single ink — splitting *kitsu* would invent a
   seam the name does not have — so the accent lands on a full stop after it instead. The browser and
   the television draw the same thing; this is the launcher's copy of it. */
const STOP = ".";
const STOP_INK = "#ff641b";
const MARK_HEIGHT = 132;
const WORD_SIZE = 96;
/* The tracking the wordmark carries everywhere else — see \`KitsuType.wordmark\`. */
const TRACKING = -0.055;
const GAP = 26;

async function draw() {
  await document.fonts.load(\`900 \${WORD_SIZE}px Inter\`);
  await document.fonts.ready;

  const canvas = document.getElementById("banner");
  const ctx = canvas.getContext("2d");

  /* ⚠️ THE RAIL'S NEAR-BLACK WITH THE SPLASH'S EMBER WASH UNDER THE LOCKUP. The launcher tile and
     the first screen the application paints are the same moment, and they should look like it. */
  ctx.fillStyle = "#171518";
  ctx.fillRect(0, 0, WIDTH, HEIGHT);

  const wash = ctx.createRadialGradient(232, 180, 0, 232, 180, 300);
  wash.addColorStop(0, "rgba(255,100,26,0.16)");
  wash.addColorStop(1, "rgba(255,100,26,0)");
  ctx.fillStyle = wash;
  ctx.fillRect(0, 0, WIDTH, HEIGHT);

  ctx.font = \`900 \${WORD_SIZE}px Inter\`;
  ctx.textBaseline = "alphabetic";
  ctx.letterSpacing = \`\${(TRACKING * WORD_SIZE).toFixed(2)}px\`;

  /* The lockup is measured whole and then centred, so changing either half re-centres both. */
  const word = ctx.measureText(WORDMARK);
  /* ⚠️ MEASURED SEPARATELY AND COUNTED IN. The stop is part of the lockup's width, and leaving it
     out of the sum centres the word and lets the stop hang off the right of centre. */
  const stopWidth = ctx.measureText(STOP).width;
  const markWidth = MARK_HEIGHT * (MARK.box.width / MARK.box.height);
  const left = (WIDTH - (markWidth + GAP + word.width + stopWidth)) / 2;
  const middle = HEIGHT / 2;

  const scale = MARK_HEIGHT / MARK.box.height;
  ctx.save();
  ctx.translate(left - MARK.box.x * scale, middle - MARK_HEIGHT / 2 - MARK.box.y * scale);
  ctx.scale(scale, scale);
  for (const segment of MARK.segments) {
    ctx.fillStyle = segment.colour;
    ctx.fill(new Path2D(segment.data));
  }
  ctx.restore();

  /* ⚠️ CENTRED ON THE CAP HEIGHT, NOT ON THE BASELINE. "kitsu" descends nowhere and ascends in \`k\`
     and \`t\`, so a baseline-centred word sits visibly low beside a symmetrical mark. */
  const wordLeft = left + markWidth + GAP;
  const baseline = middle + word.actualBoundingBoxAscent / 2;
  ctx.fillStyle = "#FFF7F1";
  ctx.fillText(WORDMARK, wordLeft, baseline);
  ctx.fillStyle = STOP_INK;
  ctx.fillText(STOP, wordLeft + word.width, baseline);

  return canvas;
}

draw().then(async (canvas) => {
  const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
  const said = document.getElementById("said");
  const answer = await fetch("/banner", { method: "POST", body: blob });
  said.textContent = \`\${blob.size} bytes → \${await answer.text()}\`;
});
</script>
</body>
</html>`;

createServer((request, answer) => {
  if (request.url === "/inter.ttf") {
    answer.writeHead(200, { "Content-Type": "font/ttf" }).end(readFileSync(FONT));
    return;
  }

  if (request.method === "POST" && request.url === "/banner") {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      const bytes = Buffer.concat(chunks);
      writeFileSync(OUTPUT, bytes);
      console.log(`wrote ${bytes.length} bytes to ${OUTPUT}`);
      answer.writeHead(200, { "Content-Type": "text/plain" }).end(`written to ${OUTPUT}`);
      /* ⚠️ It stops once it has the picture. A generator left listening is a port somebody trips
         over a week later wondering what is holding it. */
      setTimeout(() => process.exit(0), 300);
    });
    return;
  }

  answer.writeHead(200, { "Content-Type": "text/html; charset=utf-8" }).end(page);
}).listen(PORT, "127.0.0.1", () => {
  console.log(`Open http://127.0.0.1:${PORT} — the page draws the banner and this writes it.`);
});
