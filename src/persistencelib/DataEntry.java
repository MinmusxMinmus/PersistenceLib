package persistencelib;

import java.util.Collection;
import java.util.List;

class DataEntry {
    private final Key key;
    private final Collection<String> values;

    public DataEntry(Key first, Collection<String> second) {
        this.key = first;
        this.values = second;
    }

    public Key key() {
        return key;
    }

    public Collection<String> value() {
        return values;
    }
}
