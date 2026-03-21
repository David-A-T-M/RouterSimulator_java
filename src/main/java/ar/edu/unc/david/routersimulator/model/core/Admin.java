package ar.edu.unc.david.routersimulator.model.core;

/**
 * Facade that drives the simulation and prints periodic reports to stdout.
 *
 * <p>This class holds a reference to the {@link Network} it controls.
 */
public class Admin {

  private final Network network;

  /**
   * Constructs an Admin instance that manages the specified network.
   *
   * @param network The network that this Admin instance will manage. Must not be null.
   * @throws IllegalArgumentException if the provided network is null.
   */
  public Admin(Network network) {
    if (network == null) {
      throw new IllegalArgumentException("Network cannot be null");
    }
    this.network = network;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /** Prints a formatted report of the current network statistics to stdout. */
  public void printReport() {
    ar.edu.unc.david.routersimulator.model.stats.NetworkStats s = network.getStats();

    System.out.println("\n╔══════════════════════════════════════╗");
    System.out.println("║         NETWORK REPORT               ║");
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf("║ Tick:             %6d             ║%n", s.currentTick());
    System.out.printf("║ Routers:          %6d             ║%n", s.totalRouters());
    System.out.printf("║ Terminals:        %6d             ║%n", s.totalTerminals());
    System.out.println("╠══════════════════════════════════════╣");
    System.out.println("║ PACKETS                              ║");
    System.out.printf("║   Generated:      %6d             ║%n", s.packetsGenerated());
    System.out.printf("║   Sent:           %6d             ║%n", s.packetsSent());
    System.out.printf("║   Delivered:      %6d             ║%n", s.packetsDelivered());
    System.out.printf("║   Dropped:        %6d             ║%n", s.packetsDropped());
    System.out.printf("║   Timed out:      %6d             ║%n", s.packetsTimedOut());
    System.out.printf("║   In flight:      %6d             ║%n", s.packetsInFlight());
    System.out.println("╠══════════════════════════════════════╣");
    System.out.println("║ PAGES                                ║");
    System.out.printf("║   Created:        %6d             ║%n", s.pagesCreated());
    System.out.printf("║   Completed:      %6d             ║%n", s.pagesCompleted());
    System.out.printf("║   Dropped:        %6d             ║%n", s.pagesDropped());
    System.out.printf("║   Timed out:      %6d             ║%n", s.pagesTimedOut());
    System.out.println("╠══════════════════════════════════════╣");
    System.out.println("║ RATES                                ║");
    System.out.printf("║   Delivery rate:  %5.1f%%             ║%n", s.deliveryRate() * 100);
    System.out.printf("║   Success rate:   %5.1f%%             ║%n", s.successRate() * 100);
    System.out.printf("║   Drop rate:      %5.1f%%             ║%n", s.dropRate() * 100);
    System.out.println("╚══════════════════════════════════════╝\n");
  }

  /**
   * Runs the simulation for the specified number of ticks and optionally prints a report at regular
   * intervals.
   *
   * @param ticks The total number of ticks to simulate. Must be a positive integer.
   * @param reportInterval The interval at which reports will be printed. If greater than zero, a
   *     report will be printed every {@code reportInterval} ticks. If zero or negative, no reports
   *     will be printed.
   */
  public void runFor(int ticks, int reportInterval) {
    for (int i = 1; i <= ticks; i++) {
      network.simulate(1);

      if (reportInterval > 0 && i % reportInterval == 0) {
        System.out.printf("────────────── Tick %4d ──────────────", i);
        printReport();
      }
    }
  }

  /**
   * Runs the simulation for the specified number of ticks with a default report interval.
   *
   * @param ticks Total number of ticks to simulate.
   */
  public void runFor(int ticks) {
    runFor(ticks, 10);
  }
}
