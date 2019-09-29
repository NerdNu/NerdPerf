NerdPerf
=========
A Bukkit plugin to gather Minecraft-related performance statistics.
Statistics are returned as a JSON object.


Features
--------
`NerdPerf` accepts TCP clients on a configurable address and port and
returns the following statistics as a single JSON object:

 * Overall:
   * `tps` - The ticks per second, up to 20.
   * `players` - The number of connected players.
   * `memory.used` - Used heap space in MB.
   * `memory.max` - Allocated heap space in MB.
   * `memory.percent` - Memory usage expressed as a percentage of the heap.
 * For each world (`<worldname>`) configured for statistics gathering:
   * `worlds.<worldname>.hoppers` - The number of hopper blocks in the world.
   * `worlds.<worldname>.view_distance` - The view distance in the world.
   * `worlds.<worldname>.chunks` - The number of loaded chunks.
   * `worlds.<worldname>.entities.<type>` - The number of entities of type
     `<type>` in the world named `<worldname>`.
     
Counting entities and hoppers is time consuming. `NerdPerf` spreads these
counting activities over multiple server ticks and imposes a configurable upper
limit on the time spent counting in any one tick.

No authentication or authorisation of client connections is performed.
Therefore, it is advisable to configure `NerdPerf` to bind `localhost` (the
default) rather than an externally accessible IP address if you are concerned
about the performance impact of unrestricted statistics queries.


Testing
-------
You can test `NerdPerf` by using `netcat` to retrieve the JSON object and 
formatting the object as text using `jq`:

```
$ nc localhost 12345 | jq .
{
  "tps": 19.988005,
  "players": 4,
  "worlds": {
    "world": {
      "hoppers": 582,
      "view_distance": 10,
      "chunks": 1563,
      "entities": {
        "IRON_GOLEM": 0,
        "PIG_ZOMBIE": 0,
        "UNKNOWN": 0,
        "MINECART_FURNACE": 0,
        "WOLF": 14,
        "MINECART_MOB_SPAWNER": 0,
        "TIPPED_ARROW": 0,
        "BAT": 1,
        "PIG": 15,

        ... etc ...

      }
    }
  },
  "memory": {
    "percent": 11.668141157967169,
    "used": 1187,
    "max": 10173
  }
}
```

On some Linux distributions, you may need to use the `--recv-only` flag to make
`netcat` disconnect when the server closes the socket.

You can extract specific statistics by specifying a path to `jq`:
```
$ nc localhost 12345 | jq .worlds.world.hoppers
582
```


Configuration
-------------
 * `debug.config` - If `true`, log the configuration to the console when it is
   loaded.
 * `debug.overhead` - If `true`, log elapsed time when computing metrics.
 * `debug.queries` - If `true`, log client connections to the query server.
 * `debug.counts` - If `true`, log progress (number of hoppers or entites
   counted in one counting step).  Multiple steps may run in a given server
   tick, depending on the configured counting task time limit.
 * `bind.address` - The address to bind the server socket to.
 * `bind.port` - The port number to listen on.
 * `task-time-limit-millis` - The nominal maximum run time of the counting task
   during a single server tick, measured in milliseconds.  Note that the task
   repeatedly runs *counting steps* until the maximum run time is exceeded.  The
   counting steps synchronously count a configurable number of objects
   (entities or hoppers) before returning control to the task.  Therefore, if
   a step typically takes a large fraction of the task time limit to run, then
   the task may run over time by that fraction.  If the counting task regularly
   exceeds `task-time-limit-millis` by a large amount, consider reducing 
   `batch.entities` or `batch.chunks` to allow more exact enforcement of the
   time limit.
 * `batch.entities` - The number of entities to count in one step before
   checking whether the elapsed time has exceeded the limit.
 * `batch.chunks` - The number of chunks in which to count hoppers before
   checking whether the elapsed time has exceeded the limit.
 * `worlds` - A list of the names of worlds where metrics should be gathered.


Commands
--------
 * `/nerdperf reload` - Reload the configuration.
 * `/lag` - Show TPS, used and allocated heap sizes in MB.


Permissions
-----------
 * `nerdperf.admin` - Permission to use `/nerdperf`.
 * `nerdperf.lag` - Permission to us `/lag`.

