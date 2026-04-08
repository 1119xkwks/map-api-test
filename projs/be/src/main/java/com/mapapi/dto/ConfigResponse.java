package com.mapapi.dto;

import java.util.Map;

/**
 * GET /api/config 응답 DTO
 */
public class ConfigResponse {

    private boolean success;
    private Map<String, String> data;

    public ConfigResponse() {
    }

    public ConfigResponse(boolean success, Map<String, String> data) {
        this.success = success;
        this.data = data;
    }

    public static ConfigResponse ok(Map<String, String> data) {
        return new ConfigResponse(true, data);
    }

    // --- Getters & Setters ---

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }
}
