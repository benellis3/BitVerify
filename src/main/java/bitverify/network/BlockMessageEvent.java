package bitverify.network;

import bitverify.network.proto.MessageProto;

public class BlockMessageEvent {

    private final MessageProto.BlockMessage blockMessage;

    public BlockMessageEvent(MessageProto.BlockMessage m) {
        blockMessage = m;
    }

    public MessageProto.BlockMessage getBlockMessage() {
        return blockMessage;
    }

}
