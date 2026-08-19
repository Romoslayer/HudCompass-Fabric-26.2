# HudCompass Fabric 26.2 port — handoff

Paste this file into a fresh session to resume.

## Current status: feature-complete, everything confirmed working in-game

**Builds cleanly** (`BUILD SUCCESSFUL`), produces `hudcompass-26.2-fabric-1.0.0.jar`. **Every
feature from the original NeoForge mod has been ported and confirmed working live** — nothing
is stubbed, disabled, or skipped except two intentional gaps carried over unchanged from
upstream's own unfinished state (see "Known, accepted gaps" just below). This section is a
present-tense summary; the numbered sections further down go into full technical/historical
detail on each piece, including bugs found and fixed along the way — read those before touching
any of this code, since several non-obvious lessons live there.

**Confirmed working, end to end:**
- Compass HUD renders correctly (cardinal directions, waypoint icons/labels, above/below arrows,
  fade/distance behavior), including the default `HOLDING_COMPASS` display gating.
- Waypoints: add/remove via keybind, edit via the full in-game waypoint editor GUI (add/edit/
  delete, per-world grouped, foldable) — see "Waypoint editor GUI".
- Persistence survives a relog and a real network disconnect/reconnect, server-authoritative
  (not just client-local) — see "Multiplayer waypoint sync".
- Multiplayer waypoint sync exercised over a real two-instance LAN connection, not just
  singleplayer's integrated server — see "Two-instance LAN test".
- Vanilla locator bar mirroring + suppression while the compass is visible, verified against a
  real second player — see "Two-instance LAN test".
- Server-side waypoint sources: bed/spawn point, vanilla map banner markers, other-player
  tracking (`PlayerTracker`, deliberately bypassing the vanilla `locatorBar` gamerule by design
  — see "Server-side waypoint sources" for the reasoning behind that call) — see "Server-side
  waypoint sources".
- JourneyMap integration (one-way, read-only waypoint mirroring) — see "JourneyMap integration".
- In-game config screen via ModMenu + Cloth Config (soft dependencies; `config/hudcompass.json`
  still works standalone) — see "Config screen (ModMenu + Cloth Config)".
