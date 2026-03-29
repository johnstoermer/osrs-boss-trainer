package com.verziktrainer;

import java.util.LinkedHashMap;
import net.runelite.api.Model;
import net.runelite.api.NPC;

/**
 * Interface for boss overlays on the Gemstone Crab.
 */
public interface CrabBossState
{
    NPC getNpc();

    void onGameTick();

    void cleanup();

    boolean isTracking();

    /**
     * Returns debug info as ordered label-value pairs for the overlay.
     */
    LinkedHashMap<String, String> getDebugInfo();

    /**
     * Returns the boss overlay model for transparency modification, or null.
     */
    Model getBossModel();
}
