# Combat Info — Research & Build Brief

A RuneLite Plugin Hub plugin. Standalone replacement for the stock **Opponent
Information** plugin, with a better overlay and overhead rendering, covering both
NPCs and PvP opponents.

Work through the phases in order. Phase 0 and Phase 0.5 gate everything else —
report findings before writing plugin code.

---

## What this plugin is not

**It does not talk to questpath, or to any server, beyond the single hiscores
lookup described in Phase 0.**

This started as one idea — a RuneLite plugin for the questpath account — and was
split into two on purpose. Bundling an account-sync feature into a combat overlay
would be a bad trade in three separate ways:

- **Review surface.** This plugin's entire compliance argument is that it makes
  at most one hiscores request, in direct response to the user engaging one
  target. Adding an outbound data channel to a personal web app puts the whole
  plugin in a different review category.
- **Trust.** A combat overlay that also uploads your account state is the kind of
  thing users read the source of before installing, if they install it at all.
- **Audience.** A combat overlay is for everyone. An account-sync plugin is, for
  now, for one person.

The sync plugin lives in its own repo with its own brief. If you find yourself
adding an HTTP client here, stop: it belongs in the other one.

---

## Phase 0 — Compliance research

Most of the binding constraints are already in this repo. **Read `AGENTS.md`
first** — the template ships RuneLite's own rules list, and it is more specific
than the wiki pages. The ones that decide this plugin's shape:

> - No level-based PvP player indicators (highlighting attackable players or
>   those within level range)
> - No opponent freeze duration indicators
> - No PvP target scouting information
> - No identifying an opponent's opponent
> - No player group summaries (attackable counts, prayer usage, etc.)
> - No attack counters *(listed under boss restrictions)*
> - No exposing player information over HTTP
> - No crowdsourcing data about other players

Then read, and summarise what is *new* relative to the above:

- `https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features`
- `https://oldschool.runescape.wiki/w/Update:Third_Party_Client_Guidelines`
- `https://legal.jagex.com/docs/rules/macro-and-client-features-not-permitted`
- `https://github.com/runelite/plugin-hub` (README + review guidelines)

### Out of scope — do not implement, do not propose workarounds

- Any highlighting, icon, or indicator based on combat level, wilderness level,
  or whether a player is attackable.
- Hiscores lookups for any player other than the single currently engaged
  opponent. Preemptive or vicinity-wide scanning violates Jagex's rule that each
  content request be in direct response to a user request.
- Opponent freeze timers; PK/skull warnings.
- Conditional menu entry removal or menu option changes on players.
- Any network transmission of other players' data.
- Reflection, JNI, subprocesses, or runtime-downloaded code. Java only — no
  Kotlin or Scala.

### In scope, with precedent

Hiscores HP-level lookup for the **single** actor set by `InteractingChanged`
where `event.getSource() == client.getLocalPlayer()`.

Precedent: `net.runelite.client.plugins.opponentinfo.OpponentInfoOverlay` in the
base client already does this for player opponents, resolving max HP from
`HiscoreSkill.HITPOINTS`. The lookup must be async and cached.

**Hard invariant:** exactly one hiscores lookup per engagement. If any design
would require a second concurrent lookup, stop and flag it rather than building
it. That single-target gate is the entire compliance basis for this feature.

### Phase 0 findings — completed 2026-08-26

Sources read: RuneLite `Rejected-or-Rolled-Back-Features`; Jagex Third Party
Client Guidelines (via the OSRS Wiki mirror — `secure.runescape.com` returns
HTTP 403); `legal.jagex.com` macro-and-client-features rules; plugin-hub
README; RuneLite wiki `Plugin-Hub-Review` and
`Information-about-the-Plugin-Hub`; base-client `opponentinfo` and `hiscore`
sources.

- The clause this lookup rests on, from Jagex's rules: "each page/content
  request should only be in direct response to a user request each time",
  alongside a prohibition on "repeated page/content requests from our
  website". Not in `AGENTS.md`. It is the sentence a reviewer applies.
- The hiscores endpoint is `services.runescape.com` / `secure.runescape.com`
  — Jagex's own servers, not a third party. `AGENTS.md`'s mandatory warning
  string about "a 3rd-party server not controlled or verified by RuneLite
  developers" is therefore inaccurate for this feature. Do not paste it
  verbatim; write a warning that says what is actually sent.
- The base client already gates on
  `event.getSource() != client.getLocalPlayer()` in
  `OpponentInfoPlugin.onInteractingChanged`. This brief's gate matches
  precedent exactly.
- `HiscoreManager` is an `@Singleton` over a shared Guava cache
  (`maximumSize(128)`, `expireAfterWrite(1, HOURS)`, a `NONE` sentinel for
  unranked). The cache is shared with the stock plugin and the Hiscore
  plugin, so enabling both cannot double-request, and `lookupAsync` is safe
  to call per-frame. Unranked degrades to `null` cleanly, as required above.
- Jagex's list carries a catch-all: "Any features which act similarly to
  those described in the above list can also be considered unacceptable."
  Absence from the list is not permission.
