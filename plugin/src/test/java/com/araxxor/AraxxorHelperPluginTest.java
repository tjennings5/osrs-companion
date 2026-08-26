package com.osrsmcp.araxxor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Skill;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

/**
 * Guards the animation and id mapping. These are the values the whole plugin
 * hangs on, and getting one wrong fails silently — the counter simply never
 * advances — so they are pinned rather than trusted.
 */
public class AraxxorHelperPluginTest
{
	@Test
	public void everyAttackAnimationCounts()
	{
		assertTrue(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ATTACK_MELEE_01));
		assertTrue(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ATTACK_RANGED_01));
		assertTrue(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ATTACK_MAGIC_01));
		assertTrue(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ATTACK_SLOW_MELEE_01));
		assertTrue(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ATTACK_SLOW_RANGED_01));
		// The enraged cleave replaces melee but is still a standard attack, so it
		// has to keep the egg cycle moving.
		assertTrue(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ATTACK_MELEE_ENRAGED_01));
	}

	@Test
	public void specialsAndIdlesAreNotStandardAttacks()
	{
		// Specials must not advance the egg count - the cycle is six *standard*
		// attacks, so counting these would drift the prediction early.
		assertFalse(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ACID_CANNON_01));
		assertFalse(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ATTACK_ACID_SPRAY_01));
		assertFalse(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_ATTACK_ACID_LEAK_01));
		assertFalse(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_IDLE_01));
		assertFalse(AraxxorHelperPlugin.isStandardAttack(AnimationID.NPC_ARAXXOR_01_WALK_01));
	}

	@Test
	public void eachSpecialAnimationMapsToItsAttack()
	{
		assertEquals(AraxxorSpecial.ACID_BALL,
			AraxxorHelperPlugin.specialFor(AnimationID.NPC_ARAXXOR_01_ACID_CANNON_01));
		assertEquals(AraxxorSpecial.ACID_SPLATTER,
			AraxxorHelperPlugin.specialFor(AnimationID.NPC_ARAXXOR_01_ATTACK_ACID_SPRAY_01));
		assertEquals(AraxxorSpecial.ACID_DRIP,
			AraxxorHelperPlugin.specialFor(AnimationID.NPC_ARAXXOR_01_ATTACK_ACID_LEAK_01));
		assertNull(AraxxorHelperPlugin.specialFor(AnimationID.NPC_ARAXXOR_01_ATTACK_MELEE_01));
	}

	@Test
	public void bothEnrageTransitionAnimationsAreRecognised()
	{
		assertTrue(AraxxorHelperPlugin.isEnrageTransition(AnimationID.NPC_ARAXXOR_01_ENRAGE_TRANSITION_01));
		assertTrue(AraxxorHelperPlugin.isEnrageTransition(AnimationID.NPC_ARAXXOR_01_ENRAGE_TRANSITION_02));
		assertFalse(AraxxorHelperPlugin.isEnrageTransition(AnimationID.NPC_ARAXXOR_01_ATTACK_MELEE_01));
	}

	@Test
	public void eggAndMinionIdsResolveToTheSameType()
	{
		for (AraxxorMinion minion : AraxxorMinion.values())
		{
			assertEquals(minion, AraxxorMinion.byEggId(minion.getEggNpcId()));
			assertEquals(minion, AraxxorMinion.byMinionId(minion.getMinionNpcId()));
			assertTrue(AraxxorMinion.isEgg(minion.getEggNpcId()));
			// An egg id must never be mistaken for a hatched minion id.
			assertFalse(AraxxorMinion.isEgg(minion.getMinionNpcId()));
		}
	}

	@Test
	public void eggIdsAreTheOnesTheGameUses()
	{
		assertEquals(NpcID.ARAXXOR_MINION_EGG_VENOM, AraxxorMinion.ACIDIC.getEggNpcId());
		assertEquals(NpcID.ARAXXOR_MINION_EGG_MIRRORBACK, AraxxorMinion.MIRRORBACK.getEggNpcId());
		assertEquals(NpcID.ARAXXOR_MINION_EGG_EXPLODE, AraxxorMinion.RUPTURA.getEggNpcId());
	}

	@Test
	public void everyMinionMapsToADistinctSpecial()
	{
		assertEquals(AraxxorSpecial.ACID_BALL, AraxxorMinion.ACIDIC.getSpecial());
		assertEquals(AraxxorSpecial.ACID_SPLATTER, AraxxorMinion.MIRRORBACK.getSpecial());
		assertEquals(AraxxorSpecial.ACID_DRIP, AraxxorMinion.RUPTURA.getSpecial());
	}

	@Test
	public void everyCueHasAClip()
	{
		for (AraxxorMinion minion : AraxxorMinion.values())
		{
			assertNotNull(minion + " has no clip", minion.getSoundFile());
			assertNotNull("missing resource " + minion.getSoundFile(),
				AraxxorHelperPlugin.class.getResource(minion.getSoundFile()));
		}
		for (AraxxorSpecial special : AraxxorSpecial.values())
		{
			if (special == AraxxorSpecial.UNKNOWN)
			{
				continue;
			}
			assertNotNull("missing resource " + special.getSoundFile(),
				AraxxorHelperPlugin.class.getResource(special.getSoundFile()));
		}
		for (String clip : new String[]{"egg-soon.wav", "enrage-soon.wav", "enrage.wav", "dodge.wav"})
		{
			assertNotNull("missing resource " + clip, AraxxorHelperPlugin.class.getResource(clip));
		}
	}

	@Test
	public void onlyCombatSkillsFeedTheDamageEstimate()
	{
		assertTrue(AraxxorHelperPlugin.isCombatSkill(Skill.RANGED));
		assertTrue(AraxxorHelperPlugin.isCombatSkill(Skill.STRENGTH));
		assertTrue(AraxxorHelperPlugin.isCombatSkill(Skill.MAGIC));
		assertFalse(AraxxorHelperPlugin.isCombatSkill(Skill.SLAYER));
		assertFalse(AraxxorHelperPlugin.isCombatSkill(Skill.PRAYER));
	}
}
