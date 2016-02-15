package bitverify.persistence;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import static org.junit.Assert.assertEquals;

import com.j256.ormlite.table.TableUtils;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Random;

/**
 * Created by Rob on 13/02/2016.
 */
public class DatabaseStoreTest {
    static class TestObject2 {
        @DatabaseField(dataType = DataType.BYTE_ARRAY, uniqueIndex = true)
        private byte[] blockID;
        @DatabaseField()
        private int entryID;

        TestObject2() { }

        public TestObject2(byte[] blockID, int entryID) {
            this.blockID = blockID;
            this.entryID = entryID;
        }
    }

    static class TestObject {
        @DatabaseField(dataType = DataType.BYTE_ARRAY, index=true, columnDefinition = "VARBINARY(16)")
        private byte[] ID;
        @DatabaseField
        private String name;

        TestObject() { }

        public TestObject(byte[] ID, String name) {
            this.ID = ID;
            this.name = name;
        }
    }

    @Test
    public void TestUnique() throws SQLException {


        // make a database
        ConnectionSource cs = new JdbcConnectionSource("jdbc:h2:mem:account");
        TableUtils.createTableIfNotExists(cs, TestObject2.class);
        Dao<TestObject2, byte[]> testDao = DaoManager.createDao(cs, TestObject2.class);

        TestObject2 o = new TestObject2(new byte[] {1,2,3}, 5);
        TestObject2 o2 = new TestObject2(new byte[] {1,2,3}, 6);
        testDao.create(o);
        try {
            testDao.create(o2);
        } catch (SQLException e) {
            System.out.print("Cause: ");
            System.out.println(e.getCause());
            System.out.print("Cause code: ");
            System.out.println(((SQLException) e.getCause()).getErrorCode());
            System.out.print("Cause state: ");
            System.out.println(((SQLException) e.getCause()).getSQLState());
        }

        assertEquals(testDao.countOf(), 1);

    }

    @Test
    public void TestVarbinary() throws SQLException {

        // make a database
        ConnectionSource cs = new JdbcConnectionSource("jdbc:h2:mem:account");
        TableUtils.createTableIfNotExists(cs, TestObject.class);
        Dao<TestObject, byte[]> testDao = DaoManager.createDao(cs, TestObject.class);

        Random r = new Random();


        byte[] special = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};

        for (int i = 0; i < 100; i++) {
            byte[] b = new byte[16];
            r.nextBytes(b);
            testDao.create(new TestObject(b, "bob" + i));
        }

        testDao.create(new TestObject(special, "special"));

        for (int i = 100; i < 200; i++) {
            byte[] b = new byte[16];
            r.nextBytes(b);
            testDao.create(new TestObject(b, "bob" + i));
        }

        assertEquals(testDao.queryForEq("ID", special).get(0).name, "special");
    }


}
