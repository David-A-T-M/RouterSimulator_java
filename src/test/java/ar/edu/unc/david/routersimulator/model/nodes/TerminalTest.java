package ar.edu.unc.david.routersimulator.model.nodes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.Packet;
import ar.edu.unc.david.routersimulator.model.stats.TerminalStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalTest {

  private static final int ROUTER_ID = 1;
  private static final int TERMINAL_ID = 1;
  private static final IpAddress SELF = new IpAddress(ROUTER_ID, TERMINAL_ID);
  private static final IpAddress OTHER_SRC = new IpAddress(1, 2);
  private static final IpAddress OTHER_DST = new IpAddress(1, 3);

  @Mock private PacketReceiver router;
  private Terminal terminal;

  @BeforeEach
  void setUp() {
    terminal = new Terminal(router, ROUTER_ID, TERMINAL_ID, new Terminal.Config(0, 5, 0, 3));
  }

  @Test
  void config_constructor_throwsWithInvalidParameters() {
    assertThrows(IllegalArgumentException.class, () -> new Terminal.Config(-1, 5, 0, 3));
    assertThrows(IllegalArgumentException.class, () -> new Terminal.Config(0, -1, 0, 3));
    assertThrows(IllegalArgumentException.class, () -> new Terminal.Config(0, 5, -1, 3));
    assertThrows(IllegalArgumentException.class, () -> new Terminal.Config(0, 5, 0, -1));
  }

  @Test
  void constructor_throwsWhenTerminalIdIsZero() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Terminal(router, ROUTER_ID, 0, new Terminal.Config()));
  }

  @Test
  void constructor_isInitialized() {
    assertEquals(SELF, terminal.ip());
    assertEquals(0, terminal.outBufferPending());
    assertEquals(0, terminal.inputBufferPending());
    assertEquals(0, terminal.pagesCreated());
    assertEquals(0, terminal.pagesSent());
    assertEquals(0, terminal.packetsGenerated());
    assertEquals(0, terminal.packetsReceived());
    assertEquals(5, terminal.inProcCap());
    assertEquals(3, terminal.outBw());
  }

  @Test
  void constructor_defaultConfig() {
    Terminal defaultConfigTerminal = new Terminal(router, ROUTER_ID, TERMINAL_ID + 1);
    assertEquals(10, defaultConfigTerminal.inProcCap());
    assertEquals(5, defaultConfigTerminal.outBw());
  }

  @Test
  void sendPage_enqueuesPacketsAndUpdatesCounters() {
    boolean sent = terminal.sendPage(3, OTHER_DST, 0L);

    assertTrue(sent);
    assertEquals(3, terminal.outBufferPending());
    assertEquals(1, terminal.pagesCreated());
    assertEquals(1, terminal.pagesSent());
    assertEquals(3, terminal.packetsGenerated());
    assertEquals(0, terminal.pagesOutDropped());
    assertEquals(0, terminal.packetsOutDropped());
  }

  @Test
  void sendPage_dropsWhenOutputBufferHasNotEnoughSpace() {
    Terminal boundedOut =
        new Terminal(router, ROUTER_ID, TERMINAL_ID, new Terminal.Config(0, 10, 2, 5));

    boolean sent = boundedOut.sendPage(3, OTHER_DST, 0L);

    assertFalse(sent);
    assertEquals(0, boundedOut.outBufferPending());
    assertEquals(1, boundedOut.pagesCreated());
    assertEquals(0, boundedOut.pagesSent());
    assertEquals(1, boundedOut.pagesOutDropped());
    assertEquals(3, boundedOut.packetsOutDropped());
  }

  @Test
  void setAddressBook_throwsWithNull() {
    assertThrows(IllegalArgumentException.class, () -> terminal.setAddressBook(null));
  }

  @Test
  void setRng_throwsWithNull() {
    assertThrows(IllegalArgumentException.class, () -> terminal.setRng(null));
  }

  @Test
  void setTrafficProbability_throwsWithInvalidValues() {
    assertThrows(IllegalArgumentException.class, () -> terminal.setTrafficProbability(-0.1));
    assertThrows(IllegalArgumentException.class, () -> terminal.setTrafficProbability(1.1));
  }

  @Test
  void setMaxPageLength_throwsWithInvalidValues() {
    assertThrows(IllegalArgumentException.class, () -> terminal.setMaxPageLength(0));
    assertThrows(IllegalArgumentException.class, () -> terminal.setMaxPageLength(-1));
  }

  @Test
  void setOutputBandwidth_updatesBw() {
    terminal.setOutputBw(4);
    assertEquals(4, terminal.outBw());
  }

  @Test
  void setOutputBandwidth_throwsWithInvalidBw() {
    assertThrows(IllegalArgumentException.class, () -> terminal.setOutputBw(-1));
  }

  @Test
  void setInProcessingCapacity_updatesCap() {
    terminal.setInProcCap(7);
    assertEquals(7, terminal.inProcCap());
  }

  @Test
  void setInProcessingCapacity_throwsWithInvalidCap() {
    assertThrows(IllegalArgumentException.class, () -> terminal.setInProcCap(-1));
  }

  @Test
  void tick_processOutput_respectsBandwidth() {
    Terminal limitedBw =
        new Terminal(router, ROUTER_ID, TERMINAL_ID, new Terminal.Config(0, 10, 0, 2));
    limitedBw.sendPage(3, OTHER_DST, 0L);

    limitedBw.tick(1L);

    verify(router, times(2)).receivePacket(any(Packet.class));
    assertEquals(2, limitedBw.packetsSent());
    assertEquals(1, limitedBw.outBufferPending());
  }

  @Test
  void tick_dropsExpiredOutputPacketsWithoutForwarding() {
    terminal.sendPage(2, OTHER_DST, 0L);

    terminal.tick(150L);

    verify(router, never()).receivePacket(any(Packet.class));
    assertEquals(0, terminal.packetsSent());
    assertEquals(2, terminal.packetsOutTimedOut());
    assertEquals(0, terminal.outBufferPending());
  }

  @Test
  void receivePacket_enqueuesWhenValid() {
    Packet p = packetToSelf(10L, 0, 1, 0L);

    boolean accepted = terminal.receivePacket(p);

    assertTrue(accepted);
    assertEquals(1, terminal.packetsReceived());
    assertEquals(1, terminal.inputBufferPending());
  }

  @Test
  void receivePacket_dropsWhenInputBufferIsFull() {
    Terminal boundedIn =
        new Terminal(router, ROUTER_ID, TERMINAL_ID, new Terminal.Config(1, 10, 0, 5));

    assertTrue(boundedIn.receivePacket(packetToSelf(20L, 0, 2, 0L)));
    assertFalse(boundedIn.receivePacket(packetToSelf(20L, 1, 2, 0L)));

    assertEquals(2, boundedIn.packetsReceived());
    assertEquals(1, boundedIn.packetsInDropped());
    assertEquals(1, boundedIn.inputBufferPending());
  }

  @Test
  void tick_processInput_completesPage() {
    assertTrue(terminal.receivePacket(packetToSelf(30L, 0, 2, 0L)));
    assertTrue(terminal.receivePacket(packetToSelf(30L, 1, 2, 0L)));

    terminal.tick(1L);

    assertEquals(1, terminal.pagesCompleted());
    assertEquals(2, terminal.packetsSuccProcessed());
    assertEquals(0, terminal.inputBufferPending());

    TerminalStats stats = terminal.collectStats();
    assertEquals(0, stats.activeReassemblers());
    assertEquals(0, stats.quarantineSize());
  }

  @Test
  void tick_processInput_limitInProcessingCapacity() {
    Terminal limitedIn =
        new Terminal(router, ROUTER_ID, TERMINAL_ID, new Terminal.Config(0, 2, 0, 5));

    assertTrue(limitedIn.receivePacket(packetToSelf(40L, 0, 5, 0L)));
    assertTrue(limitedIn.receivePacket(packetToSelf(40L, 1, 5, 0L)));
    assertTrue(limitedIn.receivePacket(packetToSelf(40L, 2, 5, 0L)));

    limitedIn.tick(1L);
    assertEquals(3, limitedIn.inputBufferPending());
  }

  @Test
  void tick_dropsInputPacketWithWrongDestination() {
    assertTrue(terminal.receivePacket(packetToOther()));

    terminal.tick(1L);

    assertEquals(1, terminal.packetsInDropped());
    assertEquals(0, terminal.packetsSuccProcessed());
  }

  @Test
  void timedOutReassembler_addsQuarantineAndBlocksSamePageIdTemporarily() {
    assertTrue(terminal.receivePacket(packetToSelf(50L, 0, 2, 0L)));
    terminal.tick(0L);

    TerminalStats beforeTimeout = terminal.collectStats();
    assertEquals(1, beforeTimeout.activeReassemblers());

    terminal.tick(250L);

    TerminalStats afterTimeout = terminal.collectStats();
    assertEquals(0, afterTimeout.activeReassemblers());
    assertEquals(1, afterTimeout.quarantineSize());
    assertEquals(1, terminal.pagesTimedOut());
    assertEquals(1, terminal.packetsInTimedOut());

    assertFalse(terminal.receivePacket(packetToSelf(50L, 1, 2, 250L)));
    assertEquals(2, terminal.packetsInTimedOut());

    terminal.tick(350L);
    TerminalStats afterQuarantineExpiry = terminal.collectStats();
    assertEquals(0, afterQuarantineExpiry.quarantineSize());

    long afterExpiry = 351L;
    assertTrue(terminal.receivePacket(packetToSelf(50L, 0, 2, afterExpiry)));
  }

  @Test
  void tick_generateTraffic_whenConfiguredAndProbabilityMatches() {
    Terminal t = new Terminal(router, ROUTER_ID, TERMINAL_ID, new Terminal.Config(0, 10, 0, 5));
    t.setAddressBook(List.of(OTHER_DST));
    t.setTrafficProbability(1.0);
    t.setMaxPageLength(4);
    t.setRng(new StubRandom(0.0, 0, 0));

    t.tick(0L);

    assertEquals(1, t.pagesCreated());
    assertEquals(1, t.pagesSent());
    assertEquals(2, t.outBufferPending());
  }

  @Test
  void tick_quarantineNotExpiredYet_keepsEntry() {
    assertTrue(terminal.receivePacket(packetToSelf(60L, 0, 2, 0L)));
    terminal.tick(0L);

    terminal.tick(250L);
    TerminalStats afterTimeout = terminal.collectStats();
    assertEquals(1, afterTimeout.quarantineSize());

    terminal.tick(300L);
    TerminalStats stillQuarantined = terminal.collectStats();
    assertEquals(1, stillQuarantined.quarantineSize());
  }

  @Test
  void tick_cleanupReassemblers_notExpired_keepsReassemblerActive() {
    assertTrue(terminal.receivePacket(packetToSelf(70L, 0, 2, 0L)));

    terminal.tick(1L);
    terminal.tick(1L);

    TerminalStats stats = terminal.collectStats();
    assertEquals(1, stats.activeReassemblers());
    assertEquals(0, terminal.pagesTimedOut());
  }

  @Test
  void receivePacket_pageIdDifferentFromQuarantine_isAccepted() {
    assertTrue(terminal.receivePacket(packetToSelf(80L, 0, 2, 0L)));
    terminal.tick(0L);
    terminal.tick(250L);

    long timedOutBefore = terminal.packetsInTimedOut();
    TerminalStats qstats = terminal.collectStats();
    assertEquals(1, qstats.quarantineSize());

    boolean accepted = terminal.receivePacket(packetToSelf(81L, 0, 2, 250L));

    assertTrue(accepted);
    assertEquals(timedOutBefore, terminal.packetsInTimedOut());
    assertEquals(1, terminal.inputBufferPending());
  }

  @Test
  void processInputBuffer_expiredPacket() {
    terminal.receivePacket(packetToSelf(81L, 0, 2, 0));
    terminal.tick(150);

    assertEquals(1, terminal.packetsInTimedOut());
    assertEquals(0, terminal.inputBufferPending());
  }

  @Test
  void processInputBuffer_cantFindReassembler() {
    terminal.receivePacket(packetToSelf(90L, 0, 2, 0L));
    terminal.tick(1);
    terminal.receivePacket(packetToSelf(90L, 1, 3, 0L));
    terminal.tick(2);

    assertEquals(1, terminal.packetsInTimedOut());
  }

  @Test
  void processInputBuffer_duppedPacket() {
    terminal.receivePacket(packetToSelf(90L, 0, 2, 0L));
    terminal.tick(1);
    terminal.receivePacket(packetToSelf(90L, 0, 2, 0L));
    terminal.tick(2);

    assertEquals(1, terminal.packetsInDropped());
  }

  @Test
  void generateTraffic_emptyAddressBook() {
    var ab = new ArrayList<IpAddress>();
    terminal.setAddressBook(ab);
    assertDoesNotThrow(() -> terminal.tick(1L));
  }

  @Test
  void generateTraffic_probabilityNull() {
    var ab = new ArrayList<IpAddress>();
    ab.add(OTHER_DST);
    terminal.setAddressBook(ab);
    assertDoesNotThrow(() -> terminal.tick(1L));
  }

  @Test
  void generateTraffic_doesntGenerateWhenProbabilityZero() {
    var ab = new ArrayList<IpAddress>();
    ab.add(OTHER_DST);
    terminal.setAddressBook(ab);
    terminal.setRng(new StubRandom(0.0, 0, 0));
    assertDoesNotThrow(() -> terminal.tick(1L));
  }

  @Test
  void generateTraffic_doesntGenerateWhenIpIsSelf() {
    var ab = new ArrayList<IpAddress>();
    ab.add(SELF);
    terminal.setAddressBook(ab);
    terminal.setTrafficProbability(1.0);
    terminal.setRng(new StubRandom(0.0, 0, 0));
    assertDoesNotThrow(() -> terminal.tick(1L));
  }

  @Test
  void tick_samePageIdDifferent() {
    assertTrue(terminal.receivePacket(Packet.create(100L, 0, 2, OTHER_SRC, SELF, 0L)));
    assertTrue(terminal.receivePacket(Packet.create(100L, 0, 2, OTHER_DST, SELF, 0L)));
    assertDoesNotThrow(() -> terminal.tick(1L));
  }

  @Test
  void tick_samePageIdDkrent() {
    assertTrue(terminal.receivePacket(packetToSelf(100L, 0, 2, 0L)));
    assertTrue(terminal.receivePacket(packetToSelf(101L, 0, 2, 0L)));
    assertDoesNotThrow(() -> terminal.tick(1L));
  }

  // =============== Helpers ===============

  private static Packet packetToSelf(long pageId, int pos, int len, long currentTick) {
    return Packet.create(pageId, pos, len, OTHER_SRC, SELF, currentTick);
  }

  private static Packet packetToOther() {
    return Packet.create(40L, 0, 1, OTHER_SRC, OTHER_DST, 0L);
  }

  // =============== StubRandom ===============

  /**
   * A utility subclass of {@link Random} designed to provide deterministic sequences of
   * pseudo-random numbers. This class is primarily intended for testing purposes where control over
   * the output of random number generation is required.
   *
   * <p>The stub sequence consists of an array of double values and an array of integer values, both
   * of which are consumed cyclically as the corresponding methods are called.
   */
  static class StubRandom extends Random {
    private final double[] doubles;
    private final int[] ints;
    private int di = 0;
    private int ii = 0;

    StubRandom(double d, int... ints) {
      this.doubles = new double[] {d};
      this.ints = ints;
    }

    @Override
    public double nextDouble() {
      return doubles[di++ % doubles.length];
    }

    @Override
    public int nextInt(int bound) {
      return ints[ii++ % ints.length];
    }
  }

  @Test
  void toString_containsClassNameAndIp() {
    String str = terminal.toString();
    assertTrue(str.contains("Terminal{Ip: 001.001 | Sent: 0 | Reassembled: 0 | Pending: 0}"));
  }
}
