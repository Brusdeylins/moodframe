/*  moodframe.art: language switch, mood tabs, install tabs for the detected system, copy buttons, and the
    version of the latest GitHub release (the page works without this script, in English, with 2.0.0)  */
"use strict"

const REPO = "Brusdeylins/moodframe"
const DOWNLOAD = "https://moodframe.art/download/"

const TEXT = {
    en: {
        "nav.overview": "Overview", "nav.mood": "Mood", "nav.frame": "Frame", "nav.models": "Models", "nav.install": "Install",
        "hero.eyebrow": "Desktop studio for AI images",
        "hero.claim": "The mood decides how an image feels, the frame how it is rendered. One app for FLUX, Nano Banana and every OpenAI-compatible image API.",
        "hero.install.mac": "Install for macOS", "hero.install.windows": "Install for Windows",
        "hero.install.linux": "Install for Linux", "hero.install.other": "Install",
        "hero.github": "View on GitHub",
        "overview.title": "One studio, every image model",
        "overview.lead": "Everything in one window. The chapters below go into the details.",
        "overview.alt": "The MoodFrame main window with the library, the images, the prompt, and the groups Mood, Frame and Generation",
        "feature.library": "Library", "feature.library.1": "Chats, or all images", "feature.library.2": "of a mood or frame",
        "feature.library.full": "Chats, or all images of a mood or frame",
        "feature.images": "Images", "feature.images.1": "view in detail, use as", "feature.images.2": "reference, or delete",
        "feature.images.full": "view in detail, use as reference, or delete",
        "feature.prompt": "Prompt", "feature.prompt.1": "write what you want", "feature.prompt.2": "to see in the picture",
        "feature.prompt.full": "write what you want to see in the picture",
        "feature.brainstorm": "Brainstorm", "feature.brainstorm.1": "develop ideas and prompts", "feature.brainstorm.2": "with an AI assistant",
        "feature.brainstorm.full": "develop ideas and prompts with an AI assistant",
        "feature.mood.1": "atmosphere, style, color,", "feature.mood.2": "light and camera",
        "feature.mood.full": "atmosphere, style, color, light and camera",
        "feature.frame.1": "aspect ratio and resolution",
        "feature.generation": "Generation", "feature.generation.1": "model, number of images,", "feature.generation.2": "reference images and", "feature.generation.3": "Google grounding",
        "feature.generation.full": "model, number of images, reference images and Google grounding",
        "mood.title": "Switch the mood, keep the picture",
        "mood.lead": "One picture, five moods. A mood is made of building blocks, each one lockable.",
        "block.style": "Style", "block.atmosphere": "Atmosphere", "block.light": "Light", "block.color": "Color", "block.camera": "Camera",
        "mood.point.own": "Your own blocks and saved moods",
        "mood.point.image": "Mood from image: drop a picture, get its mood",
        "mood.point.remember": "Every image remembers its mood",
        "mood.golden.name": "Vintage Film", "mood.golden.style": "Analog Film",
        "mood.golden.atmosphere": "Nostalgic", "mood.golden.light": "Golden Hour",
        "mood.golden.camera": "Street 35mm · Leica M6 · 35 mm · f/11",
        "mood.fog.name": "Quiet Morning", "mood.fog.style": "Photography",
        "mood.fog.atmosphere": "Calm", "mood.fog.light": "Blue Hour",
        "mood.fog.camera": "—",
        "mood.noir.name": "Sci-Fi Cinema", "mood.noir.style": "Cinematic Film Still",
        "mood.noir.atmosphere": "Futuristic", "mood.noir.light": "Neon Practicals",
        "mood.noir.camera": "—",
        "mood.bw.name": "Film Noir", "mood.bw.style": "Black-and-White Film", "mood.bw.atmosphere": "Menacing", "mood.bw.light": "Chiaroscuro", "mood.bw.camera": "—",
        "mood.wild.name": "Wild North", "mood.wild.style": "Documentary", "mood.wild.atmosphere": "Raw", "mood.wild.light": "Overcast", "mood.wild.camera": "—",
        "frame.title": "Say the format once",
        "frame.lead": "A frame is a target aspect ratio and resolution, saved by name.",
        "frame.text": "Every model takes its closest aspect ratio and resolution, and the app shows which one before you generate.",
        "frame.target": "Frame: Presentation · 16:9 · 2K", "frame.model": "Model", "frame.gets": "Gets",
        "frame.exact": "exact", "frame.closest": "closest",
        "frame.point.saved": "Saved frames for your recurring formats", "frame.point.group": "Group your library by frame",
        "models.title": "Bring your own API key",
        "models.lead": "Three providers in one app. Your keys stay on your machine, encrypted.",
        "models.gemini": "Nano Banana · Omni video · Brainstorm", "models.openai": "OpenAI or your own compatible gateway",
        "install.title": "Up and running in a minute", "install.version": "Version",
        "install.free": "Free and open source (MIT).", "install.yours": "Your system",
        "install.mac.command": "Paste into a terminal: the command installs and starts MoodFrame.",
        "install.mac.zip": "Or download the .zip:",
        "install.mac.notarized": "The app is not notarized by Apple, so open it the first time with Control-click → Open.",
        "install.windows.button": "Download and install",
        "install.windows.text": "Runs the installer, which also keeps MoodFrame up to date.",
        "install.windows.also": "Also as",
        "install.linux.text": "For Debian and Ubuntu; updates come with apt.", "install.linux.other": "Other distributions:",
        "install.packaged": "Packaged with",
        "install.updates": "by Hydraulic · MoodFrame checks for updates on every launch and updates itself",
        "footer.built": "Built with Agentic Coding using", "footer.and": "and",
        "footer.changelog": "Changelog", "footer.releases": "Releases", "footer.imprint": "Imprint", "footer.privacy": "Privacy", "legal.back": "Back to moodframe.art",
        "copy": "Copy", "copied": "Copied"
    },
    de: {
        "nav.overview": "Überblick", "nav.mood": "Mood", "nav.frame": "Frame", "nav.models": "Modelle", "nav.install": "Installieren",
        "hero.eyebrow": "Desktop-Studio für KI-Bilder",
        "hero.claim": "Die Mood bestimmt, wie ein Bild wirkt, der Frame, wie es ausgegeben wird. Eine App für FLUX, Nano Banana und jede OpenAI-kompatible Bild-API.",
        "hero.install.mac": "Für macOS installieren", "hero.install.windows": "Für Windows installieren",
        "hero.install.linux": "Für Linux installieren", "hero.install.other": "Installieren",
        "hero.github": "Auf GitHub ansehen",
        "overview.title": "Ein Studio für jedes Bildmodell",
        "overview.lead": "Alles in einem Fenster. Die Kapitel darunter gehen ins Detail.",
        "overview.alt": "Das Hauptfenster von MoodFrame mit Bibliothek, Bildern, Prompt und den Gruppen Mood, Frame und Generierung",
        "feature.library": "Bibliothek", "feature.library.1": "Chats, oder alle Bilder", "feature.library.2": "einer Mood oder eines Frames",
        "feature.library.full": "Chats, oder alle Bilder einer Mood oder eines Frames",
        "feature.images": "Bilder", "feature.images.1": "im Detail ansehen, als", "feature.images.2": "Referenz nutzen, löschen",
        "feature.images.full": "im Detail ansehen, als Referenz nutzen oder löschen",
        "feature.prompt": "Prompt", "feature.prompt.1": "schreiben, was du", "feature.prompt.2": "im Bild sehen willst",
        "feature.prompt.full": "schreiben, was du im Bild sehen willst",
        "feature.brainstorm": "Brainstorm", "feature.brainstorm.1": "Ideen und Prompts mit", "feature.brainstorm.2": "einem KI-Assistenten entwickeln",
        "feature.brainstorm.full": "Ideen und Prompts mit einem KI-Assistenten entwickeln",
        "feature.mood.1": "Atmosphäre, Stil, Farbe,", "feature.mood.2": "Licht und Kamera",
        "feature.mood.full": "Atmosphäre, Stil, Farbe, Licht und Kamera",
        "feature.frame.1": "Seitenverhältnis und Auflösung",
        "feature.generation": "Generierung", "feature.generation.1": "Modell, Anzahl der Bilder,", "feature.generation.2": "Referenzbilder und", "feature.generation.3": "Google-Grounding",
        "feature.generation.full": "Modell, Anzahl der Bilder, Referenzbilder und Google-Grounding",
        "mood.title": "Mood wechseln, Bild behalten",
        "mood.lead": "Ein Bild, fünf Moods. Eine Mood besteht aus Bausteinen, jeder einzeln sperrbar.",
        "block.style": "Stil", "block.atmosphere": "Atmosphäre", "block.light": "Licht", "block.color": "Farbe", "block.camera": "Kamera",
        "mood.point.own": "Eigene Bausteine und gespeicherte Moods",
        "mood.point.image": "Mood aus Bild: Bild ablegen, Mood erhalten",
        "mood.point.remember": "Jedes Bild merkt sich seine Mood",
        "mood.golden.name": "Vintage Film", "mood.golden.style": "Analog Film",
        "mood.golden.atmosphere": "Nostalgic", "mood.golden.light": "Golden Hour",
        "mood.golden.camera": "Street 35mm · Leica M6 · 35 mm · f/11",
        "mood.fog.name": "Quiet Morning", "mood.fog.style": "Photography",
        "mood.fog.atmosphere": "Calm", "mood.fog.light": "Blue Hour",
        "mood.fog.camera": "—",
        "mood.noir.name": "Sci-Fi Cinema", "mood.noir.style": "Cinematic Film Still",
        "mood.noir.atmosphere": "Futuristic", "mood.noir.light": "Neon Practicals",
        "mood.noir.camera": "—",
        "mood.bw.name": "Film Noir", "mood.bw.style": "Black-and-White Film", "mood.bw.atmosphere": "Menacing", "mood.bw.light": "Chiaroscuro", "mood.bw.camera": "—",
        "mood.wild.name": "Wild North", "mood.wild.style": "Documentary", "mood.wild.atmosphere": "Raw", "mood.wild.light": "Overcast", "mood.wild.camera": "—",
        "frame.title": "Format einmal festlegen",
        "frame.lead": "Ein Frame ist Seitenverhältnis plus Auflösung, gespeichert unter einem Namen.",
        "frame.text": "Jedes Modell nimmt das nächstliegende Seitenverhältnis und die nächstliegende Auflösung, die App zeigt beides vor dem Generieren an.",
        "frame.target": "Frame: Presentation · 16:9 · 2K", "frame.model": "Modell", "frame.gets": "Ergebnis",
        "frame.exact": "exakt", "frame.closest": "nächstliegend",
        "frame.point.saved": "Gespeicherte Frames für wiederkehrende Formate", "frame.point.group": "Bibliothek nach Frame gruppieren",
        "models.title": "Mit deinem eigenen API-Schlüssel",
        "models.lead": "Drei Anbieter in einer App. Deine Schlüssel bleiben verschlüsselt auf deinem Rechner.",
        "models.gemini": "Nano Banana · Omni-Video · Brainstorm", "models.openai": "OpenAI oder dein eigenes kompatibles Gateway",
        "install.title": "In einer Minute startklar", "install.version": "Version",
        "install.free": "Kostenlos und quelloffen (MIT).", "install.yours": "Dein System",
        "install.mac.command": "Im Terminal einfügen: Der Befehl installiert und startet MoodFrame.",
        "install.mac.zip": "Oder die .zip laden:",
        "install.mac.notarized": "Die App ist nicht von Apple notarisiert; beim ersten Start mit Control-Klick → Öffnen starten.",
        "install.windows.button": "Laden und installieren",
        "install.windows.text": "Startet das Installationsprogramm, das MoodFrame auch aktuell hält.",
        "install.windows.also": "Auch als",
        "install.linux.text": "Für Debian und Ubuntu; Updates kommen über apt.", "install.linux.other": "Andere Distributionen:",
        "install.packaged": "Paketiert mit",
        "install.updates": "von Hydraulic · MoodFrame prüft bei jedem Start auf Updates und aktualisiert sich selbst",
        "footer.built": "Entwickelt mit Agentic Coding über", "footer.and": "und",
        "footer.changelog": "Änderungen", "footer.releases": "Releases", "footer.imprint": "Impressum", "footer.privacy": "Datenschutz", "legal.back": "Zurück zu moodframe.art",
        "copy": "Kopieren", "copied": "Kopiert"
    }
}

