package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Objects;

public class PrivateConnectionData implements Packet {
    public ByteBuffer data;
    public String sender;

    public PrivateConnectionData(ByteBuffer data, String sender) {
        this.data = data;
        this.sender = Objects.requireNonNull(sender);
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return data;
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
