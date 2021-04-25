package fr.uge.chatos.server;

import fr.uge.chatos.context.ServerContext;
import fr.uge.chatos.packet.*;
import fr.uge.chatos.visitor.PacketVisitor;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Using the visitor pattern, any packet receive from a client will trigger a certain operation.
 */

public class ServerPacketVisitor implements PacketVisitor {
    private static final Logger logger = Logger.getLogger(ServerPacketVisitor.class.getName());
    private final Server server;
    private final ServerContext context;

    public ServerPacketVisitor(Server server, ServerContext context) {
        this.server = Objects.requireNonNull(server);
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public void visit(ErrorShutdown errorShutdown) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(ErrorNoShutdown errorNoShutdown) {
        throw new UnsupportedOperationException();
    }

    /**
     * Accept or decline the connection request to the server. It will decline if the username is already use by someone else.
     */
    @Override
    public void visit(ConnectionRequest connectionRequest) {
        var login = connectionRequest.sender;
        context.setLogin(login);
        if (server.registerNewPublicConnection(login, context.getKey())) {
            server.privateBroadcast(connectionRequest, login);
            logger.info(login + " is now connected");
        } else {
            var error = new ErrorShutdown("The pseudo \"" + login + "\" is already used by someone else.");
            server.privateBroadcast(error, context.getKey());
            context.silentlyClose();
        }
    }

    @Override
    public void visit(ConnectionConfirmation connectionConfirmation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(PublicMessage publicMessage) {
        server.publicBroadcast(publicMessage);
        logger.info(publicMessage.sender + " send a public message");
    }

    @Override
    public void visit(PrivateMessage privateMessage) {
        server.privateBroadcast(privateMessage, privateMessage.recipient);
        logger.info(privateMessage.sender + " send a private message to " + privateMessage.recipient);
    }

    @Override
    public void visit(PCRequest pcr) {
        pcr.sender = context.getLogin();
        if (server.checkIfPrivateConnectionExists(pcr.sender, pcr.recipient)) {
            return; // paquet ignoré car connexion déjà existante
        }
        var id = server.getNewId();
        server.registerNewPrivateConnection(id, pcr.sender, pcr.recipient);
        server.privateBroadcast(pcr, pcr.recipient);
        logger.info("Demande de confirmation pour la connexion privée entre : " + pcr.sender + " et " + pcr.recipient);
    }

    @Override
    public void visit(PCSockets pcs) {
        pcs.port = server.getPrivatePort();
        pcs.recipient = context.getLogin();
        if (pcs.reply == 0) {
            server.deletePrivateConnection(pcs.sender, pcs.recipient);
            // TODO : avertir le sender que la connexion a été refusée
            logger.info("Refus de connexion privée entre " + pcs.sender + " et " + pcs.recipient);
            return;
        }
        var pcOptional = server.getPrivateConnection(pcs.sender, pcs.recipient);
        if (pcOptional.isPresent()) {
            var pc = pcOptional.get();
            pcs.id = pc.getId();
            server.privateBroadcast(pcs, pcs.sender);
            server.privateBroadcast(pcs, pcs.recipient);
            logger.info("Envoi de l'identifiant et du numéro de port");
        }

    }

    @Override
    public void visit(PCAuth pcc) {
        var pcOptional = server.getPrivateConnection(pcc.login, pcc.id);
        if (pcOptional.isPresent()) {
            var pc = pcOptional.get();
            if (!pc.addNewConnection()) {
                logger.info("Erreur : trop de client se sont connecté sur cette connexion privée.");
                return;
            }
            pc.updateOneContext(pcc.login, context);

            if (pc.getNbConnection() == 2) {
                for (var pseudo : pc.getPseudos()) {
                    server.privateConnectionBroadcast(pcc, pc, pseudo);
                }
                server.successfulAuthentication(pc);
                logger.info("Envoi de la confirmation de l'établissement de la connexion privée");
            }
        }
    }

    @Override
    public void visit(PCAuthConfirmation authConfirmation) {
        throw new UnsupportedOperationException();
    }

    /**
     * Send the packet to the attributed private socket
     * 
     * @param data
     */
    
    @Override
    public void visit(PCData data) {
        var pcOptional = server.getPrivateConnection(data.getSender(), context.getKey());
        if (pcOptional.isPresent()) {
            var pc = pcOptional.get();
            server.privateConnectionBroadcast(data, pc, data.getSender());
            logger.info("Transfert de données sur la connexion privée entre " + pc.getPseudos());

        }

    }

    @Override
    public void visit(HttpRequest httpRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(HttpData httpData) {
        throw new UnsupportedOperationException();
    }

}
