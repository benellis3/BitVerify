package bitverify.network;

/**
 * Created by Rob on 25/02/2016.
 */
public class BlockTimeoutEvent {
    private PeerHandler peer;

    public BlockTimeoutEvent(PeerHandler peer) {
        this.peer = peer;
    }

    public PeerHandler getPeer() {
        return peer;
    }
}
