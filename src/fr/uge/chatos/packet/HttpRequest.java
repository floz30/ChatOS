package fr.uge.chatos.packet;

import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represent a frame containing HTTP Request.
 *
 */
public class HttpRequest implements Packet {
    private final String filename;

    private HttpRequest(String filename) {
        this.filename = filename;
    }

    /**
     * Returns the filename in which the request was made.
     * @return
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Build a HTTP request 
     * 
     */
    public static HttpRequest create(String request){
        var tokens = request.split(" ");
        var filename = tokens[1];

        return new HttpRequest(filename);
    }

    @Override
    public ByteBuffer asByteBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(PacketVisitor visitor) {
        visitor.visit(this);
    }
}
