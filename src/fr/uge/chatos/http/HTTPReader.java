package fr.uge.chatos.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class HTTPReader {
    private final ByteBuffer bb;

    public HTTPReader(ByteBuffer bb) {
        this.bb = bb;
    }

    /**
     *
     * @return The ASCII string terminated by CRLF without the CRLF
     * @throws HTTPException If the connection is closed before a line could be read
     * @throws IOException If an other I/O error occurs
     */
    private String readLineCRLF(){
        var sb = new StringBuilder();
        var cr = false;
        var nl = false;
        bb.flip();
        while(!nl) {
            while(bb.hasRemaining()) {
                char c = (char) bb.get();
                if(c=='\r') {
                    cr = true;
                }
                else if(cr) {
                    if(c=='\n') {
                        nl = true;
                    }
                    cr=false;
                }
                sb.append(c);
            }
        }
        bb.compact();
        return sb.toString().substring(0, sb.length() - 2);
    }

    /**
     *
     * @return The HTTPHeader object corresponding to the header read.
     * @throws HTTPException If the connection is closed before a line could be read or
     *                       if the header is ill-formed
     * @throws IOException If an other I/O error occurs
     */
    public HTTPHeader readHeader() throws HTTPException {
        var firstLine = readLineCRLF();
        var fields = new HashMap<String, String>();
        String line;
        while (!(line = readLineCRLF()).isEmpty()) {
            var content = line.split(": ");
            fields.put(content[0], content[1]);
        }
        return HTTPHeader.create(firstLine, fields);
    }

    /**
     *
     * @param size Number of bytes to read
     * @return A ByteBuffer in write-mode containing size bytes read on the socket
     * @throws HTTPException If the connection is closed before a line could be read
     * @throws IOException If an other I/O error occurs
     */
    public ByteBuffer readBytes(int size){
        bb.flip();
        var buff = ByteBuffer.allocate(size);
        var i = 0;
        while (i<size) {
            buff.put(bb.get());
            i+=1;
        }
        bb.compact();
        return buff;
    }
}