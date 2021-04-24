package fr.uge.chatos.packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.StringJoiner;

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
    public static ByteBuffer ofPublicMessage(String sender, String content) {
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
     * @param opCode the operation code
     * @return a {@code ByteBuffer} in <b>write-mode</b>
     */
    public static ByteBuffer ofMessageReader(String sender, String content, byte opCode) {
        var contentBuffer = charset.encode(content);
        var expBuffer = charset.encode(sender);
        var result = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + contentBuffer.remaining() + expBuffer.remaining());
        result.put(opCode)
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
     * ByteBuffer : string.
     *
     * @param fn : filename
     * @return
     */
    public static ByteBuffer ofGETRequest(String fn, String host) {
        var request = ASCII.encode("GET " + fn + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "\r\n");
        var bb = ByteBuffer.allocate(request.remaining());
        bb.put(request);
        return bb;

    }

    public static ByteBuffer ofHTTP(ByteBuffer bb, PrivateFrame privateFrame) {
        var req = ASCII.decode(bb).toString();
        var fn = req.split(" ")[1];
        
        try (var lines = Files.lines(Path.of(privateFrame.getRoot() + "/" + fn))) {
            var file = lines.reduce("", String::concat); //inshAllah c'est bon (j'ai le fichier en un string)
            file += "\r\n";
            
            var content = charset.encode(file);
            var response = new StringJoiner("\r\n","","\r\n\r\n");
            var ext = fn.split("\\.")[1];
            response.add("HTTP/1.1 200 OK");
            response.add("Content-Length: " + (content.capacity() - 2));
            response.add("Content-Type: " + ext);
            var header = ASCII.encode(response.toString());
            var res = ByteBuffer.allocate(content.capacity() + header.capacity());
            res.put(header).put(content);
            return res;
        } catch (IOException e) {
            return ofNoShutdownErrorBuffer("an I/O error occurs opening the file");
        }
    }
}
