package fr.uge.chatos.reader;

import java.nio.ByteBuffer;


public interface Reader <T> {
    /**
     * A status indicating what happened during the process.
     */
    enum ProcessStatus { DONE, REFILL, ERROR };

    /**
     * Extracts data from {@code buffer}.
     * <p>
     * Note: {@code buffer} is in <b>write-mode</b> before and after the call.
     * </p>
     *
     * @param buffer buffer to process
     * @return a status which indicate if the process was successful, if there is not
     * enough data in the {@code buffer} or if an error occurred
     */
    ProcessStatus process(ByteBuffer buffer);

    /**
     * Returns data extracted from the buffer, only if {@code processData}
     * method returned the code {@code DONE}. If not throws an {@code IllegalStateException}.
     *
     * @return full data
     * @throws IllegalStateException if {@code processData} did not return the code {@code DONE}
     */
    T get();

    /**
     * Resets this object to be reused.
     */
    void reset();
}
