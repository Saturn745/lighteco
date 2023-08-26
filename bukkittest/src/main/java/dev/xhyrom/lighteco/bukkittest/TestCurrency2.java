package dev.xhyrom.lighteco.bukkittest;

import dev.xhyrom.lighteco.api.model.currency.Currency;

import java.math.BigDecimal;

public class TestCurrency2 extends Currency {
    @Override
    public String getIdentifier() {
        return "test2";
    }

    @Override
    public Type getType() {
        return Type.LOCAL;
    }

    @Override
    public boolean isPayable() {
        return false;
    }

    @Override
    public int getDecimalPlaces() {
       return 2;
    }

    @Override
    public BigDecimal getDefaultBalance() {
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal calculateTax(BigDecimal amount) {
        return BigDecimal.ZERO;
    }
}
