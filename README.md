# HUD Compass (Fabric port)

A HUD compass overlay that shows waypoints, cardinal directions, and (via the vanilla locator
bar) death markers and other tracked waypoints — "see where you go."

This is a **Fabric port for Minecraft 26.2** of [gigaherz's HudCompass](https://github.com/gigaherz/HudCompass),
originally written for NeoForge. All credit for the original design, art, and waypoint/icon
architecture belongs to the original author; this port adapts it to run on the Fabric loader and
against MC 26.2's APIs.

## Status

What's implemented:

- The compass HUD itself (cardinal directions, waypoint icons/labels, above/below arrows)
- Client-local waypoints: add/remove via keybind, persisted server-authoritatively per player
  (survives relogging, and now syncs over a real multiplayer connection, not just singleplayer)
- The vanilla locator bar's waypoints also show on the compass, and are suppressed while the
  compass HUD is visible (configurable)
- Server-side waypoint sources: bed/respawn-anchor spawn point, vanilla map banner/decoration
  markers, other-player tracking (filtered by team/all/none)
- JourneyMap integration: mirrors JourneyMap's own waypoints onto the compass, one-way/read-only
- An in-game config screen via ModMenu + Cloth Config (both optional — `config/hudcompass.json`
  can still be hand-edited if neither is installed)
- An in-game waypoint editor (add/edit/delete, grouped by world) via the `edit_waypoints`
  keybind — upstream's version depends on a NeoForge-only `ScrollPanel` widget, replaced here
  with a from-scratch Fabric equivalent

Every feature from the original NeoForge version has now been ported.

## License

BSD (3-clause), unmodified from upstream — see [LICENSE.txt](LICENSE.txt).
Copyright (c) 2020, David Quintana (gigaherz).

## Credits

- **gigaherz** (David Quintana) — original mod
- **Romoslayer** — Fabric 26.2 port
