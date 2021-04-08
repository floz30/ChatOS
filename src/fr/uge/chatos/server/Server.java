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

import fr.uge.chatos.reader.LongReader;
import fr.uge.chatos.reader.MessageReader;
import fr.uge.chatos.reader.Reader;
import fr.uge.chatos.reader.StringReader;
import fr.uge.chatos.utils.Message;
import fr.uge.chatos.utils.Packets;

import static fr.uge.chatos.utils.OpCode.*;

/**
 *
 */
public class Server {

    private class Packet {
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

    private class Context {
        private final SocketChannel socket;
        private final SelectionKey key;
        private final ByteBuffer bufferIn = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        private final Server server;
        private final Queue<Packet> queue = new LinkedList<>();
        private final MessageReader messageReader = new MessageReader();
        private final StringReader stringReader = new StringReader();
        private final LongReader longReader = new LongReader();
        private boolean closed;
        
        private String login;
        
        private boolean privateCo;
        private long privateId;
        private String dst;

        public Context(Server server, SelectionKey key) {
            this.key = Objects.requireNonNull(key);
            socket = (SocketChannel) key.channel();
            this.server = Objects.requireNonNull(server);
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
        private void executeReader(Function<ByteBuffer, Reader.ProcessStatus> status, Runnable runnable) {
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
         * Process the content of {@code bufferIn}.
         * Get the first byte and check what action needs to be done.
         * <p>
         * Note: {@code bufferIn} is in <b>write-mode</b> before and after the call.
         * </p>
         */
        private void processIn() {
            bufferIn.flip();
            var op = bufferIn.get();
            bufferIn.compact();

            switch (op) {
                case CONNECTION_REQUEST: // Demande de connexion
                    executeReader(stringReader::process, () -> {
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
                    executeReader(stringReader::process, () -> {
                        //server.broadcast(new Message(login, stringReader.get(), false));
                        server.broadcast(new Packet(new Message(login, stringReader.get(), false)));
                        stringReader.reset();
                    });
                    break;
                case PRIVATE_SENDER: // Message privé
                    executeReader(messageReader::process, () -> {
                        var message = messageReader.get();
                        server.privateMessage(new Packet(new Message(login, message.getContent(), true)), message.getLogin());
                        messageReader.reset();
                    });
                    break;
                    
                case PRIVATE_CONNECTION_REQUEST_SENDER: // demande de connexion privée 6
                    executeReader(messageReader::process, () -> {
                        var dst = messageReader.get().getLogin();
                        if (server.logins.contains(dst)) {
                            var packet = new Packet(Packets.ofPrivateConnection(login, PRIVATE_CONNECTION_REQUEST_RECEIVER));
                            server.privateMessage(packet, dst);
                            stringReader.reset();
                        }
                        else {
                            // renvoyer un packet d'erreur
                            // TODO: changer la RFC pour supporter 2 types de packet d'erreur
                        }
                    });
                    break;
                case PRIVATE_CONNECTION_REPLY: // confirmation connexion privée 8
                    executeReader(stringReader::process, () -> {
                        var reply = bufferIn.flip().get();
                        bufferIn.compact();
                        if (reply == 1) {
                            var dst = stringReader.get();
                            var privateId = ThreadLocalRandom.current().nextLong();
                            server.privateMessage(
                                    new Packet(Packets.ofPrivateConnectionSockets(privateId, login)), dst);
                            server.privateMessage(
                                    new Packet(Packets.ofPrivateConnectionSockets(privateId, dst)), login);
                        }
                        else {
                            //refus
                            
                        }
                    });
                    break;
                case PRIVATE_CONNECTION_AUTHENTICATION: // authentification connexion privée 10
                    executeReader(longReader::process, () -> {
                        var content = longReader.get(); // id
                        
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

        /**
         *
         * @param msg
         */
        void queueMessage(Packet msg) {
            queue.add(msg);
            processOut();
            updateInterestOps();
        }

        /**
         * Process the content of {@code bufferOut}.
         * <p>
         * Note: {@code bufferOut} is in <b>write-mode</b> before and after the call.
         * </p>
         */
        private void processOut() {
            if (!queue.isEmpty()) {
                var value = queue.peek();

                ByteBuffer buffer;
                if (value.message != null) { // si c'est un Message
                    if (value.message.isMp()) {
                        buffer = Packets.ofMessageReader(value.message.getLogin(), value.message.getContent(), PRIVATE_RECEIVER);
                    } else {
                        buffer = Packets.ofMessageReader(value.message.getLogin(), value.message.getContent(), GENERAL_RECEIVER);
                    }

                } else { // sinon pour une connexion privée
                    buffer = value.buffer;
                }

                if (bufferOut.remaining() >= buffer.remaining()) {
                    bufferOut.put(buffer.flip());
                    queue.poll();
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
         * @throws IOException
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
         * @throws IOException
         */
        void doWrite() throws IOException {
            bufferOut.flip();
            socket.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        /**
         * Try to close the socket. If an exception is thrown, it is ignored.
         */
        private void silentlyClose() {
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
        private void updateInterestOps() {
            var newInterestOps = 0;
            if (!closed && bufferIn.hasRemaining()) {
                newInterestOps |= SelectionKey.OP_READ;
            }
            if (bufferOut.position() != 0) {
                newInterestOps |= SelectionKey.OP_WRITE;
            }
            if (newInterestOps == 0) {
                silentlyClose();
                return;
            }
            key.interestOps(newInterestOps);
        }

    }

    static final int MAX_BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final HashSet<String> logins = new HashSet<>();

    public Server(int port) throws IOException {
        if (port <= 0) {
            throw new IllegalArgumentException("port number can't be negative");
        }
        
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
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
     * @throws IOException
     */
    private void doAccept(SelectionKey key) throws IOException {
        var sc = serverSocketChannel.accept();
        if (sc != null) {
            sc.configureBlocking(false);
            var clientKey = sc.register(selector, SelectionKey.OP_READ);
            clientKey.attach(new Context(this, clientKey));
        } else {
            logger.info("The selector was wrong.");
        }
    }

    /**
     *
     * @throws IOException
     */
    public void launch() throws IOException {
        logger.info("Server started...");
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

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
     * @param key
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
     * @param message Message to send.
     */
    void broadcast(Packet message) {
        for (var key: selector.keys()) {
            var context = (Context) key.attachment();
            if (context == null) {
                continue;
            }
            context.queueMessage(message);
        }
    }

    /**
     * Send a message to the specified client.
     *
     * @param message Message to send.
     * @param loginDest Message recipient.
     */
    void privateMessage(Packet message, String loginDest) {
        for (var key : selector.keys()) {
            var context = (Context) key.attachment();
            if (context == null) {
                continue;
            }
            // TODO : avertir l'expéditeur si le destinataire est déconnecté
            if (loginDest.equals(context.login) && !context.closed) {
                context.queueMessage(message);
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
