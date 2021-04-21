package fr.uge.chatos.client;

import fr.uge.chatos.packet.Packet;
import fr.uge.chatos.packet.PCData;
import fr.uge.chatos.reader.*;
import fr.uge.chatos.packet.Packets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static fr.uge.chatos.utils.OpCode.*;

/**
 *
 */
public class Client {

    /**
     *
     */
    public static abstract class Context {
        protected final SocketChannel socket;
        protected final SelectionKey key;
        protected final Client client;
        protected final ByteBuffer bufferIn = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        protected final ByteBuffer bufferOut = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        protected final Queue<ByteBuffer> queue = new LinkedList<>();
        protected final ClientPacketReader clientPacketReader = new ClientPacketReader();
        protected final ClientPacketVisitor visitor;
        private boolean closed;
        //private boolean authenticated = false;

        private Context(SelectionKey key, Client client) {
            this.key = Objects.requireNonNull(key);
            this.socket = (SocketChannel) key.channel();
            this.client = Objects.requireNonNull(client);
            visitor = new ClientPacketVisitor(client, this);
        }

        /**
         * Process the content of {@code bufferIn}.
         * <p>
         * Note: {@code bufferIn} is in <b>write-mode</b> before and after the call.
         * </p>
         */
        void processIn() {
            for (;;) {
                var status = clientPacketReader.process(bufferIn);
                switch (status) {
                    case ERROR -> silentlyClose();
                    case REFILL -> { return; }
                    case DONE -> {
                        var packet = clientPacketReader.get();
                        clientPacketReader.reset();
                        treatPacket(packet);
                    }
                }
            }
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
                if (buffer.remaining() <= bufferOut.remaining()) {
                    queue.remove();
                    bufferOut.put(buffer);
                } else {
                    break;
                }
            }
        }

