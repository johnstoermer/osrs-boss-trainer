package com.gemstonetrainer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("gemstonetrainer")
public interface GemstoneTrainerConfig extends Config
{
    @ConfigItem(
        keyName = "bossType",
        name = "Boss Type",
        description = "Which boss to overlay on the Gemstone Crab",
        position = -1
    )
    default BossType bossType()
    {
        return BossType.VERZIK_P2;
    }

    @ConfigItem(
        keyName = "enabled",
        name = "Enabled",
        description = "Enable the Gemstone Trainer overlay",
        position = 0
    )
    default boolean enabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showDebugOverlay",
        name = "Show Debug Overlay",
        description = "Show debug info: attack phase, tick counter",
        position = 1
    )
    default boolean showDebugOverlay()
    {
        return false;
    }

    // --- Sol Heredit ---

    @ConfigSection(
        name = "Sol Heredit",
        description = "Sol Heredit phase and sound settings",
        position = 2,
        closedByDefault = true
    )
    String solSection = "sol";

    @ConfigItem(
        keyName = "solSpearStartSound",
        name = "Spear Start Sound",
        description = "Sound ID for spear attack start (-1 to disable)",
        section = "sol",
        position = 4
    )
    default int solSpearStartSound()
    {
        return 8147;
    }

    @ConfigItem(
        keyName = "solSpearEndSound",
        name = "Spear End Sound",
        description = "Sound ID for spear attack end (-1 to disable)",
        section = "sol",
        position = 5
    )
    default int solSpearEndSound()
    {
        return 8047;
    }

    @ConfigItem(
        keyName = "solShieldStartSound",
        name = "Shield Start Sound",
        description = "Sound ID for shield attack start (-1 to disable)",
        section = "sol",
        position = 6
    )
    default int solShieldStartSound()
    {
        return 8150;
    }

    @ConfigItem(
        keyName = "solShieldEndSound",
        name = "Shield End Sound",
        description = "Sound ID for shield attack end (-1 to disable)",
        section = "sol",
        position = 7
    )
    default int solShieldEndSound()
    {
        return 8145;
    }

    // --- Verzik P2 ---

    @ConfigSection(
        name = "Verzik P2 Sounds",
        description = "Sound IDs for Verzik attacks (from OSRS wiki sound ID list, -1 to disable)",
        position = 20,
        closedByDefault = true
    )
    String soundSection = "sounds";

    @ConfigItem(
        keyName = "rangedSoundId",
        name = "Ranged Attack Sound",
        description = "Sound ID for urn bomb ranged attack",
        section = "sounds",
        position = 3
    )
    default int rangedSoundId()
    {
        return 3987;
    }

    @ConfigItem(
        keyName = "meleeSoundId",
        name = "Melee Attack Sound",
        description = "Sound ID for melee bounce attack",
        section = "sounds",
        position = 4
    )
    default int meleeSoundId()
    {
        return 3968;
    }

    @ConfigItem(
        keyName = "explosionSoundId",
        name = "Urn Explosion Sound",
        description = "Sound ID for urn bomb explosion on landing",
        section = "sounds",
        position = 5
    )
    default int explosionSoundId()
    {
        return 8252;
    }
}
