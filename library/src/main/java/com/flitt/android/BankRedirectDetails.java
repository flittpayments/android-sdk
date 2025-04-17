package com.flitt.android;

public class BankRedirectDetails {
    private String action;
    private String url;
    private String target;
    private String responseStatus;

    public BankRedirectDetails() {
    }

    // Constructor with all fields
    public BankRedirectDetails(String action, String url, String target, String responseStatus) {
        this.action = action;
        this.url = url;
        this.target = target;
        this.responseStatus = responseStatus;
    }

    // Getters and setters
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }
}