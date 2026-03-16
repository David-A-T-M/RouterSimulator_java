package ar.edu.unc.david.routersimulator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageTest {

  private static final IpAddress SRC = new IpAddress(1, 1);
  private static final IpAddress DST = new IpAddress(2, 1);

  // =============== Constructor básico ===============

  @Test
  void constructor_storesFields() {
    var page = new Page(1L, 5, SRC, DST);
    assertEquals(1L, page.pageId());
    assertEquals(5, page.pageLen());
    assertEquals(SRC, page.srcIp());
    assertEquals(DST, page.dstIp());
  }

  @Test
  void constructor_throwsOnInvalidPageId() {
    assertThrows(IllegalArgumentException.class, () -> new Page(-1L, 3, SRC, DST));
  }

  @Test
  void constructor_throwsOnZeroOrNegativeLen() {
    assertThrows(IllegalArgumentException.class, () -> new Page(1L, 0, SRC, DST));
    assertThrows(IllegalArgumentException.class, () -> new Page(1L, -1, SRC, DST));
  }

  @Test
  void constructor_throwsOnInvalidIps() {
    assertThrows(IllegalArgumentException.class, () -> new Page(1L, 3, IpAddress.INVALID, DST));
    assertThrows(IllegalArgumentException.class, () -> new Page(1L, 3, SRC, IpAddress.INVALID));
  }

  // =============== fromPackets ===============

  @Test
  void fromPackets_throwsOnEmptyList() {
    assertThrows(IllegalArgumentException.class, () -> Page.fromPackets(List.of()));
    assertThrows(IllegalArgumentException.class, () -> Page.fromPackets(null));
  }

  @Test
  void fromPackets_throwsOnWrongPacketCount() {
    var packets = new Page(1L, 3, SRC, DST).toPackets(0L);
    assertThrows(IllegalArgumentException.class, () -> Page.fromPackets(packets.subList(0, 2)));
  }

  @Test
  void fromPackets_throwsOnWrongPageId() {
    var p0 = Packet.create(1L, 0, 2, SRC, DST, 0L);
    var p1 = Packet.create(2L, 0, 2, SRC, DST, 0L);
    assertThrows(IllegalArgumentException.class, () -> Page.fromPackets(List.of(p0, p1)));
  }

  @Test
  void fromPackets_throwsOnWrongPageLen() {
    var p0 = Packet.create(1L, 0, 2, SRC, DST, 0L);
    var p1 = Packet.create(1L, 0, 3, SRC, DST, 0L);
    assertThrows(IllegalArgumentException.class, () -> Page.fromPackets(List.of(p0, p1)));
  }

  @Test
  void fromPackets_throwsOnWrongSrcIp() {
    var p0 = Packet.create(1L, 0, 2, SRC, DST, 0L);
    var p1 = Packet.create(1L, 0, 2, DST, DST, 0L);
    assertThrows(IllegalArgumentException.class, () -> Page.fromPackets(List.of(p0, p1)));
  }

  @Test
  void fromPackets_throwsOnWrongDstIp() {
    var p0 = Packet.create(1L, 0, 2, SRC, DST, 0L);
    var p1 = Packet.create(1L, 0, 2, SRC, SRC, 0L);
    assertThrows(IllegalArgumentException.class, () -> Page.fromPackets(List.of(p0, p1)));
  }

  @Test
  void fromPackets_throwsOnWrongPagePos() {
    var p0 = Packet.create(1L, 0, 3, SRC, DST, 0L);
    var p1 = Packet.create(1L, 2, 3, SRC, DST, 0L); // pos=2 donde debería ir pos=1
    var p2 = Packet.create(1L, 1, 3, SRC, DST, 0L);
    assertThrows(IllegalArgumentException.class, () -> Page.fromPackets(List.of(p0, p1, p2)));
  }

  // =============== toPackets ===============

  @Test
  void toPackets_generatesCorrectCount() {
    var page = new Page(7L, 4, SRC, DST);
    var packets = page.toPackets(10L);
    assertEquals(4, packets.size());
  }

  @Test
  void toPackets_positionsAreSequential() {
    var packets = new Page(1L, 3, SRC, DST).toPackets(0L);
    for (int i = 0; i < packets.size(); i++) {
      assertEquals(i, packets.get(i).pagePos());
    }
  }

  @Test
  void toPackets_firstAndLastFlagsCorrect() {
    var packets = new Page(1L, 3, SRC, DST).toPackets(0L);
    assertTrue(packets.getFirst().isFirstPacket());
    assertTrue(packets.getLast().isLastPacket());
    assertFalse(packets.get(1).isFirstPacket());
    assertFalse(packets.get(1).isLastPacket());
  }

  @Test
  void toPackets_expiryTickIsCurrentPlusTtl() {
    long currentTick = 42L;
    var packet = new Page(1L, 1, SRC, DST).toPackets(currentTick).getFirst();
    assertEquals(currentTick + Packet.TTL, packet.expiryTick());
  }

  @Test
  void toPackets_roundTrip() {
    var original = new Page(5L, 3, SRC, DST);
    var packets = original.toPackets(0L);
    var restored = Page.fromPackets(packets);
    assertEquals(original.pageId(), restored.pageId());
    assertEquals(original.pageLen(), restored.pageLen());
  }

  // =============== Equality & Comparable ===============

  @Test
  void equals_basedOnlyOnPageId() {
    var p1 = new Page(42L, 3, SRC, DST);
    var p2 = new Page(42L, 5, DST, SRC); // distinto len, src, dst
    assertEquals(p1, p2);
  }

  @Test
  void equals_differentPageIdsNotEqual() {
    var p1 = new Page(1L, 3, SRC, DST);
    var p2 = new Page(2L, 3, SRC, DST);
    assertNotEquals(p1, p2);
  }

  @Test
  void equals_sameObject_true() {
    var p = new Page(42L, 3, SRC, DST);
    var q = p;
    assertEquals(p, q);
  }

  @Test
  void equals_differentClass_false() {
    var p = new Page(42L, 3, SRC, DST);
    var q = new Object();
    assertNotEquals(p, q);
  }

  @Test
  void hashCode_basedOnPageId() {
    var p1 = new Page(42L, 3, SRC, DST);
    var p2 = new Page(42L, 5, DST, SRC);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  @Test
  void compareTo_ordersByPageId() {
    var low = new Page(1L, 1, SRC, DST);
    var high = new Page(2L, 1, SRC, DST);
    assertTrue(low.compareTo(high) < 0);
    assertTrue(high.compareTo(low) > 0);
  }

  @Test
  void toString_matchesExpectedFormat() {
    var page = new Page(3L, 4, SRC, DST);
    assertEquals("Page{ID: 3 | Len: 4 | 001.001 -> 002.001}", page.toString());
  }
}
