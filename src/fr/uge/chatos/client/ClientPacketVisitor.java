package fr.uge.chatos.client;

import fr.uge.chatos.context.ClientContext;
import fr.uge.chatos.http.HTTPException;
import fr.uge.chatos.http.HTTPProcessor;
import fr.uge.chatos.packet.*;
import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Logger;

public class ClientPacketVisitor implements PacketVisitor {
    private static final Logger logger = Logger.getLogger(ClientPacketVisitor.class.getName());
    private final Client client;
    private final ClientContext context;

    public ClientPacketVisitor(Client client, ClientContext context) {
        this.client = Objects.requireNonNull(client);
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public void visit(ConnectionConfirmation connectionConfirmation) {
        if (connectionConfirmation.confirm == (byte) 1) {
            System.out.println("Connection success.");
        } else {
            System.out.println("Connection failed.");
        }
    }

    @Override
    public void visit(ErrorShutdown errorShutdown) {
        System.out.println("Critical error : " + errorShutdown.getMessage());
        client.shutdown();
    }

    @Override
    public void visit(ErrorNoShutdown errorNoShutdown) {
        System.out.println("-> Error : " + errorNoShutdown.getMessage());
    }

    @Override
    public void visit(ConnectionRequest connectionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(PublicMessage publicMessage) {
        System.out.println(publicMessage.sender+ " : " + publicMessage.content);
    }

    @Override
    public void visit(PrivateMessage privateMessage) {
        System.out.println("[Message privé de " + privateMessage.sender + "] : " + privateMessage.content);
    }

    @Override
    public void visit(PCRequest PCRequest) {
        PCRequest.sender = PCRequest.recipient; // vu qu'on utilise le même reader que le serveur on doit changer la valeur
        PCRequest.recipient = client.getLogin();
        var msg = "[** Demande de connexion privée reçue de la part de "+ PCRequest.sender +" **]"
                    + "\n\tPour accepter => /"+ PCRequest.sender +" oui"
                    + "\n\tPour refuser => /"+ PCRequest.sender +" non";
        System.out.println(msg);
    }

    @Override
    public void visit(PCSockets PCSockets) {
        client.startPrivateConnection(PCSockets.port, PCSockets.sender, PCSockets.id);
    }

    @Override
    public void visit(PCAuth auth) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(PCAuthConfirmation pcac) {
        var pcOptional = client.getPrivateConnection(pcac.id);
        if (pcOptional.isPresent()) {
            var entry = pcOptional.get();
            entry.getValue().getContext().successfulAuthentication();
            System.out.println("Connexion privée avec "+ entry.getKey() +" établie.");
        }

    }

    @Override
    public void visit(PCData PCData) {
        // appel client HTTP
        logger.info("HTTP reçu");
    }

    @Override
    public void visit(PrivateFrame privateFrame) {
        var msg = ByteBuffer.allocate(3);
        var bb = privateFrame.asByteBuffer();
        Charset ASCII = StandardCharsets.US_ASCII;
        
        bb.flip();
        for (var b = 0; b < 3; b++) {
            msg.put(bb.get(b));
        }
        
        if (ASCII.decode(msg.flip()).toString().equals("GET")) {
            var httpbb = Packets.ofHTTP(bb, privateFrame);
            context.queueMessage(httpbb.flip());
        } else {
            bb.compact();
            try {
                HTTPProcessor.processHTTP(bb);
            } catch (HTTPException e) {
                System.out.println("HTTPException encountered with [" + privateFrame.getDst() +"]:\n"+ e.getMessage());
            }
        }
    }
}
