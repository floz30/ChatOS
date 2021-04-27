package fr.uge.chatos.packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static fr.uge.chatos.utils.OpCode.*;

/**
 * This class contains a lot of static factory methods for create a {@link java.nio.ByteBuffer}
 * with a predefined format in the ChatOS protocol RFC.
 * <p>
 *     All methods return a {@code ByteBuffer} in <b>write-mode</b>.
 * </p>
 * <p>
 *     Buffers are encoded in UTF-8.
 * </p>
 */
public class Packets {
    /**
     * The charset used to encode string.
     */
    private static final Charset charset = StandardCharsets.UTF_8;

    private static final Charset ASCII = StandardCharsets.US_ASCII;


    /**
     * Create a buffer with this format : byte | int | string.
     * <p>
     *     OpCode = 0.
     * </p>
     *
     * @param login the user's login
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofRequestConnection(String login) {
        var loginBuffer = charset.encode(login);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + loginBuffer.remaining());
        result.put(CONNECTION_REQUEST)
                .putInt(loginBuffer.remaining())
                .put(loginBuffer);
        return result;
    }

    /**
     * Create a buffer with this format : byte | byte.
     * <p>
     *     OpCode = 1.
     * </p>
     *
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofAcceptConnection() {
        var result = ByteBuffer.allocate(2*Byte.BYTES);
        result.put(CONNECTION_ACCEPT)
                .put((byte) 1);
        return result;
    }

    /**
     * Create a buffer with this format : byte | int | string | int | string.
     * <p>
     *     OpCode = 2.
     * </p>
     *
     * @param sender the sender's login
     * @param content the content of the public message
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofPublicMessageSender(String sender, String content) {
        var contentBuffer = charset.encode(content);
        var senderBuffer = charset.encode(sender);
        var result = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + senderBuffer.remaining() + contentBuffer.remaining());
        result.put(GENERAL_SENDER)
                .putInt(senderBuffer.remaining())
                .put(senderBuffer)
                .putInt(contentBuffer.remaining())
                .put(contentBuffer);
        return result;
    }

    /**
     * Create a buffer with this format : byte | int | string | int | string.
     * <p>
     *     OpCode = 3 or 5.
     * </p>
     *
     * @param sender the sender's login
     * @param content the content of the message
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofPublicMessageReceiver(String sender, String content) {
        var contentBuffer = charset.encode(content);
        var expBuffer = charset.encode(sender);
        var result = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + contentBuffer.remaining() + expBuffer.remaining());
        result.put(GENERAL_RECEIVER)
                .putInt(expBuffer.remaining())
                .put(expBuffer)
                .putInt(contentBuffer.remaining())
                .put(contentBuffer);
        return result;
    }

    /**
     * Create a buffer with this format : byte | int | string | int | string | int | string.
     * <p>
     *     OpCode = 4.
     * </p>
     *
     * @param sender the sender's login
     * @param recipient the recipient's login
     * @param content the content of the private message
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofPrivateMessageSender(String sender, String recipient, String content) {
        var recipientBuffer = charset.encode(recipient);
        var contentBuffer = charset.encode(content);
        var senderBuffer = charset.encode(sender);
        var result = ByteBuffer.allocate(Byte.BYTES + 3*Integer.BYTES + senderBuffer.remaining() +  contentBuffer.remaining() + recipientBuffer.remaining());
        result.put(PRIVATE_SENDER)
                .putInt(senderBuffer.remaining())
                .put(senderBuffer)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer)
                .putInt(contentBuffer.remaining())
                .put(contentBuffer);
        return result;
    }

    /**
     * Create a buffer with this format : byte | int | string | int | string | int | string.
     * <p>
     *     OpCode = 5.
     * </p>
     *
     * @param sender the sender's login
     * @param recipient the recipient's login
     * @param content the content of the private message
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofPrivateMessageReceiver(String sender, String recipient, String content) {
        var recipientBuffer = charset.encode(recipient);
        var contentBuffer = charset.encode(content);
        var senderBuffer = charset.encode(sender);
        var result = ByteBuffer.allocate(Byte.BYTES + 3*Integer.BYTES + senderBuffer.remaining() +  contentBuffer.remaining() + recipientBuffer.remaining());
        result.put(PRIVATE_RECEIVER)
                .putInt(senderBuffer.remaining())
                .put(senderBuffer)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer)
                .putInt(contentBuffer.remaining())
                .put(contentBuffer);
        return result;
    }

    /**
     * Create a buffer with this format : byte | int | string.
     * <p>
     *     OpCode = 6.
     * </p>
     *
     * @param recipient the recipient's login of the private connection
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofPrivateConnectionSender(String recipient) {
        var recipientBuffer = charset.encode(recipient);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + recipientBuffer.remaining());
        result.put(PRIVATE_CONNECTION_REQUEST_SENDER)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer);
        return result;
    }

    /**
     * Create a buffer with this format : byte | int | string.
     * <p>
     *     OpCode = 7.
     * </p>
     *
     * @param sender the sender's login of the private connection
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofPrivateConnectionReceiver(String sender) {
        var recipientBuffer = charset.encode(sender);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + recipientBuffer.remaining());
        result.put(PRIVATE_CONNECTION_REQUEST_RECEIVER)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer);
        return result;
    }

    /**
     * Create a buffer with this format : byte | int | string | byte.
     * <p>
     *     OpCode = 8.
     * </p>
     *
     * @param recipient the recipient's login of the private connection
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofPrivateConnectionReply(String recipient, byte reply) {
        var recipientBuffer = charset.encode(recipient);
        var result = ByteBuffer.allocate(2*Byte.BYTES + Integer.BYTES + recipientBuffer.remaining());
        result.put(PRIVATE_CONNECTION_REPLY)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer)
                .put(reply);
        return result;
    }

    /**
     * Create a buffer with this format : byte | int | string | long | int.
     * <p>
     *     OpCode = 9.
     * </p>
     *
     * @param id the ID of private connection to establish
     * @param port the server port number for the new connection
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofPrivateConnectionSockets(long id, String recipient, int port) {
        var recipientBuffer = charset.encode(recipient);
        var result = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + Long.BYTES + recipientBuffer.remaining());
        result.put(PRIVATE_CONNECTION_SOCKETS)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer)
                .putLong(id)
                .putInt(port);
        return result;
    }

    /**
     * Create a buffer with this format : byte | long | int | string.
     * <p>
     *     OpCode = 10.
     * </p>
     *
     * @param id the ID of private connection to authenticate
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofAuthentication(long id, String recipient) {
        var recipientBuffer = charset.encode(recipient);
        var result = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + Integer.BYTES + recipientBuffer.remaining());
        result.put(PRIVATE_CONNECTION_AUTHENTICATION)
                .putLong(id)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer);
        return result;
    }

    /**
     * Create an authentication confirmation buffer with this format : byte | long | byte.
     * <p>
     *     OpCode = 11.
     * </p>
     *
     * @param id the ID of private connection to confirm
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofAuthenticationConfirmation(long id, byte confirm) {
        var result = ByteBuffer.allocate(2*Byte.BYTES + Long.BYTES);
        result.put(PRIVATE_CONNECTION_CONFIRMATION)
                .putLong(id)
                .put(confirm);
        return result;
    }

    /**
     * Create an error buffer with this format : byte | int | string.
     * This packet means that the client can continue to run.
     * <p>
     *     OpCode = 98.
     * </p>
     *
     * @param content the error message content
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofNoShutdownErrorBuffer(String content) {
        var contentBuffer = charset.encode(content);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + contentBuffer.remaining());
        result.put(ERROR_NO_SHUTDOWN)
                .putInt(contentBuffer.remaining()).put(contentBuffer);
        return result;
    }

    /**
     * Create an error buffer with this format : byte | int | string.
     * This packet means that the client the client must be stop.
     * <p>
     *     OpCode = 99.
     * </p>
     *
     * @param content the error message content
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofShutdownErrorBuffer(String content) {
        var contentBuffer = charset.encode(content);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + contentBuffer.remaining());
        result.put(ERROR_SHUTDOWN)
                .putInt(contentBuffer.remaining()).put(contentBuffer);
        return result;
    }

    /**
     * Create a buffer for a HTTP request.
     *
     * @param filename the filename to resquest
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofHTTPRequest(String filename, String host) {
        var content = ASCII.encode("GET " + filename + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "\r\n");
        var result = ByteBuffer.allocate(content.remaining());
        result.put(content);
        return result;
    }

    /**
     * Create a buffer containing the HTTP response.
     *
     * @param name the filename
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */

    public static ByteBuffer ofHTTPResponse(String name) {
        var path = Path.of(name);
        if (Files.exists(path)) {
            try (var lines = Files.lines(path)) {
                var file = lines.reduce("", String::concat);
                file += "\r\n\r\n";

                var ext = getFileExtension(name);

                var content = ASCII.encode(file);
                var list = new ArrayList<String>();
                list.add("HTTP/1.1 200 OK");
                list.add("Content-Length: " + content.capacity());
                list.add("Content-Type: " + ext);
                list.add("");

                var header = String.join("\r\n", list);
                var encoded = ASCII.encode(header + "\r\n");

                var result = ByteBuffer.allocate(encoded.remaining() + content.remaining());
                return result.put(encoded).put(content);
            } catch (IOException e) {
                return ofNoShutdownErrorBuffer("an I/O error occurs opening the file");
            }
        } else {
            var content = ASCII.encode("HTTP/1.1 404 Not found\r\n\r\n\r\n"); // ;-)
            var result = ByteBuffer.allocate(content.remaining());
            return result.put(content);
        }

    }

    private static String getFileExtension(String path) {
        var lastIndex = path.lastIndexOf(".");
        if (lastIndex == -1) {
            return "";
        }
        return path.substring(lastIndex+1);
    }

}
