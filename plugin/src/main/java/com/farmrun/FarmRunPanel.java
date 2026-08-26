package com.farmrun;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class FarmRunPanel extends PluginPanel
{
	private static final Color HEADER_COLOR = new Color(200, 200, 200);
	private static final Color SUBTEXT_COLOR = new Color(160, 160, 160);

	private final FarmRunPlugin plugin;

	// Dynamically replaced on each refresh
	private JPanel contentPanel;

	FarmRunPanel(FarmRunPlugin plugin)
	{
		super(false);
		this.plugin = plugin;
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		JLabel title = new JLabel("Farm Run Guide", SwingConstants.CENTER);
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setBorder(new EmptyBorder(8, 4, 8, 4));
		add(title, BorderLayout.NORTH);

		contentPanel = new JPanel();
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(contentPanel, BorderLayout.CENTER);

		showIdle();
	}

	void showIdle()
	{
		SwingUtilities.invokeLater(() ->
		{
			replaceContent(buildIdlePanel());
		});
	}

	void showBanking(List<BankItem> checklist)
	{
		SwingUtilities.invokeLater(() ->
		{
			replaceContent(buildBankingPanel(checklist));
		});
	}

	void showActive(List<PatchStop> stops, int currentIndex)
	{
		SwingUtilities.invokeLater(() ->
		{
			replaceContent(buildActivePanel(stops, currentIndex));
		});
	}

	void showTreeActive(List<TreePatchStop> stops, int currentIndex)
	{
		SwingUtilities.invokeLater(() ->
		{
			replaceContent(buildTreeActivePanel(stops, currentIndex));
		});
	}

	void showComplete()
	{
		SwingUtilities.invokeLater(() ->
		{
			replaceContent(buildCompletePanel());
		});
	}

	private void replaceContent(JPanel newContent)
	{
		remove(contentPanel);
		contentPanel = newContent;
		add(contentPanel, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	// --- Panel builders ---

	private JPanel buildIdlePanel()
	{
		JPanel panel = basePanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(Box.createVerticalStrut(16));

		JButton herbBtn = styledButton("Start Herb Run");
		herbBtn.addActionListener(e -> plugin.startRun());
		panel.add(herbBtn);

		panel.add(Box.createVerticalStrut(6));

		JButton treeBtn = styledButton("Start Tree Run");
		treeBtn.addActionListener(e -> plugin.startTreeRun());
		panel.add(treeBtn);

		panel.add(Box.createVerticalGlue());
		return panel;
	}

	private JPanel buildBankingPanel(List<BankItem> checklist)
	{
		JPanel panel = basePanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(Box.createVerticalStrut(8));

		JLabel header = sectionLabel("Grab from bank:");
		panel.add(header);
		panel.add(Box.createVerticalStrut(6));

		for (BankItem item : checklist)
		{
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(new EmptyBorder(4, 8, 4, 8));
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

			JLabel nameLbl = new JLabel(item.getName());
			nameLbl.setForeground(Color.WHITE);
			JLabel qtyLbl = new JLabel("×" + item.getQuantity(), SwingConstants.RIGHT);
			qtyLbl.setForeground(new Color(200, 200, 100));

			row.add(nameLbl, BorderLayout.WEST);
			row.add(qtyLbl, BorderLayout.EAST);
			panel.add(row);
			panel.add(Box.createVerticalStrut(2));
		}

		panel.add(Box.createVerticalStrut(12));

		JButton readyBtn = styledButton("I'm ready →");
		readyBtn.addActionListener(e -> plugin.beginRoute());
		panel.add(readyBtn);

		panel.add(Box.createVerticalStrut(6));

		JButton cancelBtn = grayButton("Cancel");
		cancelBtn.addActionListener(e -> plugin.cancelRun());
		panel.add(cancelBtn);

		panel.add(Box.createVerticalGlue());
		return panel;
	}

	private JPanel buildActivePanel(List<PatchStop> stops, int currentIndex)
	{
		JPanel panel = basePanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(Box.createVerticalStrut(8));

		// --- Current target ---
		if (currentIndex < stops.size())
		{
			PatchStop current = stops.get(currentIndex);

			JLabel nextHeader = sectionLabel("Next patch:");
			panel.add(nextHeader);
			panel.add(Box.createVerticalStrut(4));

			JPanel targetCard = new JPanel(new GridBagLayout());
			targetCard.setBackground(new Color(30, 60, 30));
			targetCard.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(80, 140, 80), 1),
				new EmptyBorder(6, 8, 6, 8)));
			targetCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.WEST;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1.0;
			gbc.insets = new Insets(1, 0, 1, 0);

			// Patch name
			gbc.gridy = 0;
			JLabel patchNameLbl = new JLabel(current.getPatch().getDisplayName());
			patchNameLbl.setFont(FontManager.getRunescapeBoldFont());
			patchNameLbl.setForeground(Color.WHITE);
			targetCard.add(patchNameLbl, gbc);

			// State badge
			gbc.gridy = 1;
			PatchState state = current.getLastKnownState();
			JLabel stateLbl = new JLabel(state.getDisplayName());
			stateLbl.setForeground(state.getColor());
			stateLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
			targetCard.add(stateLbl, gbc);

			// Teleport
			gbc.gridy = 2;
			String teleportText = current.getTeleport() != null
				? "Use: " + current.getTeleport().getDisplayName()
				: "No teleport available";
			JLabel teleportLbl = new JLabel(teleportText);
			teleportLbl.setForeground(new Color(200, 200, 100));
			teleportLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
			targetCard.add(teleportLbl, gbc);

			panel.add(targetCard);
			panel.add(Box.createVerticalStrut(10));
		}

		// --- Remaining patches ---
		int remaining = stops.size() - currentIndex;
		if (remaining > 1)
		{
			JLabel remainingHeader = sectionLabel("Remaining (" + (remaining - 1) + "):");
			panel.add(remainingHeader);
			panel.add(Box.createVerticalStrut(4));

			for (int i = currentIndex + 1; i < stops.size(); i++)
			{
				PatchStop stop = stops.get(i);
				JPanel row = new JPanel(new BorderLayout());
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				row.setBorder(new EmptyBorder(3, 8, 3, 8));
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

				JLabel nameLbl = new JLabel(stop.getPatch().getDisplayName());
				nameLbl.setForeground(HEADER_COLOR);
				JLabel stateLbl = new JLabel(stop.getLastKnownState().getDisplayName());
				stateLbl.setForeground(stop.getLastKnownState().getColor());
				stateLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

				row.add(nameLbl, BorderLayout.WEST);
				row.add(stateLbl, BorderLayout.EAST);
				panel.add(row);
				panel.add(Box.createVerticalStrut(2));
			}
			panel.add(Box.createVerticalStrut(8));
		}

		// --- Controls ---
		JButton skipBtn = grayButton("Skip patch");
		skipBtn.addActionListener(e -> plugin.skipCurrentStop());
		panel.add(skipBtn);

		panel.add(Box.createVerticalStrut(4));

		JButton finishBtn = grayButton("Finish run");
		finishBtn.addActionListener(e -> plugin.cancelRun());
		panel.add(finishBtn);

		panel.add(Box.createVerticalGlue());
		return panel;
	}

	private JPanel buildTreeActivePanel(List<TreePatchStop> stops, int currentIndex)
	{
		JPanel panel = basePanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(Box.createVerticalStrut(8));

		if (currentIndex < stops.size())
		{
			TreePatchStop current = stops.get(currentIndex);

			panel.add(sectionLabel("Next patch:"));
			panel.add(Box.createVerticalStrut(4));

			JPanel targetCard = new JPanel(new GridBagLayout());
			targetCard.setBackground(new Color(30, 60, 30));
			targetCard.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(80, 140, 80), 1),
				new EmptyBorder(6, 8, 6, 8)));
			targetCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.WEST;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1.0;
			gbc.insets = new Insets(1, 0, 1, 0);

			gbc.gridy = 0;
			JLabel patchNameLbl = new JLabel(current.getPatch().getDisplayName());
			patchNameLbl.setFont(FontManager.getRunescapeBoldFont());
			patchNameLbl.setForeground(Color.WHITE);
			targetCard.add(patchNameLbl, gbc);

			gbc.gridy = 1;
			PatchState state = current.getLastKnownState();
			JLabel stateLbl = new JLabel(state.getDisplayName());
			stateLbl.setForeground(state.getColor());
			stateLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
			targetCard.add(stateLbl, gbc);

			gbc.gridy = 2;
			String teleportText = current.getTeleport() != null
				? "Use: " + current.getTeleport().getDisplayName()
				: "No teleport available";
			JLabel teleportLbl = new JLabel(teleportText);
			teleportLbl.setForeground(new Color(200, 200, 100));
			teleportLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
			targetCard.add(teleportLbl, gbc);

			panel.add(targetCard);
			panel.add(Box.createVerticalStrut(10));
		}

		int remaining = stops.size() - currentIndex;
		if (remaining > 1)
		{
			panel.add(sectionLabel("Remaining (" + (remaining - 1) + "):"));
			panel.add(Box.createVerticalStrut(4));

			for (int i = currentIndex + 1; i < stops.size(); i++)
			{
				TreePatchStop stop = stops.get(i);
				JPanel row = new JPanel(new BorderLayout());
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				row.setBorder(new EmptyBorder(3, 8, 3, 8));
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

				JLabel nameLbl = new JLabel(stop.getPatch().getDisplayName());
				nameLbl.setForeground(HEADER_COLOR);
				JLabel stateLbl = new JLabel(stop.getLastKnownState().getDisplayName());
				stateLbl.setForeground(stop.getLastKnownState().getColor());
				stateLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

				row.add(nameLbl, BorderLayout.WEST);
				row.add(stateLbl, BorderLayout.EAST);
				panel.add(row);
				panel.add(Box.createVerticalStrut(2));
			}
			panel.add(Box.createVerticalStrut(8));
		}

		JButton skipBtn = grayButton("Skip patch");
		skipBtn.addActionListener(e -> plugin.skipCurrentStop());
		panel.add(skipBtn);

		panel.add(Box.createVerticalStrut(4));

		JButton finishBtn = grayButton("Finish run");
		finishBtn.addActionListener(e -> plugin.cancelRun());
		panel.add(finishBtn);

		panel.add(Box.createVerticalGlue());
		return panel;
	}

	private JPanel buildCompletePanel()
	{
		JPanel panel = basePanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(Box.createVerticalStrut(20));

		JLabel doneLbl = new JLabel("Run complete!", SwingConstants.CENTER);
		doneLbl.setForeground(new Color(100, 220, 100));
		doneLbl.setFont(FontManager.getRunescapeBoldFont());
		doneLbl.setAlignmentX(CENTER_ALIGNMENT);
		panel.add(doneLbl);

		panel.add(Box.createVerticalStrut(12));

		JButton newRunBtn = styledButton("Start New Run");
		newRunBtn.addActionListener(e -> plugin.startRun());
		panel.add(newRunBtn);

		panel.add(Box.createVerticalGlue());
		return panel;
	}

	// --- Helpers ---

	private static JPanel basePanel()
	{
		JPanel p = new JPanel();
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.setBorder(new EmptyBorder(4, 8, 8, 8));
		return p;
	}

	private static JLabel sectionLabel(String text)
	{
		JLabel lbl = new JLabel(text);
		lbl.setForeground(SUBTEXT_COLOR);
		lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		lbl.setAlignmentX(LEFT_ALIGNMENT);
		return lbl;
	}

	private static JButton styledButton(String text)
	{
		JButton btn = new JButton(text);
		btn.setBackground(new Color(50, 100, 50));
		btn.setForeground(Color.WHITE);
		btn.setFocusPainted(false);
		btn.setAlignmentX(CENTER_ALIGNMENT);
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return btn;
	}

	private static JButton grayButton(String text)
	{
		JButton btn = new JButton(text);
		btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		btn.setForeground(Color.WHITE);
		btn.setFocusPainted(false);
		btn.setAlignmentX(CENTER_ALIGNMENT);
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return btn;
	}
}
