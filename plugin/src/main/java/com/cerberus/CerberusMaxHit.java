package com.osrsmcp.cerberus;

/**
 * Standard OSRS max hit arithmetic, used to estimate how much one more attack
 * could take off Cerberus before any damage has been observed.
 *
 * Deliberately only the standard formula plus prayer, attack style, and a
 * black mask / slayer helmet. It does not model weapon-specific effects —
 * demonbane multipliers, set bonuses, Dharok's scaling and the like — because
 * there are dozens of them and each is a chance to be quietly wrong.
 *
 * That omission is safe by construction, because of how the result is used:
 * the hold warning takes the largest of this estimate, the biggest hit actually
 * landed this fight, and the player's manual override. An underestimate here is
 * therefore corrected by real damage within a hit or two, and can never make
 * the warning fire later than it would have without this class. Overestimating
 * only warns early, which costs seconds; underestimating costs the attempt.
 */
final class CerberusMaxHit
{
	private CerberusMaxHit()
	{
	}

	/**
	 * @param boostedLevel  ranged or strength level including any boost
	 * @param strengthBonus summed ranged strength or melee strength from equipment
	 * @param prayerMultiplier e.g. 1.23 for Rigour or Piety
	 * @param styleBonus    attack style contribution (0-3)
	 * @param gearMultiplier multiplicative gear bonus, e.g. 1.15 for a slayer helmet on task
	 */
	static int maxHit(int boostedLevel, int strengthBonus, double prayerMultiplier, int styleBonus,
		double gearMultiplier)
	{
		if (boostedLevel <= 0)
		{
			return 0;
		}
		int effective = (int) Math.floor(boostedLevel * prayerMultiplier) + styleBonus + 8;
		int base = (int) Math.floor(0.5 + effective * (strengthBonus + 64) / 640.0);
		return (int) Math.floor(base * gearMultiplier);
	}

	/**
	 * The larger of the melee and ranged results.
	 *
	 * Computing both sidesteps having to classify the equipped weapon, and the
	 * larger of the two is the conservative choice for a "could this hit cross
	 * 400" question.
	 */
	static int bestOf(int meleeMax, int rangedMax)
	{
		return Math.max(meleeMax, rangedMax);
	}
}
