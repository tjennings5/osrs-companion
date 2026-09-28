package com.doom;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Plain-text event log for one Doom session, one line per event:
 *
 * <pre>t=1234 ANIM npc=BOSS id=12407 name=ROCK_THROW at=1311,9540,0</pre>
 *
 * Lines are prefixed with the game tick so timings can be read straight off
 * the file. Flushed once per tick so a crash or force-close loses at most the
 * current tick.
 */
@Slf4j
class DoomRecorder
{
	private static final Path DIR = RuneLite.RUNELITE_DIR.toPath().resolve("doom-helper").resolve("recordings");
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private BufferedWriter writer;
	private boolean dirty;

	@Getter
	private Path file;

	@Getter
	private int lineCount;

	boolean isOpen()
	{
		return writer != null;
	}

	void open(String header)
	{
		if (writer != null)
		{
			return;
		}
		try
		{
			Files.createDirectories(DIR);
			file = DIR.resolve("doom-" + LocalDateTime.now().format(FILE_STAMP) + ".log");
			writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
			lineCount = 0;
			writeRaw("# " + header);
			log.debug("Doom recording started: {}", file);
		}
		catch (IOException e)
		{
			log.warn("Could not start Doom recording", e);
			writer = null;
		}
	}

	void write(int tick, String line)
	{
		if (writer != null)
		{
			writeRaw("t=" + tick + " " + line);
		}
	}

	private void writeRaw(String line)
	{
		try
		{
			writer.write(line);
			writer.newLine();
			lineCount++;
			dirty = true;
		}
		catch (IOException e)
		{
			log.warn("Doom recording write failed - stopping", e);
			close();
		}
	}

	void flush()
	{
		if (writer == null || !dirty)
		{
			return;
		}
		try
		{
			writer.flush();
			dirty = false;
		}
		catch (IOException e)
		{
			log.warn("Doom recording flush failed - stopping", e);
			close();
		}
	}

	void close()
	{
		if (writer == null)
		{
			return;
		}
		try
		{
			writer.close();
			log.debug("Doom recording saved: {} ({} lines)", file, lineCount);
		}
		catch (IOException e)
		{
			log.warn("Could not close Doom recording", e);
		}
		writer = null;
	}
}
