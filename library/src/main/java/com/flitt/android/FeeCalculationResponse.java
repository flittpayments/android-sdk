package com.flitt.android;

public class FeeCalculationResponse {
    public final Double discountPercent;
    public final Double discountAmount;
    public final Double feeAmount;
    public final Double totalAmount;
    public final String promoStatus;
    public final String message;
    public final String cvv2RequirementRaw;
    public final Cvv2Requirement cvv2Requirement;

    public enum Cvv2Requirement {
        REQUIRED, OPTIONAL, ABSENT
    }

    FeeCalculationResponse(
            Double discountPercent,
            Double discountAmount,
            Double feeAmount,
            Double totalAmount,
            String promoStatus,
            String message,
            String cvv2RequirementRaw,
            Cvv2Requirement cvv2Requirement
    ) {
        this.discountPercent = discountPercent;
        this.discountAmount = discountAmount;
        this.feeAmount = feeAmount;
        this.totalAmount = totalAmount;
        this.promoStatus = promoStatus;
        this.message = message;
        this.cvv2RequirementRaw = cvv2RequirementRaw;
        this.cvv2Requirement = cvv2Requirement;
    }
}