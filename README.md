<p align="center">
  <a href="https://moodframe.art">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="assets/moodframe-logo-on-dark.svg">
      <img src="assets/moodframe-logo-on-light.svg" alt="MoodFrame" width="420">
    </picture>
  </a>
</p>

<p align="center"><a href="https://moodframe.art">moodframe.art</a> · <a href="https://moodframe.art/download">Download</a></p>

MoodFrame is a desktop studio for AI image generation. The mood decides how an image feels; the frame decides how
it is rendered. One app for several image models, with prompt building blocks and a brainstorm assistant that turns
a rough idea into a finished prompt.

## Features

- **Image models:** FLUX 3 and FLUX.2 (Black Forest Labs), Nano Banana (Google Gemini), and any OpenAI-compatible
  image API (OpenAI or your own gateway), with model families and resolutions
- **Mood:** how an image feels, combined from building blocks: style, atmosphere, light, color palette and camera
  (a visual settings dialog for camera type, focal length, aperture, perspective and shot size); built in and your
  own, each field lockable, saved as named moods; "from image" derives a mood from any picture
- **Frame:** the format of an image: a target aspect ratio, saved as named frames, of which every model takes its
  closest one; model, resolution and count are chosen under Generation
- **Brainstorm:** a guided chat with Gemini or an OpenAI-compatible model, with web research and fact checks, that
  builds the setting and hands over prompt, mood, model and resolution; several images per chat in rounds
- **Reference images:** up to 10 per request (depending on the model), from file, drag and drop or clipboard,
  scaled down automatically when too large
- **Video:** Gemini Omni
- **Library:** chats with all images, or all images of a mood across chats; each image remembers its mood, costs per
  image; one trash for chats, images, building blocks, moods and frames
- **Corporate networks:** proxy settings and custom root certificates

## Requirements

- Java 21
- An API key for at least one provider (BFL, Google Gemini, or an OpenAI-compatible endpoint)

## Build and run

```bash
./gradlew run      # start the app
./gradlew test     # run the tests
```

API keys are entered in the settings and stored encrypted in `~/.moodframe/config.properties`. The key for that
encryption is created per installation in `~/.moodframe/secret.key`. You can also pass keys as environment variables
(`BFL_API_KEY`, `GEMINI_API_KEY`, `OPENAI_API_KEY`).

## Packaging

Installers for macOS and Windows are built with [Conveyor](https://conveyor.hydraulic.dev) (`conveyor.conf`):

```bash
./gradlew deploy
```

Conveyor is free for open source projects: `app.vcs-url` points to this public repository, and the build confirms that
with Conveyor's license server. Conveyor uses its own trust store, so behind a TLS-inspecting proxy the build stops
with `Could not contact license server`; import the proxy's root certificate into Conveyor's
`runtime/Contents/Home/lib/security/cacerts` with `keytool`.

The download site is written to `build/packages/moodframe-<version>/`. Upload its content to
`https://moodframe.art/download` (`app.site.base-url`); installed apps check that address for updates on every launch.

## License

[MIT](LICENSE) © Matthias Brusdeylins. The logo is set in Space Grotesk (SIL Open Font License).
