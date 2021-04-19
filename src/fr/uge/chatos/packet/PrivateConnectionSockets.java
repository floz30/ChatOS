package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

public class PrivateConnectionSockets implements Packet {
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
            return Packets.ofPrivateConnectionSockets(id, sender, port).flip();
        }
        return Packets.ofPrivateConnectionSockets(id, recipient, port).flip();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
