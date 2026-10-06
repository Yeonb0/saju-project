package com.sajuppugi;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.application.QuoteFundingService;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.PurchaseQuote;
import com.sajuppugi.catalog.domain.TopUpProduct;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.wallet.application.WalletQueryUseCase;
import com.sajuppugi.wallet.domain.WalletBalance;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuoteFundingTest {
    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID QUOTE = UUID.randomUUID();
    private final CatalogUseCase catalog = mock(CatalogUseCase.class);
    private final WalletQueryUseCase wallets = mock(WalletQueryUseCase.class);
    private final QuoteFundingService service = new QuoteFundingService(catalog, wallets);

    @ParameterizedTest
    @ValueSource(ints = {15, 20})
    void sufficientBalanceHasZeroShortageAndNoRecommendation(int balance) {
        var quote = quote(Product.Category.READING, Price.Currency.TURTLE_SHELL, 15);
        when(catalog.getQuote(USER, QUOTE)).thenReturn(quote);
        when(wallets.getBalance(USER)).thenReturn(new WalletBalance(balance, 0));
        var view = service.get(USER, QUOTE);
        assertThat(view.quote()).isEqualTo(quote);
        assertThat(view.walletBalance()).isEqualTo(balance);
        assertThat(view.balanceAfter()).isEqualTo(balance - 15);
        assertThat(view.shortage()).isZero();
        assertThat(view.recommendedTopUp()).isNull();
        verify(catalog, never()).availableTopUps();
    }

    @Test
    void recommendsCheapestAvailableProductUsingPaidAndBonusCredits() {
        when(catalog.getQuote(USER, QUOTE)).thenReturn(quote(Product.Category.READING, Price.Currency.TURTLE_SHELL, 33));
        when(wallets.getBalance(USER)).thenReturn(new WalletBalance(0, 0));
        when(catalog.availableTopUps()).thenReturn(List.of(topUp("LARGE", 5000, 50, 6),
                topUp("MEDIUM", 3000, 30, 3), topUp("SMALL", 1000, 10, 0)));
        var view = service.get(USER, QUOTE);
        assertThat(view.shortage()).isEqualTo(33);
        assertThat(view.balanceAfter()).isNull();
        assertThat(view.recommendedTopUp()).isEqualTo("MEDIUM");
    }

    @Test
    void readsFreshBalanceAndIncludesBonusInsteadOfSavingItInQuote() {
        when(catalog.getQuote(USER, QUOTE)).thenReturn(quote(Product.Category.TALISMAN_ADDON, Price.Currency.TURTLE_SHELL, 10));
        when(wallets.getBalance(USER)).thenReturn(new WalletBalance(2, 3), new WalletBalance(12, 3));
        when(catalog.availableTopUps()).thenReturn(List.of(topUp("SMALL", 1000, 10, 0)));
        assertThat(service.get(USER, QUOTE).shortage()).isEqualTo(5);
        assertThat(service.get(USER, QUOTE).shortage()).isZero();
    }

    @Test
    void noAvailableOrLargeEnoughProductDoesNotInventARecommendation() {
        when(catalog.getQuote(USER, QUOTE)).thenReturn(quote(Product.Category.READING, Price.Currency.TURTLE_SHELL, 600));
        when(wallets.getBalance(USER)).thenReturn(new WalletBalance(0, 0));
        when(catalog.availableTopUps()).thenReturn(List.of())
                .thenReturn(List.of(topUp("LARGEST", 50000, 500, 80)));
        assertThat(service.get(USER, QUOTE).recommendedTopUp()).isNull();
        var view = service.get(USER, QUOTE);
        assertThat(view.shortage()).isEqualTo(600);
        assertThat(view.recommendedTopUp()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"QUOTE_EXPIRED", "RESOURCE_NOT_FOUND", "INVALID_REQUEST"})
    void checksQuoteAccessBeforeWalletOrRecommendation(ErrorCode code) {
        when(catalog.getQuote(USER, QUOTE)).thenThrow(new ApiException(code));
        assertThatThrownBy(() -> service.get(USER, QUOTE)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
        verifyNoInteractions(wallets);
        verify(catalog, never()).availableTopUps();
    }

    @ParameterizedTest
    @EnumSource(value = Product.Category.class, names = {"GIFT", "TOP_UP"})
    void doesNotExtendFundingContractToGiftsOrCashTopUps(Product.Category category) {
        when(catalog.getQuote(USER, QUOTE)).thenReturn(quote(category,
                category == Product.Category.TOP_UP ? Price.Currency.KRW : Price.Currency.TURTLE_SHELL, 10));
        assertThatThrownBy(() -> service.get(USER, QUOTE)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(wallets);
    }

    @Test
    void doesNotMutateWalletOrConsumeQuote() {
        when(catalog.getQuote(USER, QUOTE)).thenReturn(quote(Product.Category.READING, Price.Currency.TURTLE_SHELL, 10));
        when(wallets.getBalance(USER)).thenReturn(new WalletBalance(20, 0));
        service.get(USER, QUOTE);
        verify(wallets).getBalance(USER);
        verify(catalog).getQuote(USER, QUOTE);
        verifyNoMoreInteractions(catalog, wallets);
    }

    private PurchaseQuote quote(Product.Category category, Price.Currency currency, int amount) {
        var product = new Product(UUID.randomUUID(), "TEST", category, new Price(currency, amount),
                true, null, null, "test");
        return PurchaseQuote.issue(QUOTE, USER, product, "a".repeat(64), NOW);
    }

    private TopUpProduct topUp(String code, int price, int paid, int bonus) {
        return new TopUpProduct(new Product(UUID.randomUUID(), code, Product.Category.TOP_UP,
                new Price(Price.Currency.KRW, price), true, null, null, "test"), paid, bonus);
    }
}
