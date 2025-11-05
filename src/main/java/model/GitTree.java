package model;

import java.util.List;

public class GitTree {
    private final List<TreeEntry> entries;

    public GitTree(List<TreeEntry> entries) {
        this.entries = entries;
    }

    public List<TreeEntry> getEntries() {
        return entries;
    }
}
