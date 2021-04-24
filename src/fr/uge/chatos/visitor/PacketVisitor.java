package fr.uge.chatos.visitor;

import fr.uge.chatos.packet.*;

public interface PacketVisitor {
    /**
     * OpCode : 0 et 1.
     *
     * @param connection
     */
    void visit(Connection connection);

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
     * @param authentication
     */
    void visit(Authentication authentication);

    void visit(PCData PCData);
    
    void visit(PrivateFrame request);
}
