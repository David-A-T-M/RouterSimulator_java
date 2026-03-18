package ar.edu.unc.david.routersimulator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PageReassemblerTest {

  private static final IpAddress SRC = new IpAddress(1, 1);
  private static final IpAddress DST = new IpAddress(2, 1);
  private static final long PAGE_ID = 7L;
  private static final int TOTAL = 3;

  private PageReassembler reassembler;

  @BeforeEach
  void setUp() {
    reassembler = new PageReassembler(PAGE_ID, SRC, TOTAL, 0L);
  }

  // =============== Constructor ===============

  @Test
  void constructor_throwsOnNegativePageId() {
    assertThrows(IllegalArgumentException.class, () -> new PageReassembler(-1L, SRC, TOTAL, 0L));
  }

  @Test
  void constructor_throwsOnZeroOrNegativeTotal() {
    assertThrows(IllegalArgumentException.class, () -> new PageReassembler(PAGE_ID, SRC, 0, 0L));
    assertThrows(IllegalArgumentException.class, () -> new PageReassembler(PAGE_ID, SRC, -1, 0L));
  }

  @Test
  void constructor_throwsOnInvalidSrcIp() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PageReassembler(PAGE_ID, IpAddress.INVALID, TOTAL, 0L));
  }

  @Test
  void constructor_throwsOnInvalidExpiryTick() {
    assertThrows(
        IllegalArgumentException.class, () -> new PageReassembler(PAGE_ID, SRC, TOTAL, -1L));
  }

  // =============== Initial state ===============

  @Test
  void initialState_noPacketsReceived() {
    assertEquals(PAGE_ID, reassembler.pageId());
    assertEquals(SRC, reassembler.srcIp());
    assertEquals(TOTAL, reassembler.total());
    assertEquals(250L, reassembler.expiryTick());
    assertEquals(0, reassembler.count());
    assertEquals(TOTAL, reassembler.remaining());
    assertEquals(0.0, reassembler.completionRate());
    assertFalse(reassembler.isComplete());
  }

  @Test
  void expiryTick_isCurrentPlusMaxTtl() {
    long tick = 50L;
    var r = new PageReassembler(1L, SRC, 3, tick);
    assertEquals(tick + PageReassembler.MAX_TTL, r.expiryTick());
  }

  @Test
  void isExpired_falseBeforeExpiryTick() {
    assertFalse(reassembler.isExpired(PageReassembler.MAX_TTL - 1));
  }

  @Test
  void isExpired_trueAtOrAfterExpiryTick() {
    assertTrue(reassembler.isExpired(PageReassembler.MAX_TTL));
    assertTrue(reassembler.isExpired(PageReassembler.MAX_TTL + 1));
  }

  // =============== addPacket ===============

  @Test
  void addPacket_acceptsValidPacket() {
    var p = Packet.create(PAGE_ID, 0, TOTAL, SRC, DST, 0L);
    assertTrue(reassembler.addPacket(p));
    assertEquals(1, reassembler.count());
    assertTrue(reassembler.hasPacketAt(0));
  }

  @Test
  void addPacket_rejectsDuplicateSlot() {
    var p = Packet.create(PAGE_ID, 0, TOTAL, SRC, DST, 0L);
    reassembler.addPacket(p);
    assertFalse(reassembler.addPacket(p));
    assertEquals(1, reassembler.count());
  }

  @Test
  void addPacket_rejectsWrongPageId() {
    var p = Packet.create(999L, 0, TOTAL, SRC, DST, 0L);
    assertFalse(reassembler.addPacket(p));
  }

  @Test
  void addPacket_rejectsWrongSrcIp() {
    var other = new IpAddress(9, 9);
    var p = Packet.create(PAGE_ID, 0, TOTAL, other, DST, 0L);
    assertFalse(reassembler.addPacket(p));
  }

  @Test
  void addPacket_rejectsWrongPageLen() {
    var p = Packet.create(PAGE_ID, 0, 1, SRC, DST, 0L);
    assertFalse(reassembler.addPacket(p));
  }

  @Test
  void hasPacketAt_returnsFalseForInvalidSlot() {
    assertFalse(reassembler.hasPacketAt(0));
    assertThrows(IndexOutOfBoundsException.class, () -> reassembler.hasPacketAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> reassembler.hasPacketAt(TOTAL));
  }

  @Test
  void hasPacketAt_returnsTrueForValidSlot() {
    var p = Packet.create(PAGE_ID, 0, TOTAL, SRC, DST, 0L);
    assertTrue(reassembler.addPacket(p));
    assertTrue(reassembler.hasPacketAt(0));
  }

  // =============== isComplete / completionRate ===============

  @Test
  void isComplete_afterAllPacketsAdded() {
    for (int i = 0; i < TOTAL; i++) {
      reassembler.addPacket(Packet.create(PAGE_ID, i, TOTAL, SRC, DST, 0L));
    }

    assertTrue(reassembler.isComplete());
    assertEquals(1.0, reassembler.completionRate());
  }

  // =============== assemble ===============

  @Test
  void assemble_returnsPageWhenComplete() {
    for (int i = 0; i < TOTAL; i++) {
      reassembler.addPacket(Packet.create(PAGE_ID, i, TOTAL, SRC, DST, 0L));
    }

    var page = reassembler.assemble();
    assertEquals(PAGE_ID, page.pageId());
    assertEquals(TOTAL, page.pageLen());
    assertEquals(SRC, page.srcIp());
    assertEquals(DST, page.dstIp());
  }

  @Test
  void assemble_throwsIfIncomplete() {
    reassembler.addPacket(Packet.create(PAGE_ID, 0, TOTAL, SRC, DST, 0L));
    assertThrows(IllegalStateException.class, () -> reassembler.assemble());
  }

  // =============== Equality ===============

  @Test
  void equals_trueForSameObject() {
    assertEquals(reassembler, reassembler);
  }

  @Test
  void equals_falseForDifferentType() {
    Object o = new Object();
    assertNotEquals(reassembler, o);
  }

  @Test
  void equals_basedOnlyOnPageId() {
    var other = new PageReassembler(PAGE_ID, new IpAddress(9, 9), 5, 999L);
    assertEquals(reassembler, other);
  }

  @Test
  void equals_differentPageIdsNotEqual() {
    var other = new PageReassembler(999L, SRC, TOTAL, 0L);
    assertNotEquals(reassembler, other);
  }

  // =============== hashCode ===============
  @Test
  void hashCode_basedOnPageId() {
    assertEquals(reassembler.hashCode(), new PageReassembler(PAGE_ID, SRC, TOTAL, 0L).hashCode());
  }

  // =============== toString ===============
  @Test
  void toString_matchesExpectedFormat() {
    assertEquals(
        "PageReassembler{ID: 7 | srcIp: 001.001 | 0/3 packets | Expiry: 250}",
        reassembler.toString());
  }
}
