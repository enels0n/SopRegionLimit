package net.enelson.sopregionlimit;

public enum ClaimType {
    IN_GLOBAL("in-global"),
    IN_OWN_REGION("in-own-region");

    private final String path;

    ClaimType(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
