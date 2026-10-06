package com.farmmanagement.backend.activity;

import java.util.*;

public class FarmingActivity {
    private String id;
    private String title;
    private String season;
    private String category;
    private String assignee;
    private String sector;
    private String priority;
    private String startDate;
    private String dueDate;
    private String status;
    private int progress;
    private String description;
    private Map<String, Object> completionRecord;
    private List<Map<String, Object>> notes = new ArrayList<>();
    private List<Map<String, Object>> history = new ArrayList<>();

    public FarmingActivity() {}

    public FarmingActivity(String id, String title, String season, String category, String assignee,
                           String sector, String priority, String startDate, String dueDate,
                           String status, int progress, String description) {
        this.id = id;
        this.title = title;
        this.season = season;
        this.category = category;
        this.assignee = assignee;
        this.sector = sector;
        this.priority = priority;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.status = status;
        this.progress = progress;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getCompletionRecord() { return completionRecord; }
    public void setCompletionRecord(Map<String, Object> completionRecord) { this.completionRecord = completionRecord; }

    public List<Map<String, Object>> getNotes() { return notes; }
    public void setNotes(List<Map<String, Object>> notes) { this.notes = notes; }

    public List<Map<String, Object>> getHistory() { return history; }
    public void setHistory(List<Map<String, Object>> history) { this.history = history; }
}
