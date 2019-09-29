package nu.nerd.stats;

import java.util.function.BooleanSupplier;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.json.simple.JSONObject;

// ----------------------------------------------------------------------------
/**
 * A time-limited task that counts hoppers in a specified world and adds the
 * result to a JSONObject.
 */
public class CountHoppersTask implements BooleanSupplier {
    // ------------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param world the World to be counted.
     * @param jsonWorld the JSONObject (corresponding to the World) where the
     *        results will be stored.
     */
    public CountHoppersTask(World world, JSONObject jsonWorld) {
        _world = world;
        _index = 0;
        _count = 0;
        _jsonWorld = jsonWorld;
    }

    // ------------------------------------------------------------------------
    /**
     * @see java.util.function.BooleanSupplier#getAsBoolean()
     *
     *      This method must return true if there is more work to be done.
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean getAsBoolean() {
        if (_chunks == null) {
            _chunks = _world.getLoadedChunks();
            return true;
        }

        int startIndex = _index;
        int iterations = Math.min(_chunks.length - _index, NerdPerf.CONFIG.BATCH_CHUNKS);
        for (int i = 0; i < iterations; ++i) {
            Chunk chunk = _chunks[_index++];
            for (BlockState tileEntity : chunk.getTileEntities()) {
                if (tileEntity instanceof Hopper) {
                    ++_count;
                }
            }
        }

        if (NerdPerf.CONFIG.DEBUG_COUNTS) {
            NerdPerf.PLUGIN.getLogger().info("Hopper count in " + _world.getName() +
                                              " progressed " + (_index - startIndex) + " chunks");
        }

        boolean more = (_index < _chunks.length);
        if (!more) {
            _jsonWorld.put("chunks", _chunks.length);
            _jsonWorld.put("hoppers", _count);
            _chunks = null;
        }

        return more;
    }

    // ------------------------------------------------------------------------
    /**
     * The World whose hoppers will be counted.
     */
    protected World _world;

    /**
     * Array of loaded chunks in the World.
     */
    protected Chunk[] _chunks;

    /**
     * Index (into _chunks) of next Chunk to count.
     */
    protected int _index;

    /**
     * Total number of hoppers counted.
     */
    protected int _count;

    /**
     * JSONObject where results will be stored.
     */
    protected JSONObject _jsonWorld;
} // class CountHoppersTask