- The compass no longer overlaps an active boss bar (upstream's boss-bar-push-down trick, ported
  via Fabric's `HudElementRegistry.replaceElement`) — see the note under "MC 26.2 API findings"
  and `HudOverlay`'s class javadoc.

**Known, accepted gaps** (both are upstream's own unfinished state, not something this port left
incomplete — confirmed by checking upstream's actual source, not assumed):
- The waypoint editor's "change symbol" icon-picker button is a disabled placeholder. Upstream's
  own button is identically inert; no icon-picker UI exists on either side.
- JourneyMap-mirrored waypoints render as a generic tinted icon, not each waypoint's real custom
  texture. Upstream has a literal `// TODO: icon textures` here and never finished it either.

## What this is

A Fabric port of [gigaherz/HudCompass](https://github.com/gigaherz/HudCompass) (a NeoForge mod)
from its `master` branch (targeting NeoForge on MC **26.1.2**) to **Fabric on MC 26.2**. This is
a real NeoForge→Fabric loader port, not just a version bump — several NeoForge-only systems
(attachments, `ModConfigSpec`, `PayloadRegistrar`, a custom `ScrollPanel` widget) had no direct
Fabric equivalent and needed replacing, not just renaming.

- Working dir: `C:\Users\josep\Desktop\HudCompass-Fabric-26.2\`
- Upstream reference clone (untouched, for diffing): `.\upstream-src\` (do not edit; it's the
  NeoForge source being ported *from* -- gitignored, not part of this repo)
- Git repo: initialized, public on GitHub at
  [Romoslayer/HudCompass-Fabric-26.2](https://github.com/Romoslayer/HudCompass-Fabric-26.2).
  **Still confirm with the user before force-pushing, rewriting history, or anything else
  destructive** -- the "confirm before publishing" preference was specifically about the initial
  publish decision, which has now happened; it doesn't blanket-authorize every future git/GitHub
  action without asking.
- `fabric.mod.json`'s `contact` block (homepage/sources/issues) and the README both point at this
  repo, not upstream's -- deliberately fixed after almost shipping ModMenu's mod-info card
  pointing bug reports about this port's own code at gigaherz's actual project instead. If you
  add any new file with a homepage/issues/contact-style field (a CurseForge/Modrinth manifest, a
  GitHub issue template, etc.), point it here too, not at upstream.

## Scope decision (confirmed with user this session)

User chose **"MVP first"** over attempting a full one-shot port, given the scope was much larger
than a typical version-bump port. They also chose to leave JourneyMap integration as a stub/TODO
rather than drop it silently (upstream's own JourneyMap code was already fully commented out —
nothing functional existed to port anyway). **Revisited and implemented later in this same
session** once the user confirmed they'd actually be running JourneyMap alongside HudCompass --
see "JourneyMap integration" below.

## What's implemented (MVP)

- The compass HUD itself: cardinal directions, waypoint icons + labels, above/below arrows,
  fade/distance behavior — registered via Fabric API's `HudElementRegistry.attachElementAfter`.
- Client-local waypoints: add/remove via keybind (`key.hudcompass.add_waypoint` /
  `key.hudcompass.remove_waypoint`), persisted to disk per world/server
  (`ClientWaypointDatabase`, using vanilla `TagValueInput`/`TagValueOutput` NBT, same as
  upstream).
- The vanilla locator bar's own non-player waypoints render on the compass (`LocatorBarPoints`).
  Originally this mirrored *every* vanilla-tracked waypoint including other players; as of this
  session it deliberately excludes players (`waypoint.id().left().isPresent()` check), since
  `PlayerTracker` (see below) now owns showing players -- see "Server-side waypoint sources"
  below for why that split exists. Not death markers -- see the corrected note in the "MC 26.2
  API findings" section.
- `HudMixin` suppresses the vanilla locator bar when the compass is visible and configured to
  (a private helper inside `HudMixin` itself now -- moved out of `ConfigData` this session, see
  "Multiplayer waypoint sync" below for why), matching upstream's `GuiMixin` behavior.
- Config: `config/hudcompass.json` (Gson) as the always-available fallback, plus an in-game
  config screen via ModMenu + Cloth Config (both optional) -- see "Config screen (ModMenu + Cloth
  Config)" below.
- **Multiplayer waypoint sharing / server sync** -- see the "Multiplayer waypoint sync" section
  below for full detail.
- **Server-side waypoint sources**: bed/spawn-point marker (`SpawnPointPoints`), vanilla map
  banner/decoration markers (`VanillaMapPoints`), other-player tracking (`PlayerTracker`) --
  see the "Server-side waypoint sources" section below.
- **JourneyMap integration**: mirrors JourneyMap's own waypoints onto the compass HUD, one-way
  read-only -- see the "JourneyMap integration" section below.
- **The in-game waypoint editor GUI** (`ClientWaypointManagerScreen`) -- add/edit/delete
  waypoints in a scrollable, per-world-grouped, foldable list, opened via the
  `key.hudcompass.edit_waypoints` keybind. See the "Waypoint editor GUI" section below -- this
  was the last item on the original deferred-work list, and required writing a Fabric-native
  replacement for NeoForge's `ScrollPanel` widget from scratch.

## Deferred work

None remaining -- every item from the original list above is now implemented and confirmed
working in-game. The only intentionally-unfinished piece is upstream's own gap, carried over
unchanged rather than scope-creeped past: the waypoint editor's "change symbol" icon-picker
button is a disabled placeholder, exactly as it is in upstream's own source.

## Multiplayer waypoint sync (implemented this session)

Replaces NeoForge's serializable `AttachmentType` (which upstream attaches to the player, holding
a `PointsOfInterest` kept in sync with the server over a custom packet channel) with a
Fabric-native equivalent built from three pieces, since Fabric has no attachment-API analogue:

1. **Server-side per-player storage**: `dev.gigaherz.hudcompass.server.ServerWaypointSync` holds
   a plain `Map<UUID, PointsOfInterest>`, one entry per player who has ever connected this server
   process, created lazily. Entries are **deliberately never evicted** -- see the bug writeup
   below for why that used to be there and why it was wrong.
2. **Persistence**: `dev.gigaherz.hudcompass.mixin.ServerPlayerWaypointDataMixin` mixes into
   `ServerPlayer#addAdditionalSaveData`/`#readAdditionalSaveData` (both `protected`, confirmed via
   javap against the real 26.2 jar), writing/reading a `hudcompass` child tag via
   `ValueOutput#child(String)` / `ValueInput#child(String)` -- piggybacking on the player's own
   save data (inventory, stats, etc.) rather than a separate file. This is genuinely
   loader-agnostic: it works identically whether the player object is a dedicated-server
   `ServerPlayer` or the integrated server's `ServerPlayer` in singleplayer.
3. **Networking**: 5 of upstream's 6 custom packets ported (`ClientHello`, `ServerHello`,
   `AddWaypoint`, `RemoveWaypoint`, `SyncWaypointData` -- `UpdateWaypointsFromGui` skipped, see
   the deferred-work list above) onto Fabric Networking API v1
   (`PayloadTypeRegistry`/`ServerPlayNetworking`/`ClientPlayNetworking`), registered from the
   common `HudCompass.onInitialize()` entrypoint so both sides agree on the codec. Handshake:
   server sends `ServerHello` on `ServerPlayConnectionEvents.JOIN` (gated on
   `ServerPlayNetworking.canSend(...)`, i.e. only if the client's channel registration says it has
   the mod) -> client replies `ClientHello` -> server marks that player's `otherSideHasMod = true`
   and sends an initial `SyncWaypointData` snapshot. From then on, `ServerTickEvents.END_SERVER_TICK`
   resyncs any player whose `changeNumber` (already-existing dirty counter, reused instead of
   upstream's separate changed/removed `Set`s -- simpler since the wire format is always a full
   snapshot anyway, not an incremental diff) has moved past `syncedNumber`.

**A critical environment change this required**: `fabric.mod.json`'s `"environment"` was
`"client"` -- meaning Fabric Loader would refuse to load this mod **at all** on a dedicated
server, which would have made server-side sync impossible outside singleplayer. Changed to `"*"`.
This is a real compatibility change (the mod is no longer client-only), not an implementation
detail -- worth knowing if anything downstream assumed client-only.

That environment flip surfaced two knock-on issues, both fixed:
- `HudMixin` targets `net.minecraft.client.gui.Hud`, a genuinely client-only vanilla class (confirmed
  absent from `minecraft-extracted_server.jar` via `unzip -l`). Left in the mixin config's
  unconditional `"mixins"` list, Mixin would try to resolve that target class while parsing the
  config on a dedicated server and fail. Moved to the `"client"`-only mixin list. (The new
  `ServerPlayerWaypointDataMixin` correctly stays in the unconditional list: `ServerPlayer` exists
  in both the merged client jar and the dedicated server jar, confirmed the same way.)
- `ConfigData` (a common class, loaded on both sides via `HudCompass.onInitialize()`) had a
  `shouldShowLocatorBar()` method statically importing the client-only `HudOverlay` class. Moved
  that method's logic into `HudMixin` itself (already client-only), so the common `ConfigData`
  class carries no client-only references at all.

**A real bug found via direct save-file inspection, not just in-game testing**: the first
persistence test after this went in appeared to work in-game (no crash, keybinds fine) but
waypoints did **not** survive a relog. Diagnosed by decompressing the actual player-data `.dat`
file and inspecting the raw NBT bytes (`gunzip -c ... | od -c`) rather than trusting only what the
client displayed: the file *did* have a `hudcompass` tag (proving the save-mixin fired), but with
an empty `Worlds` list (proving the in-memory object being saved was empty, not the one the
waypoint had actually been added to). Root cause: `ServerWaypointSync` originally evicted a
player's map entry on `ServerPlayConnectionEvents.DISCONNECT`, which raced vanilla's own
save-on-quit -- the eviction reset that player back to a fresh empty `PointsOfInterest` before (or
during) the save had a chance to serialize the real one. Fix: don't evict at all; a stale
in-memory copy is harmless since `readAdditionalSaveData` fully repopulates it from disk on the
next join regardless. Re-verified by direct file inspection after the fix: the save now contains
a real `Worlds -> minecraft:overworld -> POIs` list with actual waypoint position data.

**Confirmed against a real two-instance LAN connection this session** -- see "Two-instance LAN
test" below. Waypoints are correctly private per player (adding one on instance A does not show
up for instance B and vice versa -- matches upstream's actual design; this was never meant to be
shared waypoints, just each player's own waypoints made server-authoritative), and persistence
survives a real disconnect/reconnect over an actual network connection, not just singleplayer's
integrated server.

## Server-side waypoint sources (implemented this session)

Ports the three previously-deferred `upstream-src/.../integrations/server/` classes: bed/spawn
marker, vanilla-map decoration markers, and other-player tracking. All three now live under
`dev.gigaherz.hudcompass.integrations.server.*`, matching upstream's package layout, and all
three write into the same server-authoritative per-player store from the multiplayer-sync work
above (`ServerWaypointSync.get(player)`), each as `dynamic()` points (never saved to disk/NBT,
recomputed live) using `getOrCreateAddonData` for their own per-player bookkeeping -- exactly
matching how upstream structured this against its attachment.

- **`SpawnPointPoints`**: adds a "Home" waypoint at the player's bed/respawn-anchor spawn point.
  Near-verbatim port; the only real change was NeoForge's `PlayerTickEvent.Post` -> Fabric's
  `ServerTickEvents.END_SERVER_TICK` iterating `server.getPlayerList().getPlayers()` (Fabric has
  no per-player tick event), keeping upstream's own once-per-second throttle. Confirmed working
  in-game: sleeping in a bed produces a "Home" waypoint on the compass.
- **`VanillaMapPoints`**: adds waypoints for banner and other decoration markers on any map the
  player is carrying. Needed `MapItemSavedData#bannerMarkers`/`#decorations` access-widened
  (both `private` in the real MC 26.2 jar, confirmed via javap) -- the old commented-out
  access-widener template referenced a `banners` field that doesn't actually exist in this
  version; corrected while writing the real entries. Confirmed working in-game, but only after
  discovering (also via decompile, `MapItemSavedData.toggleBanner`/`MapItem.useOn`) that vanilla
  requires an explicit right-click on the banner block while holding the map -- proximity alone
  does nothing, this isn't automatic scanning. Worth remembering if this ever needs debugging
  again: "banner doesn't show up" is very likely a missed interaction, not a mod bug, unless the
  banner icon is also missing from the vanilla paper map itself (that's the diagnostic split used
  this session to confirm it wasn't a bug before assuming it was).
- **`PlayerTracker`**: shows other online players as head-icon waypoints, filtered by
  `ConfigData.playerDisplay` (`NONE`/`TEAM`/`ALL`, vanilla scoreboard teams -- confirmed with the
  user this has nothing to do with friends lists or anything external; `TEAM` behaves like `ALL`
  on a server where nobody has ever run `/team`, since an unset team compares equal-to-null on
  both sides). **Deliberate design decision, made explicitly with the user after raising the
  concern**: this is intentionally independent of vanilla's `locatorBar` game rule, which
  `LocatorBarPoints` (the other, pre-existing player-visibility path) respects. Traced the actual
  server bytecode to confirm the gamerule gate is real (`ServerWaypointManager.createConnection`
  calls `isLocatorBarEnabledFor(receiver)`, checking that receiving player's `GameRules.LOCATOR_BAR`
  before ever creating a tracking connection) -- so `PlayerTracker` really does let players see
  each other even on servers where an admin has explicitly turned that off for PvP fairness. The
  user's call after that tradeoff was raised: port it anyway, matching upstream's original
  design (upstream predates vanilla's own locator-bar feature entirely). **Confirmed via the
  two-instance LAN test** (see "Two-instance LAN test" below): with two real connected players,
  running `/gamerule locatorBar false` removed the other player's marker from vanilla's own
  crosshair locator bar, but the compass HUD kept showing them via `PlayerTracker` -- exactly the
  intended bypass, observed live rather than just traced through bytecode.
  - Fabric has no equivalent to NeoForge's `PlayerEvent.StartTracking`/`StopTracking` (confirmed:
    no "tracking" class anywhere across any fabric-api module jar for this version, checked by
    grepping every module jar's file listing). Replaced with a periodic full scan of online
    players per observer on `ServerTickEvents.END_SERVER_TICK`, diffing against a per-observer
    `Map<UUID, PlayerWaypoint>` to add/remove as visibility changes -- functionally equivalent,
    and arguably more correct for a compass (meant to point at teammates regardless of normal
    chunk-tracking/render distance, not just those close enough to visually render).
  - Handles dimension travel explicitly: the tracking-state bookkeeping records which dimension
    it last added points into, and if an observer's dimension changes, clears out the *old*
    dimension's entries via that remembered `WorldPoints` before starting fresh in the new one.
    Without this, points added while in one dimension would never get cleaned up after traveling
    to another, since the observer's `PointsOfInterest.get(currentDimension)` would silently
    stop being the same `WorldPoints` object those stale entries live in.
  - **A necessary de-duplication fix in `LocatorBarPoints`**: since both that class and
    `PlayerTracker` are now capable of showing "this player, over there," `LocatorBarPoints` now
    explicitly skips UUID-identified vanilla waypoints (`waypoint.id().left().isPresent()`) --
    those are players, and `PlayerTracker` already added them independently. Without this, every
    server with `locatorBar` enabled would show two overlapping head icons per player.
  - **A real bug this surfaced and fixed in `PointsOfInterest`**: the existing sync-dirty counter
    (`changeNumber`) only incremented for non-dynamic (saved) points -- correct for the original
    add/remove-your-own-waypoint use case, but `PlayerTracker`'s points are `dynamic()` (never
    saved), so their changes would never have marked anything dirty and a connected client would
    never learn about a newly-visible or newly-gone player. Fixed by splitting into two counters:
    `changeNumber` (save-worthy changes only, unchanged) and a new `syncNumber` (every change,
    dynamic or not) -- `ServerWaypointSync`'s resync trigger now checks `syncNumber`, matching
    upstream's own separate `changed`/`removed` `Set`s (which it adds to unconditionally,
    regardless of `isDynamic()`) more precisely than the earlier single-counter simplification did.
- Two config options that existed in `hudcompass.json` but had no effect because nothing read
  them (`enableSpawnPointWaypoint`, `enableVanillaMapIntegration`) are now wired up to real
  "baked" static fields in `ConfigData`, matching the pattern the rest of the config already used.

## JourneyMap integration (implemented this session, confirmed working end-to-end)

One-way, read-only: mirrors JourneyMap's own waypoints onto the compass HUD. HudCompass never
creates, edits, or deletes JourneyMap waypoints -- new class
`dev.gigaherz.hudcompass.integrations.journeymap.HudCompassJourneymapPlugin`.

**Upstream's own draft was unusable as a starting point**: it existed only as a fully
commented-out file, and had bit-rotted against an older shape of `PointInfo` (referencing a
`serializeAdditional`/`deserializeAdditional` method pair that doesn't exist anywhere in the
current codebase, upstream or otherwise). Written fresh against this port's current `PointInfo`
API and the real JourneyMap API v2, both verified directly rather than assumed from the stale
draft:

- **JourneyMap API research, not guessed**: confirmed via `gh api` against
  `github.com/TeamJM/journeymap-api` that a tag exactly matching this project,
  `26.2_2.0.0_1`, exists, and read the real `IClientPlugin`/`IClientAPI`/`WaypointEvent`/
  `Waypoint`/`JourneyMapPlugin` source at that tag rather than trust the mvnrepository-search
  summaries or upstream's stale draft. Found a real published Maven artifact via
  `maven.blamejared.com`: `info.journeymap:journeymap-api-fabric:26.2-2.0.0` (a proper release,
  not the `-SNAPSHOT` the API's own generic howto.md example shows -- checked the actual repo
  listing rather than copying the doc's example verbatim). Added as `compileOnly` (matching this
  project's established "no `mod`-prefixed dependencies needed, MC 26.1+ already ships official
  names" finding -- the API jar is likewise already built against official names, confirmed by
  its own source using `net.minecraft.resources.Identifier` directly) in `build.gradle`, version
  pinned via a new `journeymap_api_version` property in `gradle.properties`, matching the existing
  convention for other pinned versions there.
- **Soft dependency, Fabric-idiomatic, not annotation-scanned like Forge/NeoForge**: per the
  API's own docs, Fabric plugins are discovered via a `"journeymap"` entrypoint list in
  `fabric.mod.json` (added, pointing at the plugin class) rather than classpath annotation
  scanning. Fabric Loader only resolves entrypoints under a given key when something explicitly
  asks for that key -- and only JourneyMap itself ever queries `"journeymap"` -- so if JourneyMap
  isn't installed, the plugin class is never loaded at all, matching the API docs' explicit
  instruction to never reference plugin classes from elsewhere in the mod. **Confirmed empirically
  both ways**: launched once with no JourneyMap jar present (no errors, mod loads fine), then
  downloaded the real JourneyMap Fabric jar (`26.2-6.0.5+fabric` from Modrinth) into `run/mods/`
  and relaunched -- log showed `journeymap) Found @JourneyMapPlugin:
  dev.gigaherz.hudcompass.integrations.journeymap.HudCompassJourneymapPlugin`, confirming real
  discovery, not just a compiles-fine assumption.
- **A real crash found and root-caused via log inspection, not assumed**: the first live test (a
  user creating a JourneyMap waypoint via its own UI) crashed and disconnected the player, twice.
  The visible exception (`WaypointCrudResult$Failure.encodePayload` NPE writing a null failure
  message) lived entirely inside JourneyMap's own network code -- but the actual root cause was in
  this integration: `CommonEventRegistry.WAYPOINT_EVENT` fires from **both** JourneyMap's client
  *and server-side* waypoint handling, even though this plugin only registers as `IClientPlugin`
  (confirmed via a later stack trace showing the same subscriber invoked from
  `journeymap.common.network.handler.ServerWaypointHandler`, on the *Server thread* -- singleplayer
  runs the integrated server on a separate thread from rendering). The handler assumed
  client-thread-only execution and threw an uncaught NPE on a null dimension from that path, which
  most likely got caught somewhere inside JourneyMap's own CRUD handling and reported back as a
  failure using the (message-less) exception -- triggering the encode-time NPE that disconnected
  the player. Fixed two ways: (1) the whole handler is wrapped in try/catch, logging instead of
  ever propagating -- a purely cosmetic mirror must never be able to take down JourneyMap's actual
  operations or the connection, regardless of the exact causal chain; (2) explicit thread-hop via
  `Minecraft.execute(...)` when not already on the render thread (`Minecraft.isSameThread()`),
  since `PointsOfInterest.INSTANCE` is not thread-safe and mutating it from the server thread was
  a real data race even on ticks where it didn't happen to throw.
- **A second real bug found via user testing, not caught by code review**: waypoints didn't
  reappear after rejoining the world, even though creating them live worked fine and they were
  still present in JourneyMap's own state. Suspected (consistent with everything observed, though
  not fully proven): JourneyMap fires `Context.READ` events to replay its waypoint cache at world
  join, likely before `Minecraft.player` is set yet, so the handler's null-player guard was
  silently dropping exactly the events that would have repopulated the mirror -- and since nothing
  else ever re-fires them, the mirror just stayed empty from then on. Rather than chase the exact
  timing further, added a second, independent update path: a periodic reconciliation pass (once a
  second, `ClientTickEvents.END_CLIENT_TICK`, same throttle convention as the other per-tick
  integrations this session) that calls `IClientAPI#getAllWaypoints(dimension)` directly -- a
  real query against JourneyMap's current state, not dependent on catching any particular event --
  and diffs against a tracked-ids set to add/remove as needed. Also handles dimension travel the
  same way `PlayerTracker` does (remembers the last dimension, clears that dimension's entries
  before switching). This is a self-healing safety net on top of the still-useful live event path,
  not a replacement for it. **Confirmed fixed by the user actually rejoining the world and finding
  the waypoint still there**, not just "should work now."
