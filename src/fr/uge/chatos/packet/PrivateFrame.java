package fr.uge.chatos.packet;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import fr.uge.chatos.visitor.PacketVisitor;

public class PrivateFrame implements Packet{
    private final ByteBuffer bb;
    private final String dst;
    private String root;

    public PrivateFrame(ByteBuffer bb, String dst, String root) {
        this.bb = ByteBuffer.allocate(bb.flip().remaining());
        this.bb.put(bb);
        bb.compact();
        this.dst = dst;
        this.root = root;
    }


    @Override
    public ByteBuffer asByteBuffer() {
        // TODO Auto-generated method stub
        return bb;
    }

    public String getDst() {
        return dst;
    }

    public String getRoot() {
        return root;
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }

}