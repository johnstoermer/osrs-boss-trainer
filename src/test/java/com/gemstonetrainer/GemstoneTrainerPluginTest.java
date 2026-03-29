package com.gemstonetrainer;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GemstoneTrainerPluginTest
{
    private GemstoneTrainerPlugin plugin;

    @Mock private Client client;
    @Mock private GemstoneTrainerConfig config;
    @Mock private NPC gemstoneCrab;
    @Mock private NPC otherNpc;

    @Before
    public void setUp() throws Exception
    {
        MockitoAnnotations.openMocks(this);

        plugin = new GemstoneTrainerPlugin();

        // Inject mocks via reflection
        setField(plugin, "client", client);
        setField(plugin, "config", config);

        when(config.enabled()).thenReturn(true);
        when(gemstoneCrab.getId()).thenReturn(14779); // GEMSTONE_CRAB_ID
        when(gemstoneCrab.getName()).thenReturn("Gemstone Crab");
        when(gemstoneCrab.getIndex()).thenReturn(1);
        when(otherNpc.getId()).thenReturn(1234);
        when(otherNpc.getName()).thenReturn("Guard");
        when(otherNpc.getIndex()).thenReturn(2);
    }

    private void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testTrackedCrabsStartsEmpty()
    {
        assertTrue(plugin.getTrackedCrabs().isEmpty());
    }

    @Test
    public void testOnNpcSpawnedIgnoresNonCrabs()
    {
        when(config.enabled()).thenReturn(true);
        NpcSpawned event = new NpcSpawned(otherNpc);
        plugin.onNpcSpawned(event);
        assertTrue(plugin.getTrackedCrabs().isEmpty());
    }

    @Test
    public void testIsGemstoneCrabMatchesById() throws Exception
    {
        Method isGemstoneCrab = GemstoneTrainerPlugin.class.getDeclaredMethod("isGemstoneCrab", NPC.class);
        isGemstoneCrab.setAccessible(true);

        assertTrue((boolean) isGemstoneCrab.invoke(plugin, gemstoneCrab));
    }

    @Test
    public void testIsGemstoneCrabMatchesByName() throws Exception
    {
        NPC crabWithDifferentId = mock(NPC.class);
        when(crabWithDifferentId.getId()).thenReturn(99999);
        when(crabWithDifferentId.getName()).thenReturn("Gemstone Crab");

        Method isGemstoneCrab = GemstoneTrainerPlugin.class.getDeclaredMethod("isGemstoneCrab", NPC.class);
        isGemstoneCrab.setAccessible(true);

        assertTrue("Crab with matching name but different ID should be detected",
            (boolean) isGemstoneCrab.invoke(plugin, crabWithDifferentId));
    }

    @Test
    public void testIsGemstoneCrabRejectsNonCrab() throws Exception
    {
        Method isGemstoneCrab = GemstoneTrainerPlugin.class.getDeclaredMethod("isGemstoneCrab", NPC.class);
        isGemstoneCrab.setAccessible(true);

        assertFalse((boolean) isGemstoneCrab.invoke(plugin, otherNpc));
    }

    @Test
    public void testOnNpcSpawnedIgnoresWhenDisabled()
    {
        when(config.enabled()).thenReturn(false);
        NpcSpawned event = new NpcSpawned(gemstoneCrab);
        plugin.onNpcSpawned(event);
        assertTrue(plugin.getTrackedCrabs().isEmpty());
    }

    @Test
    public void testOnNpcDespawnedRemovesTrackedCrab()
    {
        // Manually add a mock state
        VerzikCrabState mockState = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, mockState);

        NpcDespawned event = new NpcDespawned(gemstoneCrab);
        plugin.onNpcDespawned(event);

        assertFalse(plugin.getTrackedCrabs().containsKey(1));
        verify(mockState).cleanup();
    }

    @Test
    public void testOnNpcDespawnedIgnoresUntrackedNpc()
    {
        NpcDespawned event = new NpcDespawned(otherNpc);
        // Should not throw
        plugin.onNpcDespawned(event);
        assertTrue(plugin.getTrackedCrabs().isEmpty());
    }

    @Test
    public void testOnGameStateChangedCleansUpOnLoginScreen()
    {
        VerzikCrabState mockState = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, mockState);

        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);

        assertTrue(plugin.getTrackedCrabs().isEmpty());
        verify(mockState).cleanup();
    }

    @Test
    public void testOnGameStateChangedCleansUpOnHopping()
    {
        VerzikCrabState mockState = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, mockState);

        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.HOPPING);
        plugin.onGameStateChanged(event);

        assertTrue(plugin.getTrackedCrabs().isEmpty());
        verify(mockState).cleanup();
    }

    @Test
    public void testOnGameStateChangedDoesNothingOnLoggedIn()
    {
        VerzikCrabState mockState = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, mockState);

        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGGED_IN);
        plugin.onGameStateChanged(event);

        // Should still have tracked crabs
        assertFalse(plugin.getTrackedCrabs().isEmpty());
        verify(mockState, never()).cleanup();
    }

    @Test
    public void testShouldDrawHidesCrabWhenTracked() throws Exception
    {
        // Add tracked crab
        VerzikCrabState mockState = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, mockState);

        Method shouldDraw = GemstoneTrainerPlugin.class.getDeclaredMethod("shouldDraw", Renderable.class, boolean.class);
        shouldDraw.setAccessible(true);

        boolean result = (boolean) shouldDraw.invoke(plugin, gemstoneCrab, false);
        assertFalse("Tracked crab should be hidden", result);
    }

    @Test
    public void testShouldDrawShowsUntrackedCrab() throws Exception
    {
        // No tracked crabs
        Method shouldDraw = GemstoneTrainerPlugin.class.getDeclaredMethod("shouldDraw", Renderable.class, boolean.class);
        shouldDraw.setAccessible(true);

        boolean result = (boolean) shouldDraw.invoke(plugin, gemstoneCrab, false);
        assertTrue("Untracked crab should be shown", result);
    }

    @Test
    public void testShouldDrawShowsNonCrabNpc() throws Exception
    {
        VerzikCrabState mockState = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, mockState);

        Method shouldDraw = GemstoneTrainerPlugin.class.getDeclaredMethod("shouldDraw", Renderable.class, boolean.class);
        shouldDraw.setAccessible(true);

        boolean result = (boolean) shouldDraw.invoke(plugin, otherNpc, false);
        assertTrue("Non-crab NPC should be shown", result);
    }

    @Test
    public void testShouldDrawShowsEverythingWhenDisabled() throws Exception
    {
        when(config.enabled()).thenReturn(false);
        VerzikCrabState mockState = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, mockState);

        Method shouldDraw = GemstoneTrainerPlugin.class.getDeclaredMethod("shouldDraw", Renderable.class, boolean.class);
        shouldDraw.setAccessible(true);

        boolean result = (boolean) shouldDraw.invoke(plugin, gemstoneCrab, false);
        assertTrue("Everything should draw when disabled", result);
    }

    @Test
    public void testOnGameTickUpdatesAllTrackedCrabs()
    {
        VerzikCrabState state1 = mock(VerzikCrabState.class);
        VerzikCrabState state2 = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, state1);
        plugin.getTrackedCrabs().put(2, state2);

        plugin.onGameTick(null);

        verify(state1).onGameTick();
        verify(state2).onGameTick();
    }

    @Test
    public void testOnGameTickDoesNothingWhenDisabled()
    {
        when(config.enabled()).thenReturn(false);
        VerzikCrabState state1 = mock(VerzikCrabState.class);
        plugin.getTrackedCrabs().put(1, state1);

        plugin.onGameTick(null);

        verify(state1, never()).onGameTick();
    }
}