- Icon rendering matches upstream's own unfinished ambition, not more: `BasicIconData.generic()`
  tinted with the waypoint's `getIconColor()` (bit-unpacked into r/g/b, defaulting to no tint if
  null). Upstream had a `// TODO: icon textures` for using each waypoint's actual custom icon via
  `getIconIdentifier()` and never got to it either -- left as the same known, matching gap rather
  than scope-creeping past what upstream itself shipped.

## Config screen (ModMenu + Cloth Config)

In-game config screen replacing the "edit `config/hudcompass.json` by hand" MVP note. Both
ModMenu and Cloth Config are `compileOnly` soft dependencies (mirroring the JourneyMap
integration's established pattern) -- neither is required at runtime; `config/hudcompass.json`
remains the fallback if neither is installed.

- Real versions confirmed via each project's own maven, not guessed: ModMenu `20.0.1`
  (`com.terraformersmc:modmenu`, from `https://maven.terraformersmc.com/releases/`) and Cloth
  Config `26.2.155` (`me.shedaniel.cloth:cloth-config-fabric`, from
  `https://maven.shedaniel.me/`) -- both real GAV coordinates from the mod authors' own maven
  repos, preferred over Modrinth's opaque hash-keyed coordinate for readability, matching this
  project's existing style for the JourneyMap dependency.
- `dev.gigaherz.hudcompass.integrations.modmenu.HudCompassModMenuPlugin` -- the `"modmenu"`
  entrypoint (same lazy-discovery pattern as `"journeymap"`: Fabric Loader only resolves it if
  ModMenu itself asks). Falls back to ModMenu's own `NullScreenFactory` sentinel (checking
  `FabricLoader.isModLoaded("cloth-config")` first) so the "Configure" button simply doesn't
  appear if Cloth Config isn't installed, rather than opening a broken screen.
