package com.farmmanagement.backend.notification;

public class Notification {
    private String id;
    private String title;
    private String desc;
    private String category;
    private String priority;
    private String status;
    private String time;
    private String timestamp;

    public Notification() {}

    public Notification(String id, String title, String desc, String category, String priority, String status, String time, String timestamp) {
        this.id = id;
        this.title = title;
        this.desc = desc;
        this.category = category;
        this.priority = priority;
        this.status = status;
        this.time = time;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
