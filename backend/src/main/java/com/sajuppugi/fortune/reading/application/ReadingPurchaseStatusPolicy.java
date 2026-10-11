package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.CompensationStatus;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;

public final class ReadingPurchaseStatusPolicy {
    private ReadingPurchaseStatusPolicy() {}

    public static Projection project(Status status, CompensationStatus compensationStatus) {
        CompensationStatus compensation = compensationStatus == null ? defaultCompensation(status) : compensationStatus;
        NextAction nextAction = switch (status) {
            case CREATED, DEBITED, GENERATING -> NextAction.WAIT;
            case FULFILLED -> NextAction.VIEW_READING;
            case REFUNDED -> NextAction.RETRY_PURCHASE;
            case FAILED -> switch (compensation) {
                case PENDING -> NextAction.WAIT;
                case REVIEW_REQUIRED -> NextAction.CONTACT_SUPPORT;
                case NOT_REQUIRED, COMPLETED -> NextAction.RETRY_PURCHASE;
            };
        };
        return new Projection(compensation, nextAction);
    }

    public static Projection project(Status status) {
        return project(status, defaultCompensation(status));
    }

    private static CompensationStatus defaultCompensation(Status status) {
        return status == Status.REFUNDED ? CompensationStatus.COMPLETED : CompensationStatus.NOT_REQUIRED;
    }

    public enum NextAction { WAIT, VIEW_READING, RETRY_PURCHASE, CONTACT_SUPPORT }

    public record Projection(CompensationStatus compensationStatus, NextAction nextAction) {}
}
