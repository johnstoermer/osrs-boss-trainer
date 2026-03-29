package com.verziktrainer;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.events.ConfigChanged;
import com.google.inject.Provides;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Gemstone Trainer",
    description = "Overlays boss models and attack patterns on Gemstone Crabs for PvM training",
    tags = {"verzik", "tob", "sol", "heredit", "colosseum", "trainer", "pvm", "gemstone"}
)
public class VerzikTrainerPlugin extends Plugin
{
    private static final int GEMSTONE_CRAB_ID = 14779;
    private static final String GEMSTONE_CRAB_NAME = "Gemstone Crab";

    @Inject
    private Client client;

    @Inject
    private Hooks hooks;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private VerzikTrainerConfig config;

    @Inject
    private VerzikTrainerOverlay overlay;

    @Provides
    VerzikTrainerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(VerzikTrainerConfig.class);
    }

    @Getter
    private final Map<Integer, CrabBossState> trackedCrabs = new HashMap<>();

    private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;
    private boolean pendingRescan = false;
    private boolean pendingBossSwitch = false;

    @Override
    protected void startUp()
    {
        hooks.registerRenderableDrawListener(drawListener);
        overlayManager.add(overlay);
        pendingRescan = true;
    }

    @Override
    protected void shutDown()
    {
        hooks.unregisterRenderableDrawListener(drawListener);
        overlayManager.remove(overlay);
        cleanupAll();
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        if (!config.enabled())
        {
            return;
        }

        NPC npc = event.getNpc();
        if (isGemstoneCrab(npc))
        {
            trackCrab(npc);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        NPC npc = event.getNpc();
        int index = npc.getIndex();
        CrabBossState state = trackedCrabs.remove(index);
        if (state != null)
        {
            state.cleanup();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (pendingBossSwitch)
        {
            pendingBossSwitch = false;
            cleanupAll();
            pendingRescan = true;
        }

        if (pendingRescan)
        {
            pendingRescan = false;
            scanForCrabs();
        }

        if (!config.enabled())
        {
            return;
        }

        for (CrabBossState state : trackedCrabs.values())
        {
            state.onGameTick();
        }
    }

    private void scanForCrabs()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        for (NPC npc : client.getTopLevelWorldView().npcs())
        {
            if (npc != null && isGemstoneCrab(npc))
            {
                trackCrab(npc);
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState gameState = event.getGameState();
        if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING)
        {
            cleanupAll();
        }
    }

    private boolean isGemstoneCrab(NPC npc)
    {
        if (npc.getId() == GEMSTONE_CRAB_ID)
        {
            return true;
        }
        String name = npc.getName();
        if (GEMSTONE_CRAB_NAME.equals(name))
        {
            log.debug("Gemstone Crab matched by name but had unexpected ID={}", npc.getId());
            return true;
        }
        return false;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"verziktrainer".equals(event.getGroup()))
        {
            return;
        }
        if ("bossType".equals(event.getKey()))
        {
            pendingBossSwitch = true;
        }
    }

    private void trackCrab(NPC npc)
    {
        int index = npc.getIndex();
        if (trackedCrabs.containsKey(index))
        {
            return;
        }

        CrabBossState state;
        BossType bossType = config.bossType();
        switch (bossType)
        {
            case SOL_HEREDIT:
                state = new SolHereditState(npc, client, config);
                break;
            case VERZIK_P2:
            default:
                state = new VerzikCrabState(npc, client, config);
                break;
        }
        trackedCrabs.put(index, state);
        log.debug("Tracking Gemstone Crab index={} as {}", index, bossType.getDisplayName());
    }

    private void cleanupAll()
    {
        for (CrabBossState state : trackedCrabs.values())
        {
            try
            {
                state.cleanup();
            }
            catch (Exception e)
            {
                log.debug("Error during cleanup", e);
            }
        }
        trackedCrabs.clear();
    }

    private static final int TRANSPARENCY_ALPHA = 150;

    @Subscribe
    public void onBeforeRender(BeforeRender event)
    {
        if (!config.enabled())
        {
            return;
        }

        for (CrabBossState state : trackedCrabs.values())
        {
            applyTransparency(state.getBossModel());
        }
    }

    private void applyTransparency(Model model)
    {
        if (model == null)
        {
            return;
        }

        byte[] trans = model.getFaceTransparencies();
        if (trans == null || trans.length == 0)
        {
            return;
        }

        for (int i = 0; i < trans.length; i++)
        {
            int current = trans[i] & 0xFF;
            int combined = Math.max(current, TRANSPARENCY_ALPHA);
            trans[i] = (byte) combined;
        }
    }

    private boolean shouldDraw(Renderable renderable, boolean drawingUI)
    {
        return true;
    }
}
