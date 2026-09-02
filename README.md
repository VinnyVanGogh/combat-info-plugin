# Combat Info

A RuneLite plugin showing opponent health for NPC and player targets — a
standalone replacement for the stock **Opponent Information** plugin, with
overhead rendering.

## What it does differently

- **Draws at the target**, not only in a fixed corner panel. Position is
  configurable (Top / Middle / Bottom, plus a pixel offset) so the text does not
  collide with a large NPC's name plate or the top-screen boss health bar.
- **Works for player targets too**, which no comparable plugin does.
- **Configurable format, colour gradient, and opponent timeout** — the stock
  plugin hardcodes its timeout at five seconds and offers no gradient.
- **Stands down when it would be redundant**, rather than double-drawing over
  the game's own boss HP bar or over the stock plugin's panel.
- **Tells you what the number is actually worth.** See below.

## About that number

Opponent health is not sent to your client. The server broadcasts a health
*bar* — a ratio out of a fixed scale — and every plugin that shows a number,
this one and the base client's included, is inverting that ratio to guess.

The scale is 30. That was measured, not assumed. It means:

- **NPCs with 30 or fewer hitpoints are exact.** A Guard on 22 max resolves to
  a single value, every time.
- **Players never are.** Above 30 max hitpoints the ratio maps to a band of
  three or four possible values, so the displayed number is the middle of that
  band. It is right about a third of the time and never off by more than one or
  two.

There is a second, larger error that nothing can fix:

- **After your opponent eats, the bar you can see lags behind.** Measured across
  386 cross-checked readings in a real fight between two accounts: typically two
  game ticks, up to four, and in the worst case the displayed value was **19
  hitpoints stale**.

Nothing in the client API says an opponent has eaten, so this cannot be
detected, flagged, or corrected — not here, and not by the base client, which
carries the identical error without mentioning it.

**The practical version:** trust the number while you are trading hits. Distrust
it for a second or two after they eat, which is exactly when you most want to
trust it. That is a property of the game's networking, not of any plugin.

These are measurements, not estimates. How they were taken, the numbers behind
them, and the method used to cross-check one account's reading against another's
are written up in [BRIEF.md](BRIEF.md) under Phase 0.5, along with the analysis
script in [`docs/phase-0.5/`](docs/phase-0.5/). The raw capture files are kept
out of this repository because they name real accounts.

## Privacy

The only network request this plugin makes is a hiscores lookup for the single
opponent you are currently engaged with, used to learn their maximum hitpoints
so their health can be shown as a number rather than a percentage. It goes to
Jagex's own hiscores service, one request per opponent, cached.

The base client's Opponent Information plugin performs the same lookup with no
way to switch it off. Here it is **Targets → Look up player hitpoints**, and
turning it off degrades player targets to a percentage rather than breaking
them.

Nothing else leaves your machine. No account identifiers, no telemetry, no
third-party servers.

## Building

```bash
./gradlew build          # compile and test
./gradlew run            # launch a developer-mode client with the plugin loaded
```

Requires a JDK; the build targets Java 11 bytecode. To log in to the
development client, follow
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Credit

The ratio-to-health recovery is derived from `OpponentInfoOverlay` in the
RuneLite client, which is BSD-licensed. This plugin reimplements it with the
range made explicit rather than collapsed to a midpoint internally.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
