import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Java Concepts Playground · Ep 06 · The sale ended at the wrong midnight
 *
 * The Diwali sale in the India store should end at midnight. The server runs in UTC.
 * This file has the buggy first version (LocalDateTime and now()) and the fix (Instant, the store's ZoneId and an
 * injected Clock), side by side, at five moments of the night.
 *
 * Run it:
 *   - in GitHub Codespaces: it runs once when the codespace opens; run it again with "Run" above main(),
 *     or type  java Main.java  in the terminal
 *   - on your machine (Java 17 or later):  java Main.java
 *
 * Concepts: LocalDateTime has no time zone; Instant is one moment on the timeline; ZoneId and ZonedDateTime;
 * atStartOfDay(zone).toInstant(); an injected Clock, read once with clock.instant(); Clock.fixed in tests.
 */
public class Main {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    static final LocalDate LAST_DAY = LocalDate.of(2026, 11, 20);   // the sale runs to the end of 20 Nov in India

    // ✗ The first version: "midnight" as a LocalDateTime, compared with the server's own clock.
    //   LocalDateTime has no zone, so on a UTC server midnight means 00:00 UTC, which is 05:30 in India.
    static boolean saleOnBuggy(Clock serverClock) {
        LocalDateTime saleEnds = LAST_DAY.plusDays(1).atStartOfDay();          // 21 Nov 00:00, but where?
        return LocalDateTime.now(serverClock).isBefore(saleEnds);            // the server's zone decides
    }

    // ✓ The fix: the sale window is an Instant, computed from the store's zone once, and the clock is injected.
    static final Instant SALE_ENDS = LAST_DAY.plusDays(1).atStartOfDay(INDIA).toInstant();   // 2026-11-20T18:30:00Z

    static boolean saleOn(Clock clock) {
        Instant now = clock.instant();                                         // read once per request
        return now.isBefore(SALE_ENDS);
    }

    public static void main(String[] args) {
        System.out.println("The sale ends at midnight in India = " + SALE_ENDS + " (an Instant, in UTC)");
        System.out.println("India time        | first version (UTC server) | fixed");
        for (String india : new String[] {"23:59", "00:00", "03:00", "05:29", "05:30"}) {
            LocalDate day = india.startsWith("23") ? LAST_DAY : LAST_DAY.plusDays(1);
            Instant moment = ZonedDateTime.of(day, java.time.LocalTime.parse(india), INDIA).toInstant();
            Clock utcServer = Clock.fixed(moment, ZoneOffset.UTC);              // what the server's clock says
            System.out.printf("  %s %s  | %-26s | %s%n", day, india,
                    saleOnBuggy(utcServer) ? "sale ON  ✗".replace("✗", india.startsWith("23") ? "✓" : "✗") : "sale off",
                    saleOn(utcServer) ? "sale ON" : "sale off ✓");
        }
        Duration extra = Duration.between(SALE_ENDS, LAST_DAY.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        System.out.println("The first version runs the sale " + extra.toHoursPart() + " h " + extra.toMinutesPart() + " min too long.");

        check(SALE_ENDS.equals(Instant.parse("2026-11-20T18:30:00Z")), "midnight in India is 18:30 UTC the evening before");
        check(!saleOn(Clock.fixed(SALE_ENDS, ZoneOffset.UTC)), "at midnight in India the sale is over");
        check(saleOn(Clock.fixed(SALE_ENDS.minusSeconds(1), ZoneOffset.UTC)), "one second before, it is still on");
        System.out.println("All checks passed ✓");
    }

    static void check(boolean ok, String rule) {
        if (!ok) throw new AssertionError(rule);
    }
}
