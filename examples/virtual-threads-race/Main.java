import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Java Concepts Playground · 10,000 shoppers, 200 threads
 *
 * 10,000 shoppers open a product page at the same moment. Each page waits about 100 ms for the price and stock
 * services (simulated here with Thread.sleep, which blocks like a network call). Who serves them faster:
 * a pool of 200 platform threads, or one virtual thread per request?
 *
 * Run it (Java 21 or later):
 *   - in GitHub Codespaces: it runs once when the codespace opens; run it again with "Run" above main(),
 *     or type  java Main.java  in the terminal
 *   - on your machine:  java Main.java
 *
 * Concepts: platform threads and Executors.newFixedThreadPool, virtual threads and
 * Executors.newVirtualThreadPerTaskExecutor, blocking I/O, carrier threads, why virtual threads help waiting work
 * and not CPU work.
 */
public class Main {

    static final int SHOPPERS = 10_000;
    static final Duration SERVICE_CALL = Duration.ofMillis(100);   // waiting for price and stock

    /** One product page: wait for the downstream services, then render. */
    static String productPage(int shopper) throws InterruptedException {
        Thread.sleep(SERVICE_CALL);                                  // blocking I/O, like a real HTTP call
        return "page for shopper " + shopper;
    }

    static Duration serveAll(ExecutorService executor) throws Exception {
        Instant start = Instant.now();
        try (executor) {                                             // close() waits for every task to finish
            List<Future<String>> pages = new ArrayList<>();
            for (int i = 0; i < SHOPPERS; i++) {
                int shopper = i;
                pages.add(executor.submit(() -> productPage(shopper)));
            }
            for (Future<String> page : pages) page.get();
        }
        return Duration.between(start, Instant.now());
    }

    // CPU-heavy work: virtual threads do not make it faster (there are only so many cores)
    static long busyWork() {
        long x = 0;
        for (int i = 0; i < 400_000_000; i++) x += i % 7;
        return x;
    }

    static Duration cpuAll(ExecutorService executor, int tasks) throws Exception {
        Instant start = Instant.now();
        try (executor) {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < tasks; i++) results.add(executor.submit(Main::busyWork));
            for (Future<Long> r : results) r.get();
        }
        return Duration.between(start, Instant.now());
    }

    public static void main(String[] args) throws Exception {
        System.out.println(SHOPPERS + " shoppers, each page waits " + SERVICE_CALL.toMillis() + " ms for the services");

        Duration pool = serveAll(Executors.newFixedThreadPool(200));
        System.out.printf("  ✗ 200 platform threads:          %,d ms%n", pool.toMillis());

        Duration virtual = serveAll(Executors.newVirtualThreadPerTaskExecutor());
        System.out.printf("  ✓ one virtual thread per request: %,d ms%n", virtual.toMillis());

        int cores = Runtime.getRuntime().availableProcessors();
        cpuAll(Executors.newFixedThreadPool(cores), cores);                // warm up the JIT first, so the timing is fair
        System.out.println("CPU-heavy work, " + cores * 4 + " tasks on " + cores + " cores:");
        System.out.printf("  platform threads (one per core):  %,d ms%n", cpuAll(Executors.newFixedThreadPool(cores), cores * 4).toMillis());
        System.out.printf("  virtual threads:                  %,d ms   (about the same: the cores are the limit)%n",
                cpuAll(Executors.newVirtualThreadPerTaskExecutor(), cores * 4).toMillis());

        check(virtual.toMillis() * 5 < pool.toMillis(), "virtual threads serve waiting requests far faster");
        System.out.println("All checks passed ✓");
    }

    static void check(boolean ok, String rule) {
        if (!ok) throw new AssertionError(rule);
    }
}
