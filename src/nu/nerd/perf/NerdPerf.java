package nu.nerd.perf;

import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;

// ----------------------------------------------------------------------------
/**
 * Plugin class.
 */
public class NerdPerf extends JavaPlugin {
    // ------------------------------------------------------------------------
    /**
     * Configuration instance.
     */
    public static final Configuration CONFIG = new Configuration();

    /**
     * This plugin as a singleton.
     */
    public static NerdPerf PLUGIN;

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
     * Start gathering metrics and schedule synchronous tasks to complete the
     * process.
     *
     * The metrics will be added to the _metrics queue as a JSONObject that will
     * be returned to the client.
     */
    @SuppressWarnings("unchecked")
    public void requestMetrics() {
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
            jsonWorld.put("view_distance", world.getViewDistance());
            steps.add(new CountEntitiesTask(world, getJSONObject(jsonWorld, "entities")));
            steps.add(new CountHoppersTask(world, jsonWorld));
        }
        steps.add(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    _metrics.add(results);
                } catch (IllegalStateException ex) {
                    getLogger().warning("Cannot add metrics to the queue.");
                }
                return false;
            }
        });
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new SynchronousTimeLimitedTask(steps));

        if (CONFIG.DEBUG_OVERHEAD) {
            double elapsedMillis = 1e-6 * (System.nanoTime() - start);
            getLogger().info("Metrics setup took " + elapsedMillis + " ms.");
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
     * Block and wait for a JSONObject to be added to the results queue, then
     * take it.
     *
     * This method is called by the clients of the {@link QueryServer} in order
     * to wait for results.
     *
     * @return the results JSONObject, when it is added to the queue.
     */
    public JSONObject awaitMetrics() {
        synchronized (_metrics) {
            try {
                return _metrics.take();
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
    protected ArrayBlockingQueue<JSONObject> _metrics = new ArrayBlockingQueue<JSONObject>(10);
} // class NerdPerf