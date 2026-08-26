package com.osrsmcp.araxxor;

import lombok.Getter;
import net.runelite.api.gameval.NpcID;

/**
 * The three araxytes, keyed by the NPC ids of both the egg and the thing that
 * hatches out of it.
 *
 * Reading the egg's id is what makes the fight predictable: the id alone gives
 * the minion type <i>and</i> the special attack for the whole fight, with no
 * need to identify egg colours on screen.
 */
@Getter
enum AraxxorMinion
{
	ACIDIC(
		NpcID.ARAXXOR_MINION_EGG_VENOM,
		NpcID.ARAXXOR_MINION_VENOM,
		AraxxorSpecial.ACID_BALL,
		"Acidic",
		"Ranged, up to 15. Explodes on death - 7x7 acid splash.",
		"minion-acidic.wav"),

	MIRRORBACK(
		NpcID.ARAXXOR_MINION_EGG_MIRRORBACK,
		NpcID.ARAXXOR_MINION_MIRRORBACK,
		AraxxorSpecial.ACID_SPLATTER,
		"Mirrorback",
		"Do NOT melee it point-blank - 50% recoil. Ranged, magic, or halberd from 1 tile.",
		"minion-mirrorback.wav"),

	RUPTURA(
		NpcID.ARAXXOR_MINION_EGG_EXPLODE,
		NpcID.ARAXXOR_MINION_EXPLODE,
		AraxxorSpecial.ACID_DRIP,
		"Ruptura",
		"Walks at you and explodes: 70 under it, 49 next to it, 28 at 2 tiles. Back off.",
		"minion-ruptura.wav");

	private final int eggNpcId;
	private final int minionNpcId;
	private final AraxxorSpecial special;
	private final String displayName;
	private final String advice;
	private final String soundFile;

	AraxxorMinion(int eggNpcId, int minionNpcId, AraxxorSpecial special,
		String displayName, String advice, String soundFile)
	{
		this.eggNpcId = eggNpcId;
		this.minionNpcId = minionNpcId;
		this.special = special;
		this.displayName = displayName;
		this.advice = advice;
		this.soundFile = soundFile;
	}

	static AraxxorMinion byEggId(int npcId)
	{
		for (AraxxorMinion m : values())
		{
			if (m.eggNpcId == npcId)
			{
				return m;
			}
		}
		return null;
	}

	static AraxxorMinion byMinionId(int npcId)
	{
		for (AraxxorMinion m : values())
		{
			if (m.minionNpcId == npcId)
			{
				return m;
			}
		}
		return null;
	}

	static boolean isEgg(int npcId)
	{
		return byEggId(npcId) != null;
	}
}
