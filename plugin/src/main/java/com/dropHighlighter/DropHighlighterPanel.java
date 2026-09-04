package com.dropHighlighter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The side panel: pick a monster, see its drop table, tick the items worth lighting up.
 *
 * <p>The panel never holds its own copy of which items are highlighted. It reads that from
 * {@link HighlightManager} on every rebuild and writes straight back to it, so the ground overlay
 * and the checkboxes cannot drift apart.
 */
class DropHighlighterPanel extends PluginPanel implements DropRowPanel.Listener
{
	/**
	 * Colours picked to stay distinguishable against grass, sand, and cave floor, and from each
	 * other for the red/green colour blind. "Custom" is always available underneath.
	 */
	/** What a newly ticked item gets until you pick something else. */
	static final Color DEFAULT_COLOR = new Color(255, 200, 40);

	private static final Color[] PRESETS = {
		DEFAULT_COLOR,
		new Color(255, 64, 64),
		new Color(255, 144, 0),
		new Color(120, 255, 90),
		new Color(0, 224, 224),
		new Color(90, 150, 255),
		new Color(200, 110, 255),
		new Color(255, 255, 255),
	};

	private final HighlightManager highlights;
	private final DropTableProvider dropTables;
	private final ItemManager itemManager;
	private final ClientThread clientThread;

	/**
	 * Shared loot pools, offered from the dropdown so herbs and seeds are reachable without
	 * right-clicking something that happens to drop them. Must match the keys the generator
	 * writes into drop-tables.json.
	 */
	private static final String[] SHARED_POOLS = {
		"Rare drop table",
		"Herb drop table",
		"Seed drop table",
	};

	/**
	 * A view of everything currently lit, gathered from every table at once.
	 *
	 * <p>Needed because the ground shows the union of all tables: without it, an item ticked under
	 * a monster you have since moved on from keeps beaming with no way to find it, and unticking
	 * it where you happen to be looking does nothing. Unticking here clears it everywhere.
	 */
	private static final String ACTIVE_VIEW = "Currently highlighted";

	private final JButton clearAllButton = new JButton("Clear all");
	private final JLabel emptyMessage = new JLabel();
	private final JPanel rows = new JPanel();
	private final List<DropRowPanel> rowPanels = new ArrayList<>();
	private final JComboBox<String> tableChooser = new JComboBox<>();

	/** Which table is on screen: a monster, a shared pool, or {@link #ACTIVE_VIEW}. */
	private String currentTable;

	/**
	 * The last real monster loaded, kept so it stays in the dropdown after switching away. Without
	 * it, viewing the highlights list would strand you with no way back to the monster short of
	 * ctrl + right-clicking it again.
	 */
	private String lastMonster;

	/** Suppresses the chooser's action listener while its model is being rebuilt. */
	private boolean updatingChooser;

