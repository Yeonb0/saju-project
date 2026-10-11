package com.sajuppugi.fortune.reading;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.fortune.reading.application.ReadingPurchaseStatusPolicy;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseStatusPolicy.NextAction;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.CompensationStatus;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase.Status;
import org.junit.jupiter.api.Test;

class ReadingPurchaseStatusPolicyTest {
    @Test
    void mapsStableStatusAndCompensationCombinations() {
        assertProjection(Status.CREATED, CompensationStatus.NOT_REQUIRED, NextAction.WAIT);
        assertProjection(Status.DEBITED, CompensationStatus.NOT_REQUIRED, NextAction.WAIT);
        assertProjection(Status.GENERATING, CompensationStatus.NOT_REQUIRED, NextAction.WAIT);
        assertProjection(Status.FULFILLED, CompensationStatus.NOT_REQUIRED, NextAction.VIEW_READING);
        assertProjection(Status.FAILED, CompensationStatus.PENDING, NextAction.WAIT);
        assertProjection(Status.FAILED, CompensationStatus.REVIEW_REQUIRED, NextAction.CONTACT_SUPPORT);
        assertProjection(Status.FAILED, CompensationStatus.NOT_REQUIRED, NextAction.RETRY_PURCHASE);
        assertProjection(Status.FAILED, CompensationStatus.COMPLETED, NextAction.RETRY_PURCHASE);
        assertProjection(Status.REFUNDED, CompensationStatus.COMPLETED, NextAction.RETRY_PURCHASE);
    }

    private void assertProjection(Status status, CompensationStatus compensation, NextAction action) {
        var projection = ReadingPurchaseStatusPolicy.project(status, compensation);
        assertThat(projection.compensationStatus()).isEqualTo(compensation);
        assertThat(projection.nextAction()).isEqualTo(action);
    }
}
