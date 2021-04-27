package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

/**
 * Represent a frame containing a connection request from the client.
 * 
 */

public class ConnectionRequest implements Packet {
    public String sender;

    @Override
    public ByteBuffer asByteBuffer() {
        return Packets.ofAcceptConnection().flip();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
