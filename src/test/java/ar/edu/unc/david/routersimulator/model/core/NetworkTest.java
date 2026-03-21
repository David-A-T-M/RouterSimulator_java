package ar.edu.unc.david.routersimulator.model.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.nodes.Router;
import ar.edu.unc.david.routersimulator.model.stats.NetworkStats;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Network}.
 *
 * <p>A seeded {@link Random} is injected in every test to make topology generation deterministic
 * and reproducible.
 */
@DisplayName("Network")
class NetworkTest {

  /** Seeded RNG — topology is the same on every run. */
  static final long SEED = 42L;

  static Network smallNetwork() {
    // 4 routers, 2 terminals each, complexity 0 (spanning tree only)
    return new Network(new Network.Config(4, 2, 0, 0.5f, 5), new Random(SEED));
  }

  @Nested
  @DisplayName("Config")
  class ConfigTests {

    @Test
    @DisplayName("Config parameters are set correctly")
    void configParametersSet() {
      Network.Config cfg = new Network.Config(10, 5, 3, 0.7f, 8);
      assertEquals(10, cfg.routerCount());
      assertEquals(5, cfg.maxTerminalCount());
      assertEquals(3, cfg.complexity());
      assertEquals(0.7f, cfg.trafficProbability());
      assertEquals(8, cfg.maxPageLen());
    }

