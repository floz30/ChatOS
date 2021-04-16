package fr.uge.chatos.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.chatos.reader.*;
import fr.uge.chatos.utils.*;

import static fr.uge.chatos.utils.OpCode.*;

/**
 *
 */
public class Server {

    private static class Packet {
        private final Message message;
        private final ByteBuffer buffer;

        Packet(Message message) {
            this.message = message;
            buffer = null;
        }

        Packet(ByteBuffer buffer) {
            message = null;
            this.buffer = buffer;
        }
    }

    private static abstract class Context {
        protected final SocketChannel socket;
        protected final SelectionKey key;
        protected final Server server;
        protected final ByteBuffer bufferIn = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        protected final ByteBuffer bufferOut = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        protected final Queue<Packet> queue = new LinkedList<>();
        protected final MessageReader messageReader = new MessageReader();
        protected final StringReader stringReader = new StringReader();
        protected final LongReader longReader = new LongReader();
        private boolean closed;

        private Context(SelectionKey key, Server server) {
            this.key = Objects.requireNonNull(key);
            this.socket = (SocketChannel) key.channel();
            this.server = Objects.requireNonNull(server);
        }

        /**
         * Process the content of {@code bufferIn}.
         * Get the first byte and check what action needs to be done.
         * <p>
         * Note: {@code bufferIn} is in <b>write-mode</b> before and after the call.
         * </p>
         */
        abstract void processIn();

        /**
         * Process the content of {@code bufferOut}.
         * <p>
         * Note: {@code bufferOut} is in <b>write-mode</b> before and after the call.
         * </p>
         */
        abstract void processOut();

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
         * Process data of {@code bufferIn} with the correct reader.
         * <p>
         * Note : {@code bufferIn} is in <b>write-mode</b> before and after the call.
         * </p>
         *
         * @param status The function to call to process data of {@code bufferIn}.
         * @param runnable The action to do if data of {@code bufferIn} was successfully processed.
         */
        void processReader(Function<ByteBuffer, Reader.ProcessStatus> status, Runnable runnable) {
            for (;;) {
                switch (status.apply(bufferIn)) {
                    case DONE -> runnable.run();
                    case REFILL -> { return; }
                    case ERROR -> {
                        silentlyClose();
                        return;
                    }
                }
            }
        }

