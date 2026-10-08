package com.eternalflame;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class EternalFlameOverlay extends OverlayPanel
{
    private final EternalFlamePlugin plugin;
    private final EternalFlameConfig config;

    @Inject
    EternalFlameOverlay(EternalFlamePlugin plugin, EternalFlameConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!config.showOverlay())
        {
            return null;
        }
        panelComponent.getChildren().add(TitleComponent.builder().text("Eternal Flame").build());
        if (plugin.isSharing())
        {
            long total = plugin.getGlobalTotal();
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Everyone:").right(total < 0 ? "..." : String.format("%,d", total)).build());
        }
        else
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Everyone:").right("Sharing off").build());
        }
        panelComponent.getChildren().add(LineComponent.builder()
            .left("You:").right(String.format("%,d", plugin.getPersonalCount())).build());
        return super.render(g);
    }
}
