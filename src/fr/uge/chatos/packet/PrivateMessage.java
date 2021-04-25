package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;


/**
 * Represent a frame containing a message for one particular user.
 * 
 */
public class PrivateMessage implements Packet {
    public String sender;
    public String recipient;
    public String content;

    @Override
    public ByteBuffer asByteBuffer() {
        return Packets.ofPrivateMessageReceiver(sender, recipient, content).flip();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
