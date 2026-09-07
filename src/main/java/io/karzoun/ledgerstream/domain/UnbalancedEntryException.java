package io.karzoun.ledgerstream.domain;

import java.math.BigDecimal;

public final class UnbalancedEntryException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public UnbalancedEntryException(CurrencyCode currency, BigDecimal debit, BigDecimal credit) {
        super("unbalanced entry for " + currency + ": debit=" + debit.toPlainString()
                + ", credit=" + credit.toPlainString());
    }
}