const MOODS = {
    golden: { image: "mood-vintage-film.jpg", swatches: [ "#FFB347", "#FF9F1C", "#E85D04", "#D00000", "#370617" ] },
    fog:    { image: "mood-quiet-morning.jpg",    swatches: [ "#CAF0F8", "#ADE8F4", "#90E0EF", "#48CAE4", "#00B4D8" ] },
    noir:   { image: "mood-sci-fi-cinema.jpg",   swatches: [ "#FF00FF", "#00FFFF", "#FF006E", "#8338EC", "#3A0CA3" ] },
    bw:     { image: "mood-film-noir.jpg",     swatches: [ "#0A0A0A", "#3D3D3D", "#808080", "#C4C4C4", "#F5F5F5" ] },
    wild:   { image: "mood-wild-north.jpg",    swatches: [ "#2D6A4F", "#40916C", "#74C69D", "#B7E4C7", "#D8F3DC" ] }
}

const state = { lang: "en", mood: "golden", os: detectOS(), release: null }

/*  storage can be blocked (private mode); the page works without it  */
function remember (key, value) {
    try { localStorage.setItem(key, value) }
    catch (e) { /*  not available  */ }
}
function recall (key) {
    try { return localStorage.getItem(key) }
    catch (e) { return null }
}

function t (key) {
    return TEXT[state.lang][key] ?? TEXT.en[key] ?? key
}

