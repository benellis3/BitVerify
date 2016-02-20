package bitverify.network;

import bitverify.network.proto.MessageProto;

import java.net.InetSocketAddress;

public class BlockMessageEvent {

    private final MessageProto.BlockMessage blockMessage;
    private final InetSocketAddress peerAddress;

    public BlockMessageEvent(MessageProto.BlockMessage m, InetSocketAddress peerAddress) {
        blockMessage = m;
        this.peerAddress = peerAddress;
    }

    public MessageProto.BlockMessage getBlockMessage() {
        return blockMessage;
    }

    public InetSocketAddress getPeerAddress() {
        return peerAddress;
    }
}