        /**
         *
         * @throws IOException If some other I/O error occurs.
         */
        void doConnect() throws IOException {
            if (!socket.finishConnect()) {
                return;
            }
            key.interestOps(SelectionKey.OP_WRITE);
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
         *
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
         * Try to close the socket. If an exception is thrown, it is ignored.
         */
        void silentlyClose() {
            try {
                socket.close();
            } catch (IOException ignored) { }
        }

        /**
         *
         * @param packet the {@code packet} to treat
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

    private class ContextPublic extends Context {

        ContextPublic(SelectionKey key, Client client) {
            super(key, client);
        }

        @Override
        void doConnect() throws IOException {
            super.doConnect();
            queue.add(Packets.ofRequestConnection(login).flip());
        }
    }

    public class ContextPrivate extends Context {
        private String initialRequest;
        private final long id;
        private boolean authenticated = false;

        ContextPrivate(SelectionKey key, Client client, long id) {
            super(key, client);
            this.id = id;
        }

        @Override
        void processIn() {
            if (authenticated) {
                treatPacket(new PCData(bufferIn, "")); // TODO : à revoir
            } else {
                super.processIn();
            }
        }

        public void successfulAuthentication() {
            authenticated = true;
        }

        @Override
        void doConnect() throws IOException {
            super.doConnect();
            queue.add(Packets.ofAuthentication(id, login).flip());
        }
    }

    /**
     * This class represents a private connection.
     * <p>
     * A private connection have a unique ID.
     */
    static class PrivateConnection {
        private final long id;
        private final ContextPrivate context;

        PrivateConnection(long id, ContextPrivate context) {
            this.id = id;
            this.context = Objects.requireNonNull(context);
        }

        ContextPrivate getContext() {
            return context;
        }
    }

    private static final Logger logger = Logger.getLogger(Client.class.getName());
    private static final int MAX_BUFFER_SIZE = 1_024;
    private final SocketChannel socketPublic;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
    private final Thread console;
    private final String login;
    private final Object lock = new Object();
    private final Path repository;
    private ContextPublic contextPublic;
    private final HashMap<String, PrivateConnection> privateConnections = new HashMap<>();

    public Client(String login, InetSocketAddress serverAddress, String repository) throws IOException {
        this.serverAddress = Objects.requireNonNull(serverAddress);
        this.login = Objects.requireNonNull(login);
        socketPublic = SocketChannel.open();
        selector = Selector.open();
        console = new Thread(this::consoleRun);
        console.setDaemon(true);
        this.repository = Paths.get(repository);
    }

    /**
     * Returns the current login of this client.
     *
     * @return the current login
     */
    public String getLogin() {
        return login;
    }

    /**
     * Thread that manages the client console.
     */
    private void consoleRun() {
        try (var scan = new Scanner(System.in)) {
            while (scan.hasNextLine()) {
                var command = scan.nextLine();
                sendCommand(command);
            }
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        } finally {
            logger.info("Console thread stopping");
        }
    }

    /**
     * Treat the command written by the user and wake up the selector.
     *
     * @param command the line written by the user
     */
    private void sendCommand(String command) throws InterruptedException {
        synchronized (lock) {
            commandQueue.put(Objects.requireNonNull(command));
            selector.wakeup();
        }
    }

    /**
     * Process commands written by the client.
     */
    private void processCommands() {
        synchronized(lock) {
            while (!commandQueue.isEmpty()) {
                var tmp = commandQueue.peek();
                if (tmp == null) {
                    return;
                }
                ByteBuffer buffer;
                var cmd = Command.extractCommand(tmp);
                if (cmd.isMessage()) {
                    if (cmd.recipient() != null) {
                        buffer = Packets.ofPrivateMessage(login, cmd.recipient(), cmd.content(), PRIVATE_SENDER); // message privé
                    } else {
                        buffer = Packets.ofPublicMessage(login, cmd.content()); // message général
                    }
                } else {
                    // Connexion privée
                    var pc = privateConnections.get(cmd.recipient());
                    if (pc == null) { // si pas de connexion existante
                        if (cmd.content().equals("oui") || cmd.content().equals("non")) { // si confirmation de la connexion
                            var confirm = cmd.content().equals("oui") ? (byte) 1 : (byte) 0;
                            buffer = Packets.ofPrivateConnectionReply(cmd.recipient(), confirm);
                        } else { // sinon demande de connexion
                            buffer = Packets.ofPrivateConnection(cmd.recipient(), PRIVATE_CONNECTION_REQUEST_SENDER);
                        }
                    } else { // sur le port privé
                        if (pc.getContext().authenticated) {
                            // si déjà authentifié appel du client http
                            buffer = ByteBuffer.allocate(4); // TODO : à changer avec HTTP
                            buffer.putInt(3);
                            System.out.println("authentifié et envoi http");
                        } else {
                            // si en cours d'authentification envoi de la réponse
                            buffer = Packets.ofAuthentication(pc.getContext().id, login);
                            System.out.println("En cours d'authentification");
                        }
                        pc.getContext().queueMessage(buffer.flip());
                        commandQueue.poll();
                        continue;
                    }

                }
                contextPublic.queueMessage(buffer.flip());
                commandQueue.poll();
            }
        }
    }

    public Optional<Map.Entry<String, PrivateConnection>> getPrivateConnection(long id) {
        return privateConnections.entrySet().stream().filter(entry -> entry.getValue().id == id).findFirst();
    }

    /**
     * Initializes a new private connection with a new socketChannel.
     *
     * @param port the server port
     * @param recipient the username of the recipient
     * @param id the ID of private connection
     */
    public void startPrivateConnection(int port, String recipient, long id) {
        try {
            var socket = SocketChannel.open();
            socket.configureBlocking(false);
            var key = socket.register(selector, SelectionKey.OP_CONNECT);
            var context = new ContextPrivate(key, this, id);
            key.attach(context);
            socket.connect(new InetSocketAddress(port));
            privateConnections.put(recipient, new PrivateConnection(id, context));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error", e);
        }
    }

    /**
     * Start the main client loop.
     *
     * @throws IOException if some other I/O error occurs
     */
    private void launch() throws IOException {
        socketPublic.configureBlocking(false);
        var key = socketPublic.register(selector, SelectionKey.OP_CONNECT);
        contextPublic = new ContextPublic(key, this);
        key.attach(contextPublic);
        socketPublic.connect(serverAddress);

        console.start();

        while(!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    /**
     *
     * @param key
     */
    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
//                var buffer = Packets.ofRequestConnection(login);
//                contextPublic.queue.add(buffer.flip());
                ((Context) key.attachment()).doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch(IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     *
     * @param key
     */
    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException ignored) { }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            System.err.println("Usage : client <login> <hostname> <port> <repository>");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("The port number must be an Integer.");
            return;
        }

        new Client(args[0], new InetSocketAddress(args[1], port), args[3]).launch();
    }

}