- `dev.gigaherz.hudcompass.integrations.modmenu.HudCompassConfigScreen` -- the actual Cloth
  Config screen, kept in a separate class from the plugin above specifically so Cloth Config's
  types are never classloaded until presence is already confirmed. Two categories, Display and
  General, covering all 14 `ConfigData.Values` fields; wording/tooltips carried over from
  upstream's original `ModConfigSpec` comments. Binds directly against
  `ConfigData.getValues()`'s live fields (two small new public members added to `ConfigData` for
  this: `getValues()` and making `refresh()` public) rather than staging a separate copy -- Cloth
  Config already handles its own cancel/discard snapshot internally.
- **Verified via screenshots, not just compile success**: both categories render with the
  correct fields and correct current values (e.g. `195.0`/`200.0` fade/view distance, `Holding
  Compass`, `Same Team`). One apparent bug from the screenshots turned out not to be one: the
  tab whose content was showing appeared to have the *other* tab's label highlighted -- this is
  actually just Cloth Config's normal styling (the *currently selected* tab renders flat/disabled
  since you can't re-click it; the *other*, clickable tab keeps the raised button box) -- traced
  through Cloth Config's own `ClothConfigScreen`/`ConfigBuilderImpl` source (branch `v26.2` of
  `shedaniel/cloth-config`) to confirm before ruling it out as a non-issue.
