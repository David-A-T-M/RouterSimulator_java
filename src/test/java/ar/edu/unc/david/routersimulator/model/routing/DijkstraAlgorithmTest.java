package ar.edu.unc.david.routersimulator.model.routing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.RoutingTable;
import ar.edu.unc.david.routersimulator.model.nodes.Router;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for DijkstraAlgorithm.
 *
 * <p>All tests use empty output buffers (bufferLoad = 0 for every link), so edge weight is always 1
 * — Dijkstra reduces to BFS and hop count is the metric.
 *
 * <p>Topology used in most tests:
 *
 * <p>R1 — R2 — R3 | R4
 *
 * <p>Shortest paths from R1: R2 (1 hop), R3 (2 hops via R2), R4 (2 hops via R2).
 */
@DisplayName("DijkstraAlgorithm")
class DijkstraAlgorithmTest {

  static final IpAddress R1 = IpAddress.ofRouter(1);
  static final IpAddress R2 = IpAddress.ofRouter(2);
  static final IpAddress R3 = IpAddress.ofRouter(3);
  static final IpAddress R4 = IpAddress.ofRouter(4);
  static final IpAddress R5 = IpAddress.ofRouter(5);

  Router r1;
  Router r2;
  Router r3;
  Router r4;

  @BeforeEach
  void buildBaseTopology() {
    r1 = new Router(R1);
    r2 = new Router(R2);
    r3 = new Router(R3);
    r4 = new Router(R4);

    // R1 — R2 — R3
    //       |
    //       R4
    link(r1, r2);
    link(r2, r3);
    link(r2, r4);
  }

  /** Bidirectional link helper. */
  static void link(Router a, Router b) {
    a.connectRouter(b);
    b.connectRouter(a);
  }

  // ── computeRoutingTable ───────────────────────────────────────────────────

  @Nested
  @DisplayName("computeRoutingTable")
  class ComputeRoutingTableTests {

    @Test
    @DisplayName("Direct neighbour: next hop is the neighbour itself")
    void directNeighbour() {
      List<Router> routers = List.of(r1, r2, r3, r4);
      RoutingTable rt = DijkstraAlgorithm.computeRoutingTable(routers, R1);

      assertEquals(R2, rt.getNextHopIp(R2));
    }

    @Test
    @DisplayName("Two-hop destination: next hop is the intermediate router")
    void twoHopDestination() {
      List<Router> routers = List.of(r1, r2, r3, r4);
      RoutingTable rt = DijkstraAlgorithm.computeRoutingTable(routers, R1);

      // R3 and R4 are both two hops away via R2
      assertEquals(R2, rt.getNextHopIp(R3));
      assertEquals(R2, rt.getNextHopIp(R4));
    }

    @Test
    @DisplayName("Source router itself has no entry in its own table")
    void sourceHasNoSelfEntry() {
      List<Router> routers = List.of(r1, r2, r3, r4);
      RoutingTable rt = DijkstraAlgorithm.computeRoutingTable(routers, R1);

      assertNull(rt.getNextHopIp(R1));
    }

    @Test
    @DisplayName("Every reachable router has an entry")
    void allReachableHaveEntries() {
      List<Router> routers = List.of(r1, r2, r3, r4);
      RoutingTable rt = DijkstraAlgorithm.computeRoutingTable(routers, R1);

      assertAll(
          () -> assertNotNull(rt.getNextHopIp(R2)),
          () -> assertNotNull(rt.getNextHopIp(R3)),
          () -> assertNotNull(rt.getNextHopIp(R4)));
    }

    @Test
    @DisplayName("Unreachable router returns null next-hop")
    void unreachableReturnsNull() {
      // R5 is isolated — not connected to anyone
      Router r5 = new Router(R5);
      List<Router> routers = List.of(r1, r2, r3, r4, r5);
      RoutingTable rt = DijkstraAlgorithm.computeRoutingTable(routers, R1);

      assertNull(rt.getNextHopIp(R5));
    }

