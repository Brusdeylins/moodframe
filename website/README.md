# moodframe.art

The website of MoodFrame. It matches the splash screen: dark petrol-teal stage, the colored cloud, the glass slab,
the wordmark with its mood gradient (`#ff6b4a` → `#f5b83d` → `#2fb8a6` → `#5b6ee1`), Space Grotesk for headings.

## Content

English by default, German via an EN/DE switch.

- Hero with claim and an "Install" button that jumps to the install section at the end
- Screenshot of the app with a demo library (no real chat names)
- Mood and Frame in detail
- Logos of the supported image APIs: Black Forest Labs, Google Gemini, OpenAI
- Link to the GitHub project with the version of the latest release
- Install section at the end: the tab of the detected system open, with the commands of Conveyor's download page (no link to `/download`), the note that the app is not notarized, and "Packaged with Conveyor"
- Nav in page order: Overview, Mood, Frame, Models, Install

## Mockups

`mock/` holds four design proposals as SVG; open `mock/index.html` in a browser to switch between them.

| File | Proposal |
|---|---|
| `mock/a-glass-stage.svg` | The splash scene as a wide hero, the app on the same reflective floor |
| `mock/b-splash-column.svg` | The splash image itself in the middle of the hero, an editorial page below |
| `mock/c-studio-anatomy.svg` | The annotated app as hero, the corner cloud of the app as background |
| `mock/d-glass-stage.svg` | A with the mood switcher of C and the install command of the detected system (chosen direction) |

`mock/app-window.svg` is a drawn stand-in for the screenshot (`mock/images/app-screenshot.png`). The images in
`mock/images/` were generated with Nano Banana Pro (hero from `splash.png`, the lighthouse in three moods).
