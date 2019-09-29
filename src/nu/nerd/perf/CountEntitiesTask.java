package nu.nerd.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.json.simple.JSONObject;

// ----------------------------------------------------------------------------
/**
 * A time-limited task that counts entities in a specified world and adds the
 * result to a JSONObject.
 */
public class CountEntitiesTask implements BooleanSupplier {
    // ------------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param loadedChunks an array of all loaded chunks in the World to be
     *        counted.
     * @param worldObject the JSONObject (corresponding to the World) where the
     *        results will be stored.
     */
    public CountEntitiesTask(World world, JSONObject entitiesObject) {
        _world = world;
        _index = 0;
        _counts = new int[EntityType.values().length];
        _entitiesObject = entitiesObject;
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
        if (_entities == null) {
            List<Entity> entities = _world.getEntities();
            _entities = (entities instanceof ArrayList) ? (ArrayList<Entity>) entities
                                                       : new ArrayList<Entity>(entities);
            return true;
        }

        int startIndex = _index;
        int iterations = Math.min(_entities.size() - _index, NerdPerf.CONFIG.BATCH_ENTITIES);
        for (int i = 0; i < iterations; ++i) {
            Entity entity = _entities.get(_index++);
            ++_counts[entity.getType().ordinal()];
        }

        if (NerdPerf.CONFIG.DEBUG_COUNTS) {
            NerdPerf.PLUGIN.getLogger().info("Entities count in " + _world.getName() +
                                              " progressed " + (_index - startIndex) + " entities");
        }

        boolean more = (_index < _entities.size());
        if (!more) {
            for (EntityType entityType : EntityType.values()) {
                _entitiesObject.put(entityType.name(), _counts[entityType.ordinal()]);
            }
            _world = null;
            _entities = null;
        }

        return more;
    }

    // ------------------------------------------------------------------------
    /**
     * Maximum number of chunks to count in any one call to process().
     */
    static final int MAX_ITERATIONS = 100;

    /**
     * The World whose entities will be counted.
     */
    protected World _world;

    /**
     * Entities to process.
     */
    protected ArrayList<Entity> _entities;

    /**
     * Index (into _chunks) of next Entity.
     */
    protected int _index;

    /**
     * Counts as a lookup table, indexed by the EntityType ordinal.
     */
    protected int[] _counts;

    /**
     * JSONObject where results will be stored.
     */
    protected JSONObject _entitiesObject;
} // class CountEntitiesTask