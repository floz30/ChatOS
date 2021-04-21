package fr.uge.chatos.context;

import fr.uge.chatos.packet.Packet;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This interface represents a context for a private or public connection.
 */
public interface Context {

    /**
     *
     * @throws IOException If some other I/O error occurs.
     */
    void doConnect() throws IOException;

    /**
     * Performs the read action on {@code socket}.
     * <p>
     * Note: {@code bufferIn} and {@code bufferOut} are in <b>write-mode</b> before
     * and after the call.
     * </p>
     *
     * @throws IOException If some other I/O error occurs.
     */
    void doRead() throws IOException;

    /**
     * Performs the write action on {@code socket}.
     * <p>
     * Note: {@code bufferIn} and {@code bufferOut} are in <b>write-mode</b> before
     * and after the call.
     * </p>
     *
     * @throws IOException If some other I/O error occurs.
     */
    void doWrite() throws IOException;

    /**
     * Process the content of {@code bufferIn}.
     * <p>
     * Note: {@code bufferIn} is in <b>write-mode</b> before and after the call.
     * </p>
     */
    void processIn();

    /**
     * Process the content of {@code bufferOut}.
     * <p>
     * Note: {@code bufferOut} is in <b>write-mode</b> before and after the call.
     * </p>
     */
    void processOut();

    /**
     * Adds a message to the queue and process the content of {@code bufferOut}.
     *
     * @param buffer The buffer to send.
     */
    void queueMessage(ByteBuffer buffer);

    /**
     * Try to close the socket. If an exception is thrown, it is ignored.
     */
    void silentlyClose();

    /**
     *
     * @param packet the {@code packet} to treat
     */
    void treatPacket(Packet packet);

    /**
     * Update the interestOps of the key looking only at values of the boolean
     * closed and of both ByteBuffers.
     * <p>
     * Note: {@code bufferIn} and {@code bufferOut} are in <b>write-mode</b> before
     * and after the call. {@code process} need to be called just before this method.
     * </p>
     */
    void updateInterestOps();

}
