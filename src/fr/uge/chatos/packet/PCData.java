package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Objects;

public class PCData implements Packet {
    private final ByteBuffer data;
    private final String sender;
    private final String path;

    public PCData(ByteBuffer data, String sender) {
        this(data, sender, null);
    }

    public PCData(ByteBuffer data, String sender, String path) {
        this.sender = Objects.requireNonNull(sender);
        this.path = path;
        try {
            this.data = ByteBuffer.allocate(data.flip().remaining());
            this.data.put(data);
        } finally {
            data.compact();
        }
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return data.flip();
    }

    public String getSender() {
        return sender;
    }

    public String getPath() {
        return path;
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}