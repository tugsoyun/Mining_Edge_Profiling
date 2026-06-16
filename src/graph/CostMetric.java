package src.graph;

public enum CostMetric {
    DISTANCE("Distance", "m"),
    GRADE("Grade", "%"),
    TIME("Travel Time", "s"),
    FUEL("Fuel Used", "mL");

    private final String displayName;
    private final String unit;

    CostMetric(String displayName, String unit) {
        this.displayName = displayName;
        this.unit = unit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnit() {
        return unit;
    }
}