- RuneLite rejects generic player highlighting, not only the level-based
  kind, citing harassment and private-message-mode privacy. Wider than the
  `AGENTS.md` wording implies.
- Plugin Hub review has explicit non-goals: it does not check functionality,
  performance, or the factual accuracy of displayed information. The "honest
  uncertainty" principle in this brief is a self-imposed quality bar, not a
  review requirement, and will not earn credit in review.
- Resources ship inside a jar. Use `Class.getResourceAsStream`, never
  `getResource`.
- Icon, if added: `icon.png` at the repository root, max 48x72 px.

Verdict: no part of Phase 2 requires a second concurrent lookup. The hard
invariant holds.

---

## Phase 0.5 — Measure before building

On a PvP world, instrument and log `Actor.getHealthScale()` and
`Actor.getHealthRatio()` for player actors across a range of HP levels.

Determine whether `maxHealth <= healthScale` holds for typical PvP hitpoints
levels. The base client's recovery math is exact only under that condition;
above it, the result is an interval and the stock plugin silently returns the
midpoint.

Report the achievable precision before any overlay work. If the result is an
interval, the overlay must present a range or a percentage. It must never render
an estimated value formatted as an exact integer.

Note the testing rule in `AGENTS.md`: you cannot verify in-game behaviour
yourself, and must not try. This phase is instrumentation you write and the
**user** runs.

### Phase 0.5 findings — completed 2026-08-26

Measured with HealthScaleProbe against live NPCs and the local player.
Raw data: `.runelite/combat-info/health-scale-probe.csv`.

**`healthScale` is 30 for every actor observed** — four distinct Guard ids, a
Man, and a level-105 player. It is a fixed health-bar resolution, not the
actor's max health. The condition for exact recovery therefore reduces to
`maxHealth <= 30`.

- **NPC targets: exact.** Guard (maxHealth 22, healthScale 30, ratio 20)
  recovers min 15 / max 15 — a single value. `NPCManager.getHealth()` gives
  the max health, so the NPC readout can print an integer honestly. Bosses
  above 30 hitpoints are the exception and fall to the interval case.
- **Player targets: an interval, never exact.** Any account above 30
  hitpoints, which is all of them. Worked example at 99 hitpoints and
  ratio 15: min 48, max 51 — a 4-wide band. The base client prints the
  midpoint, 50, formatted identically to a known value.

**Consequence for Phase 2, binding:** the player readout must render a range
or a percentage. It must never format a recovered player health as a bare
integer. The "honest uncertainty" line in this brief is the whole point of
the plugin, not a nicety — stock is actively misleading here and that is the
gap worth shipping into.

Outstanding, low risk: the only player sample is the local player. An
opponent player in PvP is near-certainly also scale 30, but has not been
directly observed. Confirm opportunistically; the verdict does not depend on
it, since 30 is below any real account's hitpoints either way.

---

## Phase 1 — Reference reading

**Base client** (`runelite/runelite`, BSD-licensed — reuse with credit):

- `plugins/opponentinfo/OpponentInfoPlugin.java` — `InteractingChanged`
  handling, the 5-second opponent timeout, boss HP HUD text override
- `plugins/opponentinfo/OpponentInfoOverlay.java` — the ratio-to-health
  recovery math, NPC max HP via `NPCManager`, player max HP via hiscores,
  `hasHpHud()` suppression
- `plugins/opponentinfo/PlayerComparisonOverlay.java` — how the stat comparison
  is gated behind a config flag
- `plugins/hiscore/HiscorePlugin.java` — how lookups are gated on user action
- `client/hiscore/HiscoreManager.java` — caching and async behavior

**Plugin Hub reference:**

- `devrat0/npc-health-text-plugin` — overhead HP text rendering, per-NPC
  position overrides, color gradient, whitelist/blacklist filtering

**API surface:** `Actor.getHealthRatio()`, `Actor.getHealthScale()`,
`NPCComposition`, `NPCManager.getHealth()`, `OverlayManager`, `OverlayPosition`.

---

## Phase 2 — Scope

### The overlay

This is the core deliverable. Concrete improvements over stock Opponent
Information:

- **Overhead rendering option.** Stock is a fixed corner panel only. Support
  drawing at the actor, with per-target vertical position override
  (`Top` / `Middle` / `Bottom`) so text does not collide with the top-screen
  boss health bar on large NPCs.
- **Honest uncertainty.** Where the recovered health is an interval rather than
  an exact value, display it as such. Do not present the midpoint as exact.
- **Configurable display format.** Number, percentage, both, or a user-defined
  format string. Optional decimal precision on percentages.
- **Color gradient** keyed to remaining health fraction.
- **Configurable opponent timeout.** Stock hardcodes a 5-second `WAIT`.
- **Persist-after-timeout option** — keep the readout up until the target dies
  or despawns, rather than when the in-game bar expires.
