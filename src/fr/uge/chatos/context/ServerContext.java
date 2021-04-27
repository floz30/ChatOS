package fr.uge.chatos.context;

import fr.uge.chatos.packet.PCData;
import fr.uge.chatos.packet.Packet;
import fr.uge.chatos.reader.ServerPacketReader;
import fr.uge.chatos.server.Server;
import fr.uge.chatos.server.ServerPacketVisitor;

import java.nio.channels.SelectionKey;
import java.util.Objects;


/**
 * This context contains the server's visitor for a client. The visitor use the received frame to call necessary operation.
 * 
 */
public class ServerContext extends AbstractContext {
    private final ServerPacketVisitor visitor;
    private boolean authenticated;
    private String login;

    public ServerContext(SelectionKey key, Server server) {
        super(key, new ServerPacketReader());
        visitor = new ServerPacketVisitor(server, this);
    }

    @Override
    public void doConnect() {
        throw new UnsupportedOperationException("The server does not support this feature " +
                "because it is reserved for clients.");
    }

    /**
     * Returns the current {@code login} of this context.
     *
     * @return The current {@code login}.
     */
    public String getLogin() {
        return login;
    }

    @Override
    public void processIn() {
        if (authenticated) {
            System.out.println("packet of " + bufferIn.flip().remaining());
            bufferIn.compact();
            treatPacket(new PCData(bufferIn, login));
        } else {
            super.processIn();
        }
    }

    /**
     * Updates the {@code login}, only if the current {@code login} is null.
     * <p>
     * Note : We are not allowed to change our {@code login}.
     * </p>
     *
     * @param login The new login link to this context.
     */
    public void setLogin(String login) {
        if (this.login == null) {
            this.login = Objects.requireNonNull(login);
        }
    }

    /**
     * Updates this context by save his login and indicating that he is authenticated.
     * <p>
     *     Note : to be used only for private connections.
     * </p>
     *
     * @param login the login of the client
     */
    public void successfulAuthentication(String login) {
        authenticated = true;
        this.login = login;
    }

    @Override
    public void treatPacket(Packet packet) {
        super.treatPacket(packet);
        packet.accept(visitor);
    }
}
