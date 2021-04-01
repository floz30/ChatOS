package fr.uge.chatos.client;

import fr.uge.chatos.reader.MessageReader;
import fr.uge.chatos.reader.Reader;
import fr.uge.chatos.utils.OpCode;
import fr.uge.chatos.utils.Packets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Function;
import java.util.logging.Logger;


public class Client {

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel socket;
        private final ByteBuffer bufferIn = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
        private final Queue<ByteBuffer> queue = new LinkedList<>();
        private final MessageReader messageReader = new MessageReader();
        private boolean closed;

        public Context(SelectionKey key) {
            this.key = Objects.requireNonNull(key);
            socket = (SocketChannel) key.channel();
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
        private void processReader(Function<ByteBuffer, Reader.ProcessStatus> status, Runnable runnable) {
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
         * <p>
         * Note: {@code bufferIn} is in <b>write-mode</b> before and after the call.
         * </p>
         */
        private void processIn() {
            bufferIn.flip();
            var op = bufferIn.get();
            bufferIn.compact();

            switch (op) {
                case OpCode.ERROR -> silentlyClose();
                case OpCode.CONNECTION_ACCEPT -> {
                    bufferIn.flip();
                    if (bufferIn.get() == 1) {
                        bufferIn.compact();
                        System.out.println("Connected to server");
                        break;
                    };
                }
                case OpCode.GENERAL_RECEIVER -> {
                    processReader(messageReader::process, ()->{
                        var msg = messageReader.get();
                        System.out.println(msg.getLogin()+ " : " + msg.getContent());
                        messageReader.reset();
                    });
                }
                case OpCode.PRIVATE_RECEIVER -> {
                    processReader(messageReader::process, () -> {
                        var msg = messageReader.get();
                        System.out.println("[Message privé de " + msg.getLogin() + "] : " + msg.getContent());
                        messageReader.reset();
                    });
                }
            }
        }

        /**
         * @param message
         */
        void queueMessage(ByteBuffer message) {
            queue.add(message);
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
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers.
         * <p>
         * Note: {@code bufferIn} and {@code bufferOut} are in <b>write-mode</b> before
         * and after the call. {@code process} need to be called just before this method.
         * </p>
         */
        private void updateInterestOps() {
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

        /**
         * Try to close the socket. If an exception is thrown, it is ignored.
         */
        private void silentlyClose() {
            try {
                socket.close();
            } catch (IOException ignored) {
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
         *
         * @throws IOException
         */
        void doWrite() throws IOException {
            bufferOut.flip();
            socket.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        void doConnect() throws IOException {
            if (!socket.finishConnect()) {
                return;
            }
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }


    private static final Logger logger = Logger.getLogger(Client.class.getName());
    static final int MAX_BUFFER_SIZE = 1_024;
    private final SocketChannel socket;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
    private final Thread console;
    private final String login;
    private final Object lock = new Object();
    private Context uniqueContext;

    public Client(String login, InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = Objects.requireNonNull(serverAddress);
        this.login = Objects.requireNonNull(login);
        socket = SocketChannel.open();
        selector = Selector.open();
        console = new Thread(this::consoleRun);
        console.setDaemon(true);
    }

    /**
     * Thread that manages the console.
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
     *
     * @param command The line written by the user.
     * @throws InterruptedException
     */
    private void sendCommand(String command) throws InterruptedException {
        synchronized (lock) {
            commandQueue.put(Objects.requireNonNull(command));
            selector.wakeup();
        }
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

    /**
     *
     */
    private void processCommands() {
        synchronized(lock) {
            while (!commandQueue.isEmpty()) {
                var tmp = commandQueue.peek();
                if (tmp == null) {
                    return;
                }
                String log;
                if (!(log = extractDest(tmp)).isEmpty()) {
                    var content = tmp.substring(tmp.indexOf(" ")+1);
                    var buffer = Packets.ofPrivateMessage(log, content);
                    uniqueContext.queueMessage(buffer.flip());
                } else {
                    var buffer = Packets.ofPublicMessage(tmp);
                    uniqueContext.queueMessage(buffer.flip());
                }
                commandQueue.poll();
            }
        }
    }

    /**
     *
     * @throws IOException
     */
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

    /**
     *
     * @param key
     */
    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                var buffer = Packets.ofRequestConnection(login);
                uniqueContext.queue.add(buffer.flip());
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
        if (args.length != 3) {
            System.err.println("Usage : Client login hostname port");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("The port number must be an Integer.");
            return;
        }

        new Client(args[0], new InetSocketAddress(args[1], port)).launch();
    }

}
