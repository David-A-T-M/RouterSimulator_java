package ar.edu.unc.david.routersimulator.model.infrastructure;

import ar.edu.unc.david.routersimulator.model.Packet;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A generic bounded or unbounded FIFO queue of {@link Packet}s.
 *
 * <p>A capacity of {@code 0} means unbounded — {@link #isFull()} always returns false and {@link
 * #availableSpace()} returns {@link Integer#MAX_VALUE}.
 */
public class PacketBuffer {

  private final ArrayDeque<Packet> packets;
  private int capacity;

  // =============== Constructors ===============

  /** Unbounded buffer. */
  public PacketBuffer() {
    this(0);
  }

  /** Bounded if capacity > 0, unbounded if capacity == 0. */
  public PacketBuffer(int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException("capacity must be >= 0, got: " + capacity);
    }
    this.packets = new ArrayDeque<>();
    this.capacity = capacity;
  }

  // =============== Getters ===============

  public int capacity() {
    return capacity;
  }

  public int size() {
    return packets.size();
  }

  public boolean isEmpty() {
    return packets.isEmpty();
  }

  public boolean isFull() {
    return capacity != 0 && packets.size() >= capacity;
  }

  public int availableSpace() {
    return capacity == 0 ? Integer.MAX_VALUE : capacity - packets.size();
  }

  public double utilization() {
    return capacity == 0 ? 0.0 : (double) packets.size() / capacity;
  }

  // =============== Queue operations ===============

  /**
   * Adds the specified packet to the buffer if there is available space. If the buffer is full, the
   * packet is not added.
   *
   * @param packet the packet to be enqueued into the buffer
   * @return true if the packet was successfully enqueued, false if the buffer is full
   */
  public boolean enqueue(Packet packet) {
    if (isFull()) {
      return false;
    }
    packets.add(packet);
    return true;
  }

  /**
   * Removes and returns the packet at the head of the buffer.
   *
   * @return the packet that was removed from the head of the buffer
   * @throws NoSuchElementException if the buffer is empty
   */
  public Packet dequeue() {
    if (isEmpty()) {
      throw new NoSuchElementException("Cannot dequeue from empty buffer");
    }
    return packets.removeFirst();
  }

  /**
   * Retrieves, but does not remove, the head of the buffer (the first packet in the queue).
   *
   * @return the packet at the head of the buffer
   * @throws NoSuchElementException if the buffer is empty
   */
  public Packet peek() {
    if (isEmpty()) {
      throw new NoSuchElementException("Cannot peek empty buffer");
    }
    return packets.peekFirst();
  }

  // =============== Buffer management ===============

  /**
   * Determines if the buffer contains a packet with the specified page ID and position.
   *
   * @param pageId the unique identifier of the page to check for.
   * @param pagePos the position of the packet within the specified page.
   * @return true if a packet with the specified page ID and position exists in the buffer, false
   *     otherwise.
   */
  public boolean contains(long pageId, int pagePos) {
    for (Packet p : packets) {
      if (p.pageId() == pageId && p.pagePos() == pagePos) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes the packet at the specified index from the buffer.
   *
   * @param index the zero-based position of the packet to remove from the buffer
   * @throws IndexOutOfBoundsException if the index is out of the range {@code 0 <= index < size()}
   *     of the buffer
   */
  public void removeAt(int index) {
    if (index < 0 || index >= packets.size()) {
      throw new IndexOutOfBoundsException("Index out of range: " + index);
    }
    Iterator<Packet> it = packets.iterator();
    for (int i = 0; i <= index; i++) {
      it.next();
    }
    it.remove();
  }

  /**
   * Updates the capacity of the packet buffer. The capacity determines the maximum number of
   * packets that can be held in the buffer. A capacity of 0 indicates that the buffer is unbounded.
   *
   * @param newCapacity the new capacity of the buffer. Must be non-negative. If the buffer is
   *     bounded (capacity > 0), and the new capacity is less than the current size of the buffer,
   *     an {@code IllegalStateException} is thrown to prevent data loss.
   * @throws IllegalArgumentException if {@code newCapacity} is negative.
   * @throws IllegalStateException if {@code newCapacity > 0} and the buffer currently contains more
   *     packets than the specified capacity.
   */
  public void setCapacity(int newCapacity) {
    if (newCapacity < 0) {
      throw new IllegalArgumentException("capacity must be >= 0");
    }
    if (newCapacity > 0 && packets.size() > newCapacity) {
      throw new IllegalStateException(
          "Cannot set capacity lower than current size (" + packets.size() + ")");
    }
    this.capacity = newCapacity;
  }

  public void clear() {
    packets.clear();
  }

  // =============== toString ===============

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("PacketBuffer{Usage: ").append(packets.size());
    if (capacity > 0) {
      sb.append("/").append(capacity);
    }
    sb.append(" packets}");
    return sb.toString();
  }
}
