package fr.uge.chatos.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.chatos.context.Context;
import fr.uge.chatos.context.ServerContext;
import fr.uge.chatos.packet.Packet;


/**
 * This class implements an TCP server.
 * <p>
 * The server has two different ports :
 * <ul>
 *     <li>one for public connections</li>
 *     <li>one for private connections</li>
 * </ul>
 */
public class Server {

    /**
     * This class represents a private connection between two clients.
     * <p>
     * A private connection have a unique ID.
     */
    public static class PrivateConnection {
        private final HashMap<String, ServerContext> privateSockets = new HashMap<>();
        private final long id;
        private int nbConnection = 0;

        /**
         * Creates a new {@code PrivateConnection} with the given initial values.
         *
         * @param firstLogin the login of the first client
         * @param secondLogin the login of the second client
         * @param id the ID of this new private connection
         */
        PrivateConnection(String firstLogin, String secondLogin, long id) {
            privateSockets.put(Objects.requireNonNull(firstLogin), null);
            privateSockets.put(Objects.requireNonNull(secondLogin), null);
            this.id = id;
        }

        /**
         * Returns the {@link java.nio.channels.SelectionKey} of the {@code context} associate
         * to the specified {@code login}.
         *
         * @param login the {@code login} of the client
         * @return the current key
         */
        SelectionKey getKey(String login) {
            Objects.requireNonNull(login);
            var context = privateSockets.get(login);
            return context.getKey();
        }

        /**
         * Returns the number of clients who have connected on this private connection.
         *
         * @return the number of clients
         */
        public int getNbConnection() {
            return nbConnection;
        }

        /**
         * Returns all the usernames of connected clients on this private connection.
         *
         * @return all the usernames in a set
         */
        public Set<String> getPseudos() {
            return privateSockets.keySet();
        }

        /**
         * Returns the ID associated to this private connection.
         *
         * @return the ID of this private connection
         */
        public long getId() {
            return id;
        }

        /**
         * Checks if this private connection can still accept new clients
         * and increments the connection counter if so.
         * <p>
         * Note : a private connection can only accept a maximum of two clients.
         *
         * @return {@code true} if this private connection can still accept new clients.
         */
        public boolean addNewConnection() {
            if (nbConnection < 2) {
                nbConnection++;
                return true;
            }
            return false;
        }

        /**
         * Updates the {@code context} associate with the specified {@code login}.
         *
         * @param login the {@code login} of the client
         * @param context the new {@code context}
         */
        public void updateOneContext(String login, ServerContext context) {
            Objects.requireNonNull(login);
            Objects.requireNonNull(context);
            privateSockets.put(login, context);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PrivateConnection that = (PrivateConnection) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final ServerSocketChannel socketPublic;
    private final ServerSocketChannel socketPrivate;
    private SelectionKey privateKey;
    private SelectionKey publicKey;
    private final Selector selector;
    private final HashSet<String> logins = new HashSet<>();
    private final int privatePort;
    private final HashMap<String, SelectionKey> publicConnections = new HashMap<>();
    private final HashMap<String, List<PrivateConnection>> privateConnections = new HashMap<>();

    public Server(int port, int privatePort) throws IOException {
        if (port <= 0 || privatePort < 0) {
            throw new IllegalArgumentException("port number can't be negative");
        }
        this.privatePort = privatePort;
        selector = Selector.open();
        socketPublic = ServerSocketChannel.open();
        socketPublic.bind(new InetSocketAddress(port));
        socketPrivate = ServerSocketChannel.open();
        socketPrivate.bind(new InetSocketAddress(privatePort));
    }

    public long getNewId() {
        // TODO : à optimiser
        var ids = new ArrayList<Long>();
        var val = privateConnections.values();
        for (var list : val) {
            for (var pc : list) {
                ids.add(pc.id);
            }
        }
        long id;
        do {
            id = ThreadLocalRandom.current().nextLong();
        }
        while (ids.contains(id));
        return id;
    }

    public boolean registerNewPublicConnection(String login, SelectionKey key) {
        if (logins.add(Objects.requireNonNull(login))) {
            publicConnections.put(login, key);
            return true;
        }
        return false;
    }

    public Optional<PrivateConnection> getPrivateConnection(String pseudo, long id) {
        var a = privateConnections.get(pseudo);
        for (var pc : a) {
            if (pc.id == id) {
                return Optional.of(pc);
            }
        }
        return Optional.empty();
    }

    public Optional<PrivateConnection> getPrivateConnection(String pseudoA, String pseudoB) {
        // on vérifie que d'un côté, ça suffit sauf gros bug
        var a = privateConnections.get(pseudoA);
        for (var pc : a) {
            if (pc.privateSockets.containsKey(pseudoB)) {
                return Optional.of(pc);
            }
        }
        return Optional.empty();
    }

    public Optional<PrivateConnection> getPrivateConnection(String pseudo, SelectionKey key) {
        var a = privateConnections.get(pseudo);
        for (var pc : a) {
            if (pc.getKey(pseudo).equals(key)) {
                return Optional.of(pc);
            }
        }
        return Optional.empty();
    }

    /**
     * Updates a {@code PrivateConnection} by updating client logins.
     *
     * @param privateConnection the private connection to update
     */
    public void successfulAuthentication(PrivateConnection privateConnection) {
        Objects.requireNonNull(privateConnection);
        for (var entry : privateConnection.privateSockets.entrySet()) {
            entry.getValue().successfulAuthentication(entry.getKey());
        }
    }

    /**
     * Returns the private port of this server.
     *
     * @return the private port
     */
    public int getPrivatePort() {
        return privatePort;
    }

    /**
     * Checks if a {@code PrivateConnection} exists between the two clients
     * specified by their logins.
     *
     * @param firstLogin the {@code login} of the first client
     * @param secondLogin the {@code login} of the second client
     * @return {@code true} if a {@code PrivateConnection} exists
     */
    public boolean checkIfPrivateConnectionExists(String firstLogin, String secondLogin) {
        Objects.requireNonNull(firstLogin);
        Objects.requireNonNull(secondLogin);

        var pcList = privateConnections.get(firstLogin);
        if (pcList != null) {
            for (var pc : pcList) {
                if (pc.privateSockets.containsKey(secondLogin)) {
                    return true;
                }
            }
        } else {
            pcList = privateConnections.get(secondLogin);
            if (pcList != null) {
                for (var pc : pcList) {
                    if (pc.privateSockets.containsKey(firstLogin)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns a {@link java.util.function.BiFunction} that tries to merge {@code PrivateConnection} that have a common client.
     *
     * @param privateConnection the private connection to merge
     * @return a {@code BiFunction}
     */
    private BiFunction<String, List<PrivateConnection>, List<PrivateConnection>> computePrivateConnections(PrivateConnection privateConnection) {
        return (key, value) -> {
            if (value != null) {
                value.add(privateConnection);
            } else {
                value = new ArrayList<>(Arrays.asList(privateConnection));
            }
            return value;
        };
    }

    /**
     * Creates a new {@code PrivateConnection} with the given values and
     * register it.
     *
     * @param id the ID of this new {@code PrivateConnection}.
     * @param firstLogin the {@code login} of the first client
     * @param secondLogin the {@code login} of the second client
     */
    public void registerNewPrivateConnection(long id, String firstLogin, String secondLogin) {
        var pc = new PrivateConnection(firstLogin, secondLogin, id);
        privateConnections.compute(firstLogin, computePrivateConnections(pc));
        privateConnections.compute(secondLogin, computePrivateConnections(pc));
    }

    public void deletePrivateConnection(String firstLogin, String secondLogin) {
        var a = privateConnections.get(firstLogin);
        a.removeIf(pc -> pc.privateSockets.containsKey(secondLogin));
        var b = privateConnections.get(secondLogin);
        b.removeIf(pc -> pc.privateSockets.containsKey(firstLogin));
    }

    /**
     *
     * @param key the SelectionKey
     * @throws IOException If some other I/O error occurs.
     */
    private void doAccept(SelectionKey key) throws IOException {
        SocketChannel sc;
        if (key.equals(publicKey)) {
            if ((sc = socketPublic.accept()) != null) {
                sc.configureBlocking(false);
                var clientKey = sc.register(selector, SelectionKey.OP_READ);
                clientKey.attach(new ServerContext(clientKey, this));
                return;
            }
        } else if (key.equals(privateKey)) {
            if ((sc = socketPrivate.accept()) != null) {
                sc.configureBlocking(false);
                var clientKey = sc.register(selector, SelectionKey.OP_READ);
                clientKey.attach(new ServerContext(clientKey, this));
                return;
            }
        }
        logger.info("The selector was wrong.");
    }

    /**
     * Start the main server loop.
     *
     * @throws IOException If some other I/O error occurs.
     */
    public void launch() throws IOException {
        logger.info("Server started...");
        socketPublic.configureBlocking(false);
        publicKey = socketPublic.register(selector, SelectionKey.OP_ACCEPT);

        socketPrivate.configureBlocking(false);
        privateKey = socketPrivate.register(selector, SelectionKey.OP_ACCEPT);

        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    /**
     * Try to close the socket link to the specified {@code key}.
     * If an exception is thrown, it is ignored.
     */
    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException ignored) { }
    }

    /**
     *
     * @param key If some other I/O error occurs.
     */
    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch(IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO,"Connection closed with client due to IOException", e);
            silentlyClose(key);
        }
    }

    /**
     * Broadcasts a {@link fr.uge.chatos.packet.Packet} to all connected clients.
     *
     * @param packet the packet to send
     */
	public void publicBroadcast(Packet packet) {
	    for (var a : publicConnections.values()) {
	        var context = (Context) a.attachment(); // ne peut pas être null
            context.queueMessage(packet.asByteBuffer());
        }
	}

    /**
     * Broadcasts a {@link fr.uge.chatos.packet.Packet} to a {@code client} specified
     * by his {@code login}.
     *
     * @param packet the packet to send
     * @param login the login of the recipient client
     */
    public void privateBroadcast(Packet packet, String login) {
        var key = publicConnections.get(login);
        if (key != null) {
            var context = (Context) key.attachment();
            context.queueMessage(packet.asByteBuffer());
        } // TODO : envoyer un paquet d'erreur au client lui indiquant que le pseudo n'existe pas
    }

    /**
     * Broadcasts a {@link fr.uge.chatos.packet.Packet} to a {@code client}
     * via the {@link fr.uge.chatos.server.Server.PrivateConnection}.
     * <p>
     *     Note : You must specify the sender's login.
     * </p>
     *
     * @param packet the packet to send
     * @param privateConnection the private connection between the two clients
     * @param senderLogin the login of the sender
     */
    public void privateConnectionBroadcast(Packet packet, PrivateConnection privateConnection, String senderLogin) {
        for (var pseudo : privateConnection.privateSockets.keySet()) {
            if (!pseudo.equals(senderLogin)) {
                var key = privateConnection.getKey(pseudo); // récupération de la clef du destinataire
                var context = (Context) key.attachment(); // ne peut pas être null
                context.queueMessage(packet.asByteBuffer());
                return;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: server <public_port> <private_port>");
            return;
        }

        int publicPort, privatePort;
        try {
            publicPort = Integer.parseInt(args[0]);
            privatePort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Port number must be an Integer.");
            return;
        }

        if (publicPort == privatePort) {
            throw new IllegalArgumentException("Public and private ports cannot be the same");
        }

        // Start server
        new Server(publicPort, privatePort).launch();
    }
}
