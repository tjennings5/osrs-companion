#!/usr/bin/env python3
"""
Regenerates the bundled drop tables for the Drop Highlighter plugin.

This is a build-time tool, deliberately NOT wired into Gradle: it hits the network, and a
normal `./gradlew build` must stay offline and reproducible. Run it by hand when you want
fresher wiki data, then commit the regenerated JSON.

Why bundle instead of fetching at runtime: a plugin that calls a third-party server at runtime
has to ship the feature disabled by default and carry an IP-disclosure warning (see AGENTS.md).
Bundling avoids that entirely, works offline, and makes lookups instant.

Data source is the OSRS Wiki's Bucket API (action=bucket), which serves the same structured
rows that populate {{DropsLine}} templates - no scraping, no wikitext parsing.

Usage:
    python3 tools/generate-drop-tables.py                    # all monsters
    python3 tools/generate-drop-tables.py --monster "Cave kraken" --monster Goblin
    python3 tools/generate-drop-tables.py --out /tmp/x.json --indent 2
"""

import argparse
import collections
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import OrderedDict, defaultdict
from pathlib import Path

API = "https://oldschool.runescape.wiki/api.php"

# The wiki asks unattended clients to identify themselves and give a contact address.
USER_AGENT = (
    "drop-highlighter-tables/1.0 (personal RuneLite plugin; "
    "contact jennings.tyler@protonmail.com)"
)

PAGE_SIZE = 5000           # the API's ceiling per request
REQUEST_DELAY = 0.5        # be polite; the whole run is only a handful of requests
MAX_RETRIES = 4

DEFAULT_OUT = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/com/dropHighlighter/drop-tables.json"
)

# Rows whose rarity is one of these are placeholders, not obtainable items.
SKIP_ITEM_NAMES = {"nothing"}

# Shared loot pools are pulled out of every monster's table and emitted as their own entries, so
# the panel can offer them from a dropdown without burying what a given monster actually drops.
POOL_RARE = "Rare drop table"
POOL_HERB = "Herb drop table"
POOL_SEED = "Seed drop table"

# How many of a pool's items a monster needs before we call it "has the shared table" rather than
# "happens to drop one of these directly". Hespori dropping a single seed should keep it; a Cave
# kraken rolling the whole seed table should not list twenty of them.
POOL_THRESHOLD = 6

# How many distinct monsters must drop an item before it counts as part of a shared pool at all.
#
# Name shape alone is not enough, and getting this wrong loses real drops: "Crystal weapon seed"
# ends in " seed" but is a Gauntlet unique from exactly one source, and matching on the suffix
# swept it into the seed pool and deleted it from the monster that drops it. The real pools are
# unmistakable by breadth - every genuine herb or seed table item appears on 100+ monsters, while
# every false positive appears on 5 or fewer. Anything in that gap is a judgement call this
# threshold makes conservatively, in favour of leaving the drop on its monster.
POOL_MIN_MONSTERS = 15


def looks_like_herb(item_name: str) -> bool:
    return item_name.startswith("Grimy ")


def looks_like_seed(item_name: str) -> bool:
    return item_name.endswith(" seed") or item_name.endswith(" seeds")


def derive_pool_members(rows: list) -> "tuple[set, set]":
    """
    Works out which herb and seed names are genuinely shared pools, by counting how many distinct
    monsters drop each one. See POOL_MIN_MONSTERS for why breadth rather than name shape.
    """
    monsters_per_item = defaultdict(set)
    for row in rows:
        name = (row.get("item_name") or "").strip()
        page = (row.get("page_name") or "").strip()
        if name and page:
            monsters_per_item[name].add(page)

    def members(predicate):
        return {
            name for name, monsters in monsters_per_item.items()
            if predicate(name) and len(monsters) >= POOL_MIN_MONSTERS
        }

    herbs = members(looks_like_herb)
    seeds = members(looks_like_seed)
    print(f"shared pools: {len(herbs)} herbs, {len(seeds)} seeds "
          f"(from {sum(1 for n in monsters_per_item if looks_like_herb(n))} herb-like and "
          f"{sum(1 for n in monsters_per_item if looks_like_seed(n))} seed-like names)",
          file=sys.stderr)
    return herbs, seeds


def bucket_query(query: str) -> list:
    """Runs one Bucket query, retrying on transient failures."""
    url = API + "?" + urllib.parse.urlencode(
        {"action": "bucket", "format": "json", "query": query}
    )
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})

    last_error = None
    for attempt in range(MAX_RETRIES):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                payload = json.load(response)
            if "error" in payload:
                # A query error is our bug, not a blip - failing fast beats retrying it.
                raise SystemExit(f"Bucket API rejected the query: {payload['error']}\n  {query}")
            return payload.get("bucket", [])
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            last_error = exc
            backoff = 2 ** attempt
            print(f"  request failed ({exc}), retrying in {backoff}s", file=sys.stderr)
            time.sleep(backoff)

    raise SystemExit(f"Gave up after {MAX_RETRIES} attempts: {last_error}")


def paginate(select_and_from: str, where: str = "") -> list:
    """Walks a bucket in PAGE_SIZE chunks until a short page says we're done."""
    rows, offset = [], 0
    while True:
        page = bucket_query(f"{select_and_from}{where}.limit({PAGE_SIZE}).offset({offset}).run()")
        rows += page
        print(f"  ...{len(rows)} rows", file=sys.stderr)
        if len(page) < PAGE_SIZE:
            return rows
        offset += PAGE_SIZE
        time.sleep(REQUEST_DELAY)


def fetch_item_ids() -> dict:
    """
    Builds a name -> item id map from infobox_item.

    Done as a separate bulk fetch and joined locally rather than with Bucket's .join(), because
    several wiki pages can share one item_name - "Loop half of key" exists three times, for the
    base item and two quest variants. A server-side join emits one drop row per match, which
    silently triples those drops. Joining here lets us pick deliberately.

    The canonical row is the one whose page title *is* the item name; variants live on
    parenthesised pages like "Loop half of key (moon key)". Lowest id breaks any remaining tie,
    which favours the original over later re-releases.
    """
    print("fetching item ids...", file=sys.stderr)
    rows = paginate("bucket('infobox_item').select('item_name','item_id','page_name')")

    best = {}
    for row in rows:
        name = (row.get("item_name") or "").strip()
        item_id = first_item_id(row.get("item_id"))
        if not name or item_id is None:
            continue

        page = (row.get("page_name") or "").strip()
        # Sorts ahead of variants: canonical page first, then lowest id.
        rank = (0 if page == name else 1, item_id)
        if name not in best or rank < best[name][0]:
            best[name] = (rank, item_id)

    return {name: item_id for name, (_, item_id) in best.items()}


def is_shared_table_row(row: dict) -> bool:
    """
    True for rows that come from a shared loot pool - the rare drop table, gem table, and the
    herb/seed tables - rather than the monster's own drop table.

    The marker is the *presence* of the rare_drop_table key, not its value. Bucket omits boolean
    fields entirely when unset, so a monster's own drops have no such key at all while shared
    rows carry it (as an empty string, which is why testing truthiness finds nothing).
    """
    return "rare_drop_table" in row


def fetch_drop_rows(monsters=None) -> list:
    """Pulls raw dropsline rows. Item ids get attached later from fetch_item_ids()."""
    select = "bucket('dropsline').select('page_name','item_name','drop_json','rare_drop_table')"

    if monsters:
        rows = []
        for monster in monsters:
            escaped = monster.replace("'", "\\'")
            print(f"fetching {monster}...", file=sys.stderr)
            rows += paginate(select, f".where('page_name','{escaped}')")
            time.sleep(REQUEST_DELAY)
        return rows

    print("fetching all drop rows...", file=sys.stderr)
    return paginate(select)


