package ar.edu.unc.david.routersimulator.model.nodes;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.Packet;
import ar.edu.unc.david.routersimulator.model.RoutingTable;
import ar.edu.unc.david.routersimulator.model.stats.RouterStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RouterTest {

  static final IpAddress R1 = IpAddress.ofRouter(1);
  static final IpAddress R2 = IpAddress.ofRouter(2);
  static final IpAddress R3 = IpAddress.ofRouter(3);

  static final IpAddress T1_1 = IpAddress.ofTerminal(1, 1);
  static final IpAddress T1_2 = IpAddress.ofTerminal(1, 2);
  static final IpAddress T2_1 = IpAddress.ofTerminal(2, 1);

  static Packet freshPacket(long pageId, IpAddress src, IpAddress dst) {
    return Packet.create(pageId, 0, 1, src, dst, 0);
  }

  @Nested
  class ConfigurationTests {

    @Test
    void validConfigConstructs() {
      assertDoesNotThrow(() -> new Router.Config(1, 10, 5, 20, 10, 5));
    }

    @Test
    void negativeValuesThrow() {
      assertThrows(IllegalArgumentException.class, () -> new Router.Config(-1, 10, 5, 20, 10, 5));
      assertThrows(IllegalArgumentException.class, () -> new Router.Config(1, -10, 5, 20, 10, 5));
      assertThrows(IllegalArgumentException.class, () -> new Router.Config(1, 10, -5, 20, 10, 5));
      assertThrows(IllegalArgumentException.class, () -> new Router.Config(1, 10, 5, -20, 10, 5));
      assertThrows(IllegalArgumentException.class, () -> new Router.Config(1, 10, 5, 20, -10, 5));
      assertThrows(IllegalArgumentException.class, () -> new Router.Config(1, 10, 5, 20, 10, -5));
    }
  }

  @Nested
  class ConstructionTests {

    @Test
    void validIpConstructsOk() {
      assertDoesNotThrow(() -> new Router(R1));
    }

    @Test
    void terminalIpThrows() {
      assertThrows(IllegalArgumentException.class, () -> new Router(T1_1));
    }

    @Test
    void defaultsAreCorrect() {
      Router r = new Router(R1);
      assertAll(
          () -> assertEquals(R1, r.ip()),
          () -> assertEquals(0, r.terminalCount()),
          () -> assertEquals(0, r.routerCount()),
          () -> assertEquals(Router.DEF_INPUT_PROC, r.inProcCap()),
          () -> assertEquals(Router.DEF_LOC_BW, r.locBufferBw()),
          () -> assertEquals(Router.DEF_OUTPUT_BW, r.outBufferBw()),
          () -> assertEquals(0, r.packetsReceived()),
          () -> assertEquals(0, r.packetsDropped()),
          () -> assertEquals(0, r.packetsTimedOut()),
          () -> assertEquals(0, r.packetsForwarded()),
          () -> assertEquals(0, r.packetsDelivered()));
    }

    @Test
    void terminalCountFromConstructor() {
      Router r = new Router(R1, 3);
      assertEquals(3, r.terminalCount());
    }

    @Test
    void negativeConfigThrows() {
      assertThrows(IllegalArgumentException.class, () -> new Router.Config(-1, 10, 0, 10, 0, 5));
    }
  }

  @Nested
  class ConnectTerminalTests {

    Router router;

    @BeforeEach
    void setUp() {
      router = new Router(R1);
    }

    @Test
    void nullTerminalThrows() {
      assertThrows(IllegalArgumentException.class, () -> router.connectTerminal(null));
    }

    @Test
    void wrongRouterTerminalThrows() {
      Terminal foreign = new Terminal(new Router(R2), 2, 1);
      assertThrows(IllegalArgumentException.class, () -> router.connectTerminal(foreign));
    }

    @Test
    void duplicateTerminalThrows() {
      Terminal t = new Terminal(router, 1, 1);
      router.connectTerminal(t);
      assertThrows(IllegalArgumentException.class, () -> router.connectTerminal(t));
    }

    @Test
    void validTerminalConnects() {
      Terminal t = new Terminal(router, 1, 1);
      assertTrue(router.connectTerminal(t));
      assertEquals(1, router.terminalCount());
    }

    @Test
    void getTerminalReturnsCorrect() {
      Terminal t = new Terminal(router, 1, 1);
      router.connectTerminal(t);
      assertSame(t, router.getTerminal(T1_1));
    }

    @Test
    void getTerminalUnknownReturnsNull() {
      assertNull(router.getTerminal(T1_1));
    }
  }

  @Nested
  class ConnectRouterTests {

    Router r1;
    Router r2;

    @BeforeEach
    void setUp() {
      r1 = new Router(R1);
      r2 = new Router(R2);
    }

    @Test
    void nullNeighborThrows() {
      assertThrows(IllegalArgumentException.class, () -> r1.connectRouter(null));
    }

    @Test
    void selfConnectionReturnsFalse() {
      assertFalse(r1.connectRouter(r1));
    }

    @Test
    void validConnectionSucceeds() {
      assertTrue(r1.connectRouter(r2));
      assertEquals(1, r1.routerCount());
    }

    @Test
    void duplicateConnectionReturnsFalse() {
      r1.connectRouter(r2);
      assertFalse(r1.connectRouter(r2));
      assertEquals(1, r1.routerCount());
    }

    @Test
    void neighborIpsContainConnected() {
      r1.connectRouter(r2);
      assertTrue(r1.getNeighborIps().contains(R2));
    }
  }

  @Nested
  class ReceivePacketTests {

    Router router;

    @BeforeEach
    void setUp() {
      router = new Router(R1, 0, new Router.Config(2, 10, 0, 10, 0, 5));
    }

    @Test
    void acceptsPacketWhenSpace() {
      Packet p = freshPacket(1, T2_1, T1_1);
      assertTrue(router.receivePacket(p));
      assertAll(
          () -> assertEquals(1, router.packetsReceived()),
          () -> assertEquals(0, router.packetsDropped()),
          () -> assertEquals(1, router.packetsInPending()));
    }

    @Test
    void dropsPacketWhenFull() {
      router.receivePacket(freshPacket(1, T2_1, T1_1));
      router.receivePacket(freshPacket(2, T2_1, T1_1));
      assertFalse(router.receivePacket(freshPacket(3, T2_1, T1_1)));
      assertAll(
          () -> assertEquals(3, router.packetsReceived()),
          () -> assertEquals(1, router.packetsDropped()));
    }
  }

  @Nested
  class ProcessOutputBuffersTests {

    Router r1;
    Router r2;

    @BeforeEach
    void setUp() {
      r1 = new Router(R1, 0, new Router.Config(0, 10, 0, 10, 0, 2));
      r2 = new Router(R2);
      r1.connectRouter(r2);

      RoutingTable rt = new RoutingTable();
      rt.setNextHopIp(R2, R2);
      r1.setRoutingTable(rt);
    }

    @Test
    void forwardsPackets() {
      r1.receivePacket(freshPacket(1, T1_1, T2_1));
      r1.receivePacket(freshPacket(2, T1_1, T2_1));
      r1.processInputBuffer(0);
      int sent = r1.processOutputBuffers(0);

      assertEquals(2, sent);
      assertEquals(2, r1.packetsForwarded());
      assertEquals(0, r1.getPacketsOutPending());
    }

    @Test
    void respectsOutBwLimit() {
      r1.receivePacket(freshPacket(1, T1_1, T2_1));
      r1.receivePacket(freshPacket(2, T1_1, T2_1));
      r1.receivePacket(freshPacket(3, T1_1, T2_1));
      r1.processInputBuffer(0);

      int sent = r1.processOutputBuffers(0);

      assertEquals(2, sent);
      assertEquals(1, r1.getPacketsOutPending());
    }

    @Test
    void expiredPacketInOutputBuffer() {
      r1.receivePacket(freshPacket(1, T1_1, T2_1));
      r1.processInputBuffer(0);
      r1.processOutputBuffers(200);

      assertEquals(1, r1.packetsTimedOut());
      assertEquals(0, r1.packetsForwarded());
    }
  }

  @Nested
  class ProcessLocalBufferTests {

    Router router;

    @BeforeEach
    void setUp() {
      router = new Router(R1, 0, new Router.Config(0, 10, 0, 2, 0, 5));
    }

    @Test
    void deliversToTerminal() {
      Terminal t = new Terminal(router, 1, 1);
      router.connectTerminal(t);

      RoutingTable rt = new RoutingTable();
      router.setRoutingTable(rt);

      router.receivePacket(freshPacket(1, T2_1, T1_1));
      router.processInputBuffer(0); // routes to locBuffer (same routerId)
      int delivered = router.processLocalBuffer(0);

      assertEquals(1, delivered);
      assertEquals(1, router.packetsDelivered());
    }

    @Test
    void expiredLocalPacketTimedOut() {
      Terminal t = new Terminal(router, 1, 1);
      router.connectTerminal(t);

      router.receivePacket(freshPacket(1, T2_1, T1_1));
      router.processInputBuffer(0);
      router.processLocalBuffer(200);

      assertEquals(1, router.packetsTimedOut());
    }

    @Test
    void respectsLocBwLimit() {
      Terminal t1 = new Terminal(router, 1, 1);
      Terminal t2 = new Terminal(router, 1, 2);
      router.connectTerminal(t1);
      router.connectTerminal(t2);

      // Put 3 local packets directly by receiving + processing input (inProcCap=10)
      router.receivePacket(freshPacket(1, T2_1, T1_1));
      router.receivePacket(freshPacket(2, T2_1, T1_2));
      router.receivePacket(freshPacket(3, T2_1, T1_1));
      router.processInputBuffer(0);

      int delivered = router.processLocalBuffer(0); // locBW=2 → max 2

      assertEquals(2, delivered);
      assertEquals(1, router.packetsLocPending());
    }

    @Test
    void unknownTerminalDropped() {
      // No terminals connected; packet ends up in locBuffer with no target
      router.receivePacket(freshPacket(1, T2_1, T1_1));
      router.processInputBuffer(0);
      router.processLocalBuffer(0);

      assertEquals(1, router.packetsDropped());
    }
  }

  @Nested
  class ProcessInputBufferTests {

    Router router;

    @BeforeEach
    void setUp() {
      // inProcCap=2, unbounded input buffer
      router = new Router(R1, 0, new Router.Config(0, 2, 0, 10, 0, 5));
    }

    @Test
    void respectsProcessingCapacity() {
      Router r2 = new Router(R2);
      router.connectRouter(r2);
      RoutingTable rt = new RoutingTable();
      rt.setNextHopIp(R2, R2);
      router.setRoutingTable(rt);

      router.receivePacket(freshPacket(1, T1_1, T2_1));
      router.receivePacket(freshPacket(2, T1_1, T2_1));
      router.receivePacket(freshPacket(3, T1_1, T2_1));

      int processed = router.processInputBuffer(0);

      assertEquals(2, processed);
      assertEquals(1, router.packetsInPending());
    }

    @Test
    void expiredPacketsTimedOut() {
      long now = 200;
      router.receivePacket(freshPacket(1, T2_1, T1_1));

      router.processInputBuffer(now);

      assertEquals(1, router.packetsTimedOut());
      assertEquals(0, router.packetsForwarded());
    }

    @Test
    void noRouteDropsPacket() {
      router.setRoutingTable(new RoutingTable());
      router.receivePacket(freshPacket(1, T1_1, T2_1));
      router.processInputBuffer(0);
      assertEquals(1, router.packetsDropped());
    }

    @Test
    void localBufferOverflowDropsPacket() {
      Router r1 = new Router(R1, 0, new Router.Config(0, 10, 2, 3, 0, 5));
      r1.receivePacket(freshPacket(1, T2_1, T1_1));
      r1.receivePacket(freshPacket(2, T2_1, T1_1));
      r1.receivePacket(freshPacket(3, T2_1, T1_1));

      r1.processInputBuffer(0);

      assertEquals(1, r1.packetsDropped());
      assertEquals(2, r1.packetsLocPending());
      assertEquals(3, r1.packetsReceived());
    }

    @Test
    void neighborBufferOverflowDropsPacket() {
      Router r1 = new Router(R1, 0, new Router.Config(0, 10, 0, 10, 2, 5));
      Router r2 = new Router(R2);
      r1.connectRouter(r2);

      RoutingTable rt = new RoutingTable();
      rt.setNextHopIp(R2, R2);
      r1.setRoutingTable(rt);

      r1.receivePacket(freshPacket(1, T1_1, T2_1));
      r1.receivePacket(freshPacket(2, T1_1, T2_1));
      r1.receivePacket(freshPacket(3, T1_1, T2_1));

      r1.processInputBuffer(0);

      assertEquals(1, r1.packetsDropped());
      assertEquals(2, r1.getPacketsOutPending());
    }
  }

  @Nested
  class TickIntegrationTests {

    @Test
    void packetTravelsToNeighborRouter() {
      Router r1 = new Router(R1);
      Router r2 = new Router(R2);
      r1.connectRouter(r2);

      RoutingTable rt = new RoutingTable();
      rt.setNextHopIp(R2, R2);
      r1.setRoutingTable(rt);

      r1.receivePacket(freshPacket(1, T1_1, T2_1));

      r1.tick(1);
      assertEquals(0, r1.packetsInPending());
      assertEquals(1, r1.getPacketsOutPending());

      r1.tick(2);
      assertEquals(0, r1.getPacketsOutPending());
      assertEquals(1, r2.packetsInPending());
    }

    @Test
    void packetDeliveredLocally() {
      Router r1 = new Router(R1);
      Terminal t = new Terminal(r1, 1, 1);
      r1.connectTerminal(t);

      r1.receivePacket(freshPacket(1, T2_1, T1_1));
      r1.tick(1);
      assertEquals(1, r1.packetsLocPending());
      assertEquals(0, r1.packetsDelivered());

      r1.tick(2);
      assertEquals(1, r1.packetsDelivered());
      assertEquals(0, r1.packetsInPending());
      assertEquals(1, t.packetsReceived());
    }
  }

  @Nested
  class ListAccessorTests {

    @Test
    void getTerminalsReturnsAll() {
      Router r = new Router(R1, 2);
      List<Terminal> ts = r.getTerminals();
      assertEquals(2, ts.size());
    }

    @Test
    void getTerminalIpsReturnsAll() {
      Router r = new Router(R1, 2);
      List<IpAddress> ips = r.getTerminalIps();
      assertEquals(2, ips.size());
      assertTrue(ips.contains(T1_1));
      assertTrue(ips.contains(T1_2));
    }

    @Test
    void getNeighborIpsReturnsAll() {
      Router r1 = new Router(R1);
      Router r2 = new Router(R2);
      Router r3 = new Router(R3);
      r1.connectRouter(r2);
      r1.connectRouter(r3);

      List<IpAddress> ips = r1.getNeighborIps();
      assertEquals(2, ips.size());
      assertTrue(ips.contains(R2));
      assertTrue(ips.contains(R3));
    }
  }

  @Nested
  class StatsTests {

    @Test
    void allCountersStartAtZero() {
      Router r = new Router(R1);
      assertAll(
          () -> assertEquals(0, r.packetsReceived()),
          () -> assertEquals(0, r.packetsDropped()),
          () -> assertEquals(0, r.packetsTimedOut()),
          () -> assertEquals(0, r.packetsForwarded()),
          () -> assertEquals(0, r.packetsDelivered()),
          () -> assertEquals(0, r.packetsInPending()),
          () -> assertEquals(0, r.packetsLocPending()),
          () -> assertEquals(0, r.getPacketsOutPending()));
    }

    @Test
    void receiveStats() {
      RouterStats stats = new RouterStats(R1, 0, 0, 0, 0, 0, 0, 0);
      Router r = new Router(R1);
      RoutingTable rt = new RoutingTable();
      r.setRoutingTable(rt);

      assertEquals(stats, r.collectStats());
    }

    @Test
    void receivedCountsDroppedToo() {
      Router r = new Router(R1, 0, new Router.Config(1, 10, 0, 10, 0, 5));
      r.receivePacket(freshPacket(1, T2_1, T1_1));
      r.receivePacket(freshPacket(2, T2_1, T1_1)); // dropped

      assertEquals(2, r.packetsReceived());
      assertEquals(1, r.packetsDropped());
    }

    @Test
    void neighborBufferUsage() {
      Router r1 = new Router(R1);
      Router r2 = new Router(R2);
      r1.connectRouter(r2);

      RoutingTable rt = new RoutingTable();
      rt.setNextHopIp(R2, R2);
      r1.setRoutingTable(rt);

      r1.receivePacket(freshPacket(1, T1_1, T2_1));
      r1.receivePacket(freshPacket(2, T1_1, T2_1));
      r1.processInputBuffer(0); // both go to outBuffer for R2

      assertEquals(2, r1.getNeighborBufferUsage(R2));
    }

    @Test
    void neighborBufferUsageUnknownNeighbor() {
      Router r1 = new Router(R1);
      assertEquals(0, r1.getNeighborBufferUsage(R2));
    }

    @Test
    void localBufferUsageMatchesPending() {
      Router r1 = new Router(R1);
      Terminal t = new Terminal(r1, 1, 1);
      r1.connectTerminal(t);
      r1.setRoutingTable(new RoutingTable());

      r1.receivePacket(freshPacket(1, T2_1, T1_1));
      r1.processInputBuffer(0);

      assertEquals(r1.packetsLocPending(), r1.localBufferUsage());
      assertEquals(1, r1.localBufferUsage());
    }
  }

  @Nested
  class ShareTests {
    Router r1;
    Terminal t1;
    Terminal t2;

    @BeforeEach
    void setUp() {
      r1 = new Router(R1);
      t1 = new Terminal(r1, 1, 1);
      t2 = new Terminal(r1, 1, 2);
      r1.connectTerminal(t1);
      r1.connectTerminal(t2);
    }

    @Test
    void shareAddressBook() {
      List<IpAddress> ips = new ArrayList<>();
      ips.add(IpAddress.ofTerminal(1, 1));
      r1.shareAddressBook(ips);

      assertEquals(ips, t1.addressBook());
      assertEquals(ips, t2.addressBook());
    }

    @Test
    void shareRandomGenerator() {
      Random gen = new Random(42);
      r1.shareRandomGenerator(gen);

      assertEquals(gen, t1.rng());
      assertEquals(gen, t2.rng());
    }

    @Test
    void shareTrafficProbability() {
      double p = 0.5;
      r1.shareTrafficProbability(p);

      assertEquals(p, t1.trafficProb());
      assertEquals(p, t2.trafficProb());
    }

    @Test
    void shareMaxPageLen() {
      int maxLen = 10;
      r1.shareMaxPageLength(maxLen);

      assertEquals(maxLen, t1.maxPageLen());
      assertEquals(maxLen, t2.maxPageLen());
    }
  }

  @Nested
  class SetterTests {

    Router router;

    @BeforeEach
    void setUp() {
      router = new Router(R1);
    }

    @Test
    void setInProcCapWorks() {
      router.setInProcCap(42);
      assertEquals(42, router.inProcCap());
    }

    @Test
    void setLocBufferBwWorks() {
      router.setLocBufferBw(7);
      assertEquals(7, router.locBufferBw());
    }

    @Test
    void setOutBufferBwWorks() {
      router.setOutBufferBw(3);
      assertEquals(3, router.outBufferBw());
    }
  }
}
