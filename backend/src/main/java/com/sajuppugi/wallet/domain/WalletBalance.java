package com.sajuppugi.wallet.domain;

public record WalletBalance(int paidBalance, int bonusBalance) {
    public WalletBalance {
        if (paidBalance < 0 || bonusBalance < 0) {
            throw new IllegalArgumentException("Wallet balances must not be negative");
        }
        Math.addExact(paidBalance, bonusBalance);
    }

    public int balance() {
        return Math.addExact(paidBalance, bonusBalance);
    }
}
