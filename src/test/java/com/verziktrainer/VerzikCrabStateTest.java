package com.verziktrainer;

import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class VerzikCrabStateTest
{
    @Mock private Client client;
    @Mock private NPC npc;
    @Mock private WorldView worldView;
    @Mock private NPCComposition npcComp;
    @Mock private ModelData modelData;
    @Mock private Model model;
    @Mock private Animation idleAnim;
    @Mock private Animation rangedAnim;
    @Mock private Animation meleeAnim;
    @Mock private RuneLiteObject verzikObject;
    @Mock private Player player;
    @Mock private Projectile projectile;

    private VerzikCrabState state;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);

        // Set up client to return world view
        when(client.getTopLevelWorldView()).thenReturn(worldView);

        // Set up NPC composition loading for Verzik P2 (ID 8372)
        when(client.getNpcDefinition(8372)).thenReturn(npcComp);
        when(npcComp.transform()).thenReturn(null); // no transformation
        when(npcComp.getModels()).thenReturn(new int[]{12345});

        // Set up model loading
        when(client.loadModelData(12345)).thenReturn(modelData);
        when(client.mergeModels(any(ModelData[].class))).thenReturn(modelData);
        when(modelData.light()).thenReturn(model);

        // Set up animation loading
        when(client.loadAnimation(8113)).thenReturn(idleAnim);  // IDLE
        when(client.loadAnimation(8114)).thenReturn(rangedAnim); // RANGED
        when(client.loadAnimation(8116)).thenReturn(meleeAnim);  // MELEE

        // Set up RuneLiteObject creation
        when(client.createRuneLiteObject()).thenReturn(verzikObject);
        when(verzikObject.isActive()).thenReturn(true);

        // Set up NPC position
        WorldPoint crabPoint = new WorldPoint(3200, 3200, 0);
        when(npc.getWorldLocation()).thenReturn(crabPoint);
        when(worldView.getPlane()).thenReturn(0);

        // Mock LocalPoint.fromWorld - it's static so we need to set up the world view scene
        // LocalPoint.fromWorld uses worldView internally, we need to mock it properly
        // Since LocalPoint.fromWorld is static, we'll just ensure it doesn't NPE by mocking worldView

        // Set up local player
        when(client.getLocalPlayer()).thenReturn(player);

        // Create the state
        state = new VerzikCrabState(npc, client);
    }

    @Test
    public void testInitialState()
    {
        assertEquals(AttackPhase.IDLE, state.getCurrentPhase());
        assertEquals(0, state.getAttackTickCounter());
        assertEquals(0, state.getUrnBombCounter());
    }

    @Test
    public void testNpcGetter()
    {
        assertSame(npc, state.getNpc());
    }

    @Test
    public void testAttackTickCounterIncrements()
    {
        // First tick increments counter to 1
        state.onGameTick();
        assertEquals(1, state.getAttackTickCounter());

        // Second tick increments to 2
        state.onGameTick();
        assertEquals(2, state.getAttackTickCounter());

        // Third tick increments to 3
        state.onGameTick();
        assertEquals(3, state.getAttackTickCounter());

        // Fourth tick should reset to 0 (>= ATTACK_CYCLE_TICKS=4)
        state.onGameTick();
        assertEquals(0, state.getAttackTickCounter());
    }

    @Test
    public void testAttackCycleWrapsAround()
    {
        // Run through 2 full cycles
        for (int cycle = 0; cycle < 2; cycle++)
        {
            for (int tick = 0; tick < 4; tick++)
            {
                state.onGameTick();
            }
            assertEquals("Counter should be 0 after cycle " + cycle, 0, state.getAttackTickCounter());
        }
    }

    @Test
    public void testMeleeAttackWhenInRange()
    {
        // Set up player in melee range
        WorldArea playerArea = mock(WorldArea.class);
        WorldArea crabArea = mock(WorldArea.class);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(crabArea);
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(true);

        // Tick 1 triggers attack
        state.onGameTick();
        assertEquals(AttackPhase.MELEE_ATTACK, state.getCurrentPhase());
        // Melee attack should not increment urn bomb counter
        assertEquals(0, state.getUrnBombCounter());
    }

    @Test
    public void testMeleeAttackPlaysAnimation()
    {
        WorldArea playerArea = mock(WorldArea.class);
        WorldArea crabArea = mock(WorldArea.class);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(crabArea);
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(true);

        state.onGameTick();
        verify(verzikObject).setAnimation(meleeAnim);
        verify(verzikObject).setShouldLoop(false);
    }

    @Test
    public void testRangedAttackWhenOutOfMeleeRange()
    {
        WorldArea playerArea = mock(WorldArea.class);
        WorldArea crabArea = mock(WorldArea.class);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(crabArea);
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(false);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3205, 3205, 0));
        when(client.getGameCycle()).thenReturn(100);

        state.onGameTick();
        assertEquals(AttackPhase.RANGED_ATTACK, state.getCurrentPhase());
        assertEquals(1, state.getUrnBombCounter());
    }

    @Test
    public void testUrnBombCounterIncrementsEachRangedAttack()
    {
        WorldArea playerArea = mock(WorldArea.class);
        WorldArea crabArea = mock(WorldArea.class);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(crabArea);
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(false);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3205, 3205, 0));
        when(client.getGameCycle()).thenReturn(100);

        // Do 3 full attack cycles (each 4 ticks), all ranged
        for (int cycle = 0; cycle < 3; cycle++)
        {
            for (int tick = 0; tick < 4; tick++)
            {
                state.onGameTick();
            }
        }
        assertEquals(3, state.getUrnBombCounter());
    }

    @Test
    public void testZapBallAfterFiveUrnBombs()
    {
        WorldArea playerArea = mock(WorldArea.class);
        WorldArea crabArea = mock(WorldArea.class);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(crabArea);
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(false);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3205, 3205, 0));
        when(client.getGameCycle()).thenReturn(100);

        // Do 5 ranged attack cycles to accumulate 5 urn bombs
        for (int cycle = 0; cycle < 5; cycle++)
        {
            for (int tick = 0; tick < 4; tick++)
            {
                state.onGameTick();
            }
        }
        assertEquals(5, state.getUrnBombCounter());

        // 6th attack cycle should trigger zap ball (urnBombCounter > 5)
        state.onGameTick(); // tick 1 of cycle 6 — performs attack
        assertEquals(AttackPhase.ZAP_ATTACK, state.getCurrentPhase());
        assertEquals(0, state.getUrnBombCounter()); // counter resets
    }

    @Test
    public void testZapBallResetsCounterAndContinuesPattern()
    {
        WorldArea playerArea = mock(WorldArea.class);
        WorldArea crabArea = mock(WorldArea.class);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(crabArea);
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(false);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3205, 3205, 0));
        when(client.getGameCycle()).thenReturn(100);

        // Run through 6 attack cycles (5 urn bombs + 1 zap)
        for (int cycle = 0; cycle < 6; cycle++)
        {
            for (int tick = 0; tick < 4; tick++)
            {
                state.onGameTick();
            }
        }
        assertEquals(0, state.getUrnBombCounter());

        // Next attack should be ranged again (counter starts fresh)
        state.onGameTick(); // tick 1 of next cycle
        assertEquals(AttackPhase.RANGED_ATTACK, state.getCurrentPhase());
        assertEquals(1, state.getUrnBombCounter());
    }

    @Test
    public void testIdleAfterAnimationDuration()
    {
        WorldArea playerArea = mock(WorldArea.class);
        WorldArea crabArea = mock(WorldArea.class);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(crabArea);
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(true);

        // Tick 1: attack happens
        state.onGameTick();
        assertEquals(AttackPhase.MELEE_ATTACK, state.getCurrentPhase());

        // Tick 2: still animating
        state.onGameTick();
        assertEquals(AttackPhase.MELEE_ATTACK, state.getCurrentPhase());

        // Tick 3: ATTACK_ANIM_DURATION+1 = 3, should return to idle
        state.onGameTick();
        assertEquals(AttackPhase.IDLE, state.getCurrentPhase());
    }

    @Test
    public void testMeleeDoesNotIncrementUrnBombCounter()
    {
        WorldArea playerArea = mock(WorldArea.class);
        WorldArea crabArea = mock(WorldArea.class);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(crabArea);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3205, 3205, 0));
        when(client.getGameCycle()).thenReturn(100);

        // 2 ranged attacks
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(false);
        for (int cycle = 0; cycle < 2; cycle++)
        {
            for (int tick = 0; tick < 4; tick++)
            {
                state.onGameTick();
            }
        }
        assertEquals(2, state.getUrnBombCounter());

        // Now melee attack
        when(playerArea.isInMeleeDistance(crabArea)).thenReturn(true);
        for (int tick = 0; tick < 4; tick++)
        {
            state.onGameTick();
        }
        // Counter should stay at 2 (melee doesn't affect it)
        assertEquals(2, state.getUrnBombCounter());
    }

    @Test
    public void testCleanupDeactivatesObject()
    {
        state.cleanup();
        verify(verzikObject).setActive(false);
    }

    @Test
    public void testCleanupWhenNoObject()
    {
        // Create a state that fails model init (no models)
        when(npcComp.getModels()).thenReturn(new int[0]);
        VerzikCrabState emptyState = new VerzikCrabState(npc, client);
        // Should not throw
        emptyState.cleanup();
    }

    @Test
    public void testIsTracking()
    {
        when(verzikObject.isActive()).thenReturn(true);
        assertTrue(state.isTracking());
    }

    @Test
    public void testIsNotTrackingAfterCleanup()
    {
        state.cleanup();
        assertFalse(state.isTracking());
    }

    @Test
    public void testNoPlayerDoesNotCrash()
    {
        when(client.getLocalPlayer()).thenReturn(null);
        // Should not throw
        state.onGameTick();
        assertEquals(1, state.getAttackTickCounter());
        // Phase stays IDLE because performAttack returns early
        assertEquals(AttackPhase.IDLE, state.getCurrentPhase());
    }

    @Test
    public void testModelInitWithTransform()
    {
        NPCComposition transformedComp = mock(NPCComposition.class);
        when(npcComp.transform()).thenReturn(transformedComp);
        when(transformedComp.getModels()).thenReturn(new int[]{99999});
        when(client.loadModelData(99999)).thenReturn(modelData);

        VerzikCrabState transformedState = new VerzikCrabState(npc, client);
        assertNotNull(transformedState);
    }

    @Test
    public void testModelInitWithNullModels()
    {
        when(npcComp.getModels()).thenReturn(null);
        VerzikCrabState nullState = new VerzikCrabState(npc, client);
        assertFalse(nullState.isTracking());
    }

    @Test
    public void testModelInitWithNullModelData()
    {
        when(client.loadModelData(12345)).thenReturn(null);
        VerzikCrabState nullState = new VerzikCrabState(npc, client);
        assertFalse(nullState.isTracking());
    }

    @Test
    public void testModelInitWithNullMergedModel()
    {
        when(client.mergeModels(any(ModelData[].class))).thenReturn(null);
        VerzikCrabState nullState = new VerzikCrabState(npc, client);
        assertFalse(nullState.isTracking());
    }

    @Test
    public void testModelInitWithNullLightModel()
    {
        when(modelData.light()).thenReturn(null);
        VerzikCrabState nullState = new VerzikCrabState(npc, client);
        assertFalse(nullState.isTracking());
    }

    @Test
    public void testGameTickWithNullVerzikObject()
    {
        // Force null object by failing model init
        when(npcComp.getModels()).thenReturn(null);
        VerzikCrabState nullState = new VerzikCrabState(npc, client);
        // Should not throw
        nullState.onGameTick();
    }
}
