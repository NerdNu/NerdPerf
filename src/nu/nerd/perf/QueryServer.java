package nu.nerd.perf;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.json.simple.JSONObject;

// ----------------------------------------------------------------------------
/**
 * TCP server that accepts query clients.
 */
public class QueryServer extends Thread {
    // ------------------------------------------------------------------------
    /**
     * To the configured listening address and port.
     */
    public void bind() throws UnknownHostException, IOException {
        close();
        InetAddress address;
        try {
            address = InetAddress.getByName(NerdPerf.CONFIG.BIND_ADDRESS);
        } catch (UnknownHostException ex) {
            getLogger().severe("Unknown host: " + NerdPerf.CONFIG.BIND_ADDRESS + "; defaulting to localhost.");
            address = InetAddress.getLocalHost();
        }
        _listener = new ServerSocket(NerdPerf.CONFIG.BIND_PORT, 1, address);
    }

    // ------------------------------------------------------------------------
    /**
     * Close the server socket to stop accepting further clients.
     */
    public void close() {
        try {
            if (_listener != null) {
                _listener.close();
            }
        } catch (IOException ex) {
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Accept and process clients until stopped.
     */
    @Override
    public void run() {
        while (!_listener.isClosed()) {
            try (
            Socket client = _listener.accept();
            Writer writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));) {
                if (NerdPerf.CONFIG.DEBUG_QUERIES) {
                    getLogger().info("Accepted client: " + client.getInetAddress() + ":" + client.getPort());
                }

                Bukkit.getScheduler().scheduleSyncDelayedTask(NerdPerf.PLUGIN, () -> NerdPerf.PLUGIN.requestMetrics());
                JSONObject results = NerdPerf.PLUGIN.awaitMetrics();
                if (results == null) {
                    getLogger().info("Query server stopping.");
                } else {
                    results.writeJSONString(writer);
                }
                writer.close();
                client.close();
                if (NerdPerf.CONFIG.DEBUG_QUERIES) {
                    getLogger().info("Results sent.");
                }
            } catch (IOException ex) {
                if (_listener != null && _listener.isClosed()) {
                    getLogger().info("Query server stopping.");
                } else {
                    getLogger().severe("Query server: " + ex.getMessage());
                }
            }
        }
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

    // ------------------------------------------------------------------------
    /**
     * The server socket.
     */
    protected ServerSocket _listener;
} // class QueryServer