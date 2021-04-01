package fr.uge.chatos.utils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Packets {
    private static final Charset charset = StandardCharsets.UTF_8;

    public static ByteBuffer ofRequestConnection(String login) {
        var loginBuffer = charset.encode(login);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + loginBuffer.remaining());
        result.put(OpCode.CONNECTION_REQUEST) // operation
                .putInt(loginBuffer.remaining()).put(loginBuffer); // content
        return result;
    }

    public static ByteBuffer ofAcceptConnection() {
        var result = ByteBuffer.allocate(2*Byte.BYTES);
        result.put(OpCode.CONNECTION_ACCEPT) // operation
                .put((byte) 1); // content
        return result;
    }

    public static ByteBuffer ofPublicMessage(String content) {
        var contentBuffer = charset.encode(content);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + contentBuffer.remaining());
        result.put(OpCode.GENERAL_SENDER) // operation
                .putInt(contentBuffer.remaining())
                .put(contentBuffer); // content
        return result;
    }

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

    public static ByteBuffer ofPrivateMessage(String recipient, String content) {
        var recipientBuffer = charset.encode(recipient);
        var contentBuffer = charset.encode(content);
        var result = ByteBuffer.allocate(Byte.BYTES + 2*Integer.BYTES + contentBuffer.remaining() + recipientBuffer.remaining());
        result.put(OpCode.PRIVATE_SENDER) // operation
                .putInt(recipientBuffer.remaining()).put(recipientBuffer) // recipient
                .putInt(contentBuffer.remaining()).put(contentBuffer); // content
        return result;
    }

    public static ByteBuffer ofErrorBuffer(String content) {
        var contentBuffer = charset.encode(content);
        var result = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + contentBuffer.remaining());
        result.put(OpCode.ERROR)
                .putInt(contentBuffer.remaining()).put(contentBuffer);
        return result;
    }
}
