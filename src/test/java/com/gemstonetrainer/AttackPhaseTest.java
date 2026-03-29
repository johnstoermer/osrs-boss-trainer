package com.gemstonetrainer;

import org.junit.Test;

import static org.junit.Assert.*;

public class AttackPhaseTest
{
    @Test
    public void testAllPhasesExist()
    {
        AttackPhase[] phases = AttackPhase.values();
        assertEquals(4, phases.length);
    }

    @Test
    public void testPhaseNames()
    {
        assertEquals("IDLE", AttackPhase.IDLE.name());
        assertEquals("MELEE_ATTACK", AttackPhase.MELEE_ATTACK.name());
        assertEquals("RANGED_ATTACK", AttackPhase.RANGED_ATTACK.name());
        assertEquals("ZAP_ATTACK", AttackPhase.ZAP_ATTACK.name());
    }

    @Test
    public void testValueOf()
    {
        assertEquals(AttackPhase.IDLE, AttackPhase.valueOf("IDLE"));
        assertEquals(AttackPhase.MELEE_ATTACK, AttackPhase.valueOf("MELEE_ATTACK"));
        assertEquals(AttackPhase.RANGED_ATTACK, AttackPhase.valueOf("RANGED_ATTACK"));
        assertEquals(AttackPhase.ZAP_ATTACK, AttackPhase.valueOf("ZAP_ATTACK"));
    }
}
