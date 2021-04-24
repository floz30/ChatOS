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
 *
 */
public class Server {

    public static class Context {
        protected final SocketChannel socket;
        protected final SelectionKey key;
        protected final Server server;
        protected final ByteBuffer bufferIn = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        protected final ByteBuffer bufferOut = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        protected final Queue<ByteBuffer> queue = new LinkedList<>();
        protected final ServerPacketReader serverPacketReader = new ServerPacketReader();
        protected final ServerPacketVisitor visitor;
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
                treatPacket(new PCData(bufferIn, login)); //visit
                
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

        void successfulAuthentication(String pseudo) {
            authenticated = true;
            login = pseudo;
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

    public static class PC {
        private int nbConnection = 0;
        private final long id;
        private final HashMap<String, Context> privateSockets = new HashMap<>();

        PC(String pseudoA, String pseudoB, long id) {
            privateSockets.put(pseudoA, null);
            privateSockets.put(pseudoB, null);
            this.id = id;
        }

        SelectionKey getKey(String pseudo) {
            Objects.requireNonNull(pseudo);
            var context = privateSockets.get(pseudo);
            return context.getKey();
        }

        public int getNbConnection() {
            return nbConnection;
        }

        public Set<String> getPseudos() {
            return privateSockets.keySet();
        }

        public long getId() {
            return id;
        }

        public boolean addNewConnection() {
            if (nbConnection < 2) {
                nbConnection++;
                return true;
            }
            return false;
        }

        public void updateOneContext(String pseudo, Context context) {
            Objects.requireNonNull(pseudo);
            Objects.requireNonNull(context);
            privateSockets.put(pseudo, context);
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
    private final HashMap<String, List<PC>> privateConnections = new HashMap<>();

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

    public Optional<PC> getPrivateConnection(String pseudo, long id) {
        var a = privateConnections.get(pseudo);
        for (var pc : a) {
            if (pc.id == id) {
                return Optional.of(pc);
            }
        }
        return Optional.empty();
    }

    public Optional<PC> getPrivateConnection(String pseudoA, String pseudoB) {
        // on vérifie que d'un côté, ça suffit sauf gros bug
        var a = privateConnections.get(pseudoA);
        for (var pc : a) {
            if (pc.privateSockets.containsKey(pseudoB)) {
                return Optional.of(pc);
            }
        }
        return Optional.empty();
    }

    public Optional<PC> getPrivateConnection(String pseudo, SelectionKey key) {
        var a = privateConnections.get(pseudo);
        for (var pc : a) {
            if (pc.getKey(pseudo).equals(key)) {
                return Optional.of(pc);
            }
        }
        return Optional.empty();
    }

    public void successfulAuthentication(PC pc) {
        Objects.requireNonNull(pc);

        for (var entry : pc.privateSockets.entrySet()) {
            entry.getValue().successfulAuthentication(entry.getKey());
            //context.successfulAuthentication(pseudo);
        }
    }

    public int getPrivatePort() {
        return privatePort;
    }

    public boolean checkIfPrivateConnectionExists(String pseudoA, String pseudoB) {
        var a = privateConnections.get(pseudoA);
        if (a != null) {
            for (var pc : a) {
                if (pc.privateSockets.containsKey(pseudoB)) {
                    return true;
                }
            }
        } else {
            var b = privateConnections.get(pseudoB);
            if (b != null) {
                for (var pc : b) {
                    if (pc.privateSockets.containsKey(pseudoA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private BiFunction<String, List<PC>, List<PC>> computePrivateConnections(PC pc) {
        return (key, value) -> {
            if (value != null) {
                value.add(pc);
            } else {
                value = new ArrayList<>(Arrays.asList(pc));
            }
            return value;
        };
    }

    public void registerNewPrivateConnection(long id, String a, String b) {
        var pc = new PC(a, b, id);
        privateConnections.compute(a, computePrivateConnections(pc));
        privateConnections.compute(b, computePrivateConnections(pc));
    }

    private boolean acceptNewClient(String login) {
        return logins.add(Objects.requireNonNull(login));
    }

    private void removeOneClient(String login) {
        logins.remove(Objects.requireNonNull(login));
    }

    /**
     *
     * @param key
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
     * Send a message to all client connected.
     *
     * @param packet Message to send.
     */
	public void publicBroadcast(Packet packet) {
	    for (var a : publicConnections.values()) {
	        var context = (Context) a.attachment();
            context.queueMessage(packet.asByteBuffer()); // ne peut pas être null
        }
	}

    /**
     * Send a message to the specified client.
     *
     * @param packet Message to send.
     *
     */
    public void privateBroadcast(Packet packet, String recipientLogin) {
        var key = publicConnections.get(recipientLogin);
        var context = (Context) key.attachment();
        context.queueMessage(packet.asByteBuffer());
    }

    public void privateConnectionBroadcast(Packet packet, PC pc, String senderLogin) {
        for (var pseudo : pc.privateSockets.keySet()) {
            if (!pseudo.equals(senderLogin)) {
                var key = pc.getKey(pseudo); // récupération de la clef du destinataire
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
}
