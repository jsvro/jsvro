package io.github.jsvro.core;

import io.github.jsvro.core.People.Person;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

final class FxTransactions {
    private static final List<String> CURRENCIES = List.of("NOK", "SEK", "DKK", "EUR", "USD", "GBP", "CHF", "JPY");
    private static final List<String> COUNTRIES = List.of("NO", "SE", "DK", "FI", "DE", "GB");
    private static final List<String> PRODUCTS = List.of("ILB", "UTB", "FXS", "CPY", "PAY");
    private static final List<String> MARKET_PRODUCTS = List.of("SEPA_INSTANT", "INSTANT_INTERNAL", "INSTANT_CARD");
    private static final List<String> SETTLEMENT_SYSTEMS = List.of("NOS", "CLS", "TARGET2", "RIX");
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 25);
    private static final Instant TRADE_START = Instant.parse("2026-06-25T07:00:00Z");

    public enum Direction { INCOMING, OUTGOING }

    public enum FxTransactionFlow { INSTANT, STANDARD, OFFER }

    public enum FxTransactionState {
        PROCESSING, PARKED, UNRESOLVED, PRICED, REJECTED, RETRYABLE, UNPRICEABLE, REVERSAL_IN_PROCESS,
        CUSTOMER_REVERSED, OPERATOR_REVERSED, SYSTEM_REVERSED, OUTDATED, NOT_PROCESSABLE
    }

    public enum Side { BUY, SELL }

    public enum PricingType { MARKET, AGREEMENT, FEE, REVERSAL, RECALL, SURPLUS, OPERATOR_REVERSAL }

    record FxTransactionResponse(
            String id,
            String idempotencyKey,
            String systemId,
            String systemReferenceId,
            String correlationId,
            Person owner,
            String bic,
            String department,
            String localCurrency,
            String countryCode,
            String productId,
            String marketProduct,
            String buyCustomerNumber,
            String buyAccountNumber,
            String buyCurrency,
            LocalDate buyValueDate,
            String sellCustomerNumber,
            String sellAccountNumber,
            String sellCurrency,
            LocalDate sellValueDate,
            Side dealtSide,
            BigDecimal dealtAmount,
            Direction direction,
            Instant transactionTime,
            String registeringUser,
            String verifyingUser,
            BigDecimal fxRate,
            Instant quotedAt,
            BigDecimal settledBuyAmount,
            BigDecimal settledSellAmount,
            String settlementSystem,
            PricingType pricingType,
            FxTransactionFlow flow,
            FxTransactionState status,
            String appliedFxRateOfferId,
            String reversesFxTransactionId) {
    }

    private FxTransactions() {
    }

    static List<FxTransactionResponse> generate(int count) {
        Random random = new Random(42);
        return IntStream.range(0, count).mapToObj(i -> transaction(random, i)).toList();
    }

    private static FxTransactionResponse transaction(Random random, int index) {
        String buyCurrency = pick(random, CURRENCIES);
        String sellCurrency = pickOther(random, CURRENCIES, buyCurrency);
        String countryCode = pick(random, COUNTRIES);
        LocalDate valueDate = TRADE_DATE.plusDays(random.nextInt(3));
        Side dealtSide = random.nextBoolean() ? Side.BUY : Side.SELL;
        BigDecimal dealtAmount = BigDecimal.valueOf(100 + random.nextInt(5_000_000), 0)
                .add(BigDecimal.valueOf(random.nextInt(100), 2));
        Instant transactionTime = TRADE_START.plusMillis(index * 250L + random.nextInt(250));

        PricingType pricingType = pricingType(random);
        FxTransactionFlow flow = pricingType == PricingType.MARKET ? flow(random) : FxTransactionFlow.STANDARD;
        FxTransactionState status = status(random, pricingType);
        boolean priced = status == FxTransactionState.PRICED || status.name().endsWith("REVERSED");

        BigDecimal fxRate = null;
        Instant quotedAt = null;
        BigDecimal settledBuyAmount = null;
        BigDecimal settledSellAmount = null;
        String settlementSystem = null;
        if (priced) {
            fxRate = BigDecimal.valueOf(0.05 + random.nextDouble() * 15).setScale(6, RoundingMode.HALF_UP);
            quotedAt = transactionTime.minusMillis(random.nextInt(2_000));
            BigDecimal counterAmount = dealtAmount.multiply(fxRate).setScale(2, RoundingMode.HALF_UP);
            settledBuyAmount = dealtSide == Side.BUY ? dealtAmount : counterAmount;
            settledSellAmount = dealtSide == Side.SELL ? dealtAmount : counterAmount;
            settlementSystem = pick(random, SETTLEMENT_SYSTEMS);
        }

        String registeringUser = user(random);
        return new FxTransactionResponse(
                hex(random, 16),
                UUID.nameUUIDFromBytes(("key-" + index).getBytes()).toString(),
                "S" + (100 + random.nextInt(900)),
                "REF-" + String.format("%06d", index),
                UUID.nameUUIDFromBytes(("trace-" + index).getBytes()).toString(),
                People.person(random),
                "TEST" + countryCode + "KK" + (random.nextBoolean() ? "XXX" : String.format("%03d", random.nextInt(1000))),
                String.format("%04d", random.nextInt(10_000)),
                localCurrency(countryCode),
                countryCode,
                pick(random, PRODUCTS),
                flow == FxTransactionFlow.INSTANT ? pick(random, MARKET_PRODUCTS) : null,
                digits(random, 11),
                digits(random, 11),
                buyCurrency,
                valueDate,
                digits(random, 11),
                digits(random, 11),
                sellCurrency,
                valueDate.plusDays(random.nextInt(2)),
                dealtSide,
                dealtAmount,
                random.nextBoolean() ? Direction.INCOMING : Direction.OUTGOING,
                transactionTime,
                registeringUser,
                flow == FxTransactionFlow.OFFER ? user(random) : registeringUser,
                fxRate,
                quotedAt,
                settledBuyAmount,
                settledSellAmount,
                settlementSystem,
                pricingType,
                flow,
                status,
                flow == FxTransactionFlow.OFFER ? hex(random, 16) : null,
                isCompensating(pricingType) ? hex(random, 16) : null);
    }

    private static PricingType pricingType(Random random) {
        int roll = random.nextInt(100);
        if (roll < 80) return PricingType.MARKET;
        if (roll < 90) return PricingType.AGREEMENT;
        if (roll < 95) return PricingType.FEE;
        if (roll < 97) return PricingType.REVERSAL;
        if (roll < 98) return PricingType.RECALL;
        if (roll < 99) return PricingType.SURPLUS;
        return PricingType.OPERATOR_REVERSAL;
    }

    private static FxTransactionFlow flow(Random random) {
        int roll = random.nextInt(10);
        return roll < 6 ? FxTransactionFlow.STANDARD : roll < 9 ? FxTransactionFlow.INSTANT : FxTransactionFlow.OFFER;
    }

    private static FxTransactionState status(Random random, PricingType pricingType) {
        int roll = random.nextInt(100);
        if (roll < 85) return FxTransactionState.PRICED;
        if (roll < 89) return FxTransactionState.PARKED;
        if (roll < 92) return FxTransactionState.PROCESSING;
        if (roll < 94) return FxTransactionState.REJECTED;
        if (roll < 96) return FxTransactionState.RETRYABLE;
        if (roll < 97) return FxTransactionState.UNPRICEABLE;
        if (roll < 98) return FxTransactionState.UNRESOLVED;
        return isCompensating(pricingType) ? FxTransactionState.PRICED : FxTransactionState.CUSTOMER_REVERSED;
    }

    private static boolean isCompensating(PricingType pricingType) {
        return pricingType == PricingType.REVERSAL || pricingType == PricingType.RECALL
                || pricingType == PricingType.OPERATOR_REVERSAL;
    }

    private static String localCurrency(String countryCode) {
        return switch (countryCode) {
            case "NO" -> "NOK";
            case "SE" -> "SEK";
            case "DK" -> "DKK";
            case "GB" -> "GBP";
            default -> "EUR";
        };
    }

    private static String user(Random random) {
        return "" + (char) ('A' + random.nextInt(26)) + (char) ('A' + random.nextInt(26)) + digits(random, 5);
    }

    private static String digits(Random random, int length) {
        StringBuilder digits = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            digits.append((char) ('0' + random.nextInt(10)));
        }
        return digits.toString();
    }

    private static String hex(Random random, int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static String pick(Random random, List<String> values) {
        return values.get(random.nextInt(values.size()));
    }

    private static String pickOther(Random random, List<String> values, String excluded) {
        String value;
        do {
            value = pick(random, values);
        } while (value.equals(excluded));
        return value;
    }
}
