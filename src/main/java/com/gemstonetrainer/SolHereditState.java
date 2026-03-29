package com.gemstonetrainer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

@Slf4j
public class SolHereditState implements CrabBossState
{
    private static final int SOL_NPC_ID = 15554;

    // Animation IDs
    private static final int IDLE_ANIM = 10874;
    private static final int SPEAR_ANIM = 10883;
    private static final int SHIELD_ANIM = 10885;

    // Attack durations (ticks)
    private static final int SPEAR_DURATION = 6;
    private static final int SHIELD_DURATION = 5;
    // Ticks the attack animation plays before returning to idle
    private static final int ATTACK_ANIM_TICKS = 3;

    private static final int BOSS_HALF_SIZE = 2; // 5x5 boss, half = 2

    @Getter
    private final NPC npc;
    private final Client client;
    private final GemstoneTrainerConfig config;
    private RuneLiteObject solObject;
    private Animation idleAnimation;
    private Animation spearAnimation;
    private Animation shieldAnimation;

    // Attack state
    @Getter
    private SolAttackType currentAttack = SolAttackType.IDLE;
    @Getter
    private int ticksIntoAttack = 0;
    private int idleTicks = 0;

    // Sequencing: track last style for variant selection
    private enum AttackStyle { SPEAR, SHIELD }
    private AttackStyle lastStyle = null;
    private boolean usedSameStyleTwice = false;
    private int currentVariant = 1; // 1 or 2

    private final List<GroundSlam> activeSlams = new ArrayList<>();
    private final Random random = new Random();
    private List<Map.Entry<WorldPoint, Integer>> pendingSlams = null;
    private boolean pendingOuch = false;

    public SolHereditState(NPC npc, Client client, GemstoneTrainerConfig config)
    {
        this.npc = npc;
        this.client = client;
        this.config = config;
    }

    private void initModel()
    {
        NPCComposition solComp = client.getNpcDefinition(SOL_NPC_ID);
        try
        {
            NPCComposition transformed = solComp.transform();
            if (transformed != null)
            {
                solComp = transformed;
            }
        }
        catch (Throwable e)
        {
            // Transform may fail outside Colosseum
        }

        int[] modelIds = solComp.getModels();
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

        VerzikCrabState.applyGhostEffect(combined);

        Model model = combined.light();
        if (model == null)
        {
            return;
        }

        idleAnimation = client.loadAnimation(IDLE_ANIM);
        spearAnimation = client.loadAnimation(SPEAR_ANIM);
        shieldAnimation = client.loadAnimation(SHIELD_ANIM);

        solObject = client.createRuneLiteObject();
        solObject.setModel(model);
        solObject.setShouldLoop(true);

        if (idleAnimation != null)
        {
            solObject.setAnimation(idleAnimation);
        }

        updatePosition();
        solObject.setActive(true);
    }

    private void updatePosition()
    {
        if (solObject == null || !solObject.isActive())
        {
            return;
        }

        LocalPoint centered = getCenteredLocation();
        if (centered != null)
        {
            solObject.setLocation(centered, client.getTopLevelWorldView().getPlane());
        }
    }

    @Override
    public void onGameTick()
    {
        if (solObject == null)
        {
            initModel();
            if (solObject == null)
            {
                return;
            }
        }

        updatePosition();

        try
        {
            processSlams();

            if (currentAttack == SolAttackType.IDLE)
            {
                idleTicks++;
                if (idleTicks >= 2)
                {
                    selectNextAttack();
                    idleTicks = 0;
                }
            }
            else
            {
                processAttackTick();
            }
        }
        catch (Throwable e)
        {
            log.warn("Error in Sol attack cycle", e);
            currentAttack = SolAttackType.IDLE;
            ticksIntoAttack = 0;
        }
    }

