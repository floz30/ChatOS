package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Objects;

public class ErrorNoShutdown implements Packet {
    private final String message;

    public ErrorNoShutdown(String message) {
        this.message = Objects.requireNonNull(message);
    }

    public String getMessage() {
        return message;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return Packets.ofNoShutdownErrorBuffer(message);
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
