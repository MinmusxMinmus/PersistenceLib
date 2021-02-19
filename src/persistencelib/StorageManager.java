package persistencelib;

import java.io.*;
import java.util.*;

class Update {
    private final boolean delete;
    private final Atom newAtom;
    private final String name;

    Update(Atom newAtom) {
        this.delete = false;
        this.newAtom = newAtom;
        this.name = null;
    }

    Update(String name) {
        this.delete = true;
        this.name = name;
        this.newAtom = null;
    }


    public boolean isDelete() {
        return delete;
    }

    public Atom getNewAtom() {
        return newAtom;
    }

    public String getName() {
        return name;
    }
}

public class StorageManager {



    /*
    Base version of the manager. The database is split into different atoms, corresponding to the different
    atom types defined by the user. An empty file (minus the version) means there are no atoms. Each atom consists of
    a 30 digit number in between parentheses, indicating the amount of characters it occupies. The following characters
    belong to the atom, and are parsed directly by the Atom class.
     */
    private final Version currentVersion;

    private static final String DB_FILETYPE = "b";

    private File file;
    private final HashMap<String, Atom> regions;
    private final LinkedList<Update> updates = new LinkedList<>();


    public StorageManager(String filename, Version currentVersion) throws IOException {

        this.currentVersion = currentVersion;

        // Magic to remove the JAR part if executed from a JAR file
        String path = StorageManager.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.matches(".*\\.jar$")) path = path.substring(0, path.lastIndexOf('/') + 1);

        File file = new File(path + filename + "." + DB_FILETYPE);

