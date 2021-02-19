package persistencelib;

public class Key {
    private final String key;

    public Key(String key) {
        this.key = key;
    }

    public static Key fromString(String key) {
        return new Key(key);
    }

    @Override
    public String toString() {
        return key;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Key))
            return false;

        Key k = (Key)obj;
        return this.key.equals(k.key);
    }

    @Override
    public int hashCode() {
        int hash = 13;
        hash = 53 * hash + (key == null ? 0 : key.hashCode());
        return hash;
    }
}
