package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Objects;

public class ErrorShutdown implements Packet {
    private final String message;

    public ErrorShutdown(String message) {
        this.message = Objects.requireNonNull(message);
    }

    public String getMessage() {
        return message;
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