    @Test
    @DisplayName("Single-router network produces empty table")
    void singleRouterEmptyTable() {
      RoutingTable rt = DijkstraAlgorithm.computeRoutingTable(List.of(r1), R1);
      assertNull(rt.getNextHopIp(R1));
    }

    @Test
    @DisplayName("Null router list throws NullPointerException")
    void nullRouterListThrows() {
      assertThrows(
          NullPointerException.class, () -> DijkstraAlgorithm.computeRoutingTable(null, R1));
    }

    @Test
    @DisplayName("Null source IP throws NullPointerException")
    void nullSourceIpThrows() {
      List<Router> routers = List.of(r1, r2);
      assertThrows(
          NullPointerException.class, () -> DijkstraAlgorithm.computeRoutingTable(routers, null));
    }

    @Test
    @DisplayName("Unknown source IP throws IllegalArgumentException")
    void unknownSourceThrows() {
      List<Router> routers = List.of(r1, r2);
      assertThrows(
          IllegalArgumentException.class, () -> DijkstraAlgorithm.computeRoutingTable(routers, R5));
    }

    @Test
    @DisplayName("Table is symmetric: R3→R1 next hop is R2")
    void symmetricTable() {
      List<Router> routers = List.of(r1, r2, r3, r4);
      RoutingTable rt = DijkstraAlgorithm.computeRoutingTable(routers, R3);

      // R3 — R2 — R1  →  next hop from R3 to R1 is R2
      assertEquals(R2, rt.getNextHopIp(R1));
    }
  }

  // ── Path selection with multiple routes ───────────────────────────────────

  @Nested
  @DisplayName("Path selection")
  class PathSelectionTests {

    /**
     * Topology: R1 — R2 — R3 \ / R4 — R5.
     *
     * <p>Two paths from R1 to R3: short: R1→R2→R3 (2 hops, buffer cost 0+0 = 0) long: R1→R4→R5→R3
     * (3 hops, buffer cost 0+0+0 = 0) With equal weights Dijkstra takes the first-discovered path →
     * R2.
     */
    @Test
    @DisplayName("Prefers shorter path when weights are equal")
    void prefersShorterPath() {
      Router ra = new Router(R1);
      Router rb = new Router(R2);
      Router rc = new Router(R3);
      Router rd = new Router(R4);
      link(ra, rb);
      link(rb, rc);
      link(ra, rd);

      Router re = new Router(R5);
      link(rd, re);
      link(re, rc);

      List<Router> routers = List.of(ra, rb, rc, rd, re);
      RoutingTable rt = DijkstraAlgorithm.computeRoutingTable(routers, R1);

      // Short path R1→R2→R3: next hop to R3 should be R2
      assertEquals(R2, rt.getNextHopIp(R3));
    }
  }

  // ── computeAllRoutingTables ───────────────────────────────────────────────

  @Nested
  @DisplayName("computeAllRoutingTables")
  class ComputeAllRoutingTablesTests {

    @Test
    @DisplayName("Returns one table per router")
    void oneTablePerRouter() {
      List<Router> routers = List.of(r1, r2, r3, r4);
      List<RoutingTable> tables = DijkstraAlgorithm.computeAllRoutingTables(routers);
      assertEquals(4, tables.size());
    }

    @Test
    @DisplayName("Each table is correct for its respective router")
    void tablesAreCorrect() {
      List<Router> routers = List.of(r1, r2, r3, r4);
      List<RoutingTable> tables = DijkstraAlgorithm.computeAllRoutingTables(routers);

      // tables.get(0) is for R1 → R3 reachable via R2
      assertEquals(R2, tables.get(0).getNextHopIp(R3));

      // tables.get(2) is for R3 → R1 reachable via R2
      assertEquals(R2, tables.get(2).getNextHopIp(R1));
    }

    @Test
    @DisplayName("Empty router list returns empty table list")
    void emptyListReturnsEmpty() {
      List<RoutingTable> tables = DijkstraAlgorithm.computeAllRoutingTables(List.of());
      assertTrue(tables.isEmpty());
    }
  }
}
