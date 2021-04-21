package fr.uge.chatos.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.chatos.packet.Packet;
import fr.uge.chatos.packet.PCData;
import fr.uge.chatos.reader.*;
import fr.uge.chatos.visitor.ServerPacketVisitor;

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
     * This class represents a context for one private or public connection.
     */
    public static class Context {
        private final SocketChannel socket;
        private final SelectionKey key;
        private final Server server;
        private final ByteBuffer bufferIn = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        private final Queue<ByteBuffer> queue = new LinkedList<>();
        private final ServerPacketReader serverPacketReader = new ServerPacketReader();
        private final ServerPacketVisitor visitor;
        private boolean authenticated = false;
        private String login;
        private boolean closed;

        private Context(Server server, SelectionKey key) {
            this.key = Objects.requireNonNull(key);
            this.socket = (SocketChannel) key.channel();
            this.server = Objects.requireNonNull(server);
            visitor = new ServerPacketVisitor(server, this);
        }

        /**
         * Process the content of {@code bufferIn}.
         * Get the first byte and check what action needs to be done.
         * <p>
         * Note: {@code bufferIn} is in <b>write-mode</b> before and after the call.
         * </p>
         */
        void processIn() {
            if (authenticated) {
                treatPacket(new PCData(bufferIn, login));
            } else {
                for (;;) {
                    var status = serverPacketReader.process(bufferIn);
                    switch (status) {
                        case ERROR -> silentlyClose();
                        case REFILL -> { return; }
                        case DONE -> {
                            var packet = serverPacketReader.get();
                            serverPacketReader.reset();
                            treatPacket(packet);
                        }
                    }
                }
            }
        }

        /**
         * Updates this context by save his login and indicating that he is authenticated.
         * <p>
         *     Note : to be used only for private connections.
         * </p>
         *
         * @param login the login of the client
         */
        void successfulAuthentication(String login) {
            authenticated = true;
            this.login = login;
        }

        /**
         * Process the content of {@code bufferOut}.
         * <p>
         * Note: {@code bufferOut} is in <b>write-mode</b> before and after the call.
         * </p>
         */
        void processOut() {
            while (!queue.isEmpty()) {
                var buffer = queue.peek();
                if (bufferOut.remaining() >= buffer.remaining()) {
                    bufferOut.put(buffer);
                    queue.poll();
                } else {
                    break;
                }
            }
        }

        /**
         * Performs the read action on {@code socket}.
         * <p>
         * Note: {@code bufferIn} and {@code bufferOut} are in <b>write-mode</b> before
         * and after the call.
         * </p>
         *
         * @throws IOException If some other I/O error occurs.
         */
        void doRead() throws IOException {
            if (socket.read(bufferIn) == -1) {
                closed = true;
            }
            processIn();
            updateInterestOps();
        }

        /**
         * Performs the write action on {@code socket}.
         * <p>
         * Note: {@code bufferIn} and {@code bufferOut} are in <b>write-mode</b> before
         * and after the call.
         * </p>
         * @throws IOException If some other I/O error occurs.
         */
        void doWrite() throws IOException {
            bufferOut.flip();
            socket.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        /**
         * Returns the current selectionKey of this context;
         *
         * @return The key.
         */
        public SelectionKey getKey() {
            return key;
        }

        /**
         * Returns the current {@code login} of this context.
         *
         * @return The current {@code login}.
         */
        public String getLogin() {
            return login;
        }

        /**
         * Adds a message to the queue and process the content of {@code bufferOut}.
         *
         * @param buffer The buffer to send.
         */
        void queueMessage(ByteBuffer buffer) {
            queue.add(buffer);
            processOut();
            updateInterestOps();
        }

        /**
         * Updates the {@code login}, only if the current {@code login} is null.
         * <p>
         * Note : We are not allowed to change our {@code login}.
         * </p>
         *
         * @param login The new login link to this context.
         */
        public void setLogin(String login) {
            if (this.login == null) {
                this.login = Objects.requireNonNull(login);
            }
        }

        /**
         * Try to close the socket. If an exception is thrown, it is ignored.
         */
        void silentlyClose() {
            try {
                socket.close();
            } catch (IOException ignored) { }
        }

        /**
         *
         * @param packet
         */
        void treatPacket(Packet packet) {
            packet.accept(visitor);
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers.
         * <p>
         * Note: {@code bufferIn} and {@code bufferOut} are in <b>write-mode</b> before
         * and after the call. {@code process} need to be called just before this method.
         * </p>
         */
        void updateInterestOps() {
            var interestOps = 0;
            if (!closed && bufferIn.hasRemaining()) {
                interestOps |= SelectionKey.OP_READ;
            }
            if (bufferOut.position() != 0) {
                interestOps |= SelectionKey.OP_WRITE;
            }
            if (interestOps == 0) {
                silentlyClose();
                return;
            }
            key.interestOps(interestOps);
        }
    }

    /**
     * This class represents a private connection between two clients.
     * <p>
     * A private connection have a unique ID.
     */
    public static class PrivateConnection {
        private final HashMap<String, Context> privateSockets = new HashMap<>();
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
        public void updateOneContext(String login, Context context) {
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

    static final int MAX_BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final ServerSocketChannel socketPublic;
    private final ServerSocketChannel socketPrivate;
    private SelectionKey privateKey;
    private SelectionKey publicKey;
    private final Selector selector;
    private final HashSet<String> logins = new HashSet<>(); // à changer si thread
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

    public void registerNewPublicConnection(String login, SelectionKey key) {
        publicConnections.put(login, key);
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

    private boolean acceptNewClient(String login) {
        return logins.add(Objects.requireNonNull(login));
    }

    private void removeOneClient(String login) {
        logins.remove(Objects.requireNonNull(login));
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
                clientKey.attach(new Context(this, clientKey));
                return;
            }
        } else if (key.equals(privateKey)) {
            if ((sc = socketPrivate.accept()) != null) {
                sc.configureBlocking(false);
                var clientKey = sc.register(selector, SelectionKey.OP_READ);
                clientKey.attach(new Context(this, clientKey));
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
            printKeys();
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
	        var context = (Context) a.attachment();
            context.queueMessage(packet.asByteBuffer()); // ne peut pas être null
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
        var context = (Context) key.attachment();
        context.queueMessage(packet.asByteBuffer());
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


    ///////////////
    // A retirer //
    ///////////////


    private String interestOpsToString(SelectionKey key){
        if (!key.isValid()) {
            return "CANCELLED";
        }
        int interestOps = key.interestOps();
        ArrayList<String> list = new ArrayList<>();
        if ((interestOps&SelectionKey.OP_ACCEPT)!=0) list.add("OP_ACCEPT");
        if ((interestOps&SelectionKey.OP_READ)!=0) list.add("OP_READ");
        if ((interestOps&SelectionKey.OP_WRITE)!=0) list.add("OP_WRITE");
        return String.join("|",list);
    }

    public void printKeys() {
        Set<SelectionKey> selectionKeySet = selector.keys();
        if (selectionKeySet.isEmpty()) {
            System.out.println("The selector contains no key : this should not happen!");
            return;
        }
        System.out.println("The selector contains:");
        for (SelectionKey key : selectionKeySet){
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tKey for ServerSocketChannel : "+ interestOpsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tKey for Client "+ remoteAddressToString(sc) +" : "+ interestOpsToString(key));
            }
        }
    }

    private String remoteAddressToString(SocketChannel sc) {
        try {
            return sc.getRemoteAddress().toString();
        } catch (IOException e){
            return "???";
        }
    }

    public void printSelectedKey(SelectionKey key) {
        SelectableChannel channel = key.channel();
        if (channel instanceof ServerSocketChannel) {
            System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
        } else {
            SocketChannel sc = (SocketChannel) channel;
            System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
        }
    }

    private String possibleActionsToString(SelectionKey key) {
        if (!key.isValid()) {
            return "CANCELLED";
        }
        ArrayList<String> list = new ArrayList<>();
        if (key.isAcceptable()) list.add("ACCEPT");
        if (key.isReadable()) list.add("READ");
        if (key.isWritable()) list.add("WRITE");
        return String.join(" and ",list);
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
