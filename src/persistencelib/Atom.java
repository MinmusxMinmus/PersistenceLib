package persistencelib;

import com.sun.istack.internal.NotNull;

import java.util.*;

class Hex {
    public static String encode(String string) {
        // Guard: empty string
        if (string == null || string.isEmpty()) return "";
        char[] c = string.toCharArray();
        StringBuilder sb = new StringBuilder();
        for (char value : c) {
            String hexString;
            if (value == '\n') hexString = "00";
            else if (value == '\t') hexString = "01";
            else hexString = Integer.toHexString(value);
            sb.append(hexString);
        }
        return sb.toString();
    }

    public static String decode(String string) throws IllegalArgumentException {
        // Guard: empty string
        if (string == null || string.isEmpty()) return "";
        char[] charArray = string.toCharArray();
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < charArray.length; i = i + 2) {
            try {
                String st = "" + charArray[i] + "" + charArray[i + 1];
                char ch;
                if (st.equals("00")) ch = '\n';
                else if (st.equals("01")) ch = '\t';
                else ch = (char)Integer.parseUnsignedInt(st, 16);
                sb.append(ch);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Odd number of encoded characters", e);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unrecognized hex characters", e);
            }
        }
        return sb.toString();
    }
}

public class Atom {

    private final String name;
    private final Map<Key, Collection<String>> objects;

    // Constructors and string methods

    Atom(String name) {
        this.name = name;
        objects = new HashMap<>();
    }

    String toStorableString() {
        List<DataEntry> data = new LinkedList<>();
        objects.forEach((key, l) -> {
            data.add(new DataEntry(key, l));
        });

        StringJoiner joiner = new StringJoiner("-", name + "{", "}");
        for (DataEntry d : data) {
            StringJoiner subjoiner = new StringJoiner(",");
            subjoiner.add(Hex.encode(d.key().toString()));
            for (String s : d.value()) subjoiner.add(Hex.encode(s));
            joiner.add(subjoiner.toString());
        }
        return joiner.toString();
    }

    static Atom fromStorableString(String atom) {
        String[] content = atom.split("[{}]");
        Atom r = new Atom(content[0]);

        if (content.length < 2) return r;
        String[] entries = content[1].split("-");
        for (String data : entries) {
            String[] split = data.split(",");
            try {
                Key key = new Key(Hex.decode(split[0]));
                Collection<String> values = new LinkedList<>();
                for (int i = 1; i != split.length; i++) values.add(Hex.decode(split[i]));
                r.addItem(key, values);
            } catch (IllegalArgumentException e) {
                System.err.println("WARNING: Unable to decode an object. Discarding...");
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("WARNING: Bad encoding format for an object. Discarding...");
            }
        }
        return r;
    }


    // Atom properties

    public String getName() {
        return name;
    }

    // Data interaction methods

    public boolean addItem(Key key, Collection<String> data) {
        if (objects.get(key) != null) return false;
        objects.put(key, data);
        return true;
    }

    public boolean removeItem(Key key) {
        return objects.remove(key) != null;
    }

    public Set<Key> getItems() {
        return  new HashSet<>(objects.keySet());
    }

    public Collection<String> getItem(Key key) {
        return objects.get(key);
    }

    public int getSize() {
        return objects.size();
    }

    public boolean replaceItem(Key key, Collection<String> newData) {
        if (objects.get(key) == null) return false;
        objects.put(key, newData);
        return true;
    }
}
