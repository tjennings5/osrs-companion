package com.combat;

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
 * Plain-text event log for one boss session, one line per event:
 *
 * <pre>t=1234 ANIM npc=BOSS id=12407 name=ROCK_THROW at=1311,9540,0</pre>
 *
 * Written to {@code .runelite/<folder>/recordings/<prefix>-<timestamp>.log}.
 *
 * Lines are prefixed with the game tick so timings can be read straight off
 * the file. Flushed once per tick so a crash or force-close loses at most the
 * current tick.
 */
@Slf4j
public class FightRecorder
{
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Path dir;
	private final String prefix;

	private BufferedWriter writer;
	private boolean dirty;

	@Getter
	private Path file;

	@Getter
	private int lineCount;

	/** E.g. {@code new FightRecorder("doom-helper", "doom")}. */
	public FightRecorder(String folder, String prefix)
	{
		this.dir = RuneLite.RUNELITE_DIR.toPath().resolve(folder).resolve("recordings");
		this.prefix = prefix;
	}

	public boolean isOpen()
	{
		return writer != null;
	}

	public void open(String header)
	{
		if (writer != null)
		{
			return;
		}
		try
		{
			Files.createDirectories(dir);
			file = dir.resolve(prefix + "-" + LocalDateTime.now().format(FILE_STAMP) + ".log");
			writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
			lineCount = 0;
			writeRaw("# " + header);
			log.debug("Recording started: {}", file);
		}
		catch (IOException e)
		{
			log.warn("Could not start {} recording", prefix, e);
			writer = null;
		}
	}

	public void write(int tick, String line)
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
			log.warn("{} recording write failed - stopping", prefix, e);
			close();
		}
	}

	public void flush()
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
			log.warn("{} recording flush failed - stopping", prefix, e);
			close();
		}
	}

	public void close()
	{
		if (writer == null)
		{
			return;
		}
		try
		{
			writer.close();
			log.debug("Recording saved: {} ({} lines)", file, lineCount);
		}
		catch (IOException e)
		{
			log.warn("Could not close {} recording", prefix, e);
		}
		writer = null;
	}
}
