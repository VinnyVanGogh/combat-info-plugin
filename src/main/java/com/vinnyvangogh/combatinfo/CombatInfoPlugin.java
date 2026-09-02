package com.vinnyvangogh.combatinfo;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.NPCManager;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreManager;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Combat Info — a standalone replacement for the stock Opponent Information
 * plugin, with overhead rendering.
 *
 * The readout is rebuilt once per game tick and published as an immutable
 * snapshot. The overlays render whatever the latest snapshot holds and compute
 * nothing themselves, because they run every frame and the underlying health
 * bar only changes on a tick.
 */
@Slf4j
@PluginDescriptor(
	name = "Combat Info",
	description = "Opponent health for NPC and player targets, with overhead rendering and honest uncertainty",
	tags = {"combat", "overlay", "opponent", "health", "hitpoints", "pvp", "pvm"}
)
public class CombatInfoPlugin extends Plugin
{
	private static final String STOCK_PLUGIN_NAME = "Opponent Information";

	@Inject
	private Client client;

	@Inject
	private CombatInfoConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NPCManager npcManager;

	@Inject
	private HiscoreManager hiscoreManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private CombatInfoPanelOverlay panelOverlay;

	@Inject
	private CombatInfoOverheadOverlay overheadOverlay;

	@Inject
	private Hooks hooks;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	/** Replaced wholesale each tick, never mutated, so the overlay thread sees a consistent view. */
	@Getter
	private volatile Readout readout;

	/** Your own health. Exact, because the client knows it — never recovered. */
	@Getter
	private volatile Readout selfReadout;

	private Actor opponent;
	private Instant interactionEnded;
	private HiscoreEndpoint hiscoreEndpoint = HiscoreEndpoint.NORMAL;
	private Plugin stockPlugin;

	@Provides
	CombatInfoConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CombatInfoConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(panelOverlay);
		overlayManager.add(overheadOverlay);
		hooks.registerRenderableDrawListener(drawListener);

		stockPlugin = pluginManager.getPlugins().stream()
			.filter(p -> STOCK_PLUGIN_NAME.equals(p.getName()))
			.findFirst()
			.orElse(null);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(panelOverlay);
		overlayManager.remove(overheadOverlay);
		hooks.unregisterRenderableDrawListener(drawListener);

		readout = null;
		selfReadout = null;
		opponent = null;
		interactionEnded = null;
		stockPlugin = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				hiscoreEndpoint = HiscoreEndpoint.fromWorldTypes(client.getWorldType());
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				opponent = null;
				interactionEnded = null;
				readout = null;
				selfReadout = null;
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		// The single gate the whole compliance argument rests on: this plugin
		// only ever tracks the target the user themselves engaged.
		if (event.getSource() != client.getLocalPlayer())
		{
			return;
		}

		final Actor target = event.getTarget();
		if (target != null)
		{
			opponent = target;
			interactionEnded = null;
		}
		else
		{
			// Keep the opponent: the health bar lingers after combat and the
			// readout should too. The timeout below retires it.
			interactionEnded = Instant.now();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		expireOpponent();
		readout = buildReadout();
		selfReadout = buildSelfReadout();
	}

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

