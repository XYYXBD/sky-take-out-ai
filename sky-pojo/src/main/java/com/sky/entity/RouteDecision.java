package com.sky.entity;

public class RouteDecision {

    public enum Intent {
        QA,
        CART,
        BOTH
    }

    private Intent intent;
    private Double confidence;
    private String reason;

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}