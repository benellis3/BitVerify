package bitverify.network;

import java.net.InetSocketAddress;

/**
 * Created by Rob on 21/02/2016.
 */
public class PeerErrorEvent {
    private final PeerHandler peer;
    private final Throwable cause;

    /**
     * Raised when a peer send/receive thread encounters an exception.
     * Note that multiple errors may be raised by a single peer failing
     * (for example by both the send and receive loops).
     * @param peer the peer that encountered an exception
     * @param cause the exception that occurred
     */
    public PeerErrorEvent(PeerHandler peer, Throwable cause) {
        this.peer = peer;
        this.cause = cause;
    }

    public PeerHandler getPeer() {
        return peer;
    }

    public Throwable getCause() {
        return cause;
    }
}
