package com.sajuppugi.wallet.application;

public record RemainingTopUpRefund(int amount, int paidShells, int bonusShells) {
    public static int proratedAmount(int originalAmount, int originalPaid, int remainingPaid) {
        if (originalAmount <= 0 || originalPaid <= 0 || remainingPaid <= 0 || remainingPaid > originalPaid)
            throw new IllegalArgumentException("Invalid remaining refund snapshot");
        return Math.toIntExact((Math.multiplyExact((long) originalAmount, remainingPaid) + originalPaid - 1L) / originalPaid);
    }
}
