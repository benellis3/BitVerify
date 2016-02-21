package bitverify.network;


/**
 * Created by benellis on 08/02/2016.
 */
public class GetPeersEvent {
    private final PeerHandler peer;

    public GetPeersEvent(PeerHandler peer) {
        this.peer = peer;
    }

    public PeerHandler getPeer() {
        return peer;
    }
}
