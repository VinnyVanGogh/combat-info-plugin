package com.vinnyvangogh.combatinfo;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.NPCManager;
import net.runelite.client.util.Text;

/**
 * Phase 0.5 instrumentation. TEMPORARY — delete before Phase 2.
 *
 * BRIEF.md gates the overlay on knowing how precise the health-ratio recovery
 * actually is. The server computes
 *
 *     healthRatio = 1 + (healthScale - 1) * health / maxHealth   (health > 0)
 *
 * with integer division, so inverting it recovers an exact value only when
 * {@code maxHealth <= healthScale}. Whether that holds for players at real PvP
 * hitpoints levels is an empirical question about what the server sends, and it
 * decides whether the readout can print an integer or must print a range.
 *
 * This class answers it by recording (healthScale, healthRatio) for the actors
 * whose max health we can independently know.
 *
 * Scope is deliberately narrow: the single opponent set by InteractingChanged
 * where the source is the local player, plus the local player. It does not walk
 * the scene or sample bystanders — that would be the vicinity scanning Phase 0
 * rules out, and instrumentation is not an exemption from the compliance
 * envelope the plugin proper has to live in.
 */
@Slf4j
@Singleton
class HealthScaleProbe
{
	private static final String CSV_HEADER =
		"epochMs,instanceId,observer,world,tick,role,actorType,name,combatLevel,"
			+ "healthScale,healthRatio,npcId,knownMax,trueHp,"
			+ "recoveredMin,recoveredMax,recoveredMid,midpointHit";

	/** Guard against an unattended client filling the disk. */
	private static final int MAX_ROWS = 20_000;

	private static final long FLUSH_PERIOD_SECONDS = 10;

	/** Matches the base client's opponent timeout. */
	private static final Duration OPPONENT_TIMEOUT = Duration.ofSeconds(5);

	@Inject
	private Client client;

	@Inject
	private NPCManager npcManager;

	@Inject
	private ScheduledExecutorService executor;

	private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();

	/** The headline result: which health scales the server actually sends. */
	private final Set<String> scalesSeen = ConcurrentHashMap.newKeySet();

	private ScheduledFuture<?> flushFuture;
	private File csvFile;
	private int rowCount;
	private Actor opponent;
	private Instant interactionEnded;
	private int ticks;
	private long instanceId;

	void startUp()
	{
		// Two clients logged into two accounts must not append to one file. The
		// launch time distinguishes them, and the correlation is done on the
		// wall clock in each row rather than on which file a row landed in.
		instanceId = System.currentTimeMillis();
		csvFile = new File(new File(RuneLite.RUNELITE_DIR, "combat-info"),
			"health-scale-probe-" + instanceId + ".csv");
		rowCount = 0;
		ticks = 0;
		// Write the header eagerly, so an absent file means startUp never ran
		// rather than being indistinguishable from "ran but sampled nothing".
		executor.execute(this::ensureFile);
		flushFuture = executor.scheduleWithFixedDelay(
			this::flush, FLUSH_PERIOD_SECONDS, FLUSH_PERIOD_SECONDS, TimeUnit.SECONDS);
		log.debug("Health scale probe writing to {}", csvFile);
	}