        /**
         * Adds a message to the queue and process the content of {@code bufferOut}.
         *
         * @param packet The buffer to send.
         */
        void queueMessage(Packet packet) {
            queue.add(packet);
            processOut();
            updateInterestOps();
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

    private class PublicContext extends Context {
        private String login;

        public PublicContext(Server server, SelectionKey key) {
            super(key, server);
        }

        @Override
         void processIn() {
            bufferIn.flip();
            var op = bufferIn.get();
            bufferIn.compact();

            switch (op) {
                case CONNECTION_REQUEST: // Demande de connexion
                    processReader(stringReader::process, () -> {
                        var tmp = stringReader.get();
                        if (server.acceptNewClient(tmp)) {
                            login = tmp;
                            sendConfirmation(Packets.ofAcceptConnection());
                            logger.info(login + " is now connected");
                        } else {
                            sendConfirmation(Packets.ofErrorBuffer("The username \""+ login +"\" is already used."));
                        }
                        stringReader.reset();
                    });
                case GENERAL_SENDER: // Message général
                    processReader(stringReader::process, () -> {
                        server.publicBroadcast(new Packet(new Message(login, stringReader.get(), false)));
                        stringReader.reset();
                    });
                    break;
                case PRIVATE_SENDER: // Message privé
                    processReader(messageReader::process, () -> {
                        var message = messageReader.get();
                        server.privateMessage(new Packet(new Message(login, message.getContent(), true)), message.getLogin());
                        messageReader.reset();
                    });
                    break;
                case PRIVATE_CONNECTION_REQUEST_SENDER: // demande de connexion privée
                    processReader(stringReader::process, () -> {
                        var pseudoB = stringReader.get();
                        var id = ThreadLocalRandom.current().nextLong();
                        pcs.put(id, new PC(login, pseudoB));

                        var packet = new Packet(Packets.ofPrivateConnection(login, PRIVATE_CONNECTION_REQUEST_RECEIVER));
                        server.privateMessage(packet, pseudoB);
                        stringReader.reset();
                    });
                    break;
                case PRIVATE_CONNECTION_REPLY: // confirmation connexion privée
                    processReader(stringReader::process, () -> {
                        var pseudoA = stringReader.get(); // pseudo A
                        var confirm = bufferIn.flip().get();
                        bufferIn.compact();

                        var c = pcs.entrySet().stream()
                                .filter(entry -> entry.getValue().privateSockets.containsKey(pseudoA)
                                        && entry.getValue().privateSockets.containsKey(login))
                                .findFirst();

                        if (confirm == 1) { // co privée acceptée
                            if (c.isPresent()) {
                                var value = c.get();
                                // envoi du port
                                server.privateMessage(new Packet(Packets.ofPrivateConnectionSockets(value.getKey(), login, privatePort)), pseudoA);
                                server.privateMessage(new Packet(Packets.ofPrivateConnectionSockets(value.getKey(), pseudoA, privatePort)), login);
                            } else {
                                // erreur : étape 6 non réalisée
                                sendConfirmation(Packets.ofErrorBuffer("Le client \""+ pseudoA +"\" n'a pas fait de demande de connexion privée."));
                            }
                        } else { // co privée refusée
                            c.ifPresent(longPCEntry -> pcs.remove(longPCEntry.getKey())); // suppression de la co privée
                            server.privateMessage(new Packet(Packets.ofErrorBuffer("Le client \""+ login +"\" a refusé votre demande de connexion privée.")), pseudoA);
                            //sendConfirmation(Packets.ofErrorBuffer("Le client \""+ login +"\" a refusé votre demande de connexion privée."));
                        }
                        stringReader.reset();
                    });
                    break;
                default:
                    logger.info("The byte op is unknown ("+ op +").");
                    break;
            }
        }

        private void sendConfirmation(ByteBuffer bb) {
            if (bufferOut.remaining() >= bb.remaining()) {
                bufferOut.put(bb.flip());
            }
            updateInterestOps();
        }

        @Override
         void processOut() {
            if (!queue.isEmpty()) {
                var packet = queue.peek();

                ByteBuffer buffer;
                if (packet.message != null) { // si c'est un Message
                    if (packet.message.isMp()) {
                        buffer = Packets.ofMessageReader(packet.message.getLogin(), packet.message.getContent(), PRIVATE_RECEIVER);
                    } else {
                        buffer = Packets.ofMessageReader(packet.message.getLogin(), packet.message.getContent(), GENERAL_RECEIVER);
                    }
                } else { // sinon pour une connexion privée
                    buffer = packet.buffer;
                }

                if (bufferOut.remaining() >= buffer.remaining()) {
                    bufferOut.put(buffer.flip());
                    queue.poll();
                }
            }
        }
    }

    private class PrivateContext extends Context {
        private boolean authenticated;
        private long id;

        public PrivateContext(Server server, SelectionKey key) {
            super(key, server);
        }

        @Override
        void processIn() {
            if (authenticated) {
                server.privatePacket(bufferIn, id, socket);
            } else {
                bufferIn.flip();
                var op = bufferIn.get();
                bufferIn.compact();

                if (op == PRIVATE_CONNECTION_AUTHENTICATION) {
                    processReader(longReader::process, () -> {
                        var id = longReader.get();
                        var pc = pcs.get(id);

                        if (!pc.addNewConnection()) {
                            // error : 2 clients max
                            silentlyClose();
                            return;
                        }

                        processReader(stringReader::process, () -> {
                            var pseudo = stringReader.get();
                            for (var entry : pc.privateSockets.entrySet()) {
                                if (entry.getKey().equals(pseudo)) {
                                    entry.setValue(this);
                                    break;
                                }
                            }
                            stringReader.reset();
                        });

                        authenticated = true;
                        this.id = id;
                        longReader.reset();
                        if (pc.nbConnection == 2) {
                            server.privateBroadcast(Packets.ofAuthenticationConfirmation(id, (byte) 1), id);
                        }
                    });
                }
            }

        }

        @Override
        void processOut() {
            if (!queue.isEmpty()) {
                var packet = queue.peek();
                if (packet.buffer != null) {
                    var buffer = packet.buffer;
                    if (bufferOut.remaining() >= buffer.remaining()) {
                        bufferOut.put(buffer.flip());
                        queue.poll();
                    }
                } else {
                    throw new IllegalStateException();
                }
            }
        }
    }

    private static class PC {
        private int nbConnection = 0;
        private final HashMap<String, PrivateContext> privateSockets = new HashMap<>();

        PC(String pseudoA, String pseudoB) {
            privateSockets.put(pseudoA, null);
            privateSockets.put(pseudoB, null);
        }

        boolean addNewConnection() {
            if (nbConnection < 2) {
                nbConnection++;
                return true;
            }
            return false;
        }
    }

    enum State {REQUEST, START_AUTHENTICATION, AUTHENTICATED, CLOSED}
    static final int MAX_BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final ServerSocketChannel socketPublic;
    private final ServerSocketChannel socketPrivate;
    private final Selector selector;
    private final HashSet<String> logins = new HashSet<>(); // à changer si thread
    private final HashMap<Long, PC> pcs = new HashMap<>();
    //private final HashMap<Long, PrivateContext> priConnections = new HashMap<>();
    private final int privatePort;

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
        if ((sc = socketPublic.accept()) != null) {
            sc.configureBlocking(false);
            var clientKey = sc.register(selector, SelectionKey.OP_READ);
            clientKey.attach(new PublicContext(this, clientKey));

        } else if ((sc = socketPrivate.accept()) != null) {
            sc.configureBlocking(false);
            var clientKey = sc.register(selector, SelectionKey.OP_READ);
            clientKey.attach(new PrivateContext(this, clientKey));
        } else {
            logger.info("The selector was wrong.");
        }
    }

