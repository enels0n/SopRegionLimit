package net.enelson.sopregionlimit;

public final class ClaimLimits {
    private int maxCount;
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private boolean verticalExpand;

    public ClaimLimits(int maxCount, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, boolean verticalExpand) {
        this.maxCount = maxCount;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.verticalExpand = verticalExpand;
    }

    public void applyGroup(int maxCount, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, boolean verticalExpand) {
        this.maxCount = Math.max(this.maxCount, maxCount);
        this.minX = Math.min(this.minX, minX);
        this.maxX = Math.max(this.maxX, maxX);
        this.minY = Math.min(this.minY, minY);
        this.maxY = Math.max(this.maxY, maxY);
        this.minZ = Math.min(this.minZ, minZ);
        this.maxZ = Math.max(this.maxZ, maxZ);
        this.verticalExpand = this.verticalExpand || verticalExpand;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public boolean isVerticalExpand() {
        return verticalExpand;
    }
}
