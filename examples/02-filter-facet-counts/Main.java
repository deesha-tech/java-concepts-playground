import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Java Concepts Playground · Ep 02 · Pick Black, and Blue disappears
 *
 * A shopper ticks "Black" in a shop's colour filter, and every other colour disappears.
 * This file has the buggy first version and the fix, side by side.
 *
 * Run it (Java 17 or later, nothing to install):
 *
 *     java Main.java
 *
 * Concepts: Predicate (and chaining with and()), Stream, Collectors.groupingBy, Collectors.counting,
 * Comparator (comparingByValue, reverseOrder, thenComparing), records.
 */
public class Main {

    record Product(String name, String colour, String brand) {}

    /** What the shopper has ticked: a facet name ("colour", "brand") → the values chosen. */
    record Filters(Map<String, Set<String>> ticked) {

        /** How to read each facet's value from a product. */
        static final Map<String, Function<Product, String>> FACETS = Map.of(
                "colour", Product::colour,
                "brand", Product::brand);

        /** Every ticked filter, as one Predicate. */
        Predicate<Product> all() {
            return allExcept(null);
        }

        /**
         * Every ticked filter EXCEPT one facet's own. Each filter is a Predicate, and they are chained with and().
         * The facet being counted is skipped, so its own choice never hides its other values.
         */
        Predicate<Product> allExcept(String facet) {
            Predicate<Product> result = product -> true;
            for (Map.Entry<String, Set<String>> filter : ticked.entrySet()) {
                if (filter.getKey().equals(facet)) {
                    continue;                                            // skip the facet's own filter
                }
                Function<Product, String> valueOf = FACETS.get(filter.getKey());
                Set<String> wanted = filter.getValue();
                result = result.and(product -> wanted.contains(valueOf.apply(product)));
            }
            return result;
        }
    }

    // ✗ The first version: it counts colours AFTER applying every filter, including the colour filter itself.
    //   Once "Black" is ticked, Black is the only colour left to count.
    static Map<String, Long> colourCountsBuggy(List<Product> products, Filters filters) {
        return products.stream()
                .filter(filters.all())
                .collect(Collectors.groupingBy(Product::colour, Collectors.counting()));
    }

    // ✓ The fix: count with every filter except colour's own, in one pass, then sort the biggest count first.
    static List<Map.Entry<String, Long>> colourCounts(List<Product> products, Filters filters) {
        Map<String, Long> counts = products.stream()
                .filter(filters.allExcept("colour"))
                .collect(Collectors.groupingBy(Product::colour,          // put the products in groups by colour…
                        Collectors.counting()));                         // …and count each group
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())   // biggest count first
                        .thenComparing(Map.Entry.comparingByKey()))                          // ties: A to Z
                .toList();
    }

    public static void main(String[] args) {
        List<Product> phoneCases = Stream.of(
                        cases("Black", 12, "Armour"), cases("Blue", 8, "Armour"),
                        cases("Silver", 5, "Slim"), cases("White", 3, "Slim"))
                .flatMap(List::stream)
                .toList();

        Filters black = new Filters(Map.of("colour", Set.of("Black")));
        System.out.println("The shopper ticks Black");
        System.out.println("  ✗ first version: " + colourCountsBuggy(phoneCases, black));
        System.out.println("  ✓ fixed:         " + colourCounts(phoneCases, black));

        // The other filters still apply: tick the brand "Slim" as well, and only Slim cases are counted.
        Filters blackAndSlim = new Filters(Map.of("colour", Set.of("Black"), "brand", Set.of("Slim")));
        System.out.println("The shopper also ticks the brand Slim");
        System.out.println("  ✓ fixed:         " + colourCounts(phoneCases, blackAndSlim));

        check(colourCounts(phoneCases, black).size() == 4, "every colour should still be listed");
        check(colourCounts(phoneCases, black).get(0).equals(Map.entry("Black", 12L)), "Black (12) should come first");
        check(colourCounts(phoneCases, blackAndSlim).size() == 2, "the brand filter should still apply");
        System.out.println("All checks passed ✓");
    }

    static List<Product> cases(String colour, int howMany, String brand) {
        return IntStream.rangeClosed(1, howMany)
                .mapToObj(i -> new Product(colour + " phone case " + i, colour, brand))
                .toList();
    }

    static void check(boolean ok, String rule) {
        if (!ok) throw new AssertionError(rule);
    }
}
