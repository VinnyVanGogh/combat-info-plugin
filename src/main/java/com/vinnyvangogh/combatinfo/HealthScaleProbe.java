package com.vinnyvangogh.combatinfo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
		"tick,actorType,name,combatLevel,healthScale,healthRatio,npcId,npcMaxHealth";

	/** Guard against an unattended client filling the disk. */
	private static final int MAX_ROWS = 20_000;

	private static final long FLUSH_PERIOD_SECONDS = 10;

	@Inject
	private Client client;

	@Inject
	private NPCManager npcManager;

	@Inject
	private ScheduledExecutorService executor;

	private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();

	/** Dedupe key per distinct observation, so we log transitions and not ticks. */
	private final Set<String> seen = ConcurrentHashMap.newKeySet();

	/** The headline result: which health scales the server actually sends. */
	private final Set<String> scalesSeen = ConcurrentHashMap.newKeySet();

	private ScheduledFuture<?> flushFuture;
	private File csvFile;
	private int rowCount;
	private Actor opponent;

	void startUp()
	{
		csvFile = new File(new File(RuneLite.RUNELITE_DIR, "combat-info"), "health-scale-probe.csv");
		rowCount = 0;
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
		seen.clear();
		scalesSeen.clear();

		// shutDown() runs on the client thread, and flush() touches the disk.
		// The executor is RuneLite's shared one, so cancel our task but never
		// shut the executor itself down.
		executor.execute(this::flush);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() != client.getLocalPlayer())
		{
			return;
		}

		opponent = event.getTarget();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// The local player is the most valuable sample available: it is the one
		// actor whose true max health the person running this already knows, so
		// it tests maxHealth <= healthScale directly rather than by inference.
		record(client.getLocalPlayer());
		record(opponent);
	}

	private void record(Actor actor)
	{
		if (actor == null || rowCount >= MAX_ROWS)
		{
			return;
		}

		final int healthScale = actor.getHealthScale();
		final int healthRatio = actor.getHealthRatio();

		// healthScale is -1 until the server has sent a health bar for this actor.
		if (healthScale <= 0)
		{
			return;
		}

		final String name = actor.getName() == null ? "?" : Text.removeTags(actor.getName());
		final String type = actor instanceof Player ? "PLAYER" : "NPC";

		final String key = type + '|' + name + '|' + healthScale + '|' + healthRatio;
		if (!seen.add(key))
		{
			return;
		}

		String npcId = "";
		String npcMaxHealth = "";
		int combatLevel = -1;

		if (actor instanceof NPC)
		{
			final NPC npc = (NPC) actor;
			npcId = Integer.toString(npc.getId());

			// NPC max health is known exactly from the cache, which makes NPC rows
			// a ground truth for whether the recovery math is implemented right,
			// checkable without a PvP world.
			final Integer health = npcManager.getHealth(npc.getId());
			if (health != null)
			{
				npcMaxHealth = Integer.toString(health);
			}

			combatLevel = npc.getCombatLevel();
		}
		else if (actor instanceof Player)
		{
			combatLevel = ((Player) actor).getCombatLevel();
		}

		pending.add(String.join(",",
			Integer.toString(client.getTickCount()),
			type,
			csv(name),
			Integer.toString(combatLevel),
			Integer.toString(healthScale),
			Integer.toString(healthRatio),
			npcId,
			npcMaxHealth));
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
			final boolean fresh = !csvFile.exists();
			Files.createDirectories(csvFile.getParentFile().toPath());

			if (fresh)
			{
				rows.add(0, CSV_HEADER);
			}

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
