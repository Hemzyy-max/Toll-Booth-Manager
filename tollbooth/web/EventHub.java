package tollbooth.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================================
 *  FILE    : EventHub.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  THIS IS THE "REAL TIME" PART OF THE PROJECT.
 *
 *  It keeps every open browser connection in a list and pushes small JSON
 *  messages to all of them the moment something happens (a payment, a login, a
 *  notification). The browser receives the message immediately, without asking
 *  for it again.
 *
 *  The technique is called SERVER SENT EVENTS (SSE) and it is part of plain
 *  HTML5, so no extra library is needed :
 *
 *      browser :  new EventSource('/api/events?token=...')
 *      server  :  data: {"type":"payment", ...}\n\n
 *
 *  OOP CONCEPTS
 *   - COLLECTIONS : CopyOnWriteArrayList - a list that can be read safely while
 *     other threads add or remove clients.
 *   - MULTITHREADING : every connected browser is fed by its own thread, and
 *     data flows through a producer/consumer queue (push() -> queue -> writer).
 *   - ENCAPSULATION : the client list is private, nobody outside can break it.
 * ============================================================================
 */
public class EventHub {

    /** How many events are kept so a reconnecting browser can catch up. */
    private static final int HISTORY_SIZE = 100;

    /** If a slow browser falls this far behind, it is disconnected. */
    private static final int MAX_QUEUE_PER_CLIENT = 250;

    /** One connected browser. */
    public static final class Client {
        private final String clientId;
        private final String username;
        private final User.Role role;
        private final String clientAddress;
        private final long connectedAt;
        private final java.util.concurrent.BlockingQueue<byte[]> queue =
                new java.util.concurrent.LinkedBlockingQueue<>(MAX_QUEUE_PER_CLIENT);
        private final OutputStream output;
        private volatile boolean disconnected;
        private volatile long eventsSent;

        Client(String clientId, String username, User.Role role, String clientAddress, OutputStream output) {
            this.clientId = clientId;
            this.username = username;
            this.role = role;
            this.clientAddress = clientAddress;
            this.output = output;
            this.connectedAt = System.currentTimeMillis();
        }

        public String getClientId() {
            return clientId;
        }

        public String getUsername() {
            return username;
        }

        public User.Role getRole() {
            return role;
        }

        public String getClientAddress() {
            return clientAddress;
        }

        public long getConnectedAt() {
            return connectedAt;
        }

        public long getEventsSent() {
            return eventsSent;
        }

        public boolean isDisconnected() {
            return disconnected;
        }

        void offer(byte[] frame) {
            if (disconnected) {
                return;
            }
            if (!queue.offer(frame)) {
                // The browser is too slow : dropping it is safer than growing
                // the memory of the server without limit.
                disconnected = true;
                return;
            }
            eventsSent++;
        }

        void writeLoop() {
            try {
                output.write((": connected to the toll booth live stream " + clientId + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.flush();
                while (!disconnected) {
                    byte[] frame = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    if (frame == null) {
                        // A comment line every second keeps proxies from closing
                        // an idle connection.
                        output.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                        output.flush();
                        continue;
                    }
                    output.write(frame);
                    output.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Normal : the browser tab was closed.
            } finally {
                disconnected = true;
            }
        }

        void close() {
            disconnected = true;
            try {
                output.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private final List<String> recentFrames = new ArrayList<>();
    private final AtomicLong eventCounter = new AtomicLong();
    private final AtomicLong clientCounter = new AtomicLong();

    /** Registers a new browser connection and starts its writer thread. */
    public Client register(String username, User.Role role, String clientAddress, OutputStream output) {
        String clientId = "client-" + clientCounter.incrementAndGet();
        Client client = new Client(clientId, username, role, clientAddress, output);
        clients.add(client);

        Thread writer = new Thread(client::writeLoop, "sse-" + clientId);
        writer.setDaemon(true);
        writer.start();
        return client;
    }

    /** Removes a browser connection. */
    public void unregister(Client client) {
        if (client == null) {
            return;
        }
        clients.remove(client);
        client.close();
    }

    /**
     * Builds one SSE frame :
     *
     *   id: 12
     *   event: payment
     *   data: {"type":"payment", ...}
     *
     * @param eventName the value the browser listens to with addEventListener
     * @param payload   the JSON object of the event
     */
    private byte[] buildFrame(String eventName, Object payload) {
        long id = eventCounter.incrementAndGet();
        String frame = "id: " + id + "\n"
                + "event: " + eventName + "\n"
                + "data: " + payload.toString() + "\n\n";
        return frame.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Sends an event to every connected browser. */
    public void broadcast(String eventName, Object payload) {
        byte[] frame = buildFrame(eventName, payload);
        rememberFrame(frame);
        for (Client client : clients) {
            client.offer(frame);
        }
    }

    /**
     * Sends an event only to the logged in users of a given role.
     * Example : the audit screen of the ADMIN only.
     */
    public void broadcastToRole(String eventName, Object payload, User.Role role) {
        byte[] frame = buildFrame(eventName, payload);
        rememberFrame(frame);
        for (Client client : clients) {
            if (client.getRole() == role) {
                client.offer(frame);
            }
        }
    }

    /** Sends an event to the browsers of one user only (a private notification). */
    public void sendToUser(String username, String eventName, Object payload) {
        byte[] frame = buildFrame(eventName, payload);
        for (Client client : clients) {
            if (client.getUsername().equalsIgnoreCase(username)) {
                client.offer(frame);
            }
        }
    }

    /**
     * Replays the last events for a browser that has just connected, so that the
     * activity list is not empty after a page refresh.
     */
    public void replayHistory(Client client) {
        for (String frame : snapshotHistory()) {
            client.offer(frame.getBytes(StandardCharsets.UTF_8));
        }
    }

    private synchronized void rememberFrame(byte[] frame) {
        recentFrames.add(new String(frame, StandardCharsets.UTF_8));
        while (recentFrames.size() > HISTORY_SIZE) {
            recentFrames.remove(0);
        }
    }

    private synchronized List<String> snapshotHistory() {
        return new ArrayList<>(recentFrames);
    }

    /** The list of live connections for the admin screen. */
    public Json.JsonArray clientsToJson() {
        Json.JsonArray array = Json.arr();
        for (Client client : clients) {
            array.add(Json.obj()
                    .put("clientId", client.getClientId())
                    .put("username", client.getUsername())
                    .put("role", client.getRole().name())
                    .put("clientAddress", client.getClientAddress())
                    .put("connectedSeconds", (System.currentTimeMillis() - client.getConnectedAt()) / 1000)
                    .put("eventsSent", client.getEventsSent()));
        }
        return array;
    }

    /** Closes every connection with a "shutdown" event (used while stopping). */
    public void closeAll(String reason) {
        broadcast("shutdown", Json.obj().put("message", reason));
        for (Client client : clients) {
            client.close();
        }
        clients.clear();
    }

    public int clientCount() {
        return clients.size();
    }

    /** Removes connections whose browser has gone away. */
    public int cleanupDeadClients() {
        int removed = 0;
        for (Client client : clients) {
            if (client.isDisconnected()) {
                clients.remove(client);
                removed++;
            }
        }
        return removed;
    }

    /** A map of username -> number of open tabs, used by the presence widget. */
    public Map<String, Integer> connectionCountPerUser() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Client client : clients) {
            counts.merge(client.getUsername(), 1, Integer::sum);
        }
        return counts;
    }
}