    private void processSlams()
    {
        Iterator<GroundSlam> it = activeSlams.iterator();
        while (it.hasNext())
        {
            GroundSlam slam = it.next();
            if (!slam.tick())
            {
                it.remove();
            }
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

    private void selectNextAttack()
    {
        // Pick spear or shield (equal weight)
        AttackStyle style = random.nextBoolean() ? AttackStyle.SPEAR : AttackStyle.SHIELD;

        // Sequencing rule:
        // Same style twice in a row → second is v2, then resets to v1
        // Switching styles → always v1
        if (style == lastStyle && currentVariant == 1)
        {
            currentVariant = 2;
        }
        else
        {
            currentVariant = 1;
        }

        lastStyle = style;

        if (style == AttackStyle.SPEAR)
        {
            currentAttack = (currentVariant == 1) ? SolAttackType.SPEAR : SolAttackType.SPEAR;
        }
        else
        {
            currentAttack = (currentVariant == 1) ? SolAttackType.SHIELD : SolAttackType.SHIELD;
        }

        // Store variant in the attack type name isn't possible, so we track it separately
        ticksIntoAttack = 0;
        startAttack(style);
    }

    private void startAttack(AttackStyle style)
    {
        facePlayer();

        if (style == AttackStyle.SPEAR)
        {
            currentAttack = SolAttackType.SPEAR;
            if (spearAnimation != null)
            {
                solObject.setShouldLoop(true);
                solObject.setAnimation(spearAnimation);
            }
            playSound(config.solSpearStartSound());
        }
        else
        {
            currentAttack = SolAttackType.SHIELD;
            if (shieldAnimation != null)
            {
                solObject.setShouldLoop(true);
                solObject.setAnimation(shieldAnimation);
            }
            playSound(config.solShieldStartSound());
        }
    }

    private void processAttackTick()
    {
        ticksIntoAttack++;

        switch (currentAttack)
        {
            case SPEAR:
                processSpear();
                break;
            case SHIELD:
                processShield();
                break;
            default:
                finishAttack();
                break;
        }
    }

    private void processSpear()
    {
        if (ticksIntoAttack == 2)
        {
            // Precompute tiles and check hit 1 tick before visuals
            WorldPoint center = getBossCenter();
            Player player = client.getLocalPlayer();
            if (center != null && player != null)
            {
                int[] dir = TilePatterns.getCardinalDirection(center, player.getWorldLocation());
                if (currentVariant == 1)
                {
                    pendingSlams = TilePatterns.getSpear1(center, dir[0], dir[1], BOSS_HALF_SIZE);
                }
                else
                {
                    pendingSlams = TilePatterns.getSpear2(center, dir[0], dir[1], BOSS_HALF_SIZE);
                }
                checkHitAgainstTiles(pendingSlams);
            }
        }
        else if (ticksIntoAttack == 3)
        {
            playSound(config.solSpearEndSound());
            if (pendingSlams != null)
            {
                spawnSlams(pendingSlams, false);
                pendingSlams = null;
            }
            if (pendingOuch)
            {
                pendingOuch = false;
                sayOuch();
            }
        }
        if (ticksIntoAttack >= SPEAR_DURATION)
        {
            finishAttack();
        }
    }

    private void processShield()
    {
        if (ticksIntoAttack == 2)
        {
            // Precompute tiles and check hit 1 tick before visuals
            WorldPoint center = getBossCenter();
            if (center != null)
            {
                if (currentVariant == 1)
                {
                    pendingSlams = TilePatterns.getShield1(center, BOSS_HALF_SIZE);
                }
                else
                {
                    pendingSlams = TilePatterns.getShield2(center, BOSS_HALF_SIZE);
                }
                checkHitAgainstTiles(pendingSlams);
            }
        }
        else if (ticksIntoAttack == 3)
        {
            playSound(config.solShieldEndSound());
            if (pendingSlams != null)
            {
                spawnSlams(pendingSlams, false);
                pendingSlams = null;
            }
            if (pendingOuch)
            {
                pendingOuch = false;
                sayOuch();
            }
        }
        else if (ticksIntoAttack == 4)
        {
            returnToIdle();
        }

        if (ticksIntoAttack >= SHIELD_DURATION)
        {
            finishAttack();
        }
    }

    private void returnToIdle()
    {
        if (solObject != null && idleAnimation != null)
        {
            solObject.setAnimation(idleAnimation);
            solObject.setShouldLoop(true);
        }
    }

    private void finishAttack()
    {
        currentAttack = SolAttackType.IDLE;
        ticksIntoAttack = 0;
        idleTicks = 0;
        returnToIdle();
    }

    private void checkHitAgainstTiles(List<Map.Entry<WorldPoint, Integer>> tiles)
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }
        WorldPoint playerTile = player.getWorldLocation();
        for (Map.Entry<WorldPoint, Integer> entry : tiles)
        {
            if (entry.getValue() == 0 && playerTile.equals(entry.getKey()))
            {
                pendingOuch = true;
                return;
            }
        }
    }

    private void spawnSlams(List<Map.Entry<WorldPoint, Integer>> tiles, boolean checkHit)
    {
        Player player = client.getLocalPlayer();
        WorldPoint playerTile = player != null ? player.getWorldLocation() : null;
        boolean hit = false;

        for (Map.Entry<WorldPoint, Integer> entry : tiles)
        {
            try
            {
                activeSlams.add(new GroundSlam(client, entry.getKey(), entry.getValue()));

                if (checkHit && entry.getValue() == 0 && playerTile != null && playerTile.equals(entry.getKey()))
                {
                    hit = true;
                }
            }
            catch (Throwable e)
            {
                // Skip tiles that fail to create
            }
        }

        if (hit)
        {
            sayOuch();
        }
    }

    private void facePlayer()
    {
        if (solObject == null)
        {
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }

        LocalPoint solLp = getCenteredLocation();
        WorldView worldView = client.getTopLevelWorldView();
        LocalPoint playerLp = LocalPoint.fromWorld(worldView, player.getWorldLocation());
        if (solLp == null || playerLp == null)
        {
            return;
        }

        int dx = playerLp.getX() - solLp.getX();
        int dy = playerLp.getY() - solLp.getY();
        int angle = (int) (Math.atan2(-dx, -dy) * 1024.0 / Math.PI) & 2047;
        solObject.setOrientation(angle);
    }

    private void playSound(int soundId)
    {
        if (soundId >= 0)
        {
            client.playSoundEffect(soundId);
        }
    }

    private LocalPoint getCenteredLocation()
    {
        return getPlayerFacingEdgeLocation();
    }

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
        int edgeOffset = (npcSize - 1) * 128 / 2;

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

    private WorldPoint getBossCenter()
    {
        WorldPoint sw = npc.getWorldLocation();
        int size = npc.getComposition().getSize();
        int offset = (size - 1) / 2;
        return new WorldPoint(sw.getX() + offset, sw.getY() + offset, sw.getPlane());
    }

    @Override
    public Model getBossModel()
    {
        return solObject != null ? solObject.getModel() : null;
    }

    @Override
    public LinkedHashMap<String, String> getDebugInfo()
    {
        LinkedHashMap<String, String> info = new LinkedHashMap<>();
        info.put("Boss", "Sol Heredit");
        info.put("Attack", currentAttack.name() + (currentAttack != SolAttackType.IDLE ? " v" + currentVariant : ""));
        info.put("Attack Tick", String.valueOf(ticksIntoAttack));
        info.put("Last Style", lastStyle != null ? lastStyle.name() : "NONE");
        return info;
    }

    @Override
    public void cleanup()
    {
        if (solObject != null)
        {
            solObject.setActive(false);
            solObject = null;
        }

        for (GroundSlam slam : activeSlams)
        {
            slam.cleanup();
        }
        activeSlams.clear();
    }

    @Override
    public boolean isTracking()
    {
        return solObject != null && solObject.isActive();
    }
}
