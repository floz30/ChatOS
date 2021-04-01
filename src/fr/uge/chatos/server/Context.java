package fr.uge.chatos.server;

import fr.uge.chatos.reader.MessageReader;
import fr.uge.chatos.reader.Reader;
import fr.uge.chatos.reader.StringReader;
import fr.uge.chatos.utils.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 *
 */
public class Context {
    private static final Logger logger = Logger.getLogger(Context.class.getName());
    private final SocketChannel socket;
    private final SelectionKey key;
    private final ByteBuffer bufferIn = ByteBuffer.allocateDirect(Server.MAX_BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(Server.MAX_BUFFER_SIZE);
    private final Server server;
    private boolean closed;
    private final Queue<Message> queue = new LinkedList<>();
    private final MessageReader messageReader = new MessageReader();
    private final StringReader stringReader = new StringReader();
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private String login;

    public Context(Server server, SelectionKey key) {
        this.key = Objects.requireNonNull(key);
        socket = (SocketChannel) key.channel();
        this.server = Objects.requireNonNull(server);
    }

    /**
     * Check if the client is already connected with an username.
     *
     * @return true if the client is connected, otherwise false.
     */
    private boolean isConnected() {
        return login != null;
    }

    /**
     * Check if the client is still connected to the server.
     *
     * @return true if the client is connected, otherwise false.
     */
    boolean isStillConnected() {
        return !closed;
    }

    /**
     * Returns the login of this client.
     *
     * @return the username of this client.
     */
    public String getLogin() {
        return login;
    }

    /**
     * Get the first byte and check what action needs to be done.
     * <p>
     * Note : {@code bufferIn} is in <b>write-mode</b> before and after the call.
     * </p>
     */
    private void checkByte() {
        bufferIn.flip();
        var op = bufferIn.get();
        bufferIn.compact();
        switch (op) {
            case 0: // Demande de connexion
                if (!isConnected()) {
                    executeReader(stringReader::processData, () -> {
                        login = stringReader.get();
                        logger.info(login + " connected");
                        stringReader.reset();
                    });
                }
                break;
            case 2: // Message général
                if (isConnected()) {
                     executeReader(stringReader::processData, () -> {
                         server.broadcast(new Message(login, stringReader.get(), false));
                         stringReader.reset();
                     });
                }
                break;
            case 4: // Message privé
                if (isConnected()) {
                    executeReader(messageReader::processData, () -> {
                        var message = messageReader.get();
                        server.privateMessage(new Message(login, message.getContent(), true), message.getLogin());
                        messageReader.reset();
                    });
                }
                break;
        }
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
        switch (status.apply(bufferIn)) {
            case DONE -> runnable.run();
            case REFILL -> { }
            case ERROR -> silentlyClose();
        }
    }

    /**
     * Process the content of {@code bufferIn}.
     * <p>
     * Note: {@code bufferIn} is in <b>write-mode</b> before and after the call.
     * </p>
     */
    private void processIn() {
        checkByte();
//        for(;;) {
//            Reader.ProcessStatus status = messageReader.processData(bufferIn);
//            switch (status){
//                case DONE:
//
//                    Message msg = messageReader.get();
//                    server.broadcast(msg);
//                    login = msg.getLogin();
//                    messageReader.reset();
//                    break;
//                case REFILL:
//                    return;
//                case ERROR:
//                    silentlyClose();
//                    return;
//            }
//        }
	}

    /**
     *
     * @param msg
     */
    void queueMessage(Message msg) {
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
			var login = UTF8.encode(value.getLogin());
			var content = UTF8.encode(value.getContent());
            // TODO : optimiser l'ajout du byte d'opération pour le retour
			byte op = 3;
			if (value.isMp()) {
			    op = 5;
            }
            if (bufferOut.remaining() >= Byte.BYTES + 2*Integer.BYTES + content.remaining() + login.remaining()) {
                bufferOut.put(op).putInt(login.remaining()).put(login).putInt(content.remaining()).put(content);
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
