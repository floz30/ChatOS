package fr.uge.chatos.client;

import fr.uge.chatos.context.ClientPrivateContext;
import fr.uge.chatos.context.ClientPublicContext;
import fr.uge.chatos.context.Context;
import fr.uge.chatos.packet.Packets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;



/** 
 * Implement a non-blocking TCP client.
 *
 */
public class Client {
    /**
     * This class represents a private connection.
     * <p>
     * A private connection have a unique ID.
     */
    static class PrivateConnection {
        private final long id;
        private final ClientPrivateContext context;

        PrivateConnection(long id, ClientPrivateContext context) {
            this.id = id;
            this.context = Objects.requireNonNull(context);
        }

        ClientPrivateContext getContext() {
            return context;
        }
    }

    private static final Logger logger = Logger.getLogger(Client.class.getName());
    private final SocketChannel socketPublic;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
    private final Thread console;
    private final String login;
    private final Object lock = new Object();
    private final String repository;
    private SelectionKey publicKey;
    private ClientPublicContext contextPublic;
    private final HashMap<String, PrivateConnection> privateConnections = new HashMap<>();

    public Client(String login, InetSocketAddress serverAddress, String repository) throws IOException {
        this.serverAddress = Objects.requireNonNull(serverAddress);
        this.login = Objects.requireNonNull(login);
        socketPublic = SocketChannel.open();
        selector = Selector.open();
        console = new Thread(this::consoleRun);
        console.setDaemon(true);
        this.repository = repository;
    }

    public String getRepository() {
        return repository;
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
                        buffer = Packets.ofPrivateMessageSender(login, cmd.recipient(), cmd.content()); // message privé
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
                            buffer = Packets.ofPrivateConnectionSender(cmd.recipient());
                        }
                    } else { // sur le port privé
                        if (pc.getContext().isAuthenticated()) {
                            // si déjà authentifié appel du client http
                            buffer = Packets.ofHTTPRequest(cmd.content(), serverAddress.getHostName());
                            pc.getContext().setFileRequested(cmd.content());
                            System.out.println("Envoi requête HTTP");
                        } else {
                            // si en cours d'authentification envoi de la réponse
                            buffer = Packets.ofAuthentication(pc.getContext().getId(), login);
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
            var context = new ClientPrivateContext(key, this, id);
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
        publicKey = socketPublic.register(selector, SelectionKey.OP_CONNECT);
        contextPublic = new ClientPublicContext(publicKey, this);
        publicKey.attach(contextPublic);
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
     * Use the key attachment to either write or read.
     * 
     * @param key
     */
    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
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
     * Shutdown all private connections and the public one.
     */
    public void shutdown() { // TODO : vérifier le fonctionnement de cette méthode
        for (var pc : privateConnections.values()) {
            pc.context.silentlyClose();
        }
        silentlyClose(publicKey);
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
