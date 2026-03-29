package com.gemstonetrainer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

public class GemstoneTrainerOverlay extends Overlay
{
    private final Client client;
    private final GemstoneTrainerPlugin plugin;
    private final GemstoneTrainerConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public GemstoneTrainerOverlay(Client client, GemstoneTrainerPlugin plugin, GemstoneTrainerConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showDebugOverlay())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Gemstone Trainer")
            .leftColor(Color.YELLOW)
            .build());

        for (CrabBossState state : plugin.getTrackedCrabs().values())
        {
            LinkedHashMap<String, String> debugInfo = state.getDebugInfo();
            for (Map.Entry<String, String> entry : debugInfo.entrySet())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left(entry.getKey() + ":")
                    .right(entry.getValue())
                    .build());
            }

            boolean inMelee = false;
            if (client.getLocalPlayer() != null)
            {
                inMelee = client.getLocalPlayer().getWorldArea()
                    .isInMeleeDistance(state.getNpc().getWorldArea());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Melee Range:")
                .right(inMelee ? "YES" : "NO")
                .rightColor(inMelee ? Color.RED : Color.GREEN)
                .build());
        }

        if (plugin.getTrackedCrabs().isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("No crabs tracked")
                .leftColor(Color.GRAY)
                .build());
        }

        return panelComponent.render(graphics);
    }
}
