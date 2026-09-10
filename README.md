# QZA

Client-side Hypixel SkyBlock mod. Fabric, client-only, no mixins.

- **`/qza`** — settings GUI (category rail + search, modelled on the reference layout)
- **`/qzahelp`** — command list
- **ShitterList** — auto-kick people you've blacklisted when they join your dungeon party
- **Terminal music** — plays your own music between Storm's death and Goldor's phase in F7/M7

---

## Build

Versions are already pinned in `gradle.properties`, verified against
`meta.fabricmc.net` for 26.1.2:

```
minecraft_version=26.1.2
loader_version=0.19.5      # newest stable for 26.1.2
loom_version=1.17-SNAPSHOT
fabric_api_version=0.155.3+26.1.2
```

**Verified: `BUILD SUCCESSFUL`** with Gradle 9.6.0 on JDK 26, producing
`build/libs/qza-1.0.0.jar` (58 KB, 22 classes). Drop it in `.minecraft/mods/` alongside
Fabric API.

Build it from IntelliJ (the Gradle sync handles everything), or from the CLI once you
have a wrapper. Note Gradle **9.x is required** — 8.x will not run on JDK 26. Java 25 is
mandatory: `26.1.2` reports `javaVersion: 25` in Mojang's version manifest, which is why
`build.gradle` sets `release = 25` and `fabric.mod.json` requires `java >=25`.

### The plugin id matters

```groovy
id 'net.fabricmc.fabric-loom'   // correct
id 'fabric-loom'                // legacy — resolves, then fails
```

The legacy id resolves to the same Loom 1.17.20 but runs in legacy mode, which demands
an explicit `mappings` dependency. That is unsatisfiable here: Yarn has no 26.x build
(`meta.fabricmc.net/v2/versions/yarn/26.1.2` → empty; newest is `1.21.11`), and Mojang
no longer publishes mappings either — the version JSON for both 26.1.2 and 26.2 contains
only `client` and `server` downloads, with `client_mappings` **absent**.

With the correct plugin id, no `mappings` line is needed at all, and Loom produces a jar
named `minecraft-merged-**deobf**`. The 26.x client ships unobfuscated, so the names
below are simply Minecraft's real names. There is also no `remapJar` step in the build.

### 26.x API notes (verified with `javap` against the deobf jar)

Minecraft 26.x replaced the immediate-mode GUI with a **retained-mode** pipeline. A
screen no longer draws; it describes itself into a `GuiGraphicsExtractor`:

| Pre-26.x | 26.1.2 |
| --- | --- |
| `render(GuiGraphics, …)` | `extractRenderState(GuiGraphicsExtractor, …)` |
| `renderBackground(…)` | `extractBackground(…)` |
| `drawString(font, …)` | `text(font, …)` |
| `drawCenteredString(…)` | `centeredText(…)` |
| `renderOutline(…)` | *removed* — see the `outline()` helper in `QZAScreen` |
| `mouseClicked(double, double, int)` | `mouseClicked(MouseButtonEvent, boolean)` |
| `mouseDragged(double, double, int, …)` | `mouseDragged(MouseButtonEvent, double, double)` |
| `mouseReleased(double, double, int)` | `mouseReleased(MouseButtonEvent)` |

`MouseButtonEvent` is a record — use `event.x()`, `event.y()`, `event.button()`.
Unchanged: `fill`, `enableScissor`/`disableScissor`, `Font.width`/`split`,
`EditBox.setHint`/`setBordered`/`getValue`, `Screen.onClose`/`isPauseScreen`,
`mouseScrolled`.

Other renames that bite:

