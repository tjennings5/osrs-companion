package com.dropHighlighter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One item in the drop table: icon, name, rarity, a checkbox to highlight it, and a swatch that
 * opens the colour picker.
 *
 * <p>Purely a view. Every interaction is forwarded to the listener, which owns the decision about
 * what to persist — this keeps the panel from ever holding a second, drifting copy of the
 * highlight state.
 */
class DropRowPanel extends JPanel
{
	private static final int ICON_SIZE = 32;
	private static final Dimension SWATCH_SIZE = new Dimension(16, 16);

	interface Listener
	{
		void onToggled(DropTableEntry entry, boolean highlighted);

		void onSwatchClicked(DropTableEntry entry, JComponent swatch);
	}

	private final DropTableEntry entry;
	private final JLabel iconLabel;
	private final JCheckBox checkBox;
	private final Swatch swatch;

	DropRowPanel(DropTableEntry entry, Color assignedColor, Listener listener)
	{
		this.entry = entry;

		setLayout(new BorderLayout(6, 0));
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		add(icon, BorderLayout.WEST);
		this.iconLabel = icon;

		JLabel name = new JLabel(entry.getItemName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel rarity = new JLabel(entry.getRarity());
		rarity.setFont(FontManager.getRunescapeSmallFont());
		rarity.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(getBackground());
		text.add(name);
		text.add(rarity);
		add(text, BorderLayout.CENTER);

		swatch = new Swatch(assignedColor);
		swatch.setToolTipText("Choose highlight colour");
		swatch.addActionListener(e -> listener.onSwatchClicked(this.entry, swatch));

		checkBox = new JCheckBox();
		checkBox.setBackground(getBackground());
		checkBox.setToolTipText("Highlight this item on the ground");
		checkBox.setSelected(assignedColor != null);
		checkBox.addActionListener(e -> listener.onToggled(this.entry, checkBox.isSelected()));

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		controls.setBackground(getBackground());
		controls.add(swatch);
		controls.add(checkBox);
		add(controls, BorderLayout.EAST);

		// A row is one line of content; without this the BoxLayout in the parent stretches rows
		// to fill the panel when there are only a few of them.
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	JLabel getIconLabel()
	{
		return iconLabel;
	}

	int getItemId()
	{
		return entry.getItemId();
	}

	/** Re-syncs the row to the highlight state without rebuilding it. */
	void setAssignedColor(Color color)
	{
		checkBox.setSelected(color != null);
		swatch.setColor(color);
	}

	/**
	 * A small rounded colour chip. Drawn rather than using a bordered JButton so that the
	 * "nothing assigned" state reads as an empty outline instead of a grey button.
	 */
	private static class Swatch extends javax.swing.JButton
	{
		private Color color;

		Swatch(Color color)
		{
			this.color = color;
			setPreferredSize(SWATCH_SIZE);
			setMaximumSize(SWATCH_SIZE);
			setBorderPainted(false);
			setContentAreaFilled(false);
			setFocusPainted(false);
			setOpaque(false);
		}

		void setColor(Color color)
		{
			this.color = color;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				int w = getWidth() - 1;
				int h = getHeight() - 1;

				if (color != null)
				{
					g2.setColor(color);
					g2.fillRoundRect(0, 0, w, h, 4, 4);
				}
				g2.setColor(color == null ? ColorScheme.LIGHT_GRAY_COLOR : Color.BLACK);
				g2.drawRoundRect(0, 0, w, h, 4, 4);
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
