package fr.uge.chatos.context;

import fr.uge.chatos.packet.Packet;
import fr.uge.chatos.reader.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

class AbstractContext implements Context {
    private static final int MAX_BUFFER_SIZE = 1_024;
    protected final ByteBuffer bufferIn = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
    private final SocketChannel socket;
    private final SelectionKey key;
    private final Queue<ByteBuffer> queue = new LinkedList<>();
    private final Reader<Packet> reader;
    private boolean closed;

    AbstractContext(SelectionKey key, Reader<Packet> reader) {
        this.key = Objects.requireNonNull(key);
        socket = (SocketChannel) key.channel();
        this.reader = Objects.requireNonNull(reader);
    }

    @Override
    public void doConnect() throws IOException {
        if (!socket.finishConnect()) {
            return;
        }
        key.interestOps(SelectionKey.OP_WRITE);
    }

    @Override
    public void doRead() throws IOException {
        if (socket.read(bufferIn) == -1) {
            closed = true;
        }
        processIn();
        updateInterestOps();
    }

    @Override
    public void doWrite() throws IOException {
        bufferOut.flip();
        socket.write(bufferOut);
        bufferOut.compact();
        processOut();
        updateInterestOps();
    }

    public SelectionKey getKey() {
        return key;
    }

    @Override
    public void processIn() {
        for (;;) {
            var status = reader.process(bufferIn);
            switch (status) {
                case ERROR -> silentlyClose();
                case REFILL -> { return; }
                case DONE -> {
                    var packet = reader.get();
                    reader.reset();
                    treatPacket(packet);
                }
            }
        }
    }

    @Override
    public void processOut() {
        while (!queue.isEmpty()) {
            var buffer = queue.peek();
            if (buffer.remaining() <= bufferOut.remaining()) {
                queue.remove();
                bufferOut.put(buffer);
            } else {
                break;
            }
        }
    }

    @Override
    public void queueMessage(ByteBuffer buffer) {
        queue.add(buffer);
        processOut();
        updateInterestOps();
    }

    @Override
    public void silentlyClose() {
        try {
            socket.close();
        } catch (IOException ignored) { }
    }

    @Override
    public void treatPacket(Packet packet) {
        Objects.requireNonNull(packet);
    }

    @Override
    public void updateInterestOps() {
        var interestOps = 0;
        if (!closed && bufferIn.hasRemaining()) {
            interestOps |= SelectionKey.OP_READ;
        }
        if (bufferOut.position() != 0) {
            interestOps |= SelectionKey.OP_WRITE;
        }
        if (interestOps == 0) {
            silentlyClose();
            return;
        }
        key.interestOps(interestOps);
    }

}
