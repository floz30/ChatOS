package fr.uge.chatos.visitor;

import fr.uge.chatos.packet.*;
import fr.uge.chatos.server.Server;

import java.util.Objects;
import java.util.logging.Logger;

public class ServerPacketVisitor implements PacketVisitor {
    private static final Logger logger = Logger.getLogger(ServerPacketVisitor.class.getName());
    private final Server server;
    private final Server.Context context;

    public ServerPacketVisitor(Server server, Server.Context context) {
        this.server = Objects.requireNonNull(server);
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public void visit(Connection connection) {
        var connectionRequest = (ConnectionRequest) connection; // TODO : vérifier si pas plus opti
        // TODO : vérifier si pseudo déjà utilisé
        var login = connectionRequest.sender;
        context.setLogin(login);
        server.registerNewPublicConnection(login, context.getKey());
        server.privateBroadcast(connectionRequest, login);
        logger.info(login + " is now connected");
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
    public void visit(Authentication authentication) {
        var pcc = (PCAuth) authentication;
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
    public void visit(PCData PCData) {
        var pcOptional = server.getPrivateConnection(PCData.getSender(), context.getKey());
        if (pcOptional.isPresent()) {
            var pc = pcOptional.get();
            server.privateConnectionBroadcast(PCData, pc, PCData.getSender());
            logger.info("Transfert de données sur la connexion privée entre " + pc.getPseudos());
        }
    }

    @Override
    public void visit(PrivateFrame request) {
        // TODO Auto-generated method stub
        
    }
}