/*  the desktop system of the visitor: mac, windows, linux, or null on phones and the unknown  */
function detectOS () {
    const platform = (navigator.userAgentData?.platform ?? navigator.platform ?? "").toLowerCase()
    const agent = navigator.userAgent.toLowerCase()
    if (/android|iphone|ipad|ipod/.test(agent))
        return null
    if (platform.includes("mac") || agent.includes("mac os"))
        return "mac"
    if (platform.includes("win") || agent.includes("windows"))
        return "windows"
    if (platform.includes("linux") || agent.includes("linux") || agent.includes("x11"))
        return "linux"
    return null
}

function applyLanguage () {
    document.documentElement.lang = state.lang
    for (const element of document.querySelectorAll("[data-i18n]"))
        element.textContent = t(element.dataset.i18n)
    for (const button of document.querySelectorAll("[data-lang]"))
        button.setAttribute("aria-pressed", String(button.dataset.lang === state.lang))
    for (const button of document.querySelectorAll("[data-copy]"))
        button.setAttribute("aria-label", t("copy"))
    for (const block of document.querySelectorAll("[data-lang-block]"))
        block.hidden = block.dataset.langBlock !== state.lang
    const label = document.querySelector("[data-os-label]")
    if (label !== null)
        label.textContent = t(`hero.install.${state.os ?? "other"}`)
    applyMood()
    applyRelease()
}

