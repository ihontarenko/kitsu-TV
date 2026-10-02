/*
  Kitsu TV — the remote control, in a browser.

  The mock-ups are driven exactly as a television is: the arrow keys are the
  D-pad, Enter is OK, Backspace or Escape is BACK. There is no pointer to fall
  back on, so this file is the one thing every screen shares —

  - spatial focus: from the element in hand, the arrow picks the nearest
    candidate in that direction, by geometry rather than by DOM order;
  - a row remembers which of its tiles was last in hand, so coming back to it
    from the row above lands where you left, the way every television does;
  - a focused row is brought to the same lane on the screen, and a focused
    tile is brought to the gutter, so the eye never has to chase the cursor;
  - a dialog, while open, is the whole world: candidates outside it do not
    exist, and BACK closes it before it does anything else;
  - the icon set is one sprite, injected once, so a page writes
    <svg class="tv-icon"><use href="#i-play"/></svg> and nothing else.

  The mock-ups also switch between their states (a code waiting, a code
  admitted; a player playing, paused, ending) with the number keys 1–4, which a
  remote does not have. That is for review, and it is the only thing here a
  television would not do.
*/

(function () {
  "use strict";

  const FOCUSABLE = "a[href], button:not(:disabled), [tabindex='0']";

  /* ---------- icons ------------------------------------------------------ */

  const ICONS = {
    "i-play": "<path d='M8 5v14l11-7z' fill='currentColor' stroke='none'/>",
    "i-pause": "<path d='M7 5h4v14H7zM13 5h4v14h-4z' fill='currentColor' stroke='none'/>",
    "i-back10": "<path d='M4 12a8 8 0 1 0 3-6.2'/><path d='M4 4v5h5'/><text x='8.5' y='15.5' font-size='7' font-family='inherit' font-weight='700' fill='currentColor' stroke='none'>10</text>",
    "i-forward10": "<path d='M20 12a8 8 0 1 1-3-6.2'/><path d='M20 4v5h-5'/><text x='8.5' y='15.5' font-size='7' font-family='inherit' font-weight='700' fill='currentColor' stroke='none'>10</text>",
    "i-next": "<path d='M5 5l10 7-10 7z' fill='currentColor' stroke='none'/><path d='M18 5v14'/>",
    "i-audio": "<path d='M4 10v4h4l5 4V6L8 10z'/><path d='M16 9a4 4 0 0 1 0 6'/><path d='M18.5 6.5a8 8 0 0 1 0 11'/>",
    "i-subtitles": "<rect x='3' y='5' width='18' height='14'/><path d='M7 12h4M13 12h4M7 15.5h7'/>",
    "i-search": "<circle cx='11' cy='11' r='6'/><path d='M20 20l-4.5-4.5'/>",
    "i-home": "<path d='M4 11l8-7 8 7'/><path d='M6 10v10h12V10'/>",
    "i-film": "<rect x='3' y='4' width='18' height='16'/><path d='M3 9h18M3 15h18M8 4v16M16 4v16'/>",
    "i-series": "<rect x='3' y='6' width='18' height='12'/><path d='M8 21h8M12 18v3'/>",
    "i-heart": "<path d='M12 20s-7-4.6-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.4-7 10-7 10z'/>",
    "i-heart-filled": "<path d='M12 20s-7-4.6-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.4-7 10-7 10z' fill='currentColor'/>",
    "i-check": "<path d='M5 12l4 4L19 7'/>",
    "i-lock": "<rect x='5' y='11' width='14' height='9'/><path d='M8 11V8a4 4 0 0 1 8 0v3'/>",
    "i-server": "<rect x='3' y='4' width='18' height='6'/><rect x='3' y='14' width='18' height='6'/><path d='M7 7h.01M7 17h.01'/>",
    "i-signal": "<path d='M4 18h.01'/><path d='M4 13a5 5 0 0 1 5 5'/><path d='M4 8a10 10 0 0 1 10 10'/><path d='M4 3a15 15 0 0 1 15 15'/>",
    "i-keyboard": "<rect x='3' y='6' width='18' height='12'/><path d='M7 10h.01M11 10h.01M15 10h.01M7 14h10'/>",
    "i-arrow-right": "<path d='M5 12h14M13 6l6 6-6 6'/>",
    "i-arrow-left": "<path d='M19 12H5M11 6l-6 6 6 6'/>",
    "i-backspace": "<path d='M9 5h12v14H9l-6-7z'/><path d='M13 10l4 4M17 10l-4 4'/>",
    "i-globe": "<circle cx='12' cy='12' r='9'/><path d='M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18'/>",
    "i-info": "<circle cx='12' cy='12' r='9'/><path d='M12 11v5M12 8h.01'/>",
    "i-clock": "<circle cx='12' cy='12' r='9'/><path d='M12 7v5l3 2'/>",
    "i-settings": "<circle cx='12' cy='12' r='3'/><path d='M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1'/>",
    "i-close": "<path d='M6 6l12 12M18 6L6 18'/>",
    "i-warning": "<path d='M12 4l9 16H3z'/><path d='M12 10v4M12 17h.01'/>",
  };

  function injectIcons() {
    const sprite = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    sprite.setAttribute("aria-hidden", "true");
    sprite.style.display = "none";
    sprite.innerHTML = Object.entries(ICONS)
      .map(([id, body]) => `<symbol id="${id}" viewBox="0 0 24 24">${body}</symbol>`)
      .join("");
    document.body.prepend(sprite);
  }

  /* ---------- geometry --------------------------------------------------- */

  function isVisible(element) {
    if (element.closest("[hidden]") || element.closest("[aria-hidden='true']")) {
      return false;
    }
    const rectangle = element.getBoundingClientRect();
    return rectangle.width > 0 && rectangle.height > 0;
  }

  function focusScope() {
    const openDialog = document.querySelector("dialog[open]");
    return openDialog || document;
  }

  function candidates() {
    return Array.from(focusScope().querySelectorAll(FOCUSABLE)).filter(isVisible);
  }

  function centre(rectangle) {
    return { x: rectangle.left + rectangle.width / 2, y: rectangle.top + rectangle.height / 2 };
  }

  /* The nearest candidate in a direction. A candidate has to lie beyond the
     current element's edge on that axis; among those, the one with the least
     distance along the axis wins, with the sideways offset counted twice so a
     tile straight ahead beats a nearer tile off to one side. */
  function nearest(current, direction) {
    const from = current.getBoundingClientRect();
    const origin = centre(from);
    let best = null;
    let bestScore = Infinity;

    for (const element of candidates()) {
      if (element === current) {
        continue;
      }
      const to = element.getBoundingClientRect();
      const target = centre(to);
      let along;
      let across;

      switch (direction) {
        case "ArrowLeft":
          along = from.left - to.right;
          across = Math.abs(target.y - origin.y);
          break;
        case "ArrowRight":
          along = to.left - from.right;
          across = Math.abs(target.y - origin.y);
          break;
        case "ArrowUp":
          along = from.top - to.bottom;
          across = Math.abs(target.x - origin.x);
          break;
        case "ArrowDown":
          along = to.top - from.bottom;
          across = Math.abs(target.x - origin.x);
          break;
        default:
          return null;
      }

      /* A little tolerance lets a tile that is only just off the axis count,
         so a lifted (scaled) tile does not exclude its own neighbour. */
      if (along < -8) {
        continue;
      }

      /* Sideways moves stay in the same band of the screen; vertical moves
         prefer a horizontally overlapping candidate. */
      const horizontal = direction === "ArrowLeft" || direction === "ArrowRight";
      const overlaps = horizontal
        ? to.bottom > from.top && to.top < from.bottom
        : to.right > from.left && to.left < from.right;

      const score = Math.max(along, 0) + across * (overlaps ? 0.5 : 2) + (overlaps ? 0 : 400);
      if (score < bestScore) {
        bestScore = score;
        best = element;
      }
    }

    return best;
  }

  /* ---------- rows and lanes --------------------------------------------- */

  function rowOf(element) {
    return element.closest(".tv-row, [data-row]");
  }

  function remember(element) {
    const row = rowOf(element);
    if (row) {
      row.dataset.lastFocus = elementIndex(row, element);
    }
  }

  function elementIndex(row, element) {
    return Array.from(row.querySelectorAll(FOCUSABLE)).indexOf(element);
  }

  /* Where a row is entered when it has no memory yet: the element it names
     as its start, otherwise its first. A row marked `data-row="grid"` (the
     letter keyboard) is entered by geometry instead, straight down or up. */
  function entryPoint(row, geometric) {
    if (row.dataset.row === "grid") {
      return geometric;
    }
    const named = row.querySelector("[data-row-start]");
    if (named && isVisible(named)) {
      return named;
    }
    return Array.from(row.querySelectorAll(FOCUSABLE)).find(isVisible) || geometric;
  }

  function remembered(row) {
    const index = Number(row.dataset.lastFocus);
    if (Number.isNaN(index)) {
      return null;
    }
    return row.querySelectorAll(FOCUSABLE)[index] || null;
  }

  function bringIntoView(element) {
    const remSize = parseFloat(getComputedStyle(document.documentElement).fontSize);
    const gutter = remSize * 6;
    const lane = remSize * 12;
    const row = rowOf(element);

    /* Sideways: the tile in hand comes to the gutter, so the row reads from its
       left edge the way the heading above it does. */
    if (row && row.classList.contains("tv-row")) {
      const rectangle = element.getBoundingClientRect();
      const rowRectangle = row.getBoundingClientRect();
      const left = row.scrollLeft + (rectangle.left - rowRectangle.left) - gutter;
      row.scrollTo({ left: Math.max(0, left), behavior: "smooth" });
    }

    /* Vertically. The deck and the hero live at the top of the page and take it
       back; a shelf lands on the lane; anything else only moves if it is out of
       view. */
    if (element.closest(".tv-deck, .tv-hero")) {
      window.scrollTo({ top: 0 });
      return;
    }

    const shelf = element.closest(".tv-shelf");
    if (shelf) {
      const shelfTop = shelf.getBoundingClientRect().top;
      window.scrollTo({ top: window.scrollY + shelfTop - lane });
      return;
    }

    const rectangle = element.getBoundingClientRect();
    const deck = document.querySelector(".tv-deck");
    const deckHeight = deck ? deck.getBoundingClientRect().height : 0;
    const margin = remSize * 3;
    if (rectangle.top < deckHeight + margin || rectangle.bottom > window.innerHeight - margin) {
      element.scrollIntoView({ block: "center" });
    }
  }

  function moveFocus(direction) {
    const current = document.activeElement && document.activeElement !== document.body
      ? document.activeElement
      : null;

    if (!current) {
      focusFirst();
      return;
    }

    let next = nearest(current, direction);
    if (!next) {
      return;
    }

    /* Entering a row from outside it: land on what that row remembers, or on
       its start — the way a television does, rather than on whichever tile
       happens to be nearest to the cursor's old x. */
    const nextRow = rowOf(next);
    if (nextRow && nextRow !== rowOf(current)) {
      next = remembered(nextRow) || entryPoint(nextRow, next);
    }

    next.focus({ preventScroll: true });
    mirrorFocus();
    remember(next);
    bringIntoView(next);
  }

  function focusFirst() {
    const preferred = focusScope().querySelector("[autofocus]");
    const first = preferred && isVisible(preferred) ? preferred : candidates()[0];
    if (first) {
      first.focus({ preventScroll: true });
      mirrorFocus();
      remember(first);
      bringIntoView(first);
    }
  }

  /* ---------- dialogs ---------------------------------------------------- */

  let focusBeforeDialog = null;

  function openDialog(id) {
    const dialog = document.getElementById(id);
    if (!dialog || dialog.open) {
      return;
    }
    focusBeforeDialog = document.activeElement;
    dialog.showModal();
    focusFirst();
  }

  function closeDialog(dialog) {
    dialog.close();
    if (focusBeforeDialog && typeof focusBeforeDialog.focus === "function") {
      focusBeforeDialog.focus({ preventScroll: true });
      mirrorFocus();
    }
    focusBeforeDialog = null;
  }

  /* ---------- states ----------------------------------------------------- */

  function applyState(holder, state) {
    holder.dataset.state = state;
    for (const part of holder.querySelectorAll("[data-when]")) {
      part.hidden = part.dataset.when !== state;
    }
    focusFirst();
  }

  function applyInitialState() {
    for (const holder of document.querySelectorAll("[data-state]")) {
      applyState(holder, holder.dataset.state);
    }
  }

  function switchState(digit) {
    const holder = document.querySelector("[data-states]");
    if (!holder) {
      return;
    }
    const states = holder.dataset.states.split(/\s+/);
    const state = states[digit - 1];
    if (state) {
      applyState(holder, state);
    }
  }

  /* ---------- keys ------------------------------------------------------- */

  function onKeyDown(event) {
    const { key } = event;

    if (key.startsWith("Arrow")) {
      event.preventDefault();
      moveFocus(key);
      return;
    }

    if (key === "Enter") {
      const current = document.activeElement;
      if (current && current !== document.body) {
        event.preventDefault();
        current.click();
      }
      return;
    }

    if (key === "Backspace" || key === "Escape") {
      const openDialog = document.querySelector("dialog[open]");
      event.preventDefault();
      if (openDialog) {
        closeDialog(openDialog);
        return;
      }
      const back = document.querySelector("[data-back]");
      if (back) {
        window.location.href = back.dataset.back;
      } else {
        window.history.back();
      }
      return;
    }

    if (/^[1-9]$/.test(key)) {
      switchState(Number(key));
    }
  }

  function onClick(event) {
    const opener = event.target.closest("[data-open-dialog]");
    if (opener) {
      event.preventDefault();
      openDialog(opener.dataset.openDialog);
      return;
    }
    const closer = event.target.closest("[data-close-dialog]");
    if (closer) {
      event.preventDefault();
      closeDialog(closer.closest("dialog"));
      return;
    }
    const stateLink = event.target.closest("[data-set-state]");
    if (stateLink) {
      event.preventDefault();
      const holder = document.querySelector("[data-states]");
      if (holder) {
        applyState(holder, stateLink.dataset.setState);
      }
    }
  }

  /* A pointer is still allowed to land the cursor, so a reviewer with a mouse
     sees the same focus a remote would produce. */
  function onPointerOver(event) {
    const target = event.target.closest(FOCUSABLE);
    if (target && isVisible(target) && focusScope().contains(target)) {
      target.focus({ preventScroll: true });
      mirrorFocus();
      remember(target);
    }
  }

  /* A television always owns the focus; a browser window being reviewed does
     not, and `:focus` stops matching the moment it is in the background. The
     class mirrors the focus so a screenshot shows what the room would. */
  function mirrorFocus() {
    const previous = document.querySelector(".is-focused");
    if (previous) {
      previous.classList.remove("is-focused");
    }
    const current = document.activeElement;
    if (current && current !== document.body) {
      current.classList.add("is-focused");
    }
  }

  /* The one hook a page may need: the player switches itself between states. */
  window.KitsuTV = {
    setState(state) {
      const holder = document.querySelector("[data-states]");
      if (holder) {
        applyState(holder, state);
      }
    },
    focusFirst,
  };

  document.addEventListener("DOMContentLoaded", function () {
    injectIcons();
    applyInitialState();
    document.addEventListener("focusin", mirrorFocus);
    document.addEventListener("focusout", mirrorFocus);
    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("click", onClick);
    document.addEventListener("pointerover", onPointerOver);
    focusFirst();
  });
})();
