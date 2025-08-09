package site.pwing.pwingcurios.manager;

import java.util.List;

public class SlotDefinition {
    private final String id;
    private final int index;
    private final String name;
    private final List<String> keywords;
    private final List<String> materials; // optional whitelist of material names allowed in this slot

    // Optional per-slot placeholder configuration
    private final String placeholderMaterial; // Bukkit material name
    private final String placeholderName;
    private final Integer placeholderCustomModelData; // null if not set

    public SlotDefinition(String id, int index, String name, List<String> keywords) {
        this(id, index, name, keywords, null, null, null, null);
    }

    public SlotDefinition(String id, int index, String name, List<String> keywords,
                          String placeholderMaterial, String placeholderName, Integer placeholderCustomModelData) {
        this(id, index, name, keywords, null, placeholderMaterial, placeholderName, placeholderCustomModelData);
    }

    public SlotDefinition(String id, int index, String name, List<String> keywords,
                          List<String> materials,
                          String placeholderMaterial, String placeholderName, Integer placeholderCustomModelData) {
        this.id = id;
        this.index = index;
        this.name = name;
        this.keywords = keywords;
        this.materials = materials;
        this.placeholderMaterial = placeholderMaterial;
        this.placeholderName = placeholderName;
        this.placeholderCustomModelData = placeholderCustomModelData;
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public List<String> getMaterials() {
        return materials;
    }

    public String getPlaceholderMaterial() {
        return placeholderMaterial;
    }

    public String getPlaceholderName() {
        return placeholderName;
    }

    public Integer getPlaceholderCustomModelData() {
        return placeholderCustomModelData;
    }
}