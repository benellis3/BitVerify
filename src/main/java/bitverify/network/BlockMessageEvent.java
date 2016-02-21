package bitverify.network;

import bitverify.network.proto.MessageProto.BlockMessage;


public class BlockMessageEvent {

    private final BlockMessage blockMessage;
    private final PeerHandler peer;

    public BlockMessageEvent(BlockMessage m, PeerHandler peer) {
        blockMessage = m;
        this.peer = peer;
    }

    public BlockMessage getBlockMessage() {
        return blockMessage;
    }

    public PeerHandler getPeer() {
        return peer;
    }
}
