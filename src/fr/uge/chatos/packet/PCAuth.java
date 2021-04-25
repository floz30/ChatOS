package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

/**
 * Represent a frame containing client's authentication to the private server.
 * 
 */

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
