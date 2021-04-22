package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

public class PCRequest implements Packet {
    public String sender;
    public String recipient;

    @Override
    public ByteBuffer asByteBuffer() {
        return Packets.ofPrivateConnectionReceiver(sender).flip();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
