package com.eternalflame;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(EternalFlameConfig.GROUP)
public interface EternalFlameConfig extends Config
{
    String GROUP = "eternalflame";

    @ConfigItem(keyName = "shareToLeaderboard", name = "Share to leaderboard", position = 1,
        description = "Off by default. When ON, your character name, log counts and a random anonymous ID are sent to the plugin's "
            + "server, and the shared total and leaderboard are fetched from it. When OFF, nothing is sent or requested and "
            + "the plugin only counts locally.")
    default boolean shareToLeaderboard() { return false; }

    @ConfigItem(keyName = "serverUrl", name = "Server URL", position = 2,
        description = "Counter server shared by everyone feeding the flame (only used when sharing is on)")
    default String serverUrl() { return "https://eternal-flame.eternalflame.workers.dev"; }

    @ConfigItem(keyName = "flameId", name = "Flame ID", position = 3,
        description = "Everyone using the same ID shares one total")
    default String flameId() { return "eternal-flame"; }

    @ConfigItem(keyName = "world", name = "World", position = 4,
        description = "Only count on this world (0 = any world)")
    default int world() { return 622; }

    @ConfigItem(keyName = "showOverlay", name = "Show overlay", position = 5, description = "")
    default boolean showOverlay() { return true; }

    @ConfigItem(keyName = "debug", name = "Debug messages", position = 6,
        description = "Prints what the plugin sees in your game chat (for troubleshooting)")
    default boolean debug() { return false; }

    // hidden state
    @ConfigItem(keyName = "flameX", name = "", description = "", hidden = true)
    default int flameX() { return 3168; }
    @ConfigItem(keyName = "flameY", name = "", description = "", hidden = true)
    default int flameY() { return 3489; }
    @ConfigItem(keyName = "personalCount", name = "", description = "", hidden = true)
    default int personalCount() { return 0; }
    @ConfigItem(keyName = "clientId", name = "", description = "", hidden = true)
    default String clientId() { return ""; }
}
