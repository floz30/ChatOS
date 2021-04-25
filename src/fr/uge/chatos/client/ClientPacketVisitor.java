package fr.uge.chatos.client;

import fr.uge.chatos.context.ClientContext;
import fr.uge.chatos.context.ClientPrivateContext;
import fr.uge.chatos.packet.*;
import fr.uge.chatos.visitor.PacketVisitor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Using the visitor pattern, any packet receive from the server will trigger a certain operation.
 */

public class ClientPacketVisitor implements PacketVisitor {
    private final Client client;
    private final ClientContext context;

    public ClientPacketVisitor(Client client, ClientContext context) {
        this.client = Objects.requireNonNull(client);
        this.context = Objects.requireNonNull(context);
    }

    /**
     * Notice the status of connection request
     * 
     */
    
    @Override
    public void visit(ConnectionConfirmation connectionConfirmation) {
        if (connectionConfirmation.confirm == (byte) 1) {
            System.out.println("Connection success.");
        } else {
            System.out.println("Connection failed.");
        }
    }

    /**
     * display error and shutdown connection with server.
     */
    @Override
    public void visit(ErrorShutdown errorShutdown) {
        System.out.println("Critical error : " + errorShutdown.getMessage());
        client.shutdown();
    }

    /**
     * display error.
     */
    
    @Override
    public void visit(ErrorNoShutdown errorNoShutdown) {
        System.out.println("-> Error : " + errorNoShutdown.getMessage());
    }

    
    @Override
    public void visit(ConnectionRequest connectionRequest) {
        throw new UnsupportedOperationException();
    }

    /**
     * Display the message from a user
     */
    
    @Override
    public void visit(PublicMessage publicMessage) {
        System.out.println(publicMessage.sender+ " : " + publicMessage.content);
    }

    /**
     * Display a private message
     */
    @Override
    public void visit(PrivateMessage privateMessage) {
        System.out.println("[Message privé de " + privateMessage.sender + "] : " + privateMessage.content);
    }

    /**
     * Display the exact command for the client in order to accept the private connection
     */
    
    @Override
    public void visit(PCRequest PCRequest) {
        PCRequest.sender = PCRequest.recipient; // vu qu'on utilise le même reader que le serveur on doit changer la valeur
        PCRequest.recipient = client.getLogin();
        var msg = "[** Demande de connexion privée reçue de la part de "+ PCRequest.sender +" **]"
                    + "\n\tPour accepter => /"+ PCRequest.sender +" oui"
                    + "\n\tPour refuser => /"+ PCRequest.sender +" non";
        System.out.println(msg);
    }

    /**
     * Connect the client to the private port.
     */
    @Override
    public void visit(PCSockets PCSockets) {
        client.startPrivateConnection(PCSockets.port, PCSockets.sender, PCSockets.id);
    }

    @Override
    public void visit(PCAuth auth) {
        throw new UnsupportedOperationException();
    }

    /**
     * Display server's response to private connection authentication.
     */
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
    public void visit(PCData data) {
        throw new UnsupportedOperationException();
    }

    /**
     * Prepare the packet containing the GET request
     * 
     * @param httpRequest
     */
    
    @Override
    public void visit(HttpRequest httpRequest) {
        var path = client.getRepository() + "/" + httpRequest.getFilename();
        var buffer = Packets.ofHTTPResponse(path);
        context.queueMessage(buffer.flip());
    }

    /**
     * Prepare the packet containing the HTTP header + content
     * 
     * @param httpData
     */
    
    @Override
    public void visit(HttpData httpData) {
        if (httpData.getHeader().getCode() == 404) {
            System.out.println("-> Erreur : fichier non trouvé");
            return;
        }
        if (httpData.getHeader().getContentType().equals("txt")) {
            System.out.println("Contenu du fichier : \n\t" + httpData.getBody());
        } else {
            var c = (ClientPrivateContext) context;
            var path = client.getRepository() + "/" + c.getFileRequested();
            try (var s = new FileOutputStream(path)) {
                s.write(httpData.getContentBody());
            } catch (IOException e) {
                System.out.println("-> Erreur lors de la sauvegarde du fichier");
            }
        }
    }

}