- **Whitelist / blacklist** filtering by name.
- **Suppression when redundant.** Stand down when the in-game HP HUD is showing
  the same information, mirroring the existing `hasHpHud()` check. Also detect
  whether stock Opponent Information is enabled and avoid drawing a duplicate
  panel over it.

The config class is deliberately absent from the skeleton. It arrives here, once
the option list above survives Phase 0. Config group must be `combat-info` —
specific, per `AGENTS.md`, and never renamed afterwards without a migration.

### NPC targets

Max HP from `NPCManager.getHealth()`. Exact recovery works here. No lookup, no
network, no compliance surface.

### Player targets (PvP)

- Percentage from the health ratio — no lookup required.
- Exact or interval HP via the single-opponent hiscores lookup described in
  Phase 0.
- Two config flags, matching the base client's actual shape:
  `showPlayerHitpoints` (the HP lookup) defaults **on**;
  `showStatComparison` (the stat table) defaults **off**. Decided 2026-08-26.

  Correction: an earlier draft of this brief said the HP lookup mirrors
  `lookupOnInteraction()`. It does not. In the base client the lookup in
  `OpponentInfoOverlay.render()` is ungated by any config item —
  `lookupOnInteraction` (default false) gates only `PlayerComparisonOverlay`.
  Default-on therefore *is* stock precedent. Give it an off switch anyway;
  stock has none, and having one is worth stating in the submission PR.
- Overhead rendering and name whitelist/blacklist apply to player targets,
  not NPC-only. Decided 2026-08-26, without a Discord ask. Build it behind an
  `overheadForPlayers` toggle so the conservative fallback is a default flip
  rather than surgery on the render path.
- Handle the unranked case: a player absent from the hiscores must degrade
  cleanly to percentage display, not to a wrong number or an error.
- Optional: a combat stat comparison panel from the same cached result.

### Cut

Cut 2026-08-26 — cut, not deferred. These sit near the "impossible switches in
PvP" language in Jagex's 2022 statement, and near `AGENTS.md`'s "no attack
counters" and "no PvP target scouting information". Nothing in the overlay
needs them. Do not reintroduce without revisiting Phase 0.

- Time-to-kill or kill-threshold estimates against a player target
- Damage-dealt history or DPS tracking scoped to a PvP opponent

---

## Phase 3 — Engineering quality bar

`AGENTS.md` carries RuneLite's own rules for logging, threading, HTTP, config
naming, and packaging, and they apply in full. What is specific to this plugin:

- RuneLite dependency stays pinned to an explicit version. Already done —
  `build.gradle` pins `1.12.37`; bump deliberately, never to `latest.release`.
  Note (Phase 0): `runelite-plugin.properties` sets `build=standard`, and the
  plugin-hub packager replaces `build.gradle` and `settings.gradle` at
  submission. The pin governs local development only — it does not make the
  hub build reproducible. Keep it for local determinism; claim no more.
- No mutation of injected shared singletons. In particular, do not call
  `registerTypeAdapter` on an injected `GsonBuilder` — build any Gson instance
  once into a field.
- Thread safety: overlay and plugin state is touched from both the client thread
  and RuneLite's scheduler. Use appropriate synchronization or concurrent
  collections. Do not rely on defensive copies alone.
- `shutDown()` must fully reverse `startUp()` — every overlay removed, all
  cached state cleared, no residual markers or listeners.
- No `return` inside a `finally` block. No `Future.get()` without a timeout.
- Real unit tests for the ratio-to-health recovery math, including the
  interval case and the `healthScale == 1` edge case. This math is pure and
  needs no client, so there is no excuse for it being untested.

---

## Phase 4 — Getting it installed

Adoption is downstream of the plugin being good, but these are the parts that
are cheap and get skipped:

- **The Plugin Hub listing is the whole shop window.** `displayName`,
  `description`, and `tags` in `runelite-plugin.properties` are what users
  search and read. Tags are a search index, not decoration.
- **A README with screenshots.** Overhead rendering versus the stock corner
  panel is a visual difference, and one image argues it better than a paragraph.
- **Say what it replaces.** "Opponent Information, but it can draw over the
  target and it doesn't lie about precision" is a clearer pitch than a feature
  list.
- **Ship useful defaults.** A plugin that does nothing until configured gets
  uninstalled before it gets configured. The default state should be the
  stock-equivalent readout, with the new behaviour discoverable.
- **Do not duplicate stock.** The suppression rules in Phase 2 are an adoption
  feature as much as a correctness one: a plugin that double-draws over
  Opponent Information reads as broken.

Submission, per the plugin-hub README: fork `runelite/plugin-hub`, add a file
under `plugins/` containing `repository=<https clone URL>` and
`commit=<full 40-char hash>`, and open a PR. Non-RuneLite dependencies need
Gradle dependency verification with cryptographic hashes; the cleanest way to
keep that painless is to add no non-RuneLite dependencies at all. A BSD 2-Clause
licence is required and is already in `LICENSE`.

---

## Reporting

At the end of Phase 0 and Phase 0.5, stop and report before continuing. If any
feature under consideration lands near a Phase 0 constraint, raise it rather
than building it and asking later.