def first_item_id(raw):
    """infobox_item.item_id comes back as a list because some pages document variants."""
    if isinstance(raw, list):
        raw = raw[0] if raw else None
    if raw in (None, ""):
        return None
    try:
        value = int(str(raw).strip())
    except ValueError:
        return None
    return value if value > 0 else None


def format_rarity(drop: dict) -> str:
    """Collapses the drop_json rarity/quantity fields into one short display string."""
    rarity = (drop.get("Rarity") or "").strip() or "Unknown"

    low, high = drop.get("Quantity Low"), drop.get("Quantity High")
    quantity = ""
    if isinstance(low, int) and isinstance(high, int) and (low > 1 or high > 1):
        quantity = str(low) if low == high else f"{low}-{high}"

    notes = []
    if quantity:
        notes.append(quantity)
    if str(drop.get("Name Notes") or "").strip().lower() == "noted":
        notes.append("noted")

    return f"{rarity} ({', '.join(notes)})" if notes else rarity


def strip_shared_pools(by_monster: dict, herb_pool: set, seed_pool: set) -> int:
    """
    Removes shared loot pools from each monster's table, in place.

    The rare drop table is flagged in the source data so it always goes. Herbs and seeds are not
    flagged - they are structurally identical to a monster's own drops - so two conditions have
    to hold: the item must be a known pool member (see derive_pool_members), and the monster must
    carry enough of that pool to be rolling the table rather than dropping one item directly.
    """
    removed = 0
    for monster, entries in by_monster.items():
        herbs = sum(1 for e in entries if e["itemName"] in herb_pool)
        seeds = sum(1 for e in entries if e["itemName"] in seed_pool)

        drop_herbs = herbs >= POOL_THRESHOLD
        drop_seeds = seeds >= POOL_THRESHOLD

        kept = []
        for entry in entries:
            name = entry["itemName"]
            if entry["_rare"] \
                    or (drop_herbs and name in herb_pool) \
                    or (drop_seeds and name in seed_pool):
                removed += 1
                continue
            kept.append(entry)
        by_monster[monster] = kept
    return removed