	@Inject
	DropHighlighterPanel(HighlightManager highlights, DropTableProvider dropTables,
		ItemManager itemManager, ClientThread clientThread)
	{
		super(false);
		this.highlights = highlights;
		this.dropTables = dropTables;
		this.itemManager = itemManager;
		this.clientThread = clientThread;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		tableChooser.setFont(FontManager.getRunescapeSmallFont());
		tableChooser.setFocusable(false);
		tableChooser.setToolTipText("Switch between this monster and the shared loot pools");
		tableChooser.addActionListener(e ->
		{
			if (updatingChooser)
			{
				return;
			}
			Object chosen = tableChooser.getSelectedItem();
			if (chosen != null && !chosen.equals(currentTable))
			{
				build((String) chosen);
			}
		});

		clearAllButton.setFont(FontManager.getRunescapeSmallFont());
		clearAllButton.setFocusable(false);
		clearAllButton.setToolTipText("Untick everything highlighted across every monster");
		clearAllButton.setVisible(false);
		clearAllButton.addActionListener(e ->
		{
			highlights.clearAll();
			buildActiveView();
		});

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(getBackground());
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		header.add(tableChooser, BorderLayout.CENTER);
		header.add(clearAllButton, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(getBackground());

		emptyMessage.setFont(FontManager.getRunescapeSmallFont());
		emptyMessage.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyMessage.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel container = new JPanel(new BorderLayout());
		container.setBackground(getBackground());
		container.add(rows, BorderLayout.NORTH);
		container.add(emptyMessage, BorderLayout.CENTER);

		// PluginPanel(false) skips the built-in wrapper so the header can stay pinned while only
		// the rows scroll.
		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(container,
			javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(getBackground());
		add(scroll, BorderLayout.CENTER);

		// Open on the highlights list rather than a static message. It is the one view that is
		// useful before you have picked a monster, and it means the "why is this still lit"
		// answer is the first thing the panel shows.
		build(ACTIVE_VIEW);
	}

	/**
	 * Loads a monster's drop table. Safe to call from the client thread — the Swing work is
	 * always hopped onto the EDT.
	 */
	void displayMonster(String monsterName)
	{
		SwingUtilities.invokeLater(() -> build(monsterName));
	}

	/** Re-syncs checkbox and swatch state after the highlight set changes elsewhere. */
	void refreshSelections()
	{
		SwingUtilities.invokeLater(() ->
		{
			Map<Integer, Color> forTable = inActiveView()
				? highlights.getAllSelected()
				: highlights.getSelected(currentTable);
			for (DropRowPanel row : rowPanels)
			{
				row.setAssignedColor(forTable.get(row.getItemId()));
			}
		});
	}

	private void build(String monsterName)
	{
		if (ACTIVE_VIEW.equals(monsterName))
		{
			buildActiveView();
			return;
		}

		currentTable = monsterName;
		if (monsterName != null && !isSharedPool(monsterName))
		{
			lastMonster = monsterName;
		}
		clearAllButton.setVisible(false);
		rows.removeAll();
		rowPanels.clear();
		syncChooser();

		List<DropTableEntry> drops = dropTables.getDrops(monsterName);
		if (drops.isEmpty())
		{
			emptyMessage.setText("<html><div style='text-align:center;'>No drop table for this "
				+ "monster yet.</div></html>");
			revalidate();
			repaint();
			return;
		}

		emptyMessage.setText("");

		Map<Integer, Color> forTable = highlights.getSelected(monsterName);
		for (DropTableEntry drop : drops)
		{
			DropRowPanel row = new DropRowPanel(drop, forTable.get(drop.getItemId()), this);
			rows.add(row);
			rows.add(javax.swing.Box.createVerticalStrut(2));
			rowPanels.add(row);
			loadIcon(drop, row);
		}

		revalidate();
		repaint();
	}

	/**
	 * Lists every item lit right now, whichever table put it there. Item names have to come from
	 * the client, so the rows are assembled after a hop to the client thread and back.
	 */
	private void buildActiveView()
	{
		currentTable = ACTIVE_VIEW;
		rows.removeAll();
		rowPanels.clear();
		syncChooser();

		Map<Integer, Color> active = highlights.getAllSelected();
		clearAllButton.setVisible(!active.isEmpty());
		if (active.isEmpty())
		{
			emptyMessage.setText("<html><div style='text-align:center;'>Nothing is highlighted "
				+ "yet.<br><br>Ctrl + right-click a monster and choose <b>Highlight Drops</b>, "
				+ "or pick a shared loot pool above.</div></html>");
			revalidate();
			repaint();
			return;
		}
		emptyMessage.setText("");

		clientThread.invoke(() ->
		{
			List<DropTableEntry> entries = new ArrayList<>(active.size());
			for (Integer itemId : active.keySet())
			{
				entries.add(new DropTableEntry(itemId,
					itemManager.getItemComposition(itemId).getName(), "Highlighted"));
			}

			SwingUtilities.invokeLater(() ->
			{
				// The user may have switched tables while we were resolving names.
				if (!ACTIVE_VIEW.equals(currentTable))
				{
					return;
				}
				for (DropTableEntry entry : entries)
				{
					DropRowPanel row = new DropRowPanel(entry, active.get(entry.getItemId()), this);
					rows.add(row);
					rows.add(javax.swing.Box.createVerticalStrut(2));
					rowPanels.add(row);
					loadIcon(entry, row);
				}
				revalidate();
				repaint();
			});
		});
	}

	private boolean inActiveView()
	{
		return ACTIVE_VIEW.equals(currentTable);
	}

	/**
	 * Rebuilds the dropdown: the highlights list, the last monster loaded, then the shared pools,
	 * with whatever is on screen selected.
	 *
	 * <p>The guard stops our own edits firing the listener. It matters because adding the first
	 * item to an empty JComboBox auto-selects it and fires an action event, and because a combo
	 * only fires on a *change* — so a view that was auto-selected but never built could not be
	 * reached by clicking the entry that already appeared to be selected.
	 */
	private void syncChooser()
	{
		updatingChooser = true;
		try
		{
			tableChooser.removeAllItems();
			tableChooser.addItem(ACTIVE_VIEW);
			if (lastMonster != null)
			{
				tableChooser.addItem(lastMonster);
			}
			for (String pool : SHARED_POOLS)
			{
				if (dropTables.hasDrops(pool))
				{
					tableChooser.addItem(pool);
				}
			}
			if (currentTable != null)
			{
				tableChooser.setSelectedItem(currentTable);
			}
		}
		finally
		{
			updatingChooser = false;
		}
	}

	private static boolean isSharedPool(String name)
	{
		for (String pool : SHARED_POOLS)
		{
			if (pool.equals(name))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * {@link ItemManager#getImage} hands back an image that may still be loading, so hand the
	 * label to it and let it repaint itself rather than reading pixels now. The call itself goes
	 * through the client thread because it touches the item cache.
	 */
	private void loadIcon(DropTableEntry drop, DropRowPanel row)
	{
		clientThread.invoke(() ->
		{
			AsyncBufferedImage image = itemManager.getImage(drop.getItemId());
			SwingUtilities.invokeLater(() -> image.addTo(row.getIconLabel()));
		});
	}

	// ------------------------------------------------------------------
	// DropRowPanel.Listener
	// ------------------------------------------------------------------

	@Override
	public void onToggled(DropTableEntry entry, boolean highlighted)
	{
		if (inActiveView())
		{
			// This view is the "make it stop" list, so unticking has to reach every table that
			// selected the item, not just one.
			if (!highlighted)
			{
				highlights.deselectEverywhere(entry.getItemId());
				buildActiveView();
			}
			return;
		}

		if (highlighted)
		{
			Color existing = highlights.getSelected(currentTable).get(entry.getItemId());
			highlights.select(currentTable, entry.getItemId(),
				existing != null ? existing : DEFAULT_COLOR);
		}
		else
		{
			highlights.deselect(currentTable, entry.getItemId());
		}
		refreshSelections();
	}

	@Override
	public void onSwatchClicked(DropTableEntry entry, JComponent swatch)
	{
		JPopupMenu popup = new JPopupMenu();

		JPanel grid = new JPanel(new GridLayout(2, 4, 2, 2));
		grid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		for (Color preset : PRESETS)
		{
			JButton chip = new JButton();
			chip.setBackground(preset);
			chip.setOpaque(true);
			chip.setBorderPainted(false);
			chip.setFocusPainted(false);
			chip.setPreferredSize(new Dimension(20, 20));
			chip.addActionListener(e ->
			{
				popup.setVisible(false);
				assign(entry, preset);
			});
			grid.add(chip);
		}
		popup.add(grid);

		JMenuItem custom = new JMenuItem("Custom…");
		custom.addActionListener(e ->
		{
			Color initial = highlights.getSelected(currentTable).get(entry.getItemId());
			Color chosen = JColorChooser.showDialog(this, "Highlight colour for "
				+ entry.getItemName(), initial != null ? initial : PRESETS[0]);
			if (chosen != null)
			{
				assign(entry, chosen);
			}
		});
		popup.add(custom);

		popup.show(swatch, 0, swatch.getHeight());
	}

	/** Picking a colour implies wanting the item highlighted, so this also ticks the box. */
	private void assign(DropTableEntry entry, Color color)
	{
		if (inActiveView())
		{
			highlights.recolourEverywhere(entry.getItemId(), color);
			buildActiveView();
			return;
		}
		highlights.select(currentTable, entry.getItemId(), color);
		refreshSelections();
	}
}
