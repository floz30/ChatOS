package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Represent a frame containing a error.
 *
 */

public class ErrorShutdown implements Packet {
    private String message;

    public ErrorShutdown() {

    }

    public ErrorShutdown(String message) {
        this.message = Objects.requireNonNull(message);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return Packets.ofShutdownErrorBuffer(message);
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
