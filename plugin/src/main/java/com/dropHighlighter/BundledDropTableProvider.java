package com.dropHighlighter;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Drop tables for every monster, read from a JSON file baked into the jar at build time by
 * {@code tools/generate-drop-tables.py}.
 *
 * <p>Bundled rather than fetched. A plugin that talks to a third-party server at runtime has to
 * ship that feature disabled by default and carry an IP-disclosure warning; baking the data in
 * sidesteps that, works offline, and makes lookups instant.
 *
 * <p>Loaded once, lazily, on first lookup — a few megabytes of JSON parsed on whichever thread
 * asks first. That is a one-off cost on the first right-click, not per lookup, and no network or
 * disk IO is involved beyond reading from the already-open jar.
 */
@Slf4j
@Singleton
class BundledDropTableProvider implements DropTableProvider
{
	private static final String RESOURCE = "/com/dropHighlighter/drop-tables.json";

	private static final Type TABLE_TYPE =
		new TypeToken<LinkedHashMap<String, List<DropTableEntry>>>()
		{
		}.getType();

	private final Gson gson;

	/** Keyed by lower-cased monster name: NPC names from the client vary in casing. */
	private volatile Map<String, List<DropTableEntry>> tables;

	@Inject
	BundledDropTableProvider(Gson gson)
	{
		this.gson = gson;
	}

	@Override
	public List<DropTableEntry> getDrops(String monsterName)
	{
		if (monsterName == null)
		{
			return Collections.emptyList();
		}
		return load().getOrDefault(key(monsterName), Collections.emptyList());
	}

	@Override
	public boolean hasDrops(String monsterName)
	{
		return monsterName != null && load().containsKey(key(monsterName));
	}

	private static String key(String monsterName)
	{
		return monsterName.trim().toLowerCase(Locale.ROOT);
	}

	private Map<String, List<DropTableEntry>> load()
	{
		Map<String, List<DropTableEntry>> loaded = tables;
		if (loaded != null)
		{
			return loaded;
		}

		synchronized (this)
		{
			if (tables == null)
			{
				tables = parse();
			}
			return tables;
		}
	}

	private Map<String, List<DropTableEntry>> parse()
	{
		try (InputStream in = BundledDropTableProvider.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Drop table resource {} is missing from the jar", RESOURCE);
				return Collections.emptyMap();
			}

			Map<String, List<DropTableEntry>> raw = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), TABLE_TYPE);
			if (raw == null)
			{
				return Collections.emptyMap();
			}

			Map<String, List<DropTableEntry>> byLowerName = new LinkedHashMap<>(raw.size());
			raw.forEach((name, drops) -> byLowerName.put(key(name),
				drops == null ? Collections.emptyList() : Collections.unmodifiableList(drops)));

			log.debug("Loaded stub drop tables for {} monster(s)", byLowerName.size());
			return Collections.unmodifiableMap(byLowerName);
		}
		catch (IOException | JsonSyntaxException e)
		{
			// An unreadable stub resource should cost the panel its contents, not stop the
			// plugin from loading and rendering beams for whatever is already selected.
			log.warn("Could not read {}, drop tables will be empty", RESOURCE, e);
			return Collections.emptyMap();
		}
	}
}
