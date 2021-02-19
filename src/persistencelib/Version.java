package persistencelib;

public enum Version {
    // ##############################################
    // Versions must have exactly 4 characters! V + 3 numbers
    // ##############################################
    V100;

    @Override
    public String toString() {
        char[] name = name().toCharArray();
        return name[1] + "." + name[2] + "." + name[3];
    }
}
