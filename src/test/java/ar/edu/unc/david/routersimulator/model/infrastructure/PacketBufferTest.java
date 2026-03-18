package ar.edu.unc.david.routersimulator.model.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.Packet;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PacketBufferTest {

  private static final IpAddress SRC = new IpAddress(1, 1);
  private static final IpAddress DST = new IpAddress(2, 1);

  private Packet makePacket(int pos, int len) {
    return Packet.create(1L, pos, len, SRC, DST, 0L);
  }

  private PacketBuffer bounded;
  private PacketBuffer unbounded;

  @BeforeEach
  void setUp() {
    bounded = new PacketBuffer(3);
    unbounded = new PacketBuffer();
  }

  @Test
  void initialState_isEmpty() {
    assertEquals(3, bounded.capacity());
    assertEquals(0, bounded.size());
    assertTrue(bounded.isEmpty());
    assertFalse(bounded.isFull());
    assertEquals(3, bounded.availableSpace());
    assertEquals(0.0, bounded.utilization());
  }

  @Test
  void unbounded_isNeverFull() {
    assertFalse(unbounded.isFull());
    assertEquals(Integer.MAX_VALUE, unbounded.availableSpace());
  }

  @Test
  void unbounded_utilizationIsAlwaysZero() {
    unbounded.enqueue(makePacket(0, 1));
    assertEquals(0.0, unbounded.utilization());
  }

  @Test
  void constructor_throwsOnNegativeCapacity() {
    assertThrows(IllegalArgumentException.class, () -> new PacketBuffer(-1));
  }

  @Test
  void enqueue_acceptsUpToCapacity() {
    assertTrue(bounded.enqueue(makePacket(0, 3)));
    assertTrue(bounded.enqueue(makePacket(1, 3)));
    assertTrue(bounded.enqueue(makePacket(2, 3)));
    assertFalse(bounded.enqueue(makePacket(0, 3)));
    assertTrue(bounded.isFull());
  }

  @Test
  void dequeue_returnsFifoOrder() {
    bounded.enqueue(makePacket(0, 3));
    bounded.enqueue(makePacket(1, 3));
    assertEquals(0, bounded.dequeue().pagePos());
    assertEquals(1, bounded.dequeue().pagePos());
  }

  @Test
  void dequeue_throwsOnEmptyBuffer() {
    assertThrows(NoSuchElementException.class, () -> bounded.dequeue());
  }

  @Test
  void peek_doesNotRemovePacket() {
    bounded.enqueue(makePacket(0, 3));
    bounded.peek();
    assertEquals(1, bounded.size());
  }

  @Test
  void peek_throwsOnEmptyBuffer() {
    assertThrows(NoSuchElementException.class, () -> bounded.peek());
  }

  @Test
  void utilization_reflectsCurrentLoad() {
    bounded.enqueue(makePacket(0, 3));
    assertEquals(1.0 / 3.0, bounded.utilization(), 1e-9);
  }

  @Test
  void contains_findsByPageIdAndPos() {
    bounded.enqueue(makePacket(0, 3));
    assertTrue(bounded.contains(1L, 0));
    assertFalse(bounded.contains(1L, 1));
    assertFalse(bounded.contains(99L, 0));
  }

  @Test
  void removeAt_removesCorrectElement() {
    bounded.enqueue(makePacket(0, 3));
    bounded.enqueue(makePacket(1, 3));
    bounded.enqueue(makePacket(2, 3));
    bounded.removeAt(1);
    assertEquals(2, bounded.size());
    assertEquals(0, bounded.dequeue().pagePos());
    assertEquals(2, bounded.dequeue().pagePos());
  }

  @Test
  void removeAt_throwsOnOutOfBounds() {
    assertThrows(IndexOutOfBoundsException.class, () -> bounded.removeAt(0));
    assertThrows(IndexOutOfBoundsException.class, () -> bounded.removeAt(-1));
  }

  @Test
  void setCapacity_reducesCapacity() {
    bounded.enqueue(makePacket(0, 3));
    bounded.setCapacity(2);
    assertEquals(2, bounded.capacity());
    assertFalse(bounded.isFull());
  }

  @Test
  void setCapacity_increasesCapacity() {
    bounded.setCapacity(5);
    assertEquals(5, bounded.capacity());
    assertFalse(bounded.isFull());
  }

  @Test
  void setCapacity_toZeroMakesUnbounded() {
    bounded.setCapacity(0);
    assertFalse(bounded.isFull());
    assertEquals(Integer.MAX_VALUE, bounded.availableSpace());
  }

  @Test
  void setCapacity_throwsIfLowerThanCurrentSize() {
    bounded.enqueue(makePacket(0, 3));
    bounded.enqueue(makePacket(1, 3));
    assertThrows(IllegalStateException.class, () -> bounded.setCapacity(1));
  }

  @Test
  void setCapacity_throwsOnNegativeValue() {
    assertThrows(IllegalArgumentException.class, () -> bounded.setCapacity(-1));
  }

  @Test
  void clear_emptiesBuffer() {
    bounded.enqueue(makePacket(0, 3));
    bounded.clear();
    assertTrue(bounded.isEmpty());
  }

  @Test
  void toString_boundedBuffer() {
    bounded.enqueue(makePacket(0, 3));
    String str = bounded.toString();
    assertTrue(str.contains("PacketBuffer{Usage: 1/3 packets}"));
  }

  @Test
  void toString_unboundedBuffer() {
    unbounded.enqueue(makePacket(0, 3));
    String str = unbounded.toString();
    assertTrue(str.contains("PacketBuffer{Usage: 1 packets}"));
  }
}
