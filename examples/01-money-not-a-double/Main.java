import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Java Concepts Playground · Ep 01 · The bill is off by one paisa
 *
 * The cart says ₹579.05. The payment gateway charges ₹579.04.
 * This file has the buggy first version (prices as double) and the fix (a Money value object), side by side.
 *
 * Run it:
 *   - in GitHub Codespaces: it runs once when the codespace opens; run it again with "Run" above main(),
 *     or type  java Main.java  in the terminal
 *   - on your machine (Java 17 or later):  java Main.java
 *
 * Concepts: why double can't hold money, records, BigDecimal, Currency, scale, RoundingMode,
 * a value object that refuses invalid states, minor units (paise) for a payment gateway.
 */
public class Main {

    // ✗ The first version: prices are doubles. 0.05 has no exact binary form, so the total is really 579.04999…
    //   and turning it into paise for the gateway cuts it down to 57904.
    record CartLineV1(String name, double price, int quantity) {}

    static long totalInPaiseBuggy(List<CartLineV1> lines) {
        double total = 0;
        for (CartLineV1 line : lines) {
            total += line.price() * line.quantity();
        }
        System.out.println("  first version total = " + total + "  (really " + new BigDecimal(total) + ")");
        return (long) (total * 100);                       // for the payment gateway
    }

    // ✓ The fix: money is its own type, a value object. An amount in one currency, exact, never rounded by accident.
    record Money(BigDecimal amount, Currency currency) {

        Money {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
            int digits = currency.getDefaultFractionDigits();   // INR 2, JPY 0, KWD 3
            if (amount.stripTrailingZeros().scale() > digits) {
                throw new IllegalArgumentException(currency + " has " + digits + " decimal places, so " + amount
                        + " would have to be rounded. Round it on purpose first.");
            }
            amount = amount.setScale(digits, RoundingMode.UNNECESSARY);   // the scale always matches the currency
        }

        static Money of(String amount, String currencyCode) {    // from a String, never from a double
            return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
        }

        static Money zero(String currencyCode) {
            return of("0", currencyCode);
        }

        Money plus(Money other) {
            if (!currency.equals(other.currency)) throw new IllegalArgumentException("can't add " + other.currency + " to " + currency);
            return new Money(amount.add(other.amount), currency);
        }

        Money times(int quantity) {                              // exact: a whole quantity never needs rounding
            return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
        }

        /** The amount in minor units (paise, cents), the way payment gateways want it: ₹579.05 is 57905. */
        long minorUnits() {
            return amount.movePointRight(amount.scale()).longValueExact();
        }

        @Override
        public String toString() {
            return currency.getSymbol() + amount.toPlainString();
        }
    }

    record CartLine(String name, Money price, int quantity) {}

    static Money total(List<CartLine> lines) {
        return lines.stream()
                .map(line -> line.price().times(line.quantity()))
                .reduce(Money.zero("INR"), Money::plus);
    }

    public static void main(String[] args) {
        System.out.println("The cart: a silicone phone case ₹299.95, a screen guard ₹199.95, a USB-C cable ₹79.15");

        long buggy = totalInPaiseBuggy(List.of(
                new CartLineV1("Silicone phone case", 299.95, 1),
                new CartLineV1("Screen guard", 199.95, 1),
                new CartLineV1("USB-C cable", 79.15, 1)));
        System.out.println("  ✗ first version charges: " + buggy + " paise");

        Money total = total(List.of(
                new CartLine("Silicone phone case", Money.of("299.95", "INR"), 1),
                new CartLine("Screen guard", Money.of("199.95", "INR"), 1),
                new CartLine("USB-C cable", Money.of("79.15", "INR"), 1)));
        System.out.println("  ✓ fixed total " + total + ", charges: " + total.minorUnits() + " paise");

        System.out.println("Money refuses what would need rounding:");
        try {
            Money.of("19.999", "INR");
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ " + e.getMessage());
        }

        check(buggy == 57904, "the double version really loses a paisa");
        check(total.minorUnits() == 57905, "the Money version charges exactly 57905 paise");
        check(Money.of("1500", "JPY").minorUnits() == 1500, "yen has no decimal places");
        System.out.println("All checks passed ✓");
    }

    static void check(boolean ok, String rule) {
        if (!ok) throw new AssertionError(rule);
    }
}