def build_tables(rows: list, item_ids: dict, include_shared: bool = False) -> "OrderedDict[str, list]":
    """
    Groups rows by monster and drops anything unusable.

    Rarest-first ordering is load-bearing, not cosmetic: the plugin colours a shared tile's beam
    using the first listed item, so the drop actually worth stopping for should lead.

    Note there is no sub-table expansion to do. The wiki already flattens the gem, herb, seed and
    rare drop tables into each monster's rows with the effective end-to-end rarity computed - a
    Cave kraken's Dragon spear arrives as 1/182,044.44, not as a pointer at another page.
    """
    herb_pool, seed_pool = derive_pool_members(rows)

    by_monster = defaultdict(list)
    pools = {POOL_RARE: {}, POOL_HERB: {}, POOL_SEED: {}}
    skipped_no_id = collections.Counter()

    for row in rows:
        monster = (row.get("page_name") or "").strip()
        name = (row.get("item_name") or "").strip()
        if not monster or not name or name.lower() in SKIP_ITEM_NAMES:
            continue

        item_id = item_ids.get(name)
        if item_id is None:
            # Mostly clue scrolls, which carry one id per step and so could never be matched
            # against a single ground item anyway.
            skipped_no_id[name] += 1
            continue

        try:
            drop = json.loads(row.get("drop_json") or "{}")
        except json.JSONDecodeError:
            drop = {}

        entry = {
            "itemId": item_id,
            "itemName": name,
            "rarity": format_rarity(drop),
            "_sort": rarity_sort_key(drop),
            "_rare": is_shared_table_row(row),
        }

        # Every rare-drop-table row goes to the shared pool regardless of which monster it came
        # from; the pool is the same items either way.
        if entry["_rare"]:
            pools[POOL_RARE].setdefault(item_id, entry)
        if name in herb_pool:
            pools[POOL_HERB].setdefault(item_id, entry)
        if name in seed_pool:
            pools[POOL_SEED].setdefault(item_id, entry)

        by_monster[monster].append(entry)

    skipped_shared = 0
    if not include_shared:
        skipped_shared = strip_shared_pools(by_monster, herb_pool, seed_pool)

    tables = OrderedDict()

    # Pools first so they sit at the top of the panel's dropdown. Sorted by name rather than
    # rarity: you come to these looking for one specific herb or seed, not for the rarest.
    if not include_shared:
        for pool_name in (POOL_RARE, POOL_HERB, POOL_SEED):
            entries = sorted(pools[pool_name].values(), key=lambda e: e["itemName"])
            if entries:
                tables[pool_name] = [
                    {"itemId": e["itemId"], "itemName": e["itemName"], "rarity": "Shared pool"}
                    for e in entries
                ]

    for monster in sorted(by_monster):
        seen, entries = set(), []
        for entry in sorted(by_monster[monster], key=lambda e: (e["_sort"], e["itemName"])):
            if entry["itemId"] in seen:
                continue
            seen.add(entry["itemId"])
            entries.append({k: v for k, v in entry.items() if not k.startswith("_")})
        if entries:
            tables[monster] = entries

    total_skipped = sum(skipped_no_id.values())
    print(
        f"\n{len(tables)} monsters, {sum(len(v) for v in tables.values())} rows "
        f"({total_skipped} skipped for no resolvable item id, "
        f"{skipped_shared} shared loot pool rows excluded)",
        file=sys.stderr,
    )
    if skipped_no_id:
        top = ", ".join(f"{n} x{c}" for n, c in skipped_no_id.most_common(8))
        print(f"  most-skipped names: {top}", file=sys.stderr)
    return tables


def rarity_sort_key(drop: dict) -> float:
    """
    Sorts rarest first. "1/1200" -> 1/1200, "4/200" -> 0.02, "Always" -> 1.0.
    Unparseable rarities sort to the end.
    """
    raw = str(drop.get("Rarity") or "").strip().lower()
    if raw in ("always", "1/1", "100%"):
        return 1.0
    try:
        if "/" in raw:
            numerator, denominator = raw.split("/", 1)
            return float(numerator) / float(denominator.replace(",", ""))
        if raw.endswith("%"):
            return float(raw[:-1]) / 100.0
    except (ValueError, ZeroDivisionError):
        pass
    return 2.0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--monster", action="append", metavar="NAME",
                        help="Limit to this monster (repeatable). Default: every monster.")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT,
                        help=f"Output path (default: {DEFAULT_OUT})")
    parser.add_argument("--indent", type=int, default=1,
                        help="JSON indent. 1 keeps the file small; use 2 to read it.")
    parser.add_argument("--include-shared-tables", action="store_true",
                        help="Keep rare drop table / gem / herb / seed rows. Off by default: "
                             "they are the same pool on hundreds of monsters and bury the "
                             "drops that are actually specific to the one you are killing.")
    args = parser.parse_args()

    item_ids = fetch_item_ids()
    print(f"resolved {len(item_ids)} item names to ids\n", file=sys.stderr)

    rows = fetch_drop_rows(args.monster)
    if not rows:
        print("No rows returned - refusing to overwrite the existing tables.", file=sys.stderr)
        return 1

    tables = build_tables(rows, item_ids, args.include_shared_tables)
    if not tables:
        print("Nothing usable parsed - refusing to overwrite the existing tables.", file=sys.stderr)
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8") as handle:
        json.dump(tables, handle, indent=args.indent, ensure_ascii=False)
        handle.write("\n")

    size_kb = args.out.stat().st_size / 1024
    print(f"wrote {args.out} ({size_kb:.0f} KB)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
