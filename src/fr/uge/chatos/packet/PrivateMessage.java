package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

public class PrivateMessage implements Packet {
    public String sender;
    public String recipient;
    public String content;

    @Override
    public ByteBuffer asByteBuffer() {
        return Packets.ofMessageReader(sender, content, (byte) 5).flip();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
