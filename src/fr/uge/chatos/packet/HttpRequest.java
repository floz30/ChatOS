package fr.uge.chatos.packet;

import fr.uge.chatos.http.HttpRefillException;
import fr.uge.chatos.visitor.PacketVisitor;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class HttpRequest implements Packet {
    private final String type;
    private final String filename;
    private final String version;
    private final Map<String, String> fields;

    private HttpRequest(String type, String filename, String version, Map<String, String> fields) {
        this.type = type;
        this.filename = filename;
        this.version = version;
        this.fields = Collections.unmodifiableMap(fields);
    }

    public String getFilename() {
        return filename;
    }

    public static HttpRequest create(String request, Map<String, String> fields){
        var tokens = request.split(" ");
        var type = tokens[0];
        var filename = tokens[1];
        var version = tokens[2];
        Map<String,String> fieldsCopied = new HashMap<>();
        for (String s : fields.keySet())
            fieldsCopied.put(s.toLowerCase(),fields.get(s).trim());

        return new HttpRequest(type, filename, version, fieldsCopied);
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
