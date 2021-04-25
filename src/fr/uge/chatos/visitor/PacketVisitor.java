package fr.uge.chatos.visitor;

import fr.uge.chatos.packet.*;

public interface PacketVisitor {

    void visit(ErrorShutdown errorShutdown);

    void visit(ErrorNoShutdown errorNoShutdown);

    /**
     * OpCode : 0.
     *
     * @param connectionRequest
     */
    void visit(ConnectionRequest connectionRequest);

    /**
     * OpCode : 1.
     *
     * @param connectionConfirmation
     */
    void visit(ConnectionConfirmation connectionConfirmation);

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
     * @param PCRequest
     */
    void visit(PCRequest PCRequest);

    /**
     * OpCode : 9 et 10.
     *
     * @param PCSockets
     */
    void visit(PCSockets PCSockets);

    /**
     * OpCode : 11.
     *
     * @param auth
     */
    void visit(PCAuth auth);

    /**
     * OpCode : 11.
     *
     * @param authConfirmation
     */
    void visit(PCAuthConfirmation authConfirmation);

    /**
     * private connection data receive by server.
     *
     * @param PCData
     */
    void visit(PCData PCData);
    
    /**
     * frame from private connection receive by client.
     *
     * @param PrivateFrame
     */
    void visit(PrivateFrame request);
}