- Smoke-tested by launching the client with the real ModMenu + Cloth Config jars dropped into
  `run/mods/` -- log showed all four (`hudcompass`, `journeymap`, `modmenu`, `cloth-config`) in
  the mod list and the resource-manager reload, no crash or classloading error.

## Waypoint editor GUI

The last item from the original deferred-work list. Add/edit/delete waypoints in a scrollable,
per-world-grouped, foldable list -- opened via the `key.hudcompass.edit_waypoints` keybind
(unbound by default, like the other HudCompass keybinds; set it in Controls first).

- **`dev.gigaherz.hudcompass.client.widget.ScrollPanel`** -- a from-scratch Fabric-native
  replacement for NeoForge's `net.neoforged.neoforge.client.gui.widget.ScrollPanel` (no Fabric
  equivalent exists). The scrolling algorithm (scrollbar math, drag/wheel handling, scissor
  bounds) is carried over faithfully from NeoForge's actual LGPL-2.1 source (fetched from
  `neoforged/NeoForge` at the `1.21.11` branch -- the newest available; a `26.x`-named branch
  doesn't exist in that repo, but this widget's core algorithm has been stable for years and MC
  version is irrelevant to it). Only the rendering surface needed adapting: MC 26.2 replaced the
  old `GuiGraphics`/`render()` split with `GuiGraphicsExtractor`/`extractRenderState()` -- the
  exact method names/signatures for that were cross-checked against upstream's own
  `ClientWaypointManagerScreen` source (already written against 26.1.2's version of this same
  API split), not guessed.