    /**
     *
     * @throws IOException If some other I/O error occurs.
     */
    public void launch() throws IOException {
        logger.info("Server started...");
        socketPublic.configureBlocking(false);
        socketPublic.register(selector, SelectionKey.OP_ACCEPT);

        socketPrivate.configureBlocking(false);
        socketPrivate.register(selector, SelectionKey.OP_ACCEPT);

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
	void publicBroadcast(Packet packet) {
		for (var key: selector.keys()) {
		    try {
                var context = (PublicContext) key.attachment();
                if (context == null) {
                    continue;
                }
                context.queueMessage(packet);
            } catch (ClassCastException ignored) { }
		}
	}

    /**
     * Send a message to the specified client.
     *
     * @param packet Message to send.
     * @param loginDest Message recipient.
     */
    void privateMessage(Packet packet, String loginDest) {
        for (var key : selector.keys()) {
            try {
                var context = (PublicContext) key.attachment();
                if (context == null) {
                    continue;
                }
                // TODO : avertir l'expéditeur si le destinataire est déconnecté
                if (loginDest.equals(context.login)) {
                    context.queueMessage(packet);
                    return;
                }
            } catch (ClassCastException ignored) { }
        }
    }

	void privateBroadcast(ByteBuffer buffer, long id) {
	    for (var key : selector.keys()) {
	        try {
	            var context = (PrivateContext) key.attachment();
	            if (context == null) {
	                continue;
                }

	            if (context.id == id) {
                    context.queueMessage(new Packet(buffer));
                }
            } catch (ClassCastException ignored) { }
        }
    }

    void privatePacket(ByteBuffer buffer, long id, SocketChannel sc) {
        for (var key : selector.keys()) {
            try {
                var context = (PrivateContext) key.attachment();
                if (context == null) {
                    continue;
                }

                if (context.id == id) {
                    if (key.channel() == sc) {
                        continue;
                    }
                    context.queueMessage(new Packet(buffer));
                }
            } catch (ClassCastException ignored) { }
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
