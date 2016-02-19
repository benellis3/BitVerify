package bitverify.network;

import bitverify.block.Block;

/**
 * Created by Alex Day on 18/02/2016.
 */
public class NewMiningProofEvent {
    private Block block;
    public NewMiningProofEvent(Block b) {block = b;}
    public Block getProofBlock() {return block;}
}
