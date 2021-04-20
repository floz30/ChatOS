package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Objects;

public class PCData implements Packet {
    public ByteBuffer data;
    public String sender;

    public PCData(ByteBuffer data, String sender) {
        this.data = ByteBuffer.allocate(data.remaining());
        this.data.put(data);
        data.compact();
        this.sender = Objects.requireNonNull(sender);
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return data.flip();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
