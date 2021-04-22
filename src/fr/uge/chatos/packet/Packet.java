package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;

/**
 * This interface represents a packet exchanged between the server
 * and the clients.
 */
public interface Packet {
    /**
     * Returns a {@code ByteBuffer} that contains all of the
     * class information.
     *
     * @return a {@code ByteBuffer} in <b>read-mode</b>
     */
    ByteBuffer asByteBuffer();

    /**
     * Visit the specified visitor with this class.
     *
     * @param visitor the visitor to visit
     */
    void accept(PacketVisitor visitor);
}
