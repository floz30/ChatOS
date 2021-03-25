package fr.uge.chatos.client;

import fr.uge.chatos.reader.MessageReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

public class Context {
    private final SelectionKey key;
    private final SocketChannel socket;
    private final ByteBuffer bufferIn = ByteBuffer.allocateDirect(1024);
    private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(1024);
    private final Queue<ByteBuffer> queue = new LinkedList<>();
    private final MessageReader messageReader = new MessageReader();
    private boolean closed;

    public Context(SelectionKey key) {
        this.key = Objects.requireNonNull(key);
        socket = (SocketChannel) key.attachment();
    }

    private void processIn() {
        // TODO : optimiser cette méthode
        bufferIn.flip();
        var op = bufferIn.get();
        bufferIn.compact();
        for (;;) {
            switch (messageReader.processData(bufferIn)) {
                case DONE -> {
                    var msg = messageReader.get();
                    if (op == 3) {
                        System.out.println(msg.getLogin() + " : " + msg.getContent());
                    } else if (op == 5) {
                        System.out.println("Message privé de " + msg.getLogin() + " : " + msg.getContent());
                    }
                    messageReader.reset();
                }
                case REFILL -> { return; }
                case ERROR -> {
                    silentlyClose();
                    return;
                }
            }
        }
    }

    void queueMessage(ByteBuffer message) {
        queue.add(message);
        processOut();
        updateInterestOps();
    }

    private void processOut() {
        while (!queue.isEmpty()) {
            var buffer = queue.peek();
            if (buffer.remaining() <= bufferOut.remaining()) {
                queue.remove();
                bufferOut.put(buffer);
            }
        }
    }

    private void updateInterestOps() {
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

    private void silentlyClose() {
        try {
            socket.close();
        } catch (IOException ignored) { }
    }

    void doRead() throws IOException {
        if (socket.read(bufferIn) == -1) {
            closed = true;
        }
        processIn();
        updateInterestOps();
    }

    void doWrite() throws IOException {
        bufferOut.flip();
        socket.write(bufferOut);
        bufferOut.compact();
        processOut();
        updateInterestOps();
    }

    void doConnect() throws IOException {
        if (!socket.finishConnect()) {
            return;
        }
        key.interestOps(SelectionKey.OP_WRITE);
    }
}
