package com.eternalflame;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class EternalFlamePanel extends PluginPanel
{
    private final EternalFlamePlugin plugin;
    private final JLabel everyone = new JLabel("...", SwingConstants.RIGHT);
    private final JLabel you = new JLabel("0", SwingConstants.RIGHT);
    private final JLabel target = new JLabel(" ");
    private final JLabel world = new JLabel(" ");
    private final JCheckBox share = new JCheckBox("Share to leaderboard");
    private final JPanel board = new JPanel();

    EternalFlamePanel(EternalFlamePlugin plugin)
    {
        this.plugin = plugin;
        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Eternal Flame");
        title.setForeground(Color.ORANGE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);
        content.add(spacer(8));

        content.add(row("Everyone", everyone));
        content.add(row("You", you));
        content.add(spacer(8));

        share.setToolTipText("Off: nothing is sent anywhere. On: your name, log counts and a random ID are sent to the plugin's server.");
        share.setAlignmentX(LEFT_ALIGNMENT);
        share.addActionListener(e -> plugin.setSharing(share.isSelected()));
        content.add(share);
        content.add(spacer(10));

        JButton setTarget = new JButton("Set target");
        setTarget.setToolTipText("Click, then use logs on the flame to lock onto it");
        setTarget.addActionListener(e -> plugin.armTarget());
        setTarget.setAlignmentX(LEFT_ALIGNMENT);
        setTarget.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        content.add(setTarget);
        content.add(spacer(4));

        JButton reset = new JButton("Reset target to default");
        reset.addActionListener(e -> plugin.resetTarget());
        reset.setAlignmentX(LEFT_ALIGNMENT);
        reset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        content.add(reset);
        content.add(spacer(4));

        target.setForeground(Color.GRAY);
        target.setAlignmentX(LEFT_ALIGNMENT);
        content.add(target);
        content.add(spacer(2));
        world.setAlignmentX(LEFT_ALIGNMENT);
        content.add(world);
        content.add(spacer(12));

        JLabel lbTitle = new JLabel("Leaderboard");
        lbTitle.setForeground(Color.ORANGE);
        lbTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(lbTitle);
        content.add(spacer(4));

        board.setLayout(new BoxLayout(board, BoxLayout.Y_AXIS));
        board.setAlignmentX(LEFT_ALIGNMENT);
        content.add(board);

        add(content, BorderLayout.NORTH);
        update();
    }

    private static JPanel row(String left, JLabel right)
    {
        JPanel p = new JPanel(new GridLayout(1, 2));
        p.add(new JLabel(left));
        p.add(right);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return p;
    }

    private static JPanel spacer(int h)
    {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(1, h));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private void note(String text)
    {
        JLabel l = new JLabel("<html>" + text + "</html>");
        l.setForeground(Color.GRAY);
        l.setAlignmentX(LEFT_ALIGNMENT);
        board.add(l);
    }

    /** Must be called on the Swing thread. */
    void update()
    {
        boolean sharing = plugin.isSharing();
        share.setSelected(sharing);

        long total = plugin.getGlobalTotal();
        everyone.setText(!sharing ? "Off" : total < 0 ? "..." : String.format("%,d", total));
        you.setText(String.format("%,d", plugin.getPersonalCount()));

        int w = plugin.getCurrentWorld();
        int tw = plugin.getTargetWorld();
        boolean ok = tw <= 0 || w == tw;
        world.setText(ok ? "World " + w + ": counting" : "World " + w + ": NOT counting (needs " + tw + ")");
        world.setForeground(ok ? Color.GREEN : Color.RED);
        target.setText("Target tile: " + plugin.getTargetX() + ", " + plugin.getTargetY());

        board.removeAll();
        List<EternalFlamePlugin.LeaderEntry> entries = plugin.getLeaderboard();
        if (!sharing)
        {
            note("Tick \"Share to leaderboard\" to see the shared total and leaderboard. "
                + "This sends your character name and log counts to the plugin's server.");
        }
        else if (entries == null)
        {
            note("Leaderboard not available yet");
        }
        else if (entries.isEmpty())
        {
            note("No one on the board yet");
        }
        else
        {
            int rank = 1;
            for (EternalFlamePlugin.LeaderEntry e : entries)
            {
                JPanel p = new JPanel(new BorderLayout());
                p.setBackground(rank % 2 == 0 ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
                p.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                p.add(new JLabel(rank + ". " + e.name), BorderLayout.WEST);
                p.add(new JLabel(String.format("%,d", e.count)), BorderLayout.EAST);
                p.setAlignmentX(LEFT_ALIGNMENT);
                p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
                board.add(p);
                rank++;
            }
        }
        board.revalidate();
        board.repaint();
    }
}
