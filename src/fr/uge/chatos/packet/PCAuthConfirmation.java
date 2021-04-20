package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

public class PCAuthConfirmation implements Authentication {
    public long id;
    public byte confirm;

    @Override
    public ByteBuffer asByteBuffer() {
        return null;
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
