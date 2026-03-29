package com.gemstonetrainer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.Getter;

import net.runelite.api.Actor;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public class VerzikCrabState implements CrabBossState
{
    private static final int VERZIK_P2_NPC_ID = 8372;
    private static final int IDLE_ANIM = 8113;
    private static final int RANGED_ANIM = 8114;
    private static final int MELEE_ANIM = 8116;
    private static final int URN_BOMB_PROJ = 1583;
    private static final int ZAP_BALL_PROJ = 1585;
    private static final int URN_BOMB_GFX_MODEL = 35390;
    private static final int URN_BOMB_GFX_ANIM = 8131;
    private static final int ATTACK_SPEED = 4;
    private static final int RANGED_ATTACKS_BEFORE_ZAP = 5;
    private static final int PROJECTILE_START_HEIGHT = 100;
    private static final int PROJECTILE_END_HEIGHT = 0;
    private static final int PROJECTILE_TRAVEL_CYCLES = 40;

    @Getter
    private final NPC npc;
    private final Client client;
    private final GemstoneTrainerConfig config;
    private RuneLiteObject verzikObject;
    private Animation idleAnimation;
    private Animation rangedAnimation;
    private Animation meleeAnimation;

    @Getter
    private AttackPhase currentPhase = AttackPhase.IDLE;
    @Getter
    private int attackTickCounter = ATTACK_SPEED;
    @Getter
    private int rangedAttackCounter = 0;

    // State carried between ticks in the attack cycle
    private boolean rangeAtt = false;
    private boolean meleeAtt = false;
    private boolean zapAtt = false;
    private WorldPoint targetLoc = null;

    private final List<RuneLiteObject> activeExplosions = new ArrayList<>();

    public VerzikCrabState(NPC npc, Client client, GemstoneTrainerConfig config)
    {
        this.npc = npc;
        this.client = client;
        this.config = config;
    }

    private void initModel()
    {
        NPCComposition verzikComp = client.getNpcDefinition(VERZIK_P2_NPC_ID);
        try
        {
            NPCComposition transformed = verzikComp.transform();
            if (transformed != null)
            {
                verzikComp = transformed;
            }
        }
        catch (Exception e)
        {
            // Transform fails outside ToB due to missing varbit state
        }

        int[] modelIds = verzikComp.getModels();
        if (modelIds == null || modelIds.length == 0)
        {
            return;
        }

        net.runelite.api.ModelData[] parts = new net.runelite.api.ModelData[modelIds.length];
        for (int i = 0; i < modelIds.length; i++)
        {
            parts[i] = client.loadModelData(modelIds[i]);
            if (parts[i] == null)
            {
                return;
            }
        }

        net.runelite.api.ModelData combined = client.mergeModels(parts);
        if (combined == null)
        {
            return;
        }

        applyGhostEffect(combined);

        // Scale down to 50% (64 = half of default 128)
        combined = combined.scale(64, 64, 64);

        Model model = combined.light();
        if (model == null)
        {
            return;
        }

        idleAnimation = client.loadAnimation(IDLE_ANIM);
        rangedAnimation = client.loadAnimation(RANGED_ANIM);
        meleeAnimation = client.loadAnimation(MELEE_ANIM);

        verzikObject = client.createRuneLiteObject();
        verzikObject.setModel(model);
        verzikObject.setShouldLoop(true);

        if (idleAnimation != null)
        {
            verzikObject.setAnimation(idleAnimation);
        }

        updatePosition();
        verzikObject.setActive(true);

    }

    public void updatePosition()
    {
        if (verzikObject == null || !verzikObject.isActive())
        {
            return;
        }

        LocalPoint centered = getCenteredLocation();
        if (centered != null)
        {
            verzikObject.setLocation(centered, client.getTopLevelWorldView().getPlane());
        }
    }

    @Override
    public void onGameTick()
    {
        if (verzikObject == null)
        {
            initModel();
            if (verzikObject == null)
            {
                return;
            }
        }

        updatePosition();
        processExplosions();
        attackCycle();
    }

    /**
     * Attack cycle matching the web simulator timing.
     * Counter counts down: 4 -> 3 -> 2 -> 1 -> 0 -> (reset to 3) -> ...
     *
     * Case 1: Determine target location and attack type (melee vs ranged vs zap)
     * Case 0: Perform attack animation, reset counter
     * Case 3: Create bomb/zap projectile if ranged
     * Case 2: Detonate bomb
     */
    private void attackCycle()
    {
        int current = attackTickCounter;
        attackTickCounter--;

        switch (current)
        {
            case 3:
                // Spawn projectile from previous attack
                if (targetLoc != null)
                {
                    if (zapAtt)
                    {
                        Player player = client.getLocalPlayer();
                        spawnProjectile(ZAP_BALL_PROJ, targetLoc, player);
                    }
                    else if (rangeAtt)
                    {
                        spawnProjectile(URN_BOMB_PROJ, targetLoc, null);
                    }
                }
                break;

            case 2:
                // Detonate previous bomb
                if (rangeAtt && targetLoc != null)
                {
                    spawnExplosion(targetLoc);
                    playSound(config.explosionSoundId());
                    checkHit(targetLoc);
                }
                // Return to idle
                currentPhase = AttackPhase.IDLE;
                if (idleAnimation != null)
                {
                    verzikObject.setAnimation(idleAnimation);
                    verzikObject.setShouldLoop(true);
                }
                break;

            case 1:
                // Determine next attack: save target location and check melee range
                Player player = client.getLocalPlayer();
                if (player != null)
                {
                    targetLoc = player.getWorldLocation();
                    WorldArea playerArea = player.getWorldArea();
                    WorldArea crabArea = npc.getWorldArea();
                    meleeAtt = playerArea.isInMeleeDistance(crabArea);
                    rangeAtt = false;
                    zapAtt = false;

                    if (!meleeAtt)
                    {
                        rangedAttackCounter++;
                        if (rangedAttackCounter > RANGED_ATTACKS_BEFORE_ZAP)
                        {
                            zapAtt = true;
                            rangedAttackCounter = 0;
                        }
                        else
                        {
                            rangeAtt = true;
                        }
                    }
                }
                break;

            case 0:
                // Perform attack
                performAttack();
                attackTickCounter += ATTACK_SPEED; // reset: -1 + 4 = 3
                break;

            default:
                break;
        }
    }

    private void facePlayer()
    {
        if (verzikObject == null)
        {
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }

        LocalPoint verzikLp = getCenteredLocation();
        WorldPoint playerWp = player.getWorldLocation();
        WorldView worldView = client.getTopLevelWorldView();
        LocalPoint playerLp = LocalPoint.fromWorld(worldView, playerWp);
        if (verzikLp == null || playerLp == null)
        {
            return;
        }

        int dx = playerLp.getX() - verzikLp.getX();
        int dy = playerLp.getY() - verzikLp.getY();
        // OSRS angle: 0=south, 512=west, 1024=north, 1536=east
        // atan2 gives radians, convert to 0-2047 range
        int angle = (int) (Math.atan2(-dx, -dy) * 1024.0 / Math.PI) & 2047;
        verzikObject.setOrientation(angle);
    }

    private void performAttack()
    {
        facePlayer();

        if (meleeAtt)
        {
            currentPhase = AttackPhase.MELEE_ATTACK;
            if (meleeAnimation != null)
            {
                verzikObject.setAnimation(meleeAnimation);
                verzikObject.setShouldLoop(false);
            }
            playSound(config.meleeSoundId());
            // Bounce hits the player if in melee range
            sayOuch();
        }
        else if (zapAtt)
        {
            currentPhase = AttackPhase.ZAP_ATTACK;
            if (rangedAnimation != null)
            {
                verzikObject.setAnimation(rangedAnimation);
                verzikObject.setShouldLoop(false);
            }
            playSound(config.rangedSoundId());
        }
        else
        {
            currentPhase = AttackPhase.RANGED_ATTACK;
            if (rangedAnimation != null)
            {
                verzikObject.setAnimation(rangedAnimation);
                verzikObject.setShouldLoop(false);
            }
            playSound(config.rangedSoundId());
        }
    }

    private void checkHit(WorldPoint targetTile)
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }
        if (player.getWorldLocation().equals(targetTile))
        {
            sayOuch();
        }
    }

    private void sayOuch()
    {
        Player player = client.getLocalPlayer();
        if (player != null)
        {
            player.setOverheadText("Ouch!");
            player.setOverheadCycle(100);
        }
    }

    /**
     * Recolors a model to a ghostly pale blue tint.
     * OSRS HSL: bits [14:9]=hue, [8:7]=saturation, [6:0]=luminance
     */
    static void applyGhostEffect(net.runelite.api.ModelData modelData)
    {
        // Recolor to ghostly pale blue — recolor in-place on the original
        short[] faceColors = modelData.getFaceColors();
        if (faceColors != null)
        {
            java.util.Set<Short> seen = new java.util.HashSet<>();
            for (short color : faceColors)
            {
                seen.add(color);
            }

            for (short original : seen)
            {
                int lum = original & 0x7F;
                int ghostLum = Math.min(127, 80 + lum / 3);
                short ghostColor = (short) ((42 << 10) | (1 << 7) | ghostLum);
                modelData.recolor(original, ghostColor);
            }
        }
    }

    private void playSound(int soundId)
    {
        if (soundId < 0)
        {
            return;
        }
        client.playSoundEffect(soundId);
    }

    private LocalPoint getCenteredLocation()
    {
        return getPlayerFacingEdgeLocation();
    }

    /**
     * Returns the local point at the center of the NPC edge facing the player.
     */
    private LocalPoint getPlayerFacingEdgeLocation()
    {
        WorldView worldView = client.getTopLevelWorldView();
        WorldPoint swTile = npc.getWorldLocation();
        LocalPoint swLp = LocalPoint.fromWorld(worldView, swTile);
        if (swLp == null)
        {
            return null;
        }

        int npcSize = npc.getComposition().getSize();
        int centerOffset = (npcSize - 1) * 128 / 2;
        int edgeOffset = (npcSize - 1) * 128 / 2; // distance from center to edge

        // Default to center
        int cx = swLp.getX() + centerOffset;
        int cy = swLp.getY() + centerOffset;

        Player player = client.getLocalPlayer();
        if (player != null)
        {
            LocalPoint playerLp = LocalPoint.fromWorld(worldView, player.getWorldLocation());
            if (playerLp != null)
            {
                int dx = playerLp.getX() - cx;
                int dy = playerLp.getY() - cy;

                // Pick dominant axis for edge placement
                if (Math.abs(dx) >= Math.abs(dy))
                {
                    cx += (dx > 0 ? edgeOffset : -edgeOffset);
                }
                else
                {
                    cy += (dy > 0 ? edgeOffset : -edgeOffset);
                }
            }
        }

        return new LocalPoint(cx, cy);
    }

    private void spawnProjectile(int projectileId, WorldPoint targetTile, Actor targetActor)
    {
        WorldView worldView = client.getTopLevelWorldView();
        int plane = worldView.getPlane();
        int cycle = client.getGameCycle();

        LocalPoint sourceLp = getCenteredLocation();
        LocalPoint targetLp = LocalPoint.fromWorld(worldView, targetTile);
        if (sourceLp == null || targetLp == null)
        {
            return;
        }

        Projectile proj = worldView.createProjectile(
            projectileId,
            plane,
            sourceLp.getX(),
            sourceLp.getY(),
            PROJECTILE_START_HEIGHT,
            cycle,
            cycle + PROJECTILE_TRAVEL_CYCLES,
            0,
            PROJECTILE_START_HEIGHT,
            PROJECTILE_END_HEIGHT,
            targetActor,
            targetLp.getX(),
            targetLp.getY()
        );
    }

    private void processExplosions()
    {
        Iterator<RuneLiteObject> activeIt = activeExplosions.iterator();
        while (activeIt.hasNext())
        {
            RuneLiteObject explosion = activeIt.next();
            if (explosion.finished())
            {
                explosion.setActive(false);
                activeIt.remove();
            }
        }
    }

    private void spawnExplosion(WorldPoint tile)
    {
        WorldView worldView = client.getTopLevelWorldView();
        LocalPoint lp = LocalPoint.fromWorld(worldView, tile);
        if (lp == null)
        {
            return;
        }

        Animation explosionAnim = client.loadAnimation(URN_BOMB_GFX_ANIM);
        if (explosionAnim == null)
        {
            return;
        }

        net.runelite.api.ModelData explosionModelData = client.loadModelData(URN_BOMB_GFX_MODEL);
        if (explosionModelData == null)
        {
            return;
        }

        Model explosionModel = explosionModelData.light();
        if (explosionModel == null)
        {
            return;
        }

        RuneLiteObject explosion = client.createRuneLiteObject();
        explosion.setModel(explosionModel);
        explosion.setAnimation(explosionAnim);
        explosion.setShouldLoop(false);
        explosion.setLocation(lp, worldView.getPlane());
        explosion.setActive(true);

        activeExplosions.add(explosion);
    }

    @Override
    public Model getBossModel()
    {
        return verzikObject != null ? verzikObject.getModel() : null;
    }

    @Override
    public LinkedHashMap<String, String> getDebugInfo()
    {
        LinkedHashMap<String, String> info = new LinkedHashMap<>();
        info.put("Boss", "Verzik P2");
        info.put("Phase", currentPhase.name());
        info.put("Attack Tick", attackTickCounter + " / " + ATTACK_SPEED);
        info.put("Ranged Count", rangedAttackCounter + " / " + RANGED_ATTACKS_BEFORE_ZAP);
        return info;
    }

    @Override
    public void cleanup()
    {
        if (verzikObject != null)
        {
            verzikObject.setActive(false);
            verzikObject = null;
        }


        for (RuneLiteObject explosion : activeExplosions)
        {
            explosion.setActive(false);
        }
        activeExplosions.clear();
    }

    public boolean isTracking()
    {
        return verzikObject != null && verzikObject.isActive();
    }
}
