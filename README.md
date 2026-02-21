<p align="center">
  <img src="assets/icon.svg" width="128" height="128" alt="Overboard icon"/>
</p>

<h1 align="center">Overboard</h1>

<p align="center">
  <strong>Transparent overlay keyboard for small-screen Android devices</strong><br/>
  Your apps never resize. Your content stays visible. Your keyboard floats on top.
</p>

---

Overboard renders as a system overlay instead of a standard input method window. Apps don't resize or shift when the keyboard appears — it just layers on top. Designed for phones with square or unusual aspect ratios (like the Unihertz MindOne), but works on any Android device.

## Features

### Overlay Mode
- **Floats over your app** without resizing it — zero layout shift
- **Adjustable transparency** — see your content through the keyboard
- **Text outlines & shadow halos** — labels stay readable over any background
- **Idle fade-out** — keyboard fades after a configurable timeout, snaps back on touch
- **Peek mode** — long-press spacebar to toggle keyboard visibility

### Interaction
- **Collapse button** — tap to dismiss, drag to reposition the keyboard vertically
- **Left/right handedness** — controls which side the collapse button sits on
- **Selection-aware** — keyboard fades or hides when you're selecting text
- **Smart delayed show** — avoids covering long-press menus and selection handles
- **Swipe typing** — slide keys toward corners for extra characters (inherited from Unexpected Keyboard)

### Privacy
- No ads
- No network requests
- No telemetry
- Fully open source (GPL-3.0)

---

## Built on Unexpected Keyboard

Overboard is a fork of [**Unexpected Keyboard**](https://github.com/Julow/Unexpected-Keyboard) by [Music Josun (Julow)](https://github.com/Julow) and its many contributors.

Unexpected Keyboard is an excellent, privacy-respecting Android keyboard with a brilliant swipe-to-corner input method. It was originally designed for programmers using Termux and has grown into a fantastic everyday keyboard. Overboard would not exist without their work.

**If you're on a standard phone and don't need overlay mode, go use [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) directly. It's great.**

Thank you to the entire Unexpected Keyboard community for building such a solid, well-architected project and releasing it under a free license.

---

## Building

```sh
./gradlew assembleDebug
```

See [Contributing](CONTRIBUTING.md) for detailed build instructions.

## License

GPL-3.0 — See [LICENSE](LICENSE) for details.
