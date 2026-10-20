package uz.vocabexam.entity;
public enum AssignmentStatus {
    ACTIVE("Faol"), CANCELLED("Bekor qilingan"), COMPLETED("Yakunlangan");
    private final String label;
    AssignmentStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
