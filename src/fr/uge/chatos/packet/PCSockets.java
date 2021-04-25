package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

/**
 * Represent a frame containing the necessary information of the private connection for both user.
 * 
 */

public class PCSockets implements Packet {
    public String sender;
    public String recipient;
    public byte reply;
    public long id;
    public int port;
    private boolean needFirstPacket = true; // pour pouvoir créer les deux paquets

    @Override
    public ByteBuffer asByteBuffer() {
        if (needFirstPacket) {
            needFirstPacket = false;
            return Packets.ofPrivateConnectionSockets(id, recipient, port).flip();
        }
        needFirstPacket = true;
        return Packets.ofPrivateConnectionSockets(id, sender, port).flip();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