- **`dev.gigaherz.hudcompass.client.ClientWaypointManagerScreen`** -- ported near-verbatim from
  upstream otherwise (the full `ListItem`/`CompositeListItem`/`WorldListItem`/`WaypointListItem`/
  `NewWaypointListItem` hierarchy), with two simplifications following directly from this port's
  already-simplified data model (see that class's own javadoc for detail): no sync-listener
  re-population (this port's `PointsOfInterest.INSTANCE` has no such listener hook, and doesn't
  need one -- the only sync that matters happens well before a player could open this screen),
  and `getPlayerPositionScaled` drops upstream's custom-dimension teleport-scale lookup (needs a
  `ResourceKey<DimensionType>` this port doesn't track per world), keeping only the Nether's
  fixed 8:1 scale.
- **`UpdateWaypointsFromGui`** (network packet) + **`PointAddRemoveEntry`** -- the bulk-save
  packet driving this screen's Save button, using the same byte[]-wrapping workaround
  `SyncWaypointData` already established for the `RegistryFriendlyByteBuf`-dispatch-codec
  generics problem (see the `StreamCodec.map()`/`.cast()` wildcard-capture note further down)
  rather than hitting that problem fresh. Wired through `PointsOfInterest.applyUpdatesFromGui`,
  `ServerWaypointSync`'s receiver, and `HudCompass`'s payload registration -- same
  client-sends-if-server-has-mod-else-applies-locally pattern already used by `AddWaypoint`/
  `RemoveWaypoint`.
- **A real API removal caught via `javap` against the actual 26.2 jar, not assumed from
  upstream's 26.1.2 source**: `EditBox.setFilter(Predicate<String>)`, which upstream uses to
  restrict the X/Y/Z fields to numeric-looking input while typing, doesn't exist in MC 26.2 at
  all. Dropped the live-filtering calls entirely and kept only the existing save-time regex
  validation (`COORD_FORMAT` -- already present in upstream's own code as a second, independent
  check before actually parsing the typed value) -- functionally identical outcome to upstream's
  own behavior when the format doesn't match: the position simply doesn't update, no error shown.
- Opened via `Minecraft.setScreenAndShow(new ClientWaypointManagerScreen())`, gated on
  `mc.gui.screen() == null` (don't stomp another open screen) inside `ClientHandler`'s existing
  per-tick keybind-polling loop, alongside `ADD_WAYPOINT`/`REMOVE_WAYPOINT`.
- Lang keys for this screen (`text.hudcompass.waypoint_editor.*`, `key.hudcompass.edit_waypoints`)
  were already sitting unused in `en_us.json` from early in the project -- confirmed still
  matching what the ported screen actually needs, no changes required there.
- **Confirmed working by the user in-game**: edited a waypoint's coordinates and had it apply,
  folded/collapsed a world's waypoint list, and canceled an in-progress new-waypoint creation and
  had it correctly discarded (not added). Not independently verified by re-reading save files
  this time (unlike some earlier persistence bugs this project caught that way) -- if anything
  about saved waypoints looks off after using this screen, that's the first place to double-check.

## MC 26.2 API findings from this session (real, javap-verified — add to the shared porting notes)

These are new findings on top of what's already in the `reference-mc-26x-porting` memory (which
was compiled from 26.1.x→26.2 Fabric ports of *already-Fabric* mods). This session ported
*from NeoForge on 26.1.2*, so some findings are also just "how NeoForge concepts map to Fabric,"
not new 26.2 API changes:

- **MC 26.1+ ships already-named jars — no `mappings loom.officialMojangMappings()` line at
  all**, or Loom throws `Cannot use Mojang mappings in a non-obfuscated environment`. Confirmed
  by copying the working pattern from the DynamicSurroundingsFabric-26.2 port
  (`build.gradle` there has no `mappings` line whatsoever). Also: dependencies use plain
  `implementation`/`compileOnly`, not `modImplementation` — there's no remap step anymore.
- **Access widener header must say `accessWidener v2 official`**, not `v2 named` — this
  non-obfuscated flow expects the "official" namespace, not "named". `named` fails with
  `Expected official namespace for access widener entry, found: named`.
- **`GuiGraphicsExtractor` has no `submitGuiElementRenderState`/`peekScissorStack` methods in
  real MC 26.2**, even though upstream's 26.1.2-targeting code uses them (built around manually
  constructing `GuiElementRenderState` records). In 26.2 you just call the direct high-level
  methods already on `GuiGraphicsExtractor` instead: `fill(int,int,int,int,int)`,
  `blitSprite(RenderPipeline, TextureAtlasSprite|Identifier, x,y,w,h,color)`,
  `blit(RenderPipeline, Identifier, x,y,u,v,width,height,texWidth,texHeight)`. This let
  `HudOverlay` drop ~150 lines of custom render-state boilerplate entirely — a real
  simplification, not just a workaround. (Sub-pixel float positioning upstream used for crisp
  1px lines was rounded to `int` in the process — visually close enough for MVP, not
  pixel-identical.)
