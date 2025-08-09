package site.pwing.pwingcurios.manager;

public enum AccessorySlot {
    SLOT_1(10),
    SLOT_2(12),
    SLOT_3(14),
    SLOT_4(16);

    private final int guiIndex;

    AccessorySlot(int guiIndex) {
        this.guiIndex = guiIndex;
    }

    public int getGuiIndex() {
        return guiIndex;
    }
}
