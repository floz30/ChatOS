package fr.uge.chatos.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

/**
 *
 */
public class Context {
    private final SocketChannel socket;
    private final SelectionKey key;
    private final ByteBuffer bufferIn = ByteBuffer.allocateDirect(Server.MAX_BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(Server.MAX_BUFFER_SIZE);
    private final Server server;
    private boolean closed;

    public Context(Server server, SelectionKey key) {
        this.key = Objects.requireNonNull(key);
        socket = (SocketChannel) key.channel();
        this.server = Objects.requireNonNull(server);
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
        processBufferIn();
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
        processBufferOut();
        updateInterestOps();
    }

    /**
     * Process the content of {@code bufferIn}.
     * <p>
     * Note: {@code bufferIn} is in <b>write-mode</b> before and after the call.
     * </p>
     */
    private void processBufferIn() {

    }

    /**
     * Process the content of {@code bufferOut}.
     * <p>
     * Note: {@code bufferOut} is in <b>write-mode</b> before and after the call.
     * </p>
     */
    private void processBufferOut() {

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