		final Duration timeout = Duration.ofSeconds(Math.max(1, config.opponentTimeout()));
		if (Duration.between(interactionEnded, Instant.now()).compareTo(timeout) > 0)
		{
			opponent = null;
			interactionEnded = null;
		}
	}

	private Readout buildReadout()
	{
		final Actor actor = opponent;
		if (actor == null || actor.getName() == null)
		{
			return null;
		}

		final int scale = actor.getHealthScale();
		final int ratio = actor.getHealthRatio();

		// A despawned actor keeps a stale scale after its ratio has gone.
		if (scale <= 0 || ratio < 0)
		{
			return null;
		}

		final boolean npc = actor instanceof NPC;
		if (npc ? !config.showForNpcs() : !config.showForPlayers())
		{
			return null;
		}

		if (config.hideWhenHpHud() && hasHpHud(actor))
		{
			return null;
		}

		String name = Text.removeTags(actor.getName());
		int maxHealth = -1;
		HiscoreResult hiscore = null;

		if (npc)
		{
			final NPC target = (NPC) actor;
			final NPCComposition composition = target.getTransformedComposition();
			if (composition != null)
			{
				final String longName = composition.getStringValue(ParamID.NPC_HP_NAME);
				if (longName != null && !longName.isEmpty())
				{
					name = longName;
				}
			}

			final Integer health = npcManager.getHealth(target.getId());
			if (health != null)
			{
				maxHealth = health;
			}
		}
		else if (config.showPlayerHitpoints() || config.showStatComparison())
		{
			// One lookup per opponent, in direct response to the user engaging
			// them. HiscoreManager returns null until its cache is warm and
			// refreshes off the client thread, so this never blocks and never
			// repeats a request. The comparison reads this same result.
			hiscore = hiscoreManager.lookupAsync(name, hiscoreEndpoint);

			if (hiscore != null && config.showPlayerHitpoints())
			{
				final net.runelite.client.hiscore.Skill hitpoints =
					hiscore.getSkill(HiscoreSkill.HITPOINTS);

				// An unranked player comes back non-positive. Leaving maxHealth
				// unknown degrades to a percentage rather than a wrong number.
				if (hitpoints != null && hitpoints.getLevel() > 0)
				{
					maxHealth = hitpoints.getLevel();
				}
			}
		}

		final HealthRecovery.Range range = maxHealth > 0
			? HealthRecovery.recover(ratio, scale, maxHealth)
			: null;

		return new Readout(actor, name, ratio, scale, maxHealth, range, npc, -1, hiscore);
	}

	/**
	 * Your own health, taken from the hitpoints orb rather than inverted from a
	 * health bar.
	 *
	 * The client knows this number exactly. Running it through the recovery
	 * would print a guess in place of a fact, which would be absurd on its own
	 * terms and would undermine the one claim this plugin actually makes.
	 *
	 * Only while engaged: a permanent readout over your own head is clutter,
	 * and the orb already covers the out-of-combat case.
	 */
	private Readout buildSelfReadout()
	{
		if (!config.showOwnHealth() || opponent == null)
		{
			return null;
		}

		final Player self = client.getLocalPlayer();
		if (self == null || self.getName() == null)
		{
			return null;
		}

		final int current = client.getBoostedSkillLevel(Skill.HITPOINTS);
		final int max = client.getRealSkillLevel(Skill.HITPOINTS);
		if (max <= 0)
		{
			return null;
		}

		return new Readout(self, Text.removeTags(self.getName()), -1, -1, max, null, false, current, null);
	}

	/** Mirrors the base client: NPC only, and false for players by definition. */
	private boolean hasHpHud(Actor actor)
	{
		if (!(actor instanceof NPC) || client.getVarbitValue(VarbitID.HPBAR_HUD_BOSS_DISABLED) != 0)
		{
			return false;
		}

		final NPC npc = (NPC) actor;
		final int hudNpcId = client.getVarpValue(VarPlayerID.HPBAR_HUD_NPC);
		return hudNpcId != -1
			&& npc.getComposition() != null
			&& hudNpcId == npc.getComposition().getId();
	}

	/**
	 * Suppresses the game's own 2D layer over the current target.
	 *
	 * The engine offers no way to hide the health bar by itself — drawingUI
	 * covers the whole overhead layer, so this also removes the target's name,
	 * hitsplats and prayer icons. Hence off by default and stated plainly in the
	 * setting's description rather than discovered mid-fight.
	 *
	 * Called for every renderable every frame, so it stays a couple of
	 * reference comparisons and reads the snapshot once.
	 */
	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (!drawingUI || !config.hideGameHealthBar())
		{
			return true;
		}

		final Readout current = readout;
		return current == null || renderable != current.getActor();
	}

	/** True when the stock plugin is drawing its own panel in the same corner. */
	boolean stockPluginEnabled()
	{
		return stockPlugin != null && pluginManager.isPluginEnabled(stockPlugin);
	}

	/** Immutable snapshot of everything the overlays need for one tick. */
	@Getter
	static final class Readout
	{
		private final Actor actor;
		private final String name;
		private final int ratio;
		private final int scale;
		private final int maxHealth;
		private final HealthRecovery.Range range;
		private final boolean npc;

		/** Known exactly, or -1 when it can only be recovered from the bar. */
		private final int exactHealth;

		/** The one cached hiscores result, shared by the readout and comparison. */
		private final HiscoreResult hiscore;

		Readout(Actor actor, String name, int ratio, int scale, int maxHealth,
			HealthRecovery.Range range, boolean npc, int exactHealth, HiscoreResult hiscore)
		{
			this.actor = actor;
			this.name = name;
			this.ratio = ratio;
			this.scale = scale;
			this.maxHealth = maxHealth;
			this.range = range;
			this.npc = npc;
			this.exactHealth = exactHealth;
			this.hiscore = hiscore;
		}
	}
}
