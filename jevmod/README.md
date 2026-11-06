# JevMod — Neural Sandbox

Minecraft Java **1.21**, NeoForge **21.0.167**, Java **21**. Existing mod architecture continued with editable brains, sensors, typed AI decisions and sandboxed JavaScript powers.

The full Portuguese [power guide](src/main/resources/jevmod-defaults/POWERS.md) covers commands, every bundled parameter, custom scripts, the script API and body/transformation limits. It is also installed as `config/jevmod/POWERS.md` when starting a world with the updated jar.

## Update 2

Press **F8** or `/jevui` inside a single-player world for the light blue/white code editor and orbitable 2D/3D brain graph. `/give @s jevmod:berco` provides the two-block body cradle; right-click either half to choose vanilla/mod mob appearances, PNG skins or custom OBJ geometry. Generation advances by game ticks and reveals geometry from bottom to top; completed bodies can receive an existing brain. The Portuguese [Update 2 guide](src/main/resources/jevmod-defaults/UPDATE2.md) covers import/drop, timed assembly, saving and limitations.

## Update 3 — Imagination

`/give @s jevmod:mind_core` adds the independent Mind core. Right-click it to choose JARVIS, Friday or Ultron, compose systems, connect a body UUID and bind named block ports. The Mind keeps running when its body's energy runs out. The same in-game workbench edits `minds/`, `systems/` and `apis/`; Java addons extend the native API or add language engines through `MindExtensions`.

Bundled examples implement battery depletion, a coal-fed reactor, iron-nugget home defense with actual ammunition and projectile collisions, programmable wireless and JSON redstone rules. Select `reactor,energy` and connect `reactor` to a coal chest; select `home_defense` and connect `ammo` to an iron-nugget chest. These systems work offline without an API key. The `assistant` system uses the configured OpenRouter Jev provider only on explicit messages such as `@jarvis pare o corpo`.

The Portuguese [Update 3 guide](src/main/resources/jevmod-defaults/UPDATE3.md), installed as `config/jevmod/UPDATE3.md`, covers hooks, the complete Mind API, command recipes, JSON rules and persistence. The separate [Java addon example](extension-example/README.md) demonstrates custom modules and APIs called from Java, JS and JSON. Build it with `dev.cmd extensionExampleJar`; install `imagination-example-0.1.0.jar` alongside the main jar to use `java_counter`, `java_api_client` and `java_api_rules`. Java is compiled outside Minecraft; JSON and JS can be edited in-game.

## Update 0.3.5 — QoL

The Mind can now start/cancel/query/release the Berço through `jev:core`, returning the new body's UUID and optionally binding it. Body references expose position, health and effect queries; accepted damage/death records persist on the body and bound Mind after removal. Recipes support 90% damage resistances and arbitrary metadata. Remote-center scans search loaded living entities up to 512 blocks and return up to 256 sorted results.

The in-game editor adds history-backed, confirmed deletion and JSON/JS drag/drop with destination previews and overwrite confirmation. Explicit deletions stay deleted on restart. System definitions support `enabled:false`; the default is true. The bundled `body_factory` script demonstrates timed construction and adaptation to the previous body's fatal damage; it is disabled until the player opts in. The Portuguese [0.3.5 guide](src/main/resources/jevmod-defaults/UPDATE035.md) documents all eight changes, examples, import routing and compatibility.

## Build and try

On this Windows workspace, run `dev.cmd build`. Override `JEVMOD_JAVA_HOME` if your JDK 21 is elsewhere and `JEVMOD_CACHE` if needed. The distributable is `build/libs/jevmod-0.3.5.jar`; Rhino is bundled.

For an existing offline Minecraft profile with the matching NeoForge loader, copy the jar to that profile's `mods` folder. Back up worlds first. For development, `dev.cmd runClient` opens an isolated client under `run/`; create a disposable single-player world with commands enabled.

In chat:

```text
/jev attach jarvis
/jev on laser
/jev param laser color purple
/jev off laser
/jev on shield
/jev on dash
/jev on lightning
/jev status
```

Manual power commands work without an API key. Laser and lightning only damage hostile mobs. The shield grants resistance and renders a particle shell; dash adds motion in your look direction. All visuals currently use coloured vanilla particles.

## AI and customization

The default provider is **OpenRouter**, with **`~typesafe/jev-latest`**, using `OPENROUTER_API_KEY` from the process environment before launching. The [official OpenRouter guide](https://openrouter.ai/blog/tutorials/how-to-use-jev/) documents the alias and `POST https://openrouter.ai/api/alpha/decisions` for native typed Jev questions. The mod selects this protocol for Jev model IDs; other models use the chat API. An attached brain periodically sends sensor state and chat to the selected provider and may incur charges. Missing keys are reported without disabling manual commands.

For profiles created with the older Gemini default, keep `"provider": "openrouter"` and change `openrouter.model` to `"~typesafe/jev-latest"` in `config/jevmod/jevmod.json`. Keep `api_key_env: "OPENROUTER_API_KEY"` and `base_url: "https://openrouter.ai/api/v1"`; the adapter derives the separate Decisions URL. Preserve other settings. Remove/update any per-brain provider/model overrides, then `/jev reload`. Existing configuration files are preserved when updating the jar; a new build does not overwrite them. TypeSafe direct access remains an optional provider, with its own key.

Try `/jev say ativa o escudo` then `/jev think`; `/jev last` shows decisions. `/jev provider` shows the provider. Brain JSON files may override provider/model and control the think interval.

The first world startup writes examples to `config/jevmod`: brains `jarvis` and `sentry`; powers `laser`, `shield`, `dash`, `lightning`; sensor `threat_scan.js`. Edit these files and run `/jev reload`. Existing files are never overwritten; deleted examples return at the next server startup. `/jev errors` reports invalid content.

## Verification and limits

`dev.cmd test` runs 25 definition, script, body, geometry, import, Mind API and local HTTP contract tests without external inference or API keys. `dev.cmd runGameTestServer` runs fourteen tests in an isolated Minecraft server: body assembly/save/release, Mind persistence while its body stops, real fuel consumption/recharging, wireless/redstone, JSON rules, nugget projectile damage, separate Java module/API calls from Java/JS/JSON, a typed persona decision using a fixed provider, Mind-operated Berço, body references/resistance/damage persistence, remote-center radar, disabled modules/default deletion, and the editable factory rebuilding an adapted body. All passed. This proves the AI event path and policy, not live model behavior; the earlier live OpenRouter probe is recorded separately in MODLOG.md.

`dev.cmd runWorkbenchCheck` creates a disposable offline world, renders the real editor and body previews, imports original OBJ/PNG fixtures, captures screenshots under `run/showcase`, then exits. `dev.cmd runImaginationCheck` similarly captures the Mind screen, editor and machinery scene using Minecraft's own render target. `dev.cmd runQolCheck` exercises actual JSON/JS drop, import and delete callbacks and Mind-triggered assembly in another disposable world. These checks use no OS mouse/keyboard input. Manual power visuals and arbitrary third-party mob renderers still need their own checks. The `runDemo` task attaches a brain after joining a world but does not create a world automatically.

This continuation was made with an AI coding agent. It uses NeoForge, Minecraft's particle API and Mozilla Rhino. No game assets or decompiled game source are included. No generated art or fal assets were used.
