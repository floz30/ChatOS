package fr.uge.chatos.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());
    private final SocketChannel socket;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
    private final Thread console;
    private final String login;
    private Context uniqueContext;
    private boolean logged;

    public Client(String login, InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = Objects.requireNonNull(serverAddress);
        this.login = Objects.requireNonNull(login);
        socket = SocketChannel.open();
        selector = Selector.open();
        console = new Thread(this::consoleRun);
    }

    private void consoleRun() {
        try (var scan = new Scanner(System.in)) {
            while (scan.hasNextLine()) {
                var msg = scan.nextLine();
                sendCommand(msg);
            }
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        } finally {
            logger.info("Console thread stopping");
        }
    }

    private void sendCommand(String command) throws InterruptedException {
        commandQueue.put(command);
        selector.wakeup();
    }

    /**
     * Extracts and returns the recipient of the private message.
     *
     * @param msg
     * @return
     */
    private String extractDest(String msg) {
        Objects.requireNonNull(msg);
        if (msg.startsWith("@")) {
            var elements = msg.split(" ");
            return elements[0].substring(1);
        }
        return "";
    }

    private void processCommands() {
        // TODO : optimiser la méthode
        if (!logged) {
            var bbLogin = StandardCharsets.UTF_8.encode(login);
            var buffer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bbLogin.remaining());
            uniqueContext.queueMessage(buffer.put((byte)0).putInt(bbLogin.remaining()).put(bbLogin).flip());
            logged = true;
            return;
        }
        // à faire en boucle
        selector.keys().forEach(key -> {
            var msg = commandQueue.poll();
            if (msg == null) {
                return;
            }
            String log;
            if (!(log = extractDest(msg)).isEmpty()) { // message privé
                var bbdest = StandardCharsets.UTF_8.encode(log);
                var content = msg.substring(msg.indexOf(" ")+1);
                var bbContent = StandardCharsets.UTF_8.encode(content);
                var buffer = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + bbContent.remaining() + bbdest.remaining());
                uniqueContext.queueMessage(buffer.put((byte)4).putInt(bbdest.remaining()).put(bbdest).putInt(bbContent.remaining()).put(bbContent).flip());
                return;
            }
            // message général
            var bbContent = StandardCharsets.UTF_8.encode(msg);
            var buffer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bbContent.remaining());
            uniqueContext.queueMessage(buffer.put((byte)2).putInt(bbContent.remaining()).put(bbContent).flip());
        });
    }

    private void launch() throws IOException {
        socket.configureBlocking(false);
        var key = socket.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(key);
        key.attach(uniqueContext);
        socket.connect(serverAddress);

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

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch(IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException ignored) { }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        // TODO : optimiser la méthode et vérifier les arguments
        if (args.length != 3) {
            usage();
            return;
        }
        new Client(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : Client login hostname port");
    }
}
