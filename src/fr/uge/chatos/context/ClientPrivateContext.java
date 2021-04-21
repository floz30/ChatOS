package fr.uge.chatos.context;

import fr.uge.chatos.client.Client;
import fr.uge.chatos.client.ClientPacketVisitor;
import fr.uge.chatos.packet.PCData;
import fr.uge.chatos.packet.Packet;
import fr.uge.chatos.packet.Packets;
import fr.uge.chatos.reader.ClientPacketReader;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public class ClientPrivateContext extends AbstractContext implements ClientContext {
    private final ClientPacketVisitor visitor;
    private final long id;
    private final Client client;
    private boolean authenticated;

    public ClientPrivateContext(SelectionKey key, Client client, long id) {
        super(key, new ClientPacketReader());
        visitor = new ClientPacketVisitor(client, this);
        this.id = id;
        this.client = client;
    }

    @Override
    public void doConnect() throws IOException {
        super.doConnect();
        super.queueMessage(Packets.ofAuthentication(id, client.getLogin()).flip());
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public long getId() {
        return id;
    }

    @Override
    public void processIn() {
        if (authenticated) {
            treatPacket(new PCData(bufferIn, ""));
        } else {
            super.processIn();
        }
    }

    /**
     * Updates this context by indicating that he is authenticated.
     */
    public void successfulAuthentication() {
        authenticated = true;
    }

    @Override
    public void treatPacket(Packet packet) {
        super.treatPacket(packet);
        packet.accept(visitor);
    }
}