    @Test
    void invalidConfigThrows() {
      assertAll(
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new Network.Config(-1, 5, 3, 0.7f, 8)),
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new Network.Config(256, 5, 3, 0.7f, 8)),
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new Network.Config(10, -1, 3, 0.7f, 8)),
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new Network.Config(10, 256, 3, 0.7f, 8)),
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new Network.Config(10, 5, -1, 0.7f, 8)),
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new Network.Config(10, 5, 3, -0.1f, 8)),
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new Network.Config(10, 5, 3, 1.1f, 8)),
          () ->
              assertThrows(
                  IllegalArgumentException.class, () -> new Network.Config(10, 5, 3, 0.7f, 0)));
    }
  }

  @Nested
  @DisplayName("Construction")
  class ConstructionTests {

    @Test
    @DisplayName("Default constructor builds a network without throwing")
    void defaultConstructorWorks() {
      Network net = new Network();
      assertAll(
          () -> assertEquals(20, net.getRouters().size()),
          () -> assertEquals(10, net.getRouters().getFirst().getTerminals().size()));
    }

    @Test
    @DisplayName("Custom config builds a network without throwing")
    void customConfigWorks() {
      Network.Config cfg = new Network.Config(5, 3, 2, 0.8f, 8);
      Network net = new Network(cfg, new Random(SEED));
      assertAll(
          () -> assertEquals(5, net.getRouters().size()),
          () -> assertTrue(net.getRouters().stream().allMatch(r -> r.terminalCount() <= 3)));
    }

    @Test
    @DisplayName("Router count matches config")
    void routerCountMatchesConfig() {
      Network net = smallNetwork();
      assertEquals(4, net.getRouters().size());
    }

    @Test
    @DisplayName("Each router has the expected number of terminals")
    void terminalCountPerRouter() {
      Network net = smallNetwork(); // 2 terminals per router
      for (Router r : net.getRouters()) {
        assertEquals(2, r.terminalCount());
      }
    }

    @Test
    @DisplayName("Config with negative complexity throws")
    void negativeComplexityThrows() {
      assertThrows(IllegalArgumentException.class, () -> new Network.Config(4, 2, -1, 0.5f, 5));
    }

    @Test
    @DisplayName("Config with probability > 1 throws")
    void probabilityOutOfRangeThrows() {
      assertThrows(IllegalArgumentException.class, () -> new Network.Config(4, 2, 1, 1.5f, 5));
    }

    @Test
    @DisplayName("Config with maxPageLen = 0 throws")
    void zeroPageLenThrows() {
      assertThrows(IllegalArgumentException.class, () -> new Network.Config(4, 2, 1, 0.5f, 0));
    }
  }

  @Nested
  @DisplayName("Topology")
  class TopologyTests {

    @Test
    @DisplayName("Spanning tree: all routers have at least one connection")
    void allRoutersHaveAtLeastOneConnection() {
      Network net = smallNetwork();
      for (Router r : net.getRouters()) {
        assertTrue(r.routerCount() >= 1, "Router " + r.ip() + " has no connections");
      }
    }

    @Test
    @DisplayName("All router IPs are unique")
    void allRouterIpsAreUnique() {
      Network net = smallNetwork();
      Set<IpAddress> ips = new HashSet<>();
      for (Router r : net.getRouters()) {
        assertTrue(ips.add(r.ip()), "Duplicate IP: " + r.ip());
      }
    }

    @Test
    @DisplayName("establishLink is bidirectional")
    void establishLinkIsBidirectional() {
      Router a = new Router(IpAddress.ofRouter(10));
      Router b = new Router(IpAddress.ofRouter(11));
      Network.establishLink(a, b);

      assertTrue(a.getNeighborIps().contains(b.ip()));
      assertTrue(b.getNeighborIps().contains(a.ip()));
    }

    @Test
    @DisplayName("establishLink with same router does nothing")
    void establishLinkSelfIsNoop() {
      Router a = new Router(IpAddress.ofRouter(10));
      Network.establishLink(a, a);
      assertEquals(0, a.routerCount());
    }

    @Test
    @DisplayName("Higher complexity adds more connections on average")
    void complexityIncreasesConnections() {
      Network low = new Network(new Network.Config(6, 1, 0, 0f, 5), new Random(SEED));
      Network high = new Network(new Network.Config(6, 1, 5, 0f, 5), new Random(SEED));

      int totalLow = low.getRouters().stream().mapToInt(Router::routerCount).sum();
      int totalHigh = high.getRouters().stream().mapToInt(Router::routerCount).sum();

      assertTrue(
          totalHigh >= totalLow, "Higher complexity should produce at least as many connections");
    }

    @Test
    @DisplayName("Zero-router network produces empty router list")
    void zeroRoutersProducesEmptyList() {
      Network net = new Network(new Network.Config(0, 0, 0, 0f, 1), new Random(SEED));
      assertTrue(net.getRouters().isEmpty());
    }
  }

  @Nested
  @DisplayName("simulate")
  class SimulateTests {

    @Test
    @DisplayName("simulate(0) does not throw and leaves network intact")
    void zeroTicksDoesNotThrow() {
      Network net = smallNetwork();
      assertDoesNotThrow(() -> net.simulate(0));
      assertEquals(4, net.getRouters().size());
    }

    @Test
    @DisplayName("simulate(N) runs without exception")
    void simulateRunsWithoutException() {
      // probability=0 → no traffic generated, so no packets can get lost
      Network net = new Network(new Network.Config(4, 2, 1, 0f, 5), new Random(SEED));
      assertDoesNotThrow(() -> net.simulate(20));
    }

    @Test
    @DisplayName("After simulation, currentTick in stats equals ticks run")
    void currentTickMatchesTicksRun() {
      Network net = new Network(new Network.Config(4, 2, 1, 0f, 5), new Random(SEED));
      net.simulate(10);
      assertEquals(10, net.getStats().currentTick());
    }
  }

  @Nested
  @DisplayName("getStats")
  class GetStatsTests {

    @Test
    @DisplayName("totalRouters matches actual router count")
    void totalRoutersMatches() {
      Network net = smallNetwork();
      assertEquals(net.getRouters().size(), net.getStats().totalRouters());
    }

    @Test
    @DisplayName("totalTerminals matches sum across all routers")
    void totalTerminalsMatches() {
      Network net = smallNetwork(); // 4 routers × 2 terminals
      assertEquals(8, net.getStats().totalTerminals());
    }

    @Test
    @DisplayName("No traffic generated when probability = 0")
    void noTrafficWhenProbabilityZero() {
      Network net = new Network(new Network.Config(4, 2, 1, 0f, 5), new Random(SEED));
      net.simulate(50);
      NetworkStats s = net.getStats();
      assertEquals(0, s.packetsGenerated());
      assertEquals(0, s.packetsSent());
    }

    @Test
    @DisplayName("Stats record is a fresh snapshot each call")
    void statsIsSnapshot() {
      Network net = new Network(new Network.Config(4, 2, 1, 0f, 5), new Random(SEED));
      NetworkStats before = net.getStats();
      net.simulate(5);
      NetworkStats after = net.getStats();

      // currentTick should have advanced
      assertTrue(after.currentTick() > before.currentTick());
    }

    @Test
    @DisplayName("deliveryRate is in [0, 1] after simulation with traffic")
    void deliveryRateInRange() {
      Network net = new Network(new Network.Config(5, 3, 2, 0.8f, 5), new Random(SEED));
      net.simulate(100);
      float rate = net.getStats().deliveryRate();
      assertTrue(rate >= 0f && rate <= 1f, "deliveryRate out of range: " + rate);
    }

    @Test
    @DisplayName("packetsInFlight is non-negative")
    void packetsInFlightNonNegative() {
      Network net = new Network(new Network.Config(4, 2, 1, 0.5f, 5), new Random(SEED));
      net.simulate(10);
      assertTrue(net.getStats().packetsInFlight() >= 0);
    }
  }
}
