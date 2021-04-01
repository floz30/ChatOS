package fr.uge.chatos.reader;

import fr.uge.chatos.utils.Message;

import java.nio.ByteBuffer;

/**
 * This class allows us to read a packet in this format :
 * int | String | int | String
 */
public class MessageReader implements Reader<Message> {
    private enum State {DONE, WAITING_LOGIN, WAITING_MSG, ERROR}
    private final StringReader sr = new StringReader();
    private Message message = new Message();
    private State currentState = State.WAITING_LOGIN;
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (currentState == State.DONE || currentState == State.ERROR) {
            throw new IllegalStateException();
        }

        while (currentState != State.DONE) {
            var status = sr.process(buffer);
            if (status == ProcessStatus.DONE) {
                if (currentState == State.WAITING_LOGIN) {
                    message.setLogin(sr.get());
                    sr.reset();
                    currentState = State.WAITING_MSG;
                } else {
                    message.setContent(sr.get());
                    currentState = State.DONE;
                }
            } else {
                return status;
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public Message get() {
        if (currentState != State.DONE) {
            throw new IllegalStateException();
        }
        return message;
    }

    @Override
    public void reset() {
        currentState = State.WAITING_LOGIN;
        message = new Message();
        sr.reset();
    }
}
