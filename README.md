# HUD Compass (Fabric port)

A HUD compass overlay that shows waypoints and cardinal directions — "see where you go."

This is the **Minecraft 1.20.1 build** of an **unofficial Fabric port** of
[gigaherz's HudCompass](https://github.com/gigaherz/HudCompass), originally written for NeoForge.
All credit for the original design, art, and waypoint/icon architecture belongs to the original
author; this port adapts it to run on the Fabric loader. The port was written for MC 26.2 (see the
`master` branch); this `mc1.20.1` branch backports it.

> **Disclaimer:** This is a community port, not the original mod. It is **not affiliated with,
> endorsed by, supported by, or maintained by** gigaherz (David Quintana) or the upstream
> HudCompass project. For the original NeoForge mod, go to
> [gigaherz/HudCompass](https://github.com/gigaherz/HudCompass). Report bugs found in this Fabric
> port here, not on the upstream repo — they're two separate, independently-maintained codebases.

## Status

What's implemented:

- The compass HUD itself (cardinal directions, waypoint icons/labels, above/below arrows)
- Client-local waypoints: add/remove via keybind, persisted server-authoritatively per player
  (survives relogging, and now syncs over a real multiplayer connection, not just singleplayer)
- Server-side waypoint sources: bed/respawn-anchor spawn point, vanilla map banner/decoration
  markers, other-player tracking (filtered by team/all/none)
- JourneyMap integration: mirrors JourneyMap's own waypoints onto the compass, one-way/read-only
- An in-game config screen via ModMenu + Cloth Config (both optional — `config/hudcompass.json`
  can still be hand-edited if neither is installed)
- An in-game waypoint editor (add/edit/delete, grouped by world) via the `edit_waypoints`
  keybind — upstream's version depends on a NeoForge-only `ScrollPanel` widget, replaced here
  with a from-scratch Fabric equivalent

Every feature from the original NeoForge version has been ported, with one exception on this
branch: the vanilla locator bar mirroring (and its show/hide settings) is absent, because the
locator bar itself only exists from Minecraft 1.21.6 onward.

## License

BSD (3-clause), unmodified from upstream — see [LICENSE.txt](LICENSE.txt).
Copyright (c) 2020, David Quintana (gigaherz).

## Credits

- **gigaherz** (David Quintana) — original mod
- **Romoslayer** — Fabric 26.2 port, 1.20.1 backport
