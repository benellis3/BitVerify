package bitverify.network;

import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.network.proto.MessageProto;

import java.net.InetSocketAddress;
import java.util.Set;

/**
 * Created by Rob on 17/02/2016.
 */

public class HeadersMessageEvent {

    private final MessageProto.HeadersMessage headersMessage;

    public HeadersMessageEvent(MessageProto.HeadersMessage m) {
        headersMessage = m;
    }
    public MessageProto.HeadersMessage getHeadersMessage() {
        return headersMessage;
    }

}

