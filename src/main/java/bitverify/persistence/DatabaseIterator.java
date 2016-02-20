package bitverify.persistence;

import bitverify.entries.Entry;
import com.j256.ormlite.dao.CloseableIterator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Created by Rob on 20/02/2016.
 */
public class DatabaseIterator<T> implements AutoCloseable {
    CloseableIterator<T> ci;
    T current;

    public DatabaseIterator(CloseableIterator<T> ci) {
        this.ci = ci;
    }

    /**
     * Moves the iterator to the next item in the sequence.
     * @return true if there is a next item, false if there are no more items.
     * @throws SQLException an error occurred accessing the database
     */
    public boolean moveNext() throws SQLException {
        current = ci.nextThrow();
        return current != null;
    }

    /**
     * Gets the item the iterator is currently pointing at. Returns null if there is no such item.
     */
    public T current() {
        return current;
    }

    /**
     * Closes the iterator and its database connection. MUST be called if you do not iterate to the end of the collection.
     * @throws SQLException an error occurred accessing the database
     */
    public void close() throws SQLException {
        ci.close();
    }

    /**
     * Execute the given action for every item in the sequence
     * @param action the action to execute
     * @throws SQLException
     */
    public void forEach(Consumer<? super T> action) throws SQLException {
        try {
            while (moveNext())
                action.accept(current);
        } finally {
            close();
        }
    }

    /**
     * Gets a list containing all the items in the collection.
     * @throws SQLException an error occurred accessing the database
     */
    public List<T> toList() throws SQLException {
        ArrayList<T> list = new ArrayList<>();
        try {
            while (moveNext())
                list.add(current);
            return list;
        } finally {
            close();
        }
    }
}