function applyMood () {
    if (document.querySelector("[data-mood-image]") === null)
        return
    const mood = MOODS[state.mood]
    for (const tab of document.querySelectorAll("[data-mood]"))
        tab.setAttribute("aria-selected", String(tab.dataset.mood === state.mood))
    document.querySelector("[data-mood-image]").src = `assets/img/${mood.image}?v=20261114d`
    for (const field of document.querySelectorAll("[data-mood-field]"))
        field.textContent = t(`mood.${state.mood}.${field.dataset.moodField}`)
    const swatches = document.querySelector("[data-mood-swatches]")
    swatches.replaceChildren(...mood.swatches.map((color) => {
        const swatch = document.createElement("span")
        swatch.style.background = color
        return swatch
    }))
}

function selectOS (os) {
    for (const tab of document.querySelectorAll("[data-os]"))
        tab.setAttribute("aria-selected", String(tab.dataset.os === os))
    for (const panel of document.querySelectorAll("[data-os-panel]"))
        panel.hidden = panel.dataset.osPanel !== os
}

/*  tabs: click, and arrow keys between the tabs of one list  */
function setupTabs (list, onSelect) {
    const tabs = [ ...list.querySelectorAll("[role=tab]") ]
    tabs.forEach((tab, index) => {
        tab.addEventListener("click", () => onSelect(tab))
        tab.addEventListener("keydown", (event) => {
            const step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0
            if (step === 0)
                return
            const next = tabs[(index + step + tabs.length) % tabs.length]
            next.focus()
            onSelect(next)
        })
    })
}

