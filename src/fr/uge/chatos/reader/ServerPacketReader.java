package fr.uge.chatos.reader;

import fr.uge.chatos.packet.Packet;

import java.nio.ByteBuffer;

import static fr.uge.chatos.utils.OpCode.*;

public class ServerPacketReader implements Reader<Packet> {
    private enum State {DONE, WAITING_PACKET, ERROR}
    private final ConnectionRequestReader connectionRequestReader = new ConnectionRequestReader();
    private final PublicMessageReader publicMessageReader = new PublicMessageReader();
    private final PrivateMessageReader privateMessageReader = new PrivateMessageReader();
    private final PrivateConnectionRequestReader privateConnectionRequestReader = new PrivateConnectionRequestReader();
    private final PrivateConnectionReplyReader pcrr = new PrivateConnectionReplyReader();
    private final PrivateConnectionAuthenticationReader pcar = new PrivateConnectionAuthenticationReader();
    private State currentState = State.WAITING_PACKET;
    private Packet packet;


    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        buffer.flip();
        if (!buffer.hasRemaining()) {
            buffer.compact();
            return ProcessStatus.REFILL;
        }
        var opCode = buffer.get();
        buffer.compact();

        ProcessStatus status = ProcessStatus.ERROR;
        switch (opCode) {
            case CONNECTION_REQUEST -> {
                status = connectionRequestReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = connectionRequestReader.get();
                    connectionRequestReader.reset();
                    currentState = State.DONE;
                }
            }
            case GENERAL_SENDER -> {
                status = publicMessageReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = publicMessageReader.get();
                    publicMessageReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_SENDER -> {
                status = privateMessageReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = privateMessageReader.get();
                    privateMessageReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_REQUEST_SENDER -> {
                status = privateConnectionRequestReader.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = privateConnectionRequestReader.get();
                    privateConnectionRequestReader.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_REPLY -> {
                status = pcrr.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = pcrr.get();
                    pcrr.reset();
                    currentState = State.DONE;
                }
            }
            case PRIVATE_CONNECTION_AUTHENTICATION -> {
                status = pcar.process(buffer);
                if (status == ProcessStatus.DONE) {
                    packet = pcar.get();
                    pcar.reset();
                    currentState = State.DONE;
                }
            }
        }
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
        privateConnectionRequestReader.reset();
        pcar.reset();
        pcrr.reset();
    }
}
