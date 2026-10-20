# Still

A calm, black-and-white navigation app for e-ink phones like the Mudita Kompakt, built with the Mudita Mindful Design (MMD) library.

Still opens on a map: black roads on white, nothing else. Search for a place, see the route, and drive with one instruction at a time in large type, with voice prompts doing most of the work.

Still is a fork of [CalmDirectory](https://github.com/davidraywilson/CalmDirectory) by David Ray Wilson (v1.0.0-alpha.2), which provides the place search and details screens. Like CalmDirectory, it is licensed under the GNU GPL v3.

## What works

- Opens on the map, with zoom and recenter buttons, no rotation or tilt, and instant camera moves to limit e-ink ghosting.
- Search like Google Maps: tap "Where to?" and type, or pick a category or saved place. Search is CalmDirectory's (HERE or Google Places).
- Up to four saved places along the bottom of the map.
- Route preview with time, distance, main road, arrival time, traffic delay and road events.
- Driving with the map: it follows you with the route drawn, the next turn in a card on top, voice prompts and automatic rerouting.
- Android Auto: saved places list, turn-by-turn navigation template over Still's own map, black roads on white by day and white on black at night (car screen only). Ending a drive on the phone or the car ends it in both.
- Traffic: with a TomTom key, routes account for live traffic and slow sections show as a dashed route line. With an Ontario 511 key, roadwork, closures and collisions on the route are marked and announced.

## Map and routing data

- **Map tiles:** online from [OpenFreeMap](https://openfreemap.org) by default. For offline use, build an OpenMapTiles-schema PMTiles file (for example `planetiler --area=ontario --output=map.pmtiles`) and copy it to `Android/data/io.github.stephane849.still/files/map.pmtiles` on the phone.
- **Routing:** any OSRM-compatible server, set in Settings. The default is the public OSRM demo server, which is meant for light testing only. A self-hosted OSRM server is the intended setup.
- Map data © OpenStreetMap contributors.

## Building

- Open in Android Studio, or let GitHub Actions build it: every push to `main` produces a debug APK under the workflow run's artifacts.
- Place search with HERE needs a HERE API key: add `HERE_API_KEY=...` to `local.properties`, or set a `HERE_API_KEY` repository secret for CI builds.

## Android Auto

Still is sideloaded, so Android Auto only lists it after you turn on developer mode in Android Auto (tap the version number ten times), then in its developer settings enable "Unknown sources".

## Traffic keys

- TomTom: free developer key from developer.tomtom.com, entered in Settings. Without it, Still routes through OSRM with no traffic.
- Ontario 511: free developer key from 511on.ca (Developers), entered in Settings. The API allows ten calls a minute; Still asks once per route.

## Roadmap

- Search from the car screen by voice.
- On-device offline routing.
