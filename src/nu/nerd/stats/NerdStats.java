package nu.nerd.stats;

import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;

// ----------------------------------------------------------------------------
/**
 * Plugin class.
 */
public class NerdStats extends JavaPlugin {
    // ------------------------------------------------------------------------
    /**
     * Configuration instance.
     */
    public static final Configuration CONFIG = new Configuration();

    /**
     * This plugin as a singleton.
     */
    public static NerdStats PLUGIN;

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onCommand(org.bukkit.command.CommandSender,
     *      org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase(getName())) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                CONFIG.reload();
                startServer();
                sender.sendMessage(ChatColor.GOLD + getName() + " configuration reloaded.");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("lag")) {
            long memUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
            long memMax = Runtime.getRuntime().maxMemory() / 1048576;
            sender.sendMessage(String.format("TPS: %5.2f Mem: %dM/%dM", _tpsTask.getTPS(), memUsed, memMax));
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Invalid command syntax.");
        return true;
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        PLUGIN = this;

        saveDefaultConfig();
        CONFIG.reload();
        startServer();

        _tpsTask = new TPSTask();
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, _tpsTask, 20, 20);
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        stopServer();
    }

    // ------------------------------------------------------------------------
    /**
     * Start gathering statistics and schedule synchronous tasks to complete the
     * process.
     *
     * The statistics will be added to the _statistics queue as a JSONObject
     * that will be returned to the client.
     */
    @SuppressWarnings("unchecked")
    public void requestStatistics() {
        long start = System.nanoTime();

        JSONObject results = new JSONObject();
        results.put("players", Bukkit.getOnlinePlayers().size());
        results.put("tps", _tpsTask.getTPS());

        JSONObject memory = new JSONObject();
        long memUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
        long memMax = Runtime.getRuntime().maxMemory() / 1048576;
        memory.put("used", memUsed);
        memory.put("max", memMax);
        memory.put("percent", 100.0 * memUsed / memMax);
        results.put("memory", memory);

        JSONObject jsonAllWorlds = getJSONObject(results, "worlds");
        LinkedList<BooleanSupplier> steps = new LinkedList<BooleanSupplier>();
        for (World world : CONFIG.WORLDS) {
            JSONObject jsonWorld = getJSONObject(jsonAllWorlds, world.getName());
            jsonWorld.put("view_distance", getViewDistance(world));
            steps.add(new CountEntitiesTask(world, getJSONObject(jsonWorld, "entities")));
            steps.add(new CountHoppersTask(world, jsonWorld));
        }
        steps.add(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    _statistics.add(results);
                } catch (IllegalStateException ex) {
                    getLogger().warning("Cannot add statistics to the queue.");
                }
                return false;
            }
        });
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new SynchronousTimeLimitedTask(steps));

        if (CONFIG.DEBUG_OVERHEAD) {
            double elapsedMillis = 1e-6 * (System.nanoTime() - start);
            getLogger().info("Statistics setup took " + elapsedMillis + " ms.");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return the JSON object that is the child of the specified parent with the
     * specified name.
     *
     * @param parent the parent JSONObject.
     * @param name the name of the child.
     * @return the child with the specified name, which will be created and
     *         added to the parent as necessary.
     */
    @SuppressWarnings("unchecked")
    protected JSONObject getJSONObject(JSONObject parent, String name) {
        JSONObject child = (JSONObject) parent.get(name);
        if (child == null) {
            child = new JSONObject();
            parent.put(name, child);
        }
        return child;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the view distance in the specified world.
     *
     * @param world the World.
     * @return the view distance.
     */
    protected int getViewDistance(World world) {
        YamlConfiguration spigotConfig = Bukkit.getServer().spigot().getConfig();
        int defaultViewDistance = spigotConfig.getInt("world-settings.default.view-distance", 12);
        return spigotConfig.getInt("world-settings." + world.getName() + ".view-distance", defaultViewDistance);
    }

    // ------------------------------------------------------------------------
    /**
     * Block and wait for a JSONObject to be added to the results queue, then
     * take it.
     *
     * This method is called by the clients of the {@link QueryServer} in order
     * to wait for results.
     *
     * @return the results JSONObject, when it is added to the queue.
     */
    public JSONObject awaitStatistics() {
        synchronized (_statistics) {
            try {
                return _statistics.take();
            } catch (InterruptedException ex) {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Start the query server thread, binding to the configured listening
     * address and port.
     */
    protected void startServer() {
        stopServer();

        _server = new QueryServer();
        try {
            _server.bind();
            _server.start();
            getLogger().info("Query server listening on " + CONFIG.BIND_ADDRESS + ":" + CONFIG.BIND_PORT + ".");
        } catch (Exception ex) {
            _server = null;
            getLogger().severe("Unable to start query server: " + ex.getMessage());
            return;
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Stop the query server, unblock the client waiting on the results queue
     * and wait for the thread to die.
     */
    protected void stopServer() {
        if (_server == null) {
            return;
        }

        _server.close();
        // Interrupt clients that are blocked waiting for results.
        _server.interrupt();
        try {
            _server.join();
        } catch (InterruptedException ex) {
        }
        _server = null;
    }

    // ------------------------------------------------------------------------
    /**
     * Task that measures TPS, run every 20 ticks.
     */
    protected TPSTask _tpsTask;

    /**
     * Server socket and client handling async task.
     */
    protected QueryServer _server;

    /**
     * Queue of results returned to clients.
     */
    protected ArrayBlockingQueue<JSONObject> _statistics = new ArrayBlockingQueue<JSONObject>(10);
} // class NerdStats