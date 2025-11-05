package model;

public class TreeEntry {
    private final String mode;
    private final String name;
    private final String sha1;

    public TreeEntry(String mode, String name, String sha1) {
        this.mode = mode;
        this.name = name;
        this.sha1 = sha1;
    }

    public String getMode() {
        return mode;
    }

    public String getName() {
        return name;
    }

    public String getSha1() {
        return sha1;
    }
}
