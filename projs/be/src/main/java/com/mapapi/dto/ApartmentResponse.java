package com.mapapi.dto;

import java.util.List;

/**
 * GET /api/apartments 응답 DTO
 */
public class ApartmentResponse {

    private boolean success;
    private int count;
    private String dealYmd;
    private List<TradeItem> data;

    public ApartmentResponse() {
    }

    public ApartmentResponse(boolean success, int count, String dealYmd, List<TradeItem> data) {
        this.success = success;
        this.count = count;
        this.dealYmd = dealYmd;
        this.data = data;
    }

    public static ApartmentResponse ok(String dealYmd, List<TradeItem> data) {
        return new ApartmentResponse(true, data.size(), dealYmd, data);
    }

    // --- Getters & Setters ---

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getDealYmd() {
        return dealYmd;
    }

    public void setDealYmd(String dealYmd) {
        this.dealYmd = dealYmd;
    }

    public List<TradeItem> getData() {
        return data;
    }

    public void setData(List<TradeItem> data) {
        this.data = data;
    }
}