	void shutDown()
	{
		if (flushFuture != null)
		{
			flushFuture.cancel(false);
			flushFuture = null;
		}

		opponent = null;
		interactionEnded = null;
		scalesSeen.clear();

		// shutDown() runs on the client thread, and flush() touches the disk.
		// The executor is RuneLite's shared one, so cancel our task but never
		// shut the executor itself down.
		executor.execute(this::flush);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		final boolean fromUs = event.getSource() == client.getLocalPlayer();
		final Actor target = event.getTarget();

		log.debug("Probe interacting: fromUs={} target={} ({})",
			fromUs,
			target == null ? null : target.getName(),
			target == null ? "null" : target.getClass().getSimpleName());

		if (!fromUs)
		{
			return;
		}

		// A null target means the interaction ended, not that there is no
		// opponent worth sampling — the health bar lingers for a few seconds
		// after combat. Overwriting with null threw those samples away. Stock
		// keeps its last opponent here too and expires it on a timer instead.
		if (target != null)
		{
			opponent = target;
			interactionEnded = null;
		}
		else
		{
			interactionEnded = Instant.now();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// The local player is the most valuable sample available: it is the one
		// actor whose true max health the person running this already knows, so
		// it tests maxHealth <= healthScale directly rather than by inference.
		expireOpponent();

		record(client.getLocalPlayer(), "SELF");
		record(opponent, "OPPONENT");

		// Heartbeat. The first version of this probe logged only on a successful
		// sample, which made "produced nothing" indistinguishable from "never
		// ran". Log the raw values unconditionally instead, -1s included.
		if (++ticks % 5 == 0)
		{
			final Actor self = client.getLocalPlayer();
			log.debug("Probe tick {}: self[scale={} ratio={}] opponent={}[scale={} ratio={}] rows={}",
				ticks,
				self == null ? -99 : self.getHealthScale(),
				self == null ? -99 : self.getHealthRatio(),
				opponent == null ? null : opponent.getName(),
				opponent == null ? -99 : opponent.getHealthScale(),
				opponent == null ? -99 : opponent.getHealthRatio(),
				rowCount);
		}
	}

	private void ensureFile()
	{
		try
		{
			Files.createDirectories(csvFile.getParentFile().toPath());
			if (!csvFile.exists())
			{
				Files.write(csvFile.toPath(),
					Collections.singletonList(CSV_HEADER),
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
				log.debug("Probe file created: {}", csvFile);
			}
		}
		catch (IOException e)
		{
			log.debug("Probe could not create {}", csvFile, e);
		}
	}

	/**
	 * Keeping the last opponent through a broken interaction is right; keeping
	 * it forever is not. A dead NPC despawns but the reference stays live and
	 * reports a stale health scale, so it goes on being sampled as a nameless
	 * actor. The base client expires its opponent on the same timer.
	 */
	private void expireOpponent()
	{
		if (opponent == null || interactionEnded == null)
		{
			return;
		}

		final Player self = client.getLocalPlayer();
		if (self != null && self.getInteracting() != null)
		{
			return;
		}

		if (Duration.between(interactionEnded, Instant.now()).compareTo(OPPONENT_TIMEOUT) > 0)
		{
			opponent = null;
			interactionEnded = null;
		}
	}

	private void record(Actor actor, String role)
	{
		if (actor == null || rowCount >= MAX_ROWS)
		{
			return;
		}

		final int healthScale = actor.getHealthScale();
		final int healthRatio = actor.getHealthRatio();

		// healthScale is -1 until the server has sent a health bar for this
		// actor. A despawned actor can keep a stale scale while its ratio has
		// already gone negative, so both have to be checked.
		if (healthScale <= 0 || healthRatio < 0)
		{
			return;
		}

		final String name = actor.getName() == null ? "?" : Text.removeTags(actor.getName());
		final String type = actor instanceof Player ? "PLAYER" : "NPC";

		final boolean isSelf = actor == client.getLocalPlayer();


		// Only the local player's true health is knowable — the hitpoints orb.
		// It is the ground truth the recovered range is checked against, and it
		// belongs in the dedupe key so regeneration ticks are recorded rather
		// than folded into the previous sample.
		final int trueHp = isSelf ? client.getBoostedSkillLevel(Skill.HITPOINTS) : -1;

		String npcId = "";
		Integer knownMax = null;
		int combatLevel = -1;

		if (actor instanceof NPC)
		{
			final NPC npc = (NPC) actor;
			npcId = Integer.toString(npc.getId());
			knownMax = npcManager.getHealth(npc.getId());
			combatLevel = npc.getCombatLevel();
		}
		else if (actor instanceof Player)
		{
			combatLevel = ((Player) actor).getCombatLevel();

			if (isSelf)
			{
				knownMax = client.getRealSkillLevel(Skill.HITPOINTS);
			}
		}

		// Where the max is known the range can be recovered, and for the local
		// player it can be scored against the truth. That comparison is the
		// point of the exercise: it says how often the midpoint the base client
		// prints is actually the player's health.
		String recoveredMin = "";
		String recoveredMax = "";
		String recoveredMid = "";
		String midpointHit = "";

		if (knownMax != null && knownMax > 0)
		{
			final HealthRecovery.Range range = HealthRecovery.recover(healthRatio, healthScale, knownMax);
			recoveredMin = Integer.toString(range.min());
			recoveredMax = Integer.toString(range.max());
			recoveredMid = Integer.toString(range.midpoint());

			if (trueHp >= 0)
			{
				midpointHit = range.midpoint() == trueHp
					? "exact"
					: (trueHp >= range.min() && trueHp <= range.max() ? "inRange" : "MISS");
			}
		}

		// observer is which account produced the row, so a file identifies
		// itself and two of them can be joined without tracking which client
		// wrote which. world guards against correlating rows from two accounts
		// that were never actually in the same place.
		final Player self = client.getLocalPlayer();
		final String observer = self == null || self.getName() == null
			? "?" : Text.removeTags(self.getName());

		pending.add(String.join(",",
			Long.toString(System.currentTimeMillis()),
			Long.toString(instanceId),
			csv(observer),
			Integer.toString(client.getWorld()),
			Integer.toString(client.getTickCount()),
			role,
			type,
			csv(name),
			Integer.toString(combatLevel),
			Integer.toString(healthScale),
			Integer.toString(healthRatio),
			npcId,
			knownMax == null ? "" : Integer.toString(knownMax),
			trueHp < 0 ? "" : Integer.toString(trueHp),
			recoveredMin,
			recoveredMax,
			recoveredMid,
			midpointHit));
		rowCount++;

		if (scalesSeen.add(type + '|' + healthScale))
		{
			log.debug("Health scale probe: new scale for {} — healthScale={} (name={}, combat={})",
				type, healthScale, name, combatLevel);
		}

		if (rowCount == MAX_ROWS)
		{
			log.debug("Health scale probe: row cap {} reached, no longer sampling", MAX_ROWS);
		}
	}

	private static String csv(String value)
	{
		// Display names can carry commas via clan titles and the like.
		return value.indexOf(',') < 0 ? value : '"' + value.replace("\"", "\"\"") + '"';
	}

	private void flush()
	{
		if (pending.isEmpty())
		{
			return;
		}

		final List<String> rows = new ArrayList<>();
		for (String row = pending.poll(); row != null; row = pending.poll())
		{
			rows.add(row);
		}

		try
		{
			ensureFile();
			Files.write(csvFile.toPath(),
				rows,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		}
		catch (IOException e)
		{
			log.debug("Health scale probe could not write {}", csvFile, e);
		}
	}
}
