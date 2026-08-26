package com.araxxor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * When each egg hatches, and which araxyte comes out.
 *
 * Two independent facts from the wiki drive this:
 * <ul>
 *   <li>The first egg hatches after 3 standard attacks, and every 6 after that.</li>
 *   <li>Eggs hatch clockwise starting from the south-easternmost one.</li>
 * </ul>
 *
 * The wiki lists three possible rotations — Green>White>Red, White>Red>Green and
 * Red>Green>White — but those are the same cycle read from three different
 * starting points, so there is really only one rotation and the only unknown is
 * where it starts. Rather than rely on that (or on identifying egg colours),
 * the order is read straight off the eggs' actual positions and ids at spawn,
 * which needs no assumption about colour at all.
 *
 * Deliberately free of any client type so the ordering can be tested against
 * hand-built coordinates.
 */
final class AraxxorEggCycle
{
	/** Standard attacks before the first egg hatches. */
	static final int FIRST_HATCH_ATTACK = 3;

	/** Standard attacks between hatches after the first. */
	static final int HATCH_PERIOD = 6;

	/** Three of each type. */
	static final int TOTAL_EGGS = 9;

	/** An egg's position and type, independent of any client class. */
	static final class Egg
	{
		final int x;
		final int y;
		final AraxxorMinion type;

		Egg(int x, int y, AraxxorMinion type)
		{
			this.x = x;
			this.y = y;
			this.type = type;
		}
	}

	private AraxxorEggCycle()
	{
	}

	/** Standard-attack number at which the {@code index}-th egg (0-based) hatches. */
	static int attackForHatch(int index)
	{
		return FIRST_HATCH_ATTACK + HATCH_PERIOD * index;
	}

	/** How many eggs have hatched after {@code standardAttacks} standard attacks. */
	static int hatchesSoFar(int standardAttacks)
	{
		if (standardAttacks < FIRST_HATCH_ATTACK)
		{
			return 0;
		}
		return 1 + (standardAttacks - FIRST_HATCH_ATTACK) / HATCH_PERIOD;
	}

	/** Standard attacks remaining before the next egg hatches. */
	static int attacksUntilNextHatch(int standardAttacks)
	{
		return attackForHatch(hatchesSoFar(standardAttacks)) - standardAttacks;
	}

	/**
	 * Orders eggs into their hatch sequence: clockwise, starting from the
	 * south-easternmost.
	 *
	 * South-east is the largest {@code x - y} (east is +x, north is +y), and
	 * clockwise from above is increasing compass bearing, which is why the angle
	 * is {@code atan2(dx, dy)} rather than the usual {@code atan2(dy, dx)}.
	 */
	static List<AraxxorMinion> hatchOrder(List<Egg> eggs)
	{
		List<AraxxorMinion> order = new ArrayList<>();
		if (eggs == null || eggs.isEmpty())
		{
			return order;
		}

		double cx = eggs.stream().mapToInt(e -> e.x).average().orElse(0);
		double cy = eggs.stream().mapToInt(e -> e.y).average().orElse(0);

		Egg southEast = eggs.stream()
			.max(Comparator.comparingInt(e -> e.x - e.y))
			.orElse(eggs.get(0));
		double start = bearing(southEast, cx, cy);

		List<Egg> sorted = new ArrayList<>(eggs);
		sorted.sort(Comparator.comparingDouble(e ->
		{
			double delta = bearing(e, cx, cy) - start;
			// Wrap into [0, 2pi) so the south-eastern egg is always first and the
			// rest follow it around the ring rather than restarting at due north.
			return delta < 0 ? delta + 2 * Math.PI : delta;
		}));

		for (Egg e : sorted)
		{
			order.add(e.type);
		}
		return order;
	}

	/**
	 * The type hatching at {@code index}, wrapping once the ring is exhausted.
	 * The last two sets of three repeat the first, so wrapping is correct even
	 * when fewer than nine eggs were seen.
	 */
	static AraxxorMinion typeAt(List<AraxxorMinion> hatchOrder, int index)
	{
		if (hatchOrder == null || hatchOrder.isEmpty() || index < 0)
		{
			return null;
		}
		return hatchOrder.get(index % hatchOrder.size());
	}

	private static double bearing(Egg e, double cx, double cy)
	{
		return Math.atan2(e.x - cx, e.y - cy);
	}
}