        // Nonexistant file
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw new IOException("Unable to create file");
            }
            // Version writing
            FileWriter writer = new FileWriter(file);
            writer.write('[');
            writer.write(currentVersion.toString());
            writer.write(']');
            writer.close();
        }

        this.file = file;
        this.regions = new HashMap<>();
        FileReader reader = new FileReader(file);
        int read;

        char[] v = new char[7];
        read = reader.read(v, 0, 7);
        if (read < 7) throw new IOException("Invalid database file, restore backup");
        String version = String.copyValueOf(v);
        version = version.substring(1, version.length() - 1);

        // Version checking
        if (version.equals(Version.V100.toString()))
            read_version100(reader);
        else
            read_version100(reader);
        reader.close();
    }

    /**
     * @return a {@link Set} containing all currently available regions (as names) in the database.
     */
    public Set<String> getRegions() {
        // Get base regions
        Set<String> r = new HashSet<>(regions.keySet());

        // Iterate to apply changes in order
        for (Update u : updates)
            if ((u.isDelete())) r.remove(u.getName());
            else r.add(u.getNewAtom().getName());
        return Collections.unmodifiableSet(r);
    }

    /**
     * @param regionName The region's name, as a case-insensitive string.
     * @return The corresponding {@link Atom} in the database, or {@code null} if there is no match.
     */
    public Atom getRegion(String regionName) {
        // Search for the latest update first
        ListIterator<Update> li = updates.listIterator(updates.size());
        while (li.hasPrevious()) {
            Update u = li.previous();
            if (u.isDelete() && u.getName().equals(regionName.toUpperCase(Locale.ROOT))) return null;
            else if (!u.isDelete() && u.getNewAtom().getName().equals(regionName.toUpperCase(Locale.ROOT))) return u.getNewAtom();
        }
        return regions.get(regionName.toUpperCase(Locale.ROOT));
    }

    /**
     * Replaces an atom in the database with the offered one. Will not replace nonexistant atoms.
     * @param atom The new atom. Replaces the database atom with the same name.
     * @return {@code true} if the atom existed and was succesfully replaced, {@code false} otherwise.
     */
    public boolean replaceRegion(Atom atom) {
        // Guard against nonexistent region
        if (!regionExists(atom.getName())) return false;
        // Adding the update to the queue
        return updates.add(new Update(atom));
    }

    /**
     * Adds a new {@link Atom} to the database. To succesfully add an atom, it must fulfill a set of conditions:
     * <p>
     *     - The atom must not exist in the database already. <br>
     *     - The atom's name must only contain letters, numbers, and/or the characters "-", "_", "?" and "!".
     * </p>
     * @param name The name of the new atom, as a case-insensitive string.
     * @return {@code true} if the atom didn't exist, the name matched the allowed characters, and the atom was succesfully added. {@code false} otherwise.
     */
    public boolean addRegion(String name) {
        // Guard against existent region
        if (regionExists(name.toUpperCase(Locale.ROOT))) return false;
        // Guard against illegal characters
        if (name.matches(".*[^a-zA-Z0-9\\-_?!].*")) return false;
        // Adding the update to the queue
        updates.add(new Update(new Atom(name.toUpperCase(Locale.ROOT))));
        return true;
    }

    /**
     * Removes an {@link Atom} from the database. This method will only work if the atom already exists.
     * @param regionName The atom's name, as a case-insensitive string
     * @return {@code true} if the region exists and was deleted succesfully, {@code false} otherwise.
     */
    public boolean removeRegion(String regionName) {
        // Guard against nonexistent region
        if (!regionExists(regionName)) return false;
        // Adding the update to the queue
        updates.add(new Update(regionName));
        return true;
    }

    /**
     * Saves all changes to the database.
     * @throws IOException when there are issues reading/writing to the database file.
     */
    public void save() throws IOException {
        // No changes guard
        if (updates.isEmpty()) return;

        // Making the pertinent changes
        for (Update u : updates)
            if (u.isDelete()) regions.remove(u.getName());
            else regions.put(u.getNewAtom().getName(), u.getNewAtom());
        updates.clear();

        // Close all handles to the file, because apparently calling close() isn't enough
        System.gc();

        File backup = new File(file.getPath() + ".bak");

        // Deleting the previous backup
        if (backup.exists() && !backup.delete()) throw new IOException("Backup file unable to be deleted");
        // Renaming current file
        String name = file.getPath();
        if (!file.renameTo(backup)) throw new IOException("File unable to be renamed");
        // Creating new file
        File update = new File(name);
        if (!update.createNewFile()) throw new IOException("New file unable to be created, please restore backup");

        // New file setup
        file = update;
        FileWriter writer = new FileWriter(file);
        writer.write('[');
        writer.write(currentVersion.toString());
        writer.write(']');

        write_version100(writer);

        writer.close();
    }

    /**
     * Resets all changes done to the database since the last save, or since the creation of this {@link StorageManager} if there were no previous saves.
     * @throws IOException when there are issues reading/writing to the database file.
     */
    public void restore() throws IOException {
        // Close all handles to the file, because apparently calling close() isn't enough
        System.gc();

        File backup = new File(file.getPath() + ".bak");

        if (!backup.exists()) throw new FileNotFoundException("Backup file not found");

        // Attempting to read backup before changing anything
        FileReader reader = new FileReader(file);
        int read;
        char[] v = new char[7];
        read = reader.read(v, 0, 7);
        if (read < 7) throw new IOException("Invalid backup file");
        String version = String.copyValueOf(v);
        version = version.substring(1, version.length() - 1);

        // Version checking
        if (version.equals(Version.V100.toString()))
            read_version100(reader);
        else
            read_version100(reader);
        reader.close();

        // Close all handles to the backup, because apparently calling close() isn't enough
        System.gc();

        if (!file.delete()) throw new IOException("Unable to delete file");

        if (!backup.renameTo(file)) throw new IOException("Unable to rename backup file, restore manually");

        file = backup;
    }

    /**
     * Checks if a region exists. This method is useful to determine the fail reason of other methods such as
     * {@link StorageManager#addRegion(String)} or {@link StorageManager#removeRegion(String)}.
     * @param name The {@link Atom}'s name
     * @return {@code true} if an atom with the given name exists on the database, {@code false} otherwise
     */
    private boolean regionExists(String name) {
        boolean regionExists = regions.containsKey(name);
        ListIterator<Update> li = updates.listIterator(updates.size());

        // Search for different conditions depending on status.
        // Avoids one pointless conditional for each iteration
        if (regionExists) {
            for (Update u : updates)
                if (u.isDelete() && u.getName().equals(name)) {
                    regionExists = false;
                    break;
                }
        } else
            for (Update u : updates)
                if (u.getNewAtom().getName().equals(name)) {
                    regionExists = true;
                    break;
                }
        return regionExists;
    }

    // I/O from different versions

    private void read_version100(FileReader reader) throws IOException {
        int read;
        LinkedList<char[]> zones = new LinkedList<>();
        // File reading
        while (true) {
            // Reading size
            char[] size = new char[32];
            read = reader.read(size, 0, 32);
            // End of stream?
            if (read == -1) break;
            // Reading zone
            int amount = Integer.parseInt(String.copyValueOf(size).substring(1, size.length - 1));
            // No regions check
            if (amount == 0) return;
            char[] zone = new char[amount];
            read = reader.read(zone, 0, amount);
            if (read == -1) {
                System.err.println("ERROR: Unexpected end of file.");
                break;
            }
            // Storing zone
            zones.add(zone);
        }

        // Zone processing
        Atom atom;
        for (char[] zone : zones) {
            atom = Atom.fromStorableString(String.copyValueOf(zone));
            regions.put(atom.getName(), atom);
        }
    }

    private void write_version100(FileWriter writer) throws IOException {
        for (Map.Entry<String, Atom> zone : regions.entrySet()) {
            String storable = zone.getValue().toStorableString();
            StringBuilder size = new StringBuilder(storable.length() + ")");
            while (size.length() != 31) size.insert(0, "0");
            size.insert(0, "(");
            writer.write(size.toString());
            writer.write(storable);
        }
    }
}
