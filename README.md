# Combat Info

A RuneLite plugin showing opponent health for NPC and player targets — a
standalone replacement for the stock **Opponent Information** plugin, with
overhead rendering and honest uncertainty.

> **Status: skeleton.** The plugin loads and does nothing yet. Behaviour is
> gated behind the compliance and measurement phases in [BRIEF.md](BRIEF.md),
> deliberately, so that nothing gets built before it is known to be allowed and
> known to be accurate.

## What it will do differently

- **Draw at the target**, not only in a fixed corner panel, with a per-target
  vertical offset so the text does not collide with the boss health bar.
- **Never present an estimate as exact.** Where the health recovered from the
  ratio is a range rather than a value, it says so.
- **Configurable format, gradient, timeout, and name filters**, none of which
  the stock plugin offers.
- **Stand down when it would be redundant**, rather than double-drawing over
  the in-game HP HUD or the stock plugin.

It makes no network requests except an optional, off-by-default hiscores lookup
for the single opponent you are currently engaged with — the same lookup the
base client's own Opponent Information plugin performs, and no more.

## Building

```bash
./gradlew build          # compile and test
./gradlew run            # launch a developer-mode client with the plugin loaded
```

Requires a JDK; the build targets Java 11 bytecode. To log in to the
development client, follow
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
