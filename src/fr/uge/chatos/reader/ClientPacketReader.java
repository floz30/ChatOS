package fr.uge.chatos.reader;

import fr.uge.chatos.packet.ConnectionConfirmation;
import fr.uge.chatos.packet.Packet;

import java.nio.ByteBuffer;

import static fr.uge.chatos.utils.OpCode.*;

public class ClientPacketReader implements Reader<Packet> {
    private enum State {DONE, WAITING_PACKET, ERROR}
    private final ByteReader byteReader = new ByteReader();
    private final ConnectionRequestReader connectionRequestReader = new ConnectionRequestReader();
    private final PublicMessageReader publicMessageReader = new PublicMessageReader();
    private final PrivateMessageReader privateMessageReader = new PrivateMessageReader();
    private final PCRequestReader PCRequestReader = new PCRequestReader();
    private final PCSocketsReader PCSocketsReader = new PCSocketsReader();
    private final PCAuthConfirmationReader pcar = new PCAuthConfirmationReader();
    private final HttpRequestReader httpRequestReader = new HttpRequestReader();
    private final HttpDataReader httpDataReader = new HttpDataReader();
    private final ErrorShutdownReader errorShutdownReader =  new ErrorShutdownReader();
    private final ErrorNoShutdownReader errorNoShutdownReader = new ErrorNoShutdownReader();
    private State currentState = State.WAITING_PACKET;
    private Packet packet;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        buffer.flip();
        var internalBuffer = ByteBuffer.allocate(buffer.remaining());
        internalBuffer.put(buffer);
        internalBuffer.flip();
        if (!internalBuffer.hasRemaining()) {
            buffer.compact();
            return ProcessStatus.REFILL;
        }
        var opCode = internalBuffer.get();
        internalBuffer.compact();

        var status = ProcessStatus.ERROR;
        switch (opCode) {
            case CONNECTION_ACCEPT -> {
                status = byteReader.process(internalBuffer); // on utilise directement un ByteReader (+ simple)
                if (status == ProcessStatus.DONE) {
                    packet = new ConnectionConfirmation(byteReader.get());
                    connectionRequestReader.reset();
                    currentState = State.DONE;
                }
            }
            case GENERAL_RECEIVER -> {
                status = publicMessageReader.process(internalBuffer);
                if (status == ProcessStatus.DONE) {
                    packet = publicMessageReader.get();
                    publicMessageReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_RECEIVER -> {
                status = privateMessageReader.process(internalBuffer);
                if (status == ProcessStatus.DONE) {
                    packet = privateMessageReader.get();
                    privateMessageReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_REQUEST_RECEIVER -> {
                status = PCRequestReader.process(internalBuffer);
                if (status == ProcessStatus.DONE) {
                    packet = PCRequestReader.get();
                    PCRequestReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_SOCKETS -> {
                status = PCSocketsReader.process(internalBuffer);
                if (status == ProcessStatus.DONE) {
                    packet = PCSocketsReader.get();
                    PCSocketsReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_CONFIRMATION -> {
                status = pcar.process(internalBuffer);
                if (status == ProcessStatus.DONE) {
                    packet = pcar.get();
                    pcar.reset();
                    currentState = State.DONE;
                }
            } // TODO : ajouter les cas des paquets d'erreur
            case 71 -> { // GET request
                internalBuffer.flip();
                var b = ByteBuffer.allocate(internalBuffer.remaining() + Byte.BYTES);
                b.put(opCode).put(internalBuffer);
                status = httpRequestReader.process(b);
                if (status == ProcessStatus.DONE) {
                    packet = httpRequestReader.get();
                    httpRequestReader.reset();
                    currentState = State.DONE;
                }
            }
            case 72 -> { // HTTP response
                internalBuffer.flip();
                var b = ByteBuffer.allocate(internalBuffer.remaining() + Byte.BYTES);
                b.put(opCode).put(internalBuffer);
                status = httpDataReader.process(b);
                if (status == ProcessStatus.DONE) {
                    packet = httpDataReader.get();
                    httpDataReader.reset();
                    currentState = State.DONE;
                }
            }
            case ERROR_NO_SHUTDOWN -> {
                status = errorNoShutdownReader.process(internalBuffer);
                if (status == ProcessStatus.DONE) {
                    packet = errorNoShutdownReader.get();
                    errorNoShutdownReader.reset();
                    currentState = State.DONE;
                }
            }
            case ERROR_SHUTDOWN -> {
                status = errorShutdownReader.process(internalBuffer);
                if (status == ProcessStatus.DONE) {
                    packet = errorShutdownReader.get();
                    errorShutdownReader.reset();
                    currentState = State.DONE;
                }
            }
        }
        buffer.compact();
        return status;
    }

    @Override
    public Packet get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return packet;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_PACKET;
        packet = null; // à revoir
        connectionRequestReader.reset();
        publicMessageReader.reset();
        privateMessageReader.reset();
        PCRequestReader.reset();
        pcar.reset();
        PCSocketsReader.reset();
    }
}
