package bitverify.network;

import bitverify.network.proto.MessageProto;

import java.net.InetSocketAddress;

public class BlockMessageEvent {

    private final MessageProto.BlockMessage blockMessage;
    private final InetSocketAddress peer;

    public BlockMessageEvent(MessageProto.BlockMessage m, InetSocketAddress peer) {
        blockMessage = m;
        this.peer = peer;
    }

    public MessageProto.BlockMessage getBlockMessage() {
        return blockMessage;
    }

    public InetSocketAddress getPeer() {
        return peer;
    }
}
