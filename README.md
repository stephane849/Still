# Still

A calm, black-and-white navigation app for e-ink phones like the Mudita Kompakt, built with the Mudita Mindful Design (MMD) library.

Still opens on a map: black roads on white, nothing else. Search for a place, see the route, and drive with one instruction at a time in large type, with voice prompts doing most of the work.

Still is a fork of [CalmDirectory](https://github.com/davidraywilson/CalmDirectory) by David Ray Wilson (v1.0.0-alpha.2), which provides the place search and details screens. Like CalmDirectory, it is licensed under the GNU GPL v3.

## What works in v0.1

- Map home screen with zoom and recenter buttons, no rotation or tilt, and instant camera moves to limit e-ink ghosting.
- Up to four saved places along the bottom of the map.
- Place search from CalmDirectory (HERE or Google Places), with a new Directions button.
- Route preview with time, distance, main road and arrival time.
- Turn-by-turn drive screen with voice prompts and automatic rerouting.

## Map and routing data

- **Map tiles:** online from [OpenFreeMap](https://openfreemap.org) by default. For offline use, build an OpenMapTiles-schema PMTiles file (for example `planetiler --area=ontario --output=map.pmtiles`) and copy it to `Android/data/io.github.stephane849.still/files/map.pmtiles` on the phone.
- **Routing:** any OSRM-compatible server, set in Settings. The default is the public OSRM demo server, which is meant for light testing only. A self-hosted OSRM server is the intended setup.
- Map data © OpenStreetMap contributors.

## Building

- Open in Android Studio, or let GitHub Actions build it: every push to `main` produces a debug APK under the workflow run's artifacts.
- Place search with HERE needs a HERE API key: add `HERE_API_KEY=...` to `local.properties`, or set a `HERE_API_KEY` repository secret for CI builds.

## Roadmap

- v0.2: Android Auto (navigation template, night mode on the car screen only).
- v0.3: live traffic (TomTom) and Ontario 511 incidents.
- Later: on-device offline routing.