- **Fabric API 0.157.0+26.2 renamed/moved several modules** vs. older Fabric API versions people
  may remember:
  - `KeyBindingHelper` → `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper`
    (`.registerKeyMapping(...)`, not `.registerKeyBinding(...)`)
  - HUD rendering is a real, separate hook now:
    `net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry` /
    `.HudElement` (functional interface, single method `extractRenderState(GuiGraphicsExtractor,
    DeltaTracker)`) / `.VanillaHudElements` (has `BOSS_BAR`, not `BOSS_HEALTH_BAR`).
    `HudElementRegistry` only has `addFirst`/`addLast`/`attachElementBefore`/
    `attachElementAfter`/`removeElement`/`replaceElement` — no `addAfter`.
  - **`replaceElement(Identifier, Function<HudElement, HudElement>)`** is Fabric's equivalent to
    NeoForge's `RenderGuiLayerEvent.Pre`/`.Post` on a named layer -- the function receives the
    *original* element (vanilla's own, or whatever another mod last replaced it with) and returns
    a new one to install in its place, so wrapping (translate the pose stack, then delegate to
    the original, matching upstream's boss-bar-push-down trick) is just `original -> (graphics,
    delta) -> { ...; original.extractRenderState(graphics, delta); ...; }`. An earlier session
    assumed this had no equivalent and left the boss-bar-avoidance behavior unported -- that
    assumption was wrong, corrected this session (see `HudOverlay`'s javadoc and `ClientHandler`).
  - `ClientTickEvents.START_CLIENT_TICK`/`END_CLIENT_TICK` unchanged; confirmed present.
  - `ClientPlayConnectionEvents.JOIN` unchanged; confirmed present.
- **New this session — Fabric Networking API v1 (`fabric-networking-api-v1` 6.3.3+72073ef09e,
  pinned by fabric-api 0.157.0+26.2's own POM -- confirmed by grepping the actual `.pom` file in
  the Gradle module cache, not guessed) surface, javap-verified against the real jar**:
  - `PayloadTypeRegistry.serverboundPlay()` / `.clientboundPlay()` — **not** `playC2S()`/`playS2C()`
    as older Fabric API naming might suggest. Both return a registry typed to
    `RegistryFriendlyByteBuf`, so a plain `StreamCodec<FriendlyByteBuf, T>` (e.g. for a
    field-less/no-registry-access payload like a hello packet) needs widening -- see the
    `StreamCodec.cast()` note below.
  - `ServerPlayNetworking`/`ClientPlayNetworking` are symmetric: `registerGlobalReceiver(Type,
    (payload, context) -> ...)`, `send(player, payload)` / `send(payload)`, `canSend(player, Type)`.
    Handler lambdas run on the main thread (server or client respectively) already -- no
    NeoForge-style `context.enqueueWork(...)` equivalent needed or available.
  - `ServerPlayConnectionEvents.JOIN`/`DISCONNECT` (from the same networking-api-v1 module, not
    a separate lifecycle-events module) give `(ServerGamePacketListenerImpl handler,
    PacketSender sender, MinecraftServer server)` / `(handler, server)` -- the player is
    `handler.player`, a public field.
  - `ServerTickEvents.END_SERVER_TICK` lives in `fabric-lifecycle-events-v1` (pinned version
    4.1.3+4575b05f9e by the same POM lookup), giving just `(MinecraftServer server)` -- iterate
    `server.getPlayerList().getPlayers()` yourself for a per-player per-tick hook, there's no
    dedicated per-player tick event in this version.
  - **`StreamCodec<ByteBuf, T>.map(...)` followed by `.cast()` to widen to
    `StreamCodec<RegistryFriendlyByteBuf, T>` fails to compile with a wildcard-capture error**
    when the mapped type itself contains a wildcard (e.g. `StreamCodec<..., MyType<?>>`) -- javac
    can't unify the independent `?` captures across the map lambdas and the final assignment,
    even with an explicit `.<RegistryFriendlyByteBuf>cast()` type witness. Workaround: skip
    `.map()`/`.cast()` entirely and hand-write the codec via `StreamCodec.of((buf, value) -> ...,
    buf -> ...)` with the target buffer type spelled out directly in the field's declared type --
    no wildcard-capture issue since there's no intermediate generic inference step.
- **`GameRenderer.getMainCamera()` → `mainCamera()`** — already documented in the shared
  porting-notes memory from the earlier 26.1.x→26.2 ports; reconfirmed here.
- **New: `Gui#nextContextualInfoState()` moved to a new `Hud` class**, becoming
  `Hud#nextContextualInfoState()` returning `Hud$ContextualInfo` (was `Gui$ContextualInfo`).
  This is the same "stuff moved from `Gui` to a new `Hud` class" pattern the shared notes already
  documented for `Minecraft.gui.setScreen`/`Minecraft.gui.hud.isHidden()` — apparently a broader
  rewrite, not isolated to those two methods. **Caught this via the mandatory post-compile mixin
  audit** (javap-verifying the mixin's target class/method against the real jar) — a clean
  compile did NOT catch it, since the mixin target is resolved by string at Mixin-apply time, not
  by the Java compiler. Confirmed via bytecode decompile that
  `ClientWaypointManager.hasWaypoints()` is still the sole call inside the new
  `Hud#nextContextualInfoState()` body (ordinal=0 implicit match is still safe for the
  `@ModifyExpressionValue`).
- **New: `Minecraft.getWaypointStyles()` doesn't exist — it's `Minecraft.gui.hud.getWaypointStyles()`**
  (found via bytecode decompile of the vanilla `LocatorBar` class, which is itself the new home
  of the locator-bar rendering logic, at `net.minecraft.client.gui.contextualbar.LocatorBar`).
  Same `Minecraft.gui.<thing>` / `Gui.hud.<thing>` relocation pattern as above.
- **New: `WaypointStyle.sprite(float)` returns a plain `Identifier`, not a `TextureAtlasSprite`**
  — matches the `blitSprite(RenderPipeline, Identifier, ...)` overload directly, no atlas lookup
  needed.
- Dependency versions reused as-is from the verified-working DynamicSurroundingsFabric-26.2 /
  FBP Reforged ports: Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, Loom 1.17.+, Gradle wrapper
  9.5.1, Java 25.
- **New this session: `Entity#addAdditionalSaveData(ValueOutput)`/`#readAdditionalSaveData
  (ValueInput)` are `protected` (not `private`) on both `Entity` and the `ServerPlayer` override**
  (javap-confirmed on both the client-merged jar's and the dedicated-server jar's copy of
  `ServerPlayer.class`, byte-for-byte identical signatures) -- mixin-injectable directly with no
  access widener needed, since a `@Mixin` targeting the same class can reach `protected` members
  without one (access wideners are only for referencing an inaccessible member *from unrelated
  code*). This is the piggyback point used for the new per-player waypoint persistence instead of
  a NeoForge-style attachment.
- **New this session: `ValueOutput#child(String)` returns a `ValueOutput` directly; the
  `ValueInput` side is `Optional`** (`ValueInput#child(String): Optional<ValueInput>`, plus a
  non-optional `childOrEmpty(String)` convenience variant) -- the single-child-tag counterpart to
  the already-known `childrenList(String)`/`childrenListOrEmpty(String)` list API, useful for
  nesting one arbitrary sub-object's data (like this mod's whole waypoint store) under a single
  named tag inside someone else's save data, as opposed to a list of many.
- **New: `EditBox.setFilter(Predicate<String>)` (used by upstream's 26.1.2-targeting waypoint
  editor to restrict coordinate fields to numeric input) doesn't exist on the real MC 26.2
  `EditBox`** -- javap-confirmed absent entirely, not renamed. No direct replacement; the port
  just dropped live filtering and relied on save-time regex validation instead (see "Waypoint
  editor GUI" above).
- **New: `Minecraft.setScreen` doesn't exist as a top-level method either** -- the actual API
  surface, confirmed via javap, is `Minecraft.gui.setScreen(Screen)` (matches the already-known
  `Minecraft.gui.<thing>` relocation pattern) *or* the higher-level `Minecraft.setScreenAndShow
  (Screen)`, which exists directly on `Minecraft` itself. Used `setScreenAndShow` for opening the
  waypoint editor from a keybind, since it reads as the more complete, user-facing entry point
  (as opposed to `gui.setScreen`, which reads as the lower-level primitive vanilla itself likely
  calls internally) -- an inference from the name and API shape, not confirmed by decompiling
  vanilla's own inventory-key handling.
  Checking whether a screen is already open uses `Minecraft.gui.screen() == null`, not a field
  read (`Minecraft` has no public `screen` field).
- **New: `Screen.addWidget(T)` vs `Screen.addRenderableWidget(T)`** -- both still exist, same
  distinction as older MC versions: `addRenderableWidget` registers a widget for both input
  handling *and* the screen's own automatic per-frame render pass, while `addWidget` only
  registers it for input/focus handling. Used `addWidget` for the waypoint editor's `ScrollPanel`
  specifically because that screen's `extractRenderState` override already calls
  `scrollPanel.extractRenderState(...)` manually itself -- using `addRenderableWidget` there
  would have rendered it a second time via the screen's own automatic pass.
- **New: `Screen.extractMenuBackground(GuiGraphicsExtractor, int x, int y, int w, int h)`**
  (instance method, protected) and the static **`Screen.extractMenuBackgroundTexture
  (GuiGraphicsExtractor, Identifier, int, int, float, float, int, int)`** are the 26.2 names for
  what older versions called `renderMenuBackgroundTexture` -- both javap-confirmed present and
  used in the waypoint editor port (the instance method for the screen's own background behind
  the scroll list; the static one is what `ScrollPanel`'s own default `drawBackground` calls,
  ported from NeoForge's older `Screen.renderMenuBackgroundTexture` call of the same purpose).

## In-game verification status

Launched via `./gradlew.bat runClient` and playtested by the user. **Confirmed working:**
- No Mixin apply-time crash (the `Gui`→`Hud` retarget from this session's audit was correct)
- Mod loads cleanly (`hudcompass` listed in Fabric's resource-manager reload log,
  `config/hudcompass.json` written on init)
- The compass HUD renders (after discovering the default `displayWhen: HOLDING_COMPASS` config
  gates it — that's correct upstream-matching behavior, not a bug; testing switched it to
  `ALWAYS` to isolate the HUD-registration check first)
- Keybinds work; add/remove waypoint both function and waypoints render correctly on the compass
- Default `HOLDING_COMPASS` display gating: `run/config/hudcompass.json`'s `displayWhen` was
  reverted from the `ALWAYS` testing override back to `HOLDING_COMPASS` (the code default, per
  `ConfigData.java`), and the user confirmed in-game that the HUD correctly shows only while
  holding a `minecraft:compass` (checked via the `hudcompass:makes_hudcompass_visible` item tag)
  and hides otherwise
- Waypoint persistence across a relog: confirmed three times now. First against the old
  client-local-disk MVP path (`ClientWaypointDatabase`) earlier in the project. Then, after this
  session's multiplayer-sync work landed and made the server-authoritative path (see
  "Multiplayer waypoint sync" above) the one actually exercised in singleplayer too, re-confirmed
  against *that* path specifically — including catching and fixing a real bug where it silently
  didn't work at first (see that section for the full diagnosis via direct save-file inspection).
  Finally confirmed a third time over a real two-instance network connection (not just
  singleplayer's integrated server) — see "Two-instance LAN test" below.
- Locator-bar suppression (`HudMixin` hiding the vanilla locator bar when the compass HUD is
  visible and `disableLocatorBarWhen` is `WHEN_VISIBLE`) — confirmed via the two-instance LAN
  test below, after an earlier singleplayer-only attempt this session correctly found nothing to
  suppress (see that test's writeup for why: vanilla's locator-bar/waypoint system tracks *other
  connected players*, not death markers as originally assumed -- there's no second player to
  track in singleplayer).
- `PlayerTracker` bypassing the vanilla `locatorBar` gamerule as designed — confirmed via the
  two-instance LAN test (see below): `/gamerule locatorBar false` removed the other player from
  vanilla's own crosshair marker but not from the compass HUD.
- The in-game config screen (ModMenu + Cloth Config) — confirmed via screenshots showing both
  categories with correct fields and correct current values (see "Config screen (ModMenu + Cloth
  Config)" above).
- The waypoint editor GUI — confirmed by the user: coordinate edits apply, fold/collapse works,
  Cancel correctly discards an in-progress new waypoint (see "Waypoint editor GUI" above).

## Two-instance LAN test (this session)

Ran two Fabric dev clients simultaneously to get a real second player to test against, for both
the multiplayer waypoint sync work above and the previously-untestable-solo locator-bar
suppression. A new Loom run config was added to `build.gradle` for this and left in place for
future reuse:

```groovy
client2 {
    client()
    setConfigName('Fabric: Client 2')
    runDir('run2')
}
```

This exposes a `runClient2` Gradle task alongside the existing `runClient`, using a separate
`run2/` game directory so the two instances' config/log/options files don't collide when both run
at once. Each dev-client launch already gets its own random offline-mode username (confirmed
across many launches this session — "Player303", "Player239", "Player791", etc.), so the two
instances were naturally distinct players with no extra setup needed there.

**Test procedure**: instance 1 hosts a singleplayer world and uses Open to LAN; instance 2
Direct-Connects to `localhost:<port>`.

**Results, all confirmed working:**
- Waypoints are correctly private per player — adding one on instance 1 does not appear on
  instance 2's compass, and vice versa. This matches upstream's actual design (this feature was
  always about making each player's *own* waypoints server-authoritative, not about sharing
  waypoints between players) and rules out a plausible failure mode (e.g. accidentally keying the
  server-side store by something other than player UUID, causing cross-player bleed).
- Each instance sees the other player as a head-icon waypoint on their own compass HUD — this is
  `LocatorBarPoints` (the vanilla-locator-bar-mirroring feature) working correctly against a real
  second player for the first time; previously only verified against the code/decompiled vanilla
  classes, never observed live.
- Waypoint persistence survives a real disconnect/reconnect (instance 2 left the server and
  rejoined; its waypoint was still there) — the strongest persistence confirmation yet, since it
  exercises the actual network disconnect path rather than singleplayer's integrated-server quit.
- Locator-bar suppression: with both players near each other, the vanilla locator bar (near the
  crosshair) showed a marker for the other player. Holding a compass (making the HudCompass HUD
  visible) made that vanilla marker disappear; putting the compass away made it reappear. Exactly
  the intended behavior, and the last previously-unverified item from earlier in this project.

## Attribution / licensing (matters to this user — see prior porting-workflow feedback)

- `LICENSE.txt` copied byte-for-byte unmodified from upstream (BSD 3-clause, David Quintana /
  gigaherz, 2020).
- `fabric.mod.json`: `authors: ["gigaherz"]`, `contributors: ["Romoslayer (Fabric 26.2 port)"]`,
  `description` explicitly states "Unofficial Fabric port... not affiliated with or endorsed by
  the original author", and `contact.homepage`/`sources`/`issues` all point at this port's own
  repo, not upstream's.
- `README.md` credits gigaherz for the original mod, states clearly this is a Fabric port, and
  carries an explicit disclaimer block: not affiliated with/endorsed by/maintained by gigaherz,
  with a pointer to the real upstream repo and an explicit "report bugs in this port here, not
  upstream" line.
- Public GitHub repo: [Romoslayer/HudCompass-Fabric-26.2](https://github.com/Romoslayer/HudCompass-Fabric-26.2).
  Not published to Modrinth/CurseForge yet -- if that happens, carry the same disclaimer language
  into that listing's description too, and make sure its "source"/"issues" links point here.
