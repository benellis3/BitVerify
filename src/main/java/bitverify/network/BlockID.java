package bitverify.network;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.Base64;

/**
 * Created by Rob on 23/02/2016.
 */
public class BlockID {
    private byte[] blockID;

    public BlockID(byte[] blockID) {
        this.blockID = blockID;
    }

    public BlockID(ByteString blockID) {
        this.blockID = blockID.toByteArray();
    }

    public byte[] getBlockID() {
        return blockID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockID blockID1 = (BlockID) o;
        return Arrays.equals(blockID, blockID1.blockID);

    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(blockID);
    }

    @Override
    public String toString() {
        return Base64.getEncoder().encodeToString(blockID);
    }
}
