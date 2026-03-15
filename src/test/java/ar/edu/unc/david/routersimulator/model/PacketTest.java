package ar.edu.unc.david.routersimulator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PacketTest {

  private static final IpAddress SRC = new IpAddress(1, 1);
  private static final IpAddress DST = new IpAddress(2, 1);

  @Test
  void constructor_throwsOnInvalidPageId() {
    assertThrows(IllegalArgumentException.class, () -> new Packet(-1, 0, 5, 0, SRC, DST));
  }

  @Test
  void constructor_throwsOnInvalidPageLen() {
    assertThrows(IllegalArgumentException.class, () -> new Packet(1, 0, -2, 0, SRC, DST));
    assertThrows(IllegalArgumentException.class, () -> new Packet(1, 0, 0, 0, SRC, DST));
  }

  @Test
  void constructor_throwsOnInvalidPagePos() {
    assertThrows(IllegalArgumentException.class, () -> new Packet(1, 5, 5, 0, SRC, DST));
    assertThrows(IllegalArgumentException.class, () -> new Packet(1, -1, 5, 0, SRC, DST));
  }

  @Test
  void constructor_throwsOnInvalidExpTick() {
    assertThrows(IllegalArgumentException.class, () -> new Packet(1, 2, 5, -1, SRC, DST));
  }

  @Test
  void constructor_throwsOnInvalidIps() {
    assertThrows(
        IllegalArgumentException.class, () -> Packet.create(1, 0, 1, IpAddress.INVALID, DST, 0));
    assertThrows(
        IllegalArgumentException.class, () -> Packet.create(1, 0, 1, SRC, IpAddress.INVALID, 0));
  }

  @Test
  void create_setsExpiryTickCorrectly() {
    var p = Packet.create(1, 0, 4, SRC, DST, 50L);
    assertEquals(50L + Packet.TTL, p.expiryTick());
  }

  @Test
  void isFirstPacket_onlyWhenPosIsZero() {
    assertTrue(Packet.create(1, 0, 3, SRC, DST, 0).isFirstPacket());
    assertFalse(Packet.create(1, 1, 3, SRC, DST, 0).isFirstPacket());
  }

  @Test
  void isLastPacket_onlyWhenPosIsPageLenMinusOne() {
    assertTrue(Packet.create(1, 2, 3, SRC, DST, 0).isLastPacket());
    assertFalse(Packet.create(1, 1, 3, SRC, DST, 0).isLastPacket());
  }

  @Test
  void isExpired_comparesAgainstCurrentTick() {
    var p = Packet.create(1, 0, 3, SRC, DST, 0L);
    assertFalse(p.isExpired(99L));
    assertTrue(p.isExpired(100L));
    assertTrue(p.isExpired(200L));
  }

  @Test
  void equals_sameObject_true() {
    var p = Packet.create(42, 0, 5, SRC, DST, 0L);
    var q = p;
    assertEquals(p, q);
  }

  @Test
  void equals_differentClass_false() {
    var p = Packet.create(42, 0, 5, SRC, DST, 0L);
    var q = new Object();
    assertNotEquals(p, q);
  }

  @Test
  void equals_basedOnlyOnPageIdAndPagePos() {
    var p1 = Packet.create(42, 1, 5, SRC, DST, 0L);
    var p2 = Packet.create(42, 1, 5, DST, SRC, 999L);
    assertEquals(p1, p2);
  }

  @Test
  void equals_differentPagePos_notEqual() {
    var p1 = Packet.create(42, 0, 5, SRC, DST, 0L);
    var p2 = Packet.create(42, 1, 5, SRC, DST, 0L);
    assertNotEquals(p1, p2);
  }

  @Test
  void equals_differentPageId_notEqual() {
    var p1 = Packet.create(42, 0, 5, SRC, DST, 0L);
    var p2 = Packet.create(43, 0, 5, SRC, DST, 0L);
    assertNotEquals(p1, p2);
  }

  @Test
  void hashCode_basedOnPageIdAndPagePos() {
    var p1 = Packet.create(42, 1, 5, SRC, DST, 0L);
    var p2 = Packet.create(42, 1, 5, DST, SRC, 999L);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  @Test
  void toString_matchesExpectedFormat() {
    var p = Packet.create(7, 2, 5, SRC, DST, 0L);
    assertEquals("Src: 001.001 -> Dst: 002.001 | ID: 000007-2/5", p.toString());
  }
}
