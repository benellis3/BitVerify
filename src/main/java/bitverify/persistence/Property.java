package bitverify.persistence;

import com.j256.ormlite.field.DatabaseField;

/**
 * Created by Rob on 13/02/2016.
 */
public class Property {
    @DatabaseField(id = true)
    private String key;
    @DatabaseField
    private String value;

    Property() {
    }

    public Property(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
