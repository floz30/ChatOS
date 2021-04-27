package fr.uge.chatos.context;

import fr.uge.chatos.client.Client;
import fr.uge.chatos.client.ClientPacketVisitor;
import fr.uge.chatos.packet.Packet;
import fr.uge.chatos.packet.Packets;
import fr.uge.chatos.reader.ClientPacketReader;

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * This key attachment allows the finalization of the connection to server on the public port.
 */

public class ClientPublicContext extends AbstractContext implements ClientContext {
    private final ClientPacketVisitor visitor;
    private final Client client;

    public ClientPublicContext(SelectionKey key, Client client) {
        super(key, new ClientPacketReader());
        visitor = new ClientPacketVisitor(client, this);
        this.client = client;
    }

    @Override
    public void doConnect() throws IOException {
        super.doConnect();
        super.queueMessage(Packets.ofRequestConnection(client.getLogin()).flip());
    }

    @Override
    public void treatPacket(Packet packet) {
        super.treatPacket(packet);
        packet.accept(visitor);
    }

}
