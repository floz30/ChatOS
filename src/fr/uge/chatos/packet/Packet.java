package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

public interface Packet {
    ByteBuffer asByteBuffer();
    void accept(PacketVisitor visitor);
}
