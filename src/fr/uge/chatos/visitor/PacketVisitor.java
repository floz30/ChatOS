package fr.uge.chatos.visitor;

import fr.uge.chatos.packet.*;

public interface PacketVisitor {
    /**
     * OpCode : 0 et 1.
     *
     * @param connectionRequest
     */
    void visit(ConnectionRequest connectionRequest);

    /**
     * OpCode : 2 et 3.
     *
     * @param publicMessage
     */
    void visit(PublicMessage publicMessage);

    /**
     * OpCode : 4 et 5.
     *
     * @param privateMessage
     */
    void visit(PrivateMessage privateMessage);

    /**
     * OpCode : 6, 7 et 8.
     *
     * @param privateConnectionRequest
     */
    void visit(PrivateConnectionRequest privateConnectionRequest);

    /**
     * OpCode : 9 et 10.
     *
     * @param privateConnectionSockets
     */
    void visit(PrivateConnectionSockets privateConnectionSockets);

    /**
     * OpCode : 11.
     *
     * @param privateConnectionConfirmation
     */
    void visit(PrivateConnectionConfirmation privateConnectionConfirmation);

    void visit(PrivateConnectionData privateConnectionData);
}
