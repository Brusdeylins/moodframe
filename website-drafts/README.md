# moodframe.art: notes and drafts

This folder holds the notes, the shot list and the design proposals (`mock/`); the site itself is in
[`../website/`](../website/), whose content goes online unchanged.

The website of MoodFrame: static pages (`index.html`, `imprint.html`, `privacy.html`, `assets/`), no build step, no requests to
third parties. It matches the splash screen: dark petrol-teal stage, the colored cloud, the
glass slab, the wordmark with its gradient (`#ff6b4a` → `#f5b83d` → `#2fb8a6` → `#5b6ee1`), Space Grotesk for
headings, Inter for text, JetBrains Mono for commands (all served from `assets/fonts/`, SIL Open Font License).

## Open locally

```bash
cd website && python3 -m http.server 8000     # then http://localhost:8000, ?lang=de for German
```

## Content

English by default, German via the EN/DE switch (remembered in the browser, or `?lang=de`). The nav follows the
order of the page.

| Chapter | Content |
|---|---|
| Hero | Claim, "Install for <system>" (jumps to Install), "View on GitHub" |
| Overview | The main window with its main capabilities marked; a list below the screenshot on phones |
| Mood | Tabs switch image and recipe of three moods |
| Frame | Aspect ratios over one image; what FLUX.2, Nano Banana Pro and gpt-image make of 16:9 (from `AspectRatios`) |
| Models | The three providers |
| Install | Tab of the detected system open; the commands and files of Conveyor's download page (no link to `/download`), the note that the app is not notarized, "Packaged with Conveyor" |

`assets/js/site.js` holds the texts of both languages, the moods, the system detection and the release lookup
(`/download/metadata.properties`, which Conveyor publishes next to the files: version, file names, link to the
GitHub release `v<version>`). Locally, or without JavaScript, the page shows version 2.0.0.

## Before going live

- [ ] Screenshot of the real app (English UI, demo library, throwaway `user.home`) as `assets/img/app-screenshot.png`;
      the dots in the overview may need moving to match it
- [ ] Mood images: the current ones (1600 px) follow the real blocks of Vintage Film, Quiet Morning and Sci-Fi Cinema
      (generated with Nano Banana Pro, same picture restyled); replace them with images made in MoodFrame if wanted
- [ ] Official provider logos, monochrome white, in place of the names in "Models"
- [ ] GitHub release `v2.0.0`
- [x] Imprint and privacy notice (`imprint.html`, `privacy.html`; German binding, English translation)
- [ ] Confirm the hosting paragraph of the privacy notice (copied from brusdeylins.info: own EU server, no access logs)
- [ ] Upload: the content of `website/` to the root of `https://moodframe.art`, next to `/download`

## Mockups

`mock/` holds the four design proposals as SVG (`mock/index.html` switches between them); D is the one built here.
`mock/app-window.svg` is the drawn stand-in for the screenshot. The images were generated with Nano Banana Pro (hero
from `splash.png`, the lighthouse in three moods).
