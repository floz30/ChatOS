package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;

public class PCData implements Packet {
    private final ByteBuffer bb;
    private final String sender;
    /*private final Path path;*/

    public PCData(ByteBuffer bb, String sender/*, Path path*/) {
        this.bb = ByteBuffer.allocate(bb.flip().remaining());
        this.bb.put(bb);
        bb.compact();
        this.sender = Objects.requireNonNull(sender);
        //this.path = path;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return bb;
    }
    
    public String getSender() {
        return sender;
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
