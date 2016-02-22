package bitverify.network;

import bitverify.network.proto.MessageProto.BlockNotFoundMessage;


/**
 * Created by Rob on 21/02/2016.
 */
public class BlockNotFoundMessageEvent {
    private final BlockNotFoundMessage message;
    private final PeerHandler peer;

    public BlockNotFoundMessageEvent(BlockNotFoundMessage m, PeerHandler peer) {
        this.message = m;
        this.peer = peer;
    }

    public BlockNotFoundMessage getMessage() {
        return message;
    }

    public PeerHandler getPeer() {
        return peer;
    }
}
