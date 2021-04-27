package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

/**
 * Represent a frame containing the confirmation from the server.
 */
public class ConnectionConfirmation implements Packet {
    public byte confirm;

    public ConnectionConfirmation(byte confirm) {
        this.confirm = confirm;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return null;
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
