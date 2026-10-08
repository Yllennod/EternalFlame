package com.eternalflame;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
    name = "Eternal Flame Counter",
    description = "Counts logs you feed the Eternal Flame (world 622 GE). Optional: share to a community total and leaderboard",
    tags = {"firemaking", "fire", "counter", "community", "leaderboard"}
)
public class EternalFlamePlugin extends Plugin
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    // Ticks after clicking the fire during which a log decrease counts as feeding it
    private static final int FEED_WINDOW_TICKS = 25;

    public static class LeaderEntry
    {
        final String name;
        final long count;

        LeaderEntry(String name, long count)
        {
            this.name = name;
            this.count = count;
        }
    }

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private EternalFlameConfig config;
    @Inject private ConfigManager configManager;
    @Inject private OverlayManager overlayManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private EternalFlameOverlay overlay;
    @Inject private ItemManager itemManager;
    @Inject private OkHttpClient okHttpClient;
    @Inject private Gson gson;
    @Inject private ScheduledExecutorService executor;

    private final AtomicInteger pending = new AtomicInteger();
    private volatile long globalTotal = -1;
    private volatile List<LeaderEntry> leaderboard = null;
    private volatile String playerName = null;
    private volatile int currentWorld = -1;
    private volatile boolean armed = false; // "Set target" pressed, waiting for you to use logs on the flame
    private int lastFeedTick = -1000;
    private int lastLogCount = -1;
    private ScheduledFuture<?> flushTask, fetchTask;
    private EternalFlamePanel panel;
    private NavigationButton navButton;

    @Provides
    EternalFlameConfig provideConfig(ConfigManager cm)
    {
        return cm.getConfig(EternalFlameConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);

        panel = new EternalFlamePanel(this);
        navButton = NavigationButton.builder()
            .tooltip("Eternal Flame")
            .icon(makeIcon())
            .priority(7)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        // These tasks do nothing at all unless "Share to leaderboard" is switched on.
        flushTask = executor.scheduleWithFixedDelay(this::flush, 10, 10, TimeUnit.SECONDS);
        fetchTask = executor.scheduleWithFixedDelay(this::fetchAll, 1, 30, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);
        if (flushTask != null) flushTask.cancel(false);
        if (fetchTask != null) fetchTask.cancel(false);
        flush();
        lastLogCount = -1;
        armed = false;
    }

    private static BufferedImage makeIcon()
    {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 140, 0));
        g.fillOval(2, 1, 12, 14);
        g.setColor(new Color(255, 220, 80));
        g.fillOval(5, 6, 6, 8);
        g.dispose();
        return img;
    }

    public boolean isSharing() { return config.shareToLeaderboard(); }

    public void setSharing(boolean on)
    {
        configManager.setConfiguration(EternalFlameConfig.GROUP, "shareToLeaderboard", on);
    }

    public long getGlobalTotal() { return globalTotal; }
    public int getPersonalCount() { return config.personalCount(); }
    public int getTargetX() { return config.flameX(); }
    public int getTargetY() { return config.flameY(); }
    public int getCurrentWorld() { return currentWorld; }
    public int getTargetWorld() { return config.world(); }
    public List<LeaderEntry> getLeaderboard() { return leaderboard; }

    private boolean worldOk()
    {
        return config.world() <= 0 || client.getWorld() == config.world();
    }

    private void refreshPanel()
    {
        if (panel != null)
        {
            SwingUtilities.invokeLater(panel::update);
        }
    }

    private void msg(String text)
    {
        clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", text, null));
    }

    private void debug(String text)
    {
        if (config.debug())
        {
            msg("[EF debug] " + text);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!EternalFlameConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }
        if ("shareToLeaderboard".equals(event.getKey()))
        {
            if (config.shareToLeaderboard())
            {
                executor.execute(this::fetchAll);
            }
            else
            {
                // Sharing turned off: forget anything unsent and any fetched data
                pending.set(0);
                globalTotal = -1;
                leaderboard = null;
            }
        }
        refreshPanel();
    }

    /** Called by the "Set target" button. */
    public void armTarget()
    {
        armed = true;
        msg("Eternal Flame: now use logs on the flame. That fire's tile will be remembered.");
    }

    /** Called by the "Reset target" button. */
    public void resetTarget()
    {
        armed = false;
        configManager.unsetConfiguration(EternalFlameConfig.GROUP, "flameX");
        configManager.unsetConfiguration(EternalFlameConfig.GROUP, "flameY");
        msg("Eternal Flame: target reset to the default tile.");
        refreshPanel();
    }

    private static boolean isObjectAction(MenuAction a)
    {
        return a == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
            || a == MenuAction.GAME_OBJECT_FIRST_OPTION
            || a == MenuAction.GAME_OBJECT_SECOND_OPTION
            || a == MenuAction.GAME_OBJECT_THIRD_OPTION
            || a == MenuAction.GAME_OBJECT_FOURTH_OPTION
            || a == MenuAction.GAME_OBJECT_FIFTH_OPTION;
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        int w = client.getWorld();
        if (w != currentWorld)
        {
            currentWorld = w;
            refreshPanel();
        }
    }

    /**
     * Player clicked an object (use logs on it, or one of its options). Counting only
     * happens if that object is on the target tile.
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        MenuAction action = event.getMenuAction();
        if (action == MenuAction.WALK)
        {
            lastFeedTick = -1000; // walking away interrupts feeding
            return;
        }
        if (!isObjectAction(action))
        {
            return;
        }

        WorldPoint tile = WorldPoint.fromScene(client, event.getParam0(), event.getParam1(), client.getPlane());
        String target = Text.removeTags(event.getMenuTarget());

        debug("click " + action + " | '" + Text.removeTags(event.getMenuOption()) + "' on '" + target
            + "' | id " + event.getId() + " | tile " + tile.getX() + "," + tile.getY()
            + " | world " + client.getWorld());

        if (armed)
        {
            configManager.setConfiguration(EternalFlameConfig.GROUP, "flameX", tile.getX());
            configManager.setConfiguration(EternalFlameConfig.GROUP, "flameY", tile.getY());
            armed = false;
            msg("Eternal Flame target set: '" + target + "' at tile " + tile.getX() + ", " + tile.getY() + ".");
            refreshPanel();
        }

        boolean match = worldOk()
            && tile.getX() == config.flameX()
            && tile.getY() == config.flameY();
        lastFeedTick = match ? client.getTickCount() : -1000;
        debug(match ? "on target tile - counting armed" : "NOT counted (world ok: " + worldOk()
            + ", target tile " + config.flameX() + "," + config.flameY() + ")");
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getItemContainer() != client.getItemContainer(InventoryID.INVENTORY))
        {
            return;
        }
        Player local = client.getLocalPlayer();
        if (local != null && local.getName() != null)
        {
            playerName = local.getName();
        }

        int count = countLogs(event.getItemContainer());
        int tick = client.getTickCount();

        if (lastLogCount >= 0 && count < lastLogCount
            && tick - lastFeedTick <= FEED_WINDOW_TICKS && worldOk())
        {
            int delta = lastLogCount - count;
            if (config.shareToLeaderboard())
            {
                pending.addAndGet(delta); // only queued for sending if you opted in
            }
            configManager.setConfiguration(EternalFlameConfig.GROUP, "personalCount",
                config.personalCount() + delta);
            lastFeedTick = tick; // keep the window open while you keep feeding
            refreshPanel();
        }
        if (lastLogCount >= 0 && count < lastLogCount)
        {
            debug("logs dropped by " + (lastLogCount - count) + " | ticks since fire click "
                + (tick - lastFeedTick) + " | world ok " + worldOk());
        }
        lastLogCount = count;
    }

    private int countLogs(ItemContainer inv)
    {
        int total = 0;
        for (Item item : inv.getItems())
        {
            if (item.getId() < 0) continue;
            ItemComposition comp = itemManager.getItemComposition(item.getId());
            if (comp.getNote() == -1 && comp.getName().toLowerCase().endsWith("logs"))
            {
                total += item.getQuantity();
            }
        }
        return total;
    }

    /** Sends your log count to the server. Does nothing unless sharing is switched on. */
    private void flush()
    {
        if (!config.shareToLeaderboard())
        {
            pending.set(0);
            return;
        }
        String name = playerName;
        if (name == null)
        {
            return; // not logged in yet; keep the count and try again
        }
        int n = pending.getAndSet(0);
        if (n <= 0) return;

        String id = config.clientId();
        if (id.isEmpty())
        {
            id = UUID.randomUUID().toString();
            configManager.setConfiguration(EternalFlameConfig.GROUP, "clientId", id);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("flame", config.flameId());
        body.put("client", id); // random anonymous ID
        body.put("name", name);
        body.put("count", n);

        Request request = new Request.Builder()
            .url(config.serverUrl() + "/contribute")
            .post(RequestBody.create(JSON, gson.toJson(body)))
            .build();
        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                pending.addAndGet(n); // retry next flush
            }
            else if (response.body() != null)
            {
                globalTotal = gson.fromJson(response.body().string(), JsonObject.class).get("total").getAsLong();
                refreshPanel();
            }
        }
        catch (Exception e)
        {
            pending.addAndGet(n);
            log.debug("Flush failed", e);
        }
    }

    /** Fetches the shared total and leaderboard. Does nothing unless sharing is switched on. */
    private void fetchAll()
    {
        if (!config.shareToLeaderboard())
        {
            return;
        }
        fetchTotal();
        fetchLeaderboard();
        refreshPanel();
    }

    private void fetchTotal()
    {
        Request request = new Request.Builder()
            .url(config.serverUrl() + "/total?flame=" + java.net.URLEncoder.encode(config.flameId()))
            .build();
        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (response.isSuccessful() && response.body() != null)
            {
                globalTotal = gson.fromJson(response.body().string(), JsonObject.class).get("total").getAsLong();
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.debug("Fetch failed", e);
        }
    }

    /** Expects {"entries":[{"name":"x","count":123}, ...]} from GET /leaderboard?flame=ID */
    private void fetchLeaderboard()
    {
        Request request = new Request.Builder()
            .url(config.serverUrl() + "/leaderboard?flame=" + java.net.URLEncoder.encode(config.flameId()))
            .build();
        try (Response response = okHttpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                leaderboard = null;
                return;
            }
            JsonArray arr = gson.fromJson(response.body().string(), JsonObject.class).getAsJsonArray("entries");
            List<LeaderEntry> list = new ArrayList<>();
            for (JsonElement el : arr)
            {
                JsonObject o = el.getAsJsonObject();
                list.add(new LeaderEntry(o.get("name").getAsString(), o.get("count").getAsLong()));
            }
            leaderboard = Collections.unmodifiableList(list);
        }
        catch (IOException | RuntimeException e)
        {
            leaderboard = null;
            log.debug("Leaderboard fetch failed", e);
        }
    }
}
