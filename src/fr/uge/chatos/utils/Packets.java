package fr.uge.chatos.utils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Packets {
    private static final Charset charset = StandardCharsets.UTF_8;

    /**
     * Create a ByteBuffer : byte | int | string.
     * OpCode = 0.
     *
     * @param login The user's login.
     * @return
     */
    public static ByteBuffer ofRequestConnection(String login) {
        var loginBuffer = charset.encode(login);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + loginBuffer.remaining());
        result.put(OpCode.CONNECTION_REQUEST) // operation
                .putInt(loginBuffer.remaining())
                .put(loginBuffer); // content
        return result;
    }

    /**
     * Create a ByteBuffer : byte | byte.
     * OpCode = 1.
     *
     * @return
     */
    public static ByteBuffer ofAcceptConnection() {
        var result = ByteBuffer.allocate(2*Byte.BYTES);
        result.put(OpCode.CONNECTION_ACCEPT) // operation
                .put((byte) 1); // content
        return result;
    }

    /**
     * Create a ByteBuffer : byte | int | string.
     * OpCode = 2.
     *
     * @param content
     * @return
     */
    public static ByteBuffer ofPublicMessage(String content) {
        var contentBuffer = charset.encode(content);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + contentBuffer.remaining());
        result.put(OpCode.GENERAL_SENDER) // operation
                .putInt(contentBuffer.remaining())
                .put(contentBuffer); // content
        return result;
    }

    /**
     * Create a ByteBuffer : byte | int | string | int | string.
     * OpCode = 3 or 5.
     *
     * @param exp
     * @param content
     * @param opCode
     * @return
     */
    public static ByteBuffer ofMessageReader(String exp, String content, byte opCode) {
        var contentBuffer = charset.encode(content);
        var expBuffer = charset.encode(exp);
        var result = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + contentBuffer.remaining() + expBuffer.remaining());
        result.put(opCode) // operation
                .putInt(expBuffer.remaining())
                .put(expBuffer)
                .putInt(contentBuffer.remaining())
                .put(contentBuffer); // content
        return result;
    }

    /**
     * Create a ByteBuffer : byte | int | string | int | string.
     * OpCode = 4.
     *
     * @param recipient
     * @param content
     * @return
     */
    public static ByteBuffer ofPrivateMessage(String recipient, String content) {
        var recipientBuffer = charset.encode(recipient);
        var contentBuffer = charset.encode(content);
        var result = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + contentBuffer.remaining() + recipientBuffer.remaining());
        result.put(OpCode.PRIVATE_SENDER) // operation
                .putInt(recipientBuffer.remaining()).put(recipientBuffer) // recipient
                .putInt(contentBuffer.remaining()).put(contentBuffer); // content
        return result;
    }

    /**
     * Create a ByteBuffer : byte | int | string.
     * OpCode = 6 or 7.
     *
     * @param recipient
     * @param opCOde
     * @return
     */
    public static ByteBuffer ofPrivateConnection(String recipient, byte opCOde) {
        var recipientBuffer = charset.encode(recipient);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + recipientBuffer.remaining());
        result.put(opCOde)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer);
        return result;
    }

    /**
     * Create a ByteBuffer : byte | int | string | byte.
     * OpCode = 8.
     *
     * @param recipient
     * @return
     */
    public static ByteBuffer ofPrivateConnectionReply(String recipient, byte reply) {
        var recipientBuffer = charset.encode(recipient);
        var result = ByteBuffer.allocate(2*Byte.BYTES + Integer.BYTES + recipientBuffer.remaining());
        result.put(OpCode.PRIVATE_CONNECTION_REPLY)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer)
                .put(reply);
        return result;
    }

    /**
     * Create a ByteBuffer : byte | string | long | int.
     * OpCode = 9.
     *
     * @param id
     * @param port
     * @return
     */
    public static ByteBuffer ofPrivateConnectionSockets(long id, String recipient, int port) {
        var recipientBuffer = charset.encode(recipient);
        var result = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + Long.BYTES + recipientBuffer.remaining());
        result.put(OpCode.PRIVATE_CONNECTION_SOCKETS)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer)
                .putLong(id)
                .putInt(port);
        return result;
    }

    /**
     * Create a ByteBuffer : byte | long.
     * OpCode = 10.
     *
     * @param id
     * @return
     */
    public static ByteBuffer ofAuthentication(long id, String recipient) {
        var recipientBuffer = charset.encode(recipient);
        var result = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + Integer.BYTES + recipientBuffer.remaining());
        result.put(OpCode.PRIVATE_CONNECTION_AUTHENTICATION)
                .putLong(id)
                .putInt(recipientBuffer.remaining())
                .put(recipientBuffer);
        return result;
    }

    /**
     * Create a ByteBuffer : byte | long | byte.
     * OpCode = 11.
     *
     * @param id
     * @return
     */
    public static ByteBuffer ofAuthenticationConfirmation(long id, byte confirm) {
        var result = ByteBuffer.allocate(2*Byte.BYTES + Long.BYTES);
        result.put(OpCode.PRIVATE_CONNECTION_CONFIRMATION)
                .putLong(id)
                .put(confirm);
        return result;
    }

    /**
     * Create a ByteBuffer : byte | int | string.
     * OpCode = 99.
     *
     * @param content
     * @return
     */
    public static ByteBuffer ofErrorBuffer(String content) {
        var contentBuffer = charset.encode(content);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + contentBuffer.remaining());
        result.put(OpCode.ERROR)
                .putInt(contentBuffer.remaining()).put(contentBuffer);
        return result;
    }
}
