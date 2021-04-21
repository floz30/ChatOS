package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

public class PCAuth implements Packet {
    public String login;
    public long id;

    @Override
    public ByteBuffer asByteBuffer() {
        return Packets.ofAuthenticationConfirmation(id, (byte) 1).flip();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
