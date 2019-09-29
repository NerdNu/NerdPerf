package nu.nerd.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

// ----------------------------------------------------------------------------
/**
 * Configuration wrapper.
 */
public class Configuration {
    // ------------------------------------------------------------------------
    /**
     * If true, log configuration loading.
     */
    public boolean DEBUG_CONFIG;

    /**
     * If true, log elapsed time computing metrics.
     */
    public boolean DEBUG_OVERHEAD;

    /**
     * If true, log queries.
     */
    public boolean DEBUG_QUERIES;

    /**
     * If true, log progress of the counting tasks.
     */
    public boolean DEBUG_COUNTS;

    /**
     * Address to bind to.
     */
    public String BIND_ADDRESS;

    /**
     * Port to listen on.
     */
    public int BIND_PORT;

    /**
     * Time limit, in milliseconds, of any metrics gathering task.
     */
    public int TASK_TIME_LIMIT_MILLIS;

    /**
     * Number of entities to count before checking for a timeout on the entity
     * counting task.
     */
    public int BATCH_ENTITIES;

    /**
     * Number of chunks to count before checking for a timeout on the hopper
     * counting task.
     */
    public int BATCH_CHUNKS;

    /**
     * Worlds where metrics are gathered.
     */
    public List<World> WORLDS = new ArrayList<World>();

    // ------------------------------------------------------------------------
    /**
     * Reload the configuration file.
     */
    public void reload() {
        NerdPerf.PLUGIN.reloadConfig();

        DEBUG_CONFIG = getConfig().getBoolean("debug.config");
        DEBUG_OVERHEAD = getConfig().getBoolean("debug.overhead");
        DEBUG_QUERIES = getConfig().getBoolean("debug.queries");
        DEBUG_COUNTS = getConfig().getBoolean("debug.counts");
        BIND_ADDRESS = getConfig().getString("bind.address");
        BIND_PORT = getConfig().getInt("bind.port");
        TASK_TIME_LIMIT_MILLIS = getConfig().getInt("task-time-limit-millis");
        BATCH_ENTITIES = Math.max(10, getConfig().getInt("batch.entities"));
        BATCH_CHUNKS = Math.max(10, getConfig().getInt("batch.chunks"));

        WORLDS.clear();
        for (String worldName : getConfig().getStringList("worlds")) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("Invalid world name: " + worldName);
            } else {
                WORLDS.add(world);
            }
        }

        if (DEBUG_CONFIG) {
            getLogger().info("Configuration:");
            getLogger().info("DEBUG_OVERHEAD: " + DEBUG_OVERHEAD);
            getLogger().info("DEBUG_QUERIES: " + DEBUG_QUERIES);
            getLogger().info("DEBUG_COUNTS: " + DEBUG_COUNTS);
            getLogger().info("BIND_ADDRESS: " + BIND_ADDRESS);
            getLogger().info("BIND_PORT: " + BIND_PORT);
            getLogger().info("TASK_TIME_LIMIT_MILLIS: " + TASK_TIME_LIMIT_MILLIS);
            getLogger().info("BATCH_ENTITIES: " + BATCH_ENTITIES);
            getLogger().info("BATCH_CHUNKS: " + BATCH_CHUNKS);
            getLogger().info("WORLDS: " + WORLDS.stream().map(World::getName).collect(Collectors.joining(", ")));
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return the plugin's FileConfiguration.
     *
     * @return the plugin's FileConfiguration.
     */
    protected FileConfiguration getConfig() {
        return NerdPerf.PLUGIN.getConfig();
    }

    // ------------------------------------------------------------------------
    /**
     * Return the plugin's Logger.
     *
     * @return the plugin's Logger.
     */
    protected Logger getLogger() {
        return NerdPerf.PLUGIN.getLogger();
    }
} // class Configuration