package bitverify.network;

import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.block.Block;
import bitverify.network.proto.MessageProto.*;
import com.google.protobuf.ByteString;
import com.j256.ormlite.logger.Log;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;


/**
 * Handles sending a single request for headers to a peer, then awaiting the response.
 */
public class HeadersFuture extends ProtocolFuture<List<Block>> {
    private final PeerHandler peer;
    private final List<byte[]> fromBlockIDs;

    public HeadersFuture(PeerHandler peer, List<byte[]> fromBlockIDs, Bus bus) {
        super(bus);
        this.peer = peer;
        this.fromBlockIDs = fromBlockIDs;
    }

    @Override
    public void run() {
        // send a GetBlockHeaders message
        // ask for blocks from our latest known block
        GetHeadersMessage.Builder getHeadersMessageBuilder = GetHeadersMessage.newBuilder();
        for (byte[] blockID : fromBlockIDs) {
            getHeadersMessageBuilder.addFrom(ByteString.copyFrom(blockID));
        }
        GetHeadersMessage getHeadersMessage = getHeadersMessageBuilder.build();

        Message m = Message.newBuilder()
                .setType(Message.Type.GET_HEADERS)
                .setGetHeaders(getHeadersMessage)
                .build();

        // if peer is shut down, return null straight away
        if (peer.send(m)) {
            // register for replies once we've sent the request
            bus.register(this);
        } else {
            resultLatch.countDown();
        }
    }

    @Subscribe
    public void onHeadersMessage(HeadersMessageEvent e) {
        // deregister now we've got our response
        bus.unregister(this);

        List<ByteString> serializedHeaders = e.getHeadersMessage().getHeadersList();
        List<Block> headers = new ArrayList<>(serializedHeaders.size());

        try {
            for (ByteString bytes : serializedHeaders)
                headers.add(Block.deserialize(bytes.toByteArray()));
            result = headers;
        } catch (IOException ex) {
            // a header was invalidly formatted, we will discard the sequence and re-request from another peer
        }

        // notify that we got a response (even if it was rubbish)
        resultLatch.countDown();
    }
}

