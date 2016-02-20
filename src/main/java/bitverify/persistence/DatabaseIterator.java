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

    public boolean moveNext() throws SQLException {
        current = ci.nextThrow();
        return current != null;
    }

    public T current() {
        return current;
    }

    private T next() throws SQLException {
        return ci.nextThrow();
    }


    public void close() throws SQLException {
        ci.close();
    }

    public void forEach(Consumer<? super T> action) throws SQLException {
        try {
            while (moveNext())
                action.accept(current);
        } finally {
            close();
        }
    }

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
