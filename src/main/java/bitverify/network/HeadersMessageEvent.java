package bitverify.network;

import bitverify.network.proto.MessageProto;

/**
 * Created by Rob on 17/02/2016.
 */

public class HeadersMessageEvent {

    private final MessageProto.HeadersMessage headersMessage;
    private final PeerHandler peer;

    public HeadersMessageEvent(MessageProto.HeadersMessage m, PeerHandler peer) {
        headersMessage = m;
        this.peer = peer;
    }
    public MessageProto.HeadersMessage getHeadersMessage() {
        return headersMessage;
    }

    public PeerHandler getPeer() {
        return peer;
    }
}

