#!/usr/bin/env python3
"""Cross-account validation for the Phase 0.5 health probe.

Each client records its own true hitpoints (SELF rows, read from the orb) and
the ratio it observes for its opponent (OPPONENT rows). Neither client can see
the other's real health -- that is the whole reason the ratio exists. But two
clients together can: A's observation of B is scored against B's own record of
itself, matched on wall clock.

That tests the assumption the readout rests on and which nothing has checked --
that recovering health from *another* actor's broadcast ratio works the same way
as recovering it from your own.

    python3 join_probe_csvs.py fileA.csv fileB.csv
    python3 join_probe_csvs.py --selftest
"""
import csv
import sys

TICK_MS = 600


def recover(ratio, scale, max_health):
    """Mirror of HealthRecovery.recover in Java. Keep the two in step."""
    if max_health <= 0 or scale <= 0:
        return None
    if ratio <= 0:
        return 0, 0
    lo = 1
    if scale > 1:
        if ratio > 1:
            lo = (max_health * (ratio - 1) + scale - 2) // (scale - 1)
        hi = min((max_health * ratio - 1) // (scale - 1), max_health)
    else:
        hi = max_health
    lo = max(1, min(lo, max_health))
    hi = max(lo, min(hi, max_health))
    return lo, hi


def load(paths):
    selves, opponents = {}, []
    for path in paths:
        with open(path, newline="") as fh:
            for row in csv.DictReader(fh):
                try:
                    row["epochMs"] = int(row["epochMs"])
                except (KeyError, ValueError):
                    continue
                if row["role"] == "SELF" and row["actorType"] == "PLAYER":
                    selves.setdefault(row["observer"], []).append(row)
                elif row["role"] == "OPPONENT" and row["actorType"] == "PLAYER":
                    opponents.append(row)
    for rows in selves.values():
        rows.sort(key=lambda r: r["epochMs"])
    return selves, opponents


def nearest(rows, when, tolerance):
    best, best_gap = None, None
    for row in rows:
        gap = abs(row["epochMs"] - when)
        if best_gap is None or gap < best_gap:
            best, best_gap = row, gap
    return (best, best_gap) if best is not None and best_gap <= tolerance else (None, None)


def report(paths, tolerance=TICK_MS):
    selves, opponents = load(paths)
    print("observers found: %s" % (", ".join(sorted(selves)) or "none"))

    if len(selves) < 2:
        print("\nNeed SELF rows from two accounts to cross-check. "
              "Both clients must be in combat so each has a health bar.")
        return 1

    pairs = unmatched = boosted = 0
    in_range = exact = 0
    misses = []
    worst = 0

    for obs in opponents:
        target = obs["name"]
        truth_rows = selves.get(target)
        if not truth_rows:
            unmatched += 1
            continue

        truth, gap = nearest(truth_rows, obs["epochMs"], tolerance)
        if truth is None:
            unmatched += 1
            continue

        if obs["world"] != truth["world"]:
            continue

        try:
            max_health = int(truth["knownMax"])
            true_hp = int(truth["trueHp"])
            band = recover(int(obs["healthRatio"]), int(obs["healthScale"]), max_health)
        except (ValueError, TypeError):
            continue
        if band is None:
            continue

        # A Saradomin brew puts current health above the base level, so the
        # band built from the base max legitimately will not contain it. That
        # is a boost, not a broken model, and counting it as a miss would raise
        # a false alarm about the one thing this test exists to detect.
        if true_hp > max_health:
            boosted += 1
            continue

        lo, hi = band
        mid = (lo + hi + 1) // 2
        pairs += 1
        if lo <= true_hp <= hi:
            in_range += 1
            if mid == true_hp:
                exact += 1
        else:
            misses.append((obs["observer"], target, obs["healthRatio"], lo, hi, true_hp, gap))
        worst = max(worst, abs(mid - true_hp))

    print("\ncross-account pairs: %d   (unmatched: %d, excluded as boosted: %d)"
          % (pairs, unmatched, boosted))
    if boosted:
        print("Boosted samples were skipped. Avoid HP-raising items during the run"
              "\nso the base max stays the true max.")
    if not pairs:
        print("No pairs. Were both accounts fighting each other at the same time?")
        return 1

    print("true health inside recovered range: %d/%d (%.1f%%)"
          % (in_range, pairs, 100.0 * in_range / pairs))
    print("midpoint exactly correct:           %d/%d (%.1f%%)"
          % (exact, pairs, 100.0 * exact / pairs))
    print("worst midpoint error:               %d hp" % worst)

    if misses:
        print("\n%d MISSES -- the range excluded the true health. This would mean"
              "\nobserving another actor is not the same path as observing yourself,"
              "\nand the readout is wrong regardless of how it is formatted:" % len(misses))
        for observer, target, ratio, lo, hi, true_hp, gap in misses[:10]:
            print("  %s saw %s at ratio %s -> %d-%d, but %s was on %d  (%dms apart)"
                  % (observer, target, ratio, lo, hi, target, true_hp, gap))
        return 1

    print("\nNo misses. Recovering health from another actor's ratio behaves"
          "\nidentically to recovering it from your own.")
    return 0


def selftest():
    """Generate a known two-client fight and confirm the join recovers it."""
    import os
    import tempfile

    scale = 30
    accounts = {"AccountA": 99, "AccountB": 88}
    health = {"AccountA": 99, "AccountB": 88}
    header = ("epochMs,instanceId,observer,world,tick,role,actorType,name,combatLevel,"
              "healthScale,healthRatio,npcId,knownMax,trueHp,"
              "recoveredMin,recoveredMax,recoveredMid,midpointHit").split(",")

    def server_ratio(hp, max_health):
        return 0 if hp <= 0 else 1 + (scale - 1) * hp // max_health

    rows = {name: [] for name in accounts}
    base = 1_700_000_000_000
    for tick in range(60):
        for name in accounts:
            health[name] = max(1, health[name] - (tick % 3 == 0))
        for i, (name, other) in enumerate([("AccountA", "AccountB"), ("AccountB", "AccountA")]):
            # Deliberate clock skew between the two clients, under one tick.
            when = base + tick * TICK_MS + i * 120
            for role, actor in (("SELF", name), ("OPPONENT", other)):
                is_self = role == "SELF"
                rows[name].append({
                    "epochMs": when, "instanceId": 1000 + i, "observer": name, "world": 301,
                    "tick": tick, "role": role, "actorType": "PLAYER", "name": actor,
                    "combatLevel": 100, "healthScale": scale,
                    "healthRatio": server_ratio(health[actor], accounts[actor]),
                    "npcId": "", "knownMax": accounts[name] if is_self else "",
                    "trueHp": health[name] if is_self else "",
                    "recoveredMin": "", "recoveredMax": "", "recoveredMid": "", "midpointHit": "",
                })

    paths = []
    tmp = tempfile.mkdtemp()
    for name, data in rows.items():
        path = os.path.join(tmp, "%s.csv" % name)
        with open(path, "w", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=header)
            writer.writeheader()
            writer.writerows(data)
        paths.append(path)

    print("SELF TEST -- synthetic fight, AccountA 99hp vs AccountB 88hp\n")
    return report(paths)


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or args[0] == "--selftest":
        sys.exit(selftest())
    sys.exit(report(args))