/*  the released version, from the metadata Conveyor publishes next to the files (same site, no third party):
    commands, file links and the link to its GitHub release; falls back to the static 2.0.0  */
async function loadRelease () {
    try {
        const response = await fetch(DOWNLOAD + "metadata.properties", { cache: "no-cache" })
        if (!response.ok)
            return
        const version = (await response.text()).match(/^app\.version=(.+)$/m)?.[1]?.trim()
        if (version === undefined)
            return
        state.release = { version, url: `https://github.com/${REPO}/releases/tag/v${version}` }
        applyRelease()
    }
    catch (e) {
        /*  offline or not served from moodframe.art: keep the static version  */
    }
}

function applyRelease () {
    const release = state.release
    if (release === null)
        return
    for (const element of document.querySelectorAll("[data-version]"))
        element.textContent = release.version
    for (const element of document.querySelectorAll("[data-version-tag]"))
        element.textContent = `v${release.version}`
    for (const link of document.querySelectorAll("[data-file]"))
        link.href = DOWNLOAD + link.dataset.file.replace("{v}", release.version)
    for (const link of document.querySelectorAll("[data-release-link]"))
        link.href = release.url
}

function setupCopy () {
    for (const button of document.querySelectorAll("[data-copy]")) {
        button.addEventListener("click", async () => {
            const pre = button.parentElement.querySelector("pre").cloneNode(true)
            pre.querySelectorAll(".prompt").forEach((prompt) => prompt.remove())
            try {
                await navigator.clipboard.writeText(pre.textContent.trim())
                button.classList.add("done")
                button.textContent = "✓"
                button.setAttribute("aria-label", t("copied"))
                setTimeout(() => {
                    button.classList.remove("done")
                    button.textContent = "⧉"
                    button.setAttribute("aria-label", t("copy"))
                }, 1600)
            }
            catch (e) {
                /*  clipboard not allowed: the command stays selectable  */
            }
        })
    }
}

function setupNav () {
    const nav = document.getElementById("nav")
    const toggle = nav.querySelector(".menu-toggle")
    const update = () => nav.classList.toggle("scrolled", window.scrollY > 40)
    window.addEventListener("scroll", update, { passive: true })
    update()
    toggle.addEventListener("click", () => {
        const open = nav.classList.toggle("open")
        toggle.setAttribute("aria-expanded", String(open))
    })
    for (const link of nav.querySelectorAll(".links a"))
        link.addEventListener("click", () => {
            nav.classList.remove("open")
            toggle.setAttribute("aria-expanded", "false")
        })
}

function init () {
    const asked = new URLSearchParams(location.search).get("lang")
    const stored = recall("lang")
    state.lang = TEXT[asked] ? asked : TEXT[stored] ? stored : "en"

    for (const button of document.querySelectorAll("[data-lang]"))
        button.addEventListener("click", () => {
            state.lang = button.dataset.lang
            remember("lang", state.lang)
            applyLanguage()
        })

    /*  the main page only: mood and install tabs; imprint and privacy have just the language switch  */
    const moodTabs = document.querySelector("[data-mood-tabs]")
    if (moodTabs !== null)
        setupTabs(moodTabs, (tab) => {
            state.mood = tab.dataset.mood
            applyMood()
        })
    const osTabs = document.querySelector("[data-os-tabs]")
    if (osTabs !== null) {
        setupTabs(osTabs, (tab) => selectOS(tab.dataset.os))
        if (state.os !== null) {
            selectOS(state.os)
            document.querySelector(`[data-os="${state.os}"] .hint`).hidden = false
        }
        document.querySelector("[data-install-button]").addEventListener("click", () => {
            if (state.os !== null)
                selectOS(state.os)
        })
    }

    setupNav()
    setupCopy()
    applyLanguage()
    loadRelease()
}

init()
