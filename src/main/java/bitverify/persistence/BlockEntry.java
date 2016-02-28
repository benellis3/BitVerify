package bitverify.persistence;

import bitverify.block.Block;
import bitverify.entries.Entry;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;

import java.util.UUID;

/**
 * Represents an entry belonging to a particular block. Used to implement many-to-many mapping in database.
 */
public class BlockEntry {
    @DatabaseField(dataType = DataType.BYTE_ARRAY, columnDefinition = "VARBINARY(32)", uniqueCombo = true)
    private byte[] blockID;
    @DatabaseField(uniqueCombo = true)
    private UUID entryID;

    BlockEntry() {}

    public BlockEntry(byte[] blockID, UUID entryID) {
        this.blockID = blockID;
        this.entryID = entryID;
    }
}