| Elsewhere | 26.1.2 |
| --- | --- |
| `ClientCommandManager.literal/argument` | `ClientCommands.literal/argument` |
| `net.minecraft.Util` | `net.minecraft.util.Util` |
| `GameProfile.getName()` | `GameProfile.name()` (it's a record) |
| `chat.addMessage(Component)` | `chat.addClientSystemMessage(Component)` |

That last one is the clean path for mod output — the surviving 4-arg `addMessage` wants
`(Component, MessageSignature, GuiMessageSource, GuiMessageTag)`.

If you copy code from an existing SkyBlock mod or any tutorial, it will be written
against Yarn *and* the old render pipeline, and will need both translations.

---

## Feature 1 — ShitterList

```
/shitter add <ign> [reason]     add someone (reason is free text, spaces fine)
/shitter remove <ign>           take them off
/shitter list [page]            8 per page, clickable << >> arrows
/shitter clear                  wipe the list
```

`/shitterlist` works as an alias for all of the above. Names are case-insensitive.
The list is stored in `config/qza/shitterlist.json`.

When a listed player joins, QZA prints

```
[QZA] Shitter detected... Kicking Notch (ping abuser)
```

then runs `/party kick Notch`.

**Timing is deliberately unhurried.** Nothing fires on the tick a join arrives:

- each detected shitter is queued a second further out than the last — 1s, 2s, 3s …
- the party-chat announcement and the kick command are spaced ~6 ticks apart
- the `!k` fallback waits another half second

so no two packets ever share a tick and nothing looks machine-timed.

**If you are not party leader**, Hypixel refuses the kick. QZA watches for that reply
and retries as `/pc !k <ign>`, the party-command form a leader running a party-command
mod will action for you.

Lines it reacts to (rank prefixes optional):

- `Bob joined the party.`
- `Party Finder > Bob joined the dungeon group! (2/5)`
- `You'll be partying with: [MVP+] Bob, [VIP] Joe` — catches shitters already in a
  party *you* just joined (toggleable)

Options under **Shitter List → Auto-Kick**: master toggle, dungeon-groups-only,
scan-party-on-join, announce-in-party-chat, and a per-player kick cooldown.

## Feature 2 — Terminal music (F7 / M7)

Triggered purely off boss chat lines, so it works on both F7 and M7:

Music plays across the **terminal phase**:

| Event | Chat line | Action |
| --- | --- | --- |
| Terminals start | `[BOSS] Goldor: Who dares trespass into my domain?` | fade in, start |
| Terminals done | `The Core entrance is opening!` | fade out, stop |

Both are matched as **substrings** and live in `config/qza/config.json` as
`musicStartTrigger` / `musicStopTrigger`, so you can re-point them without rebuilding
if Hypixel rewords a line.

The music **loops** for as long as the phase lasts, and also stops if you leave the run
— a client-tick watcher compares the `ClientLevel` by identity, so getting sent back to
the hub cuts the audio.

### Adding your own music

**Music → Add Music → Open Folder** opens `config/qza/music/` in Explorer; drag files
in, then hit **Reload Playlist**. `/qza music folder` does the same from chat.

Supported: **`.ogg`** (recommended), `.wav`, `.aiff`, `.au`.
**MP3 is not supported** — the JDK has no MP3 decoder and I didn't want to bundle one.
Convert in Audacity (`File → Export → Export as OGG`).

Playback options: volume, fade length, loop, shuffle, and a Test Playback button.

### How playback works

Minecraft's sound engine can only play sounds registered from a resource pack, so
arbitrary user files aren't possible through it. Instead QZA decodes tracks itself —
LWJGL's STB Vorbis bindings for Ogg (already on Minecraft's classpath), Java Sound for
WAV — and streams the PCM to its own `SourceDataLine` on a daemon thread. Consequences:

- Volume is independent of Minecraft's music slider (use QZA's own).
- Nothing touches the render thread or OpenAL, so it can't stutter the game or fight
  Minecraft's audio context.
- Fades and gain are applied in software, per sample.

---

## Layout of the GUI

Sidebar order matches the reference: Shitter List, F7 / M7, Highlights, Ticks, Slayers,
Autopet Popup, Music, Miscellaneous. **Highlights, Ticks, Slayers and Autopet Popup are
present but empty** — they show "No settings in this category yet." rather than
non-functional switches. Adding a setting to one is a single entry in
`SettingsRegistry.build()`.

The search box filters across *all* categories at once and labels results
`Category / Section`.

The reference screenshot uses a custom font from a resource pack; QZA draws with
Minecraft's font, so text will look different even though the layout matches.

## Files on disk

```
config/qza/config.json         all GUI settings
config/qza/shitterlist.json    the list
config/qza/music/              drop music here
```
