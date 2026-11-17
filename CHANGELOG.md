# Changelog

All notable changes to this project will be documented in this file.

## 2.1.2 (2026-11-17)

### IMPROVEMENT
- Right panel: the edit pencil of an image unfolds the group Generation with the attached image and folds Mood and
  Frame, like a click on its title; the paperclip, drag and drop and paste leave the groups as they are
- Brainstorm with OpenAI: researches with the built-in web search of the OpenAI Responses API; Settings check the web
  search together with the language model and with every connection test, a language model without web search
  stays chosen for the other chat features (e.g. the mood analysis)
- Every program start checks each account in the background: connection, language model and web search of OpenAI
  and Google, the connection of BFL; the hint of the API key display in the status bar shows the result per account
  with the reason of a failure; a check that fails for the moment (no network, timeout, HTTP 429 or 5xx) keeps the
  last result; a disabled account gets its connection checked only, and with OpenAI as the only provider in use
  Google and BFL are not contacted at the start
- The brainstorm is offered only with an account that has a language model and a web search; the hint of a disabled
  brainstorm button names the reason. After the update, an account is offered as soon as the first program start
  with network has checked it

## 2.1.1 (2026-11-16)

### IMPROVEMENT
- Status bar: the hints on memory and images appear after half a second, stay while the pointer rests on them and
  follow the new values
- Downloaded images and videos are named by their chat and creation time, like a macOS screenshot:
  "Lighthouse series 2026-11-16 17.36.17.png"; umlauts and other letters of the chat name stay, an existing file is
  kept and the copy numbered
- MCP: the new tool `download_image` copies an image or video like the download button, into the Downloads folder or
  another one; `get_image`, `get_entry` and `generate` tell the file name the download button gives each image
- New images in the library are named "moodframe_..." with a readable model name like "nano-banana-2" instead of
  "flux_..."; existing files keep their name
- Large preview: a frame in the colors of the logo, with a soft glow, marks the edges of every image and video, also
  of dark ones; its colors slowly turn around the image (Settings → Appearance switches the motion off)
- Large preview: a dark stage in all themes, also in the light ones, like in photo programs; the app behind it only
  shows faintly instead of distracting from the image
- Large preview: every button lies on a round dark surface, so it shows on white, black and colorful images alike
- Large preview: zoomed in, the image pans up to its edges, where the frame shows that the edge is reached
- Large preview: the buttons at the top keep the same distance from the sides as the arrows and stand right above
  them

### FIX
- Status bar: symbol and text of each display share one hint, which no longer jumps between them and shows in the
  font size of all other hints; the API key display explains its state in its hint
- Light themes: the hints of the buttons in the full view and on the bar over an image show dark text instead of
  white text on a light background
- Large preview: a smaller window fits the image and the video anew, so the buttons no longer cover them; zoomed in,
  the zoom stays
- Large preview: in a small window, also one shrunk from a large size, all buttons lie inside it again instead of
  being cut off at the sides
- Large preview: the frame lies right at the edge of the image and the video, without a dark seam of a pixel in
  between, also zoomed in, panned and on Retina screens
- Large preview: image and video keep the same distance from the buttons on all four sides, fitted and zoomed in;
  panned up to an edge, the image stops at that distance instead of running under the arrows

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
