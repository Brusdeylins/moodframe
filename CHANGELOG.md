# Changelog

All notable changes to this project will be documented in this file.

## 2.1.0 (2026-11-16)

### FEATURE
- MCP server: AI agents on your computer, like Claude Code, read and change the library and generate images
  (Settings → MCP, local only, with a token); entries made by an agent carry an "MCP" badge
- Camera dialog like a real camera: sliders for focal length, aperture, shutter and ISO, camera types with a fixed
  lens or automatic set them read-only, contradicting settings are locked with the reason
- New lens "Shift (Architecture)" next to "Tilt-Shift (Miniature)"
- New example images in the camera dialog: one scene for every setting, in double resolution
- Linux: MoodFrame notices a new version itself; all systems show "update available" next to the version
- Arch Linux: AUR package moodframe-bin

### IMPROVEMENT
- Camera texts rewritten and checked against photography, so every setting shows in the image; edits of a
  reference image get their own lead sentence
- Right panel: one group open at a time, each with a summary and a colored line; the panel is wider so the lists fit
- Generation group: folded it shows count, model and attachments; the model and its parameters come first
- Camera dialog: the stage is called "Lens", the example image shows only while you point at or change a value, the
  preview follows aperture and tilt-shift
- Theme: dark when the system reports nothing; light and dark detected on Linux
- Video: when JavaFX cannot play a clip, MoodFrame offers the system player and says what to install
- JavaFX 23: videos also play with ffmpeg 7

### FIX
- FLUX images show their price instead of $0; older FLUX entries are filled in once with the list price
- Dialog buttons follow the language of the app instead of the system's
- The edit and lock buttons of a building block no longer stay visible after a click
- `preview_generation` shows the prompt with the lead sentence for reference images; a failed generation reports
  its error

## 2.0.1 (2026-11-14)

### IMPROVEMENT
- The groups in the right panel keep a gap to its scroll bar

### FIX
- A successful test of the BFL key now enables BFL, so its models appear without a restart
- FLUX: the safety tolerance defaults to 2; if the BFL account allows less than requested, the generation is retried
  with the approved maximum instead of failing

## 2.0.0 (2026-11-14)

### FEATURE
- First public version of MoodFrame
- Moods: style, atmosphere, light, color palette and camera as building blocks, each lockable, saved as named moods,
  with 17 base moods
- Camera building block with a visual settings dialog and an example image for every stage; only stages with a
  visible effect remain
- Texts of the base building blocks tuned against test images
- Mood from image: colors, atmosphere, style, light and camera derived from a picture
- Camera also in chat entries, in Brainstorm and in "Mood from image"
- Frames: target aspect ratios and resolutions saved as named frames, with 7 base frames; every model takes its
  closest format and resolution
- Right panel in foldable groups Mood, Frame and Generation; the chat and mood list shows the cloud through
- Each chat entry remembers its mood and frame; "Apply mood" from an entry
- Sidebar shows chats, moods or frames; a mood or frame lists all its images across chats
- Brainstorm knows moods and blocks, builds the setting and defines several images per chat in rounds
- One trash for chats, images, building blocks, moods and frames
- Favorites: a star in the lists of moods, frames and building blocks puts an entry on top, also by right-click
- Chat entries with badges for mood, frame and model, image actions and image size on hover
- Images view: all images of a chat as a grid with a size slider, sharp thumbnails on Retina screens
- "Use mood" takes the stored mood of an own image, without an image analysis
- Chats and brainstorms sorted by drag and drop
- YouTube videos in Brainstorm, formatted answers in the Brainstorm chat
- Base frames with English names, like moods and building blocks
- New app icon after Apple's macOS template
- Installers from https://moodframe.art/download, updates are checked on every launch
