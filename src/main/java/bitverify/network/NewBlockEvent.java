package bitverify.network;

import bitverify.block.Block;

/**
 * Created by benellis on 08/02/2016.
 */
public class NewBlockEvent {
    private Block block;
    public NewBlockEvent(Block b) {block = b;}
    public Block getNewBlock() {return block;}
}
