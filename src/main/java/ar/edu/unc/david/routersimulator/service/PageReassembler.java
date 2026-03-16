package ar.edu.unc.david.routersimulator.service;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.Packet;
import ar.edu.unc.david.routersimulator.model.Page;
import java.util.List;

/**
 * Collects incoming {@link Packet}s for a single {@link Page} and detects completion.
 *
 * <p>Each slot in the internal array corresponds to a packet position. A null slot means the packet
 * hasn't arrived yet. Once all slots are filled, {@link #isComplete()} returns true and {@link
 * #assemble()} can be called to reconstruct the Page.
 *
 * <p>The {@code expiryTick} is an absolute simulation tick — the Terminal checks it externally to
 * decide whether to discard this reassembler.
 */
public class PageReassembler {

  public static final int MAX_TTL = 250;

  private final long pageId;
  private final IpAddress srcIp;
  private final long expiryTick;
  private final Packet[] slots;
  private int count;

  // =============== Constructor ===============
  /** Initializes a new PageReassembler for the given pageId, srcIp, and total packet count. */
  public PageReassembler(long pageId, IpAddress srcIp, int total, long currentTick) {
    if (pageId < 0) {
      throw new IllegalArgumentException("pageId must be non-negative, got: " + pageId);
    }
    if (total <= 0) {
      throw new IllegalArgumentException("total must be > 0, got: " + total);
    }
    if (!srcIp.isValid()) {
      throw new IllegalArgumentException("srcIp must be valid");
    }
    if (currentTick < 0) {
      throw new IllegalArgumentException("currentTick must be non-negative, got: " + currentTick);
    }

    this.pageId = pageId;
    this.srcIp = srcIp;
    this.expiryTick = currentTick + MAX_TTL;
    this.slots = new Packet[total];
    this.count = 0;
  }

  // =============== Getters ===============

  public long pageId() {
    return pageId;
  }

  public IpAddress srcIp() {
    return srcIp;
  }

  public int total() {
    return slots.length;
  }

  public long expiryTick() {
    return expiryTick;
  }

  public int count() {
    return count;
  }

  public int remaining() {
    return slots.length - count;
  }

  public double completionRate() {
    return (double) count / slots.length;
  }

  public boolean isComplete() {
    return count == slots.length;
  }

  public boolean isExpired(long currTick) {
    return currTick >= expiryTick;
  }

  /**
   * Checks if the packet at the given position has been received.
   *
   * @param position Must be in [0, total).
   */
  public boolean hasPacketAt(int position) {
    if (position < 0 || position >= slots.length) {
      throw new IndexOutOfBoundsException("Position out of range: " + position);
    }
    return slots[position] != null;
  }

  // =============== Modifiers ===============

  /**
   * Tries to add a packet to this reassembler. The packet must match the pageId, srcIp, and total
   * packet count of this reassembler. The packet's pagePos must be a valid index and not already
   * occupied.
   *
   * @return true if the packet was successfully added, false if it was rejected.
   */
  public boolean addPacket(Packet p) {
    if (p.pageId() != pageId) {
      return false;
    }
    if (!p.srcIp().equals(srcIp)) {
      return false;
    }
    if (p.pageLen() != slots.length) {
      return false;
    }

    int pos = p.pagePos();

    if (slots[pos] != null) {
      return false;
    }

    slots[pos] = p;
    count++;
    return true;
  }

  /**
   * Assembles the complete page from the collected packets. This should only be called if {@link
   * #isComplete()} returns true, otherwise an exception is thrown. After assembly, the reassembler
   * is reset to an empty state, allowing it to be reused for a new page if desired. The returned
   * list of packets is immutable and ordered by page position.
   *
   * @throws IllegalStateException if the page is not yet complete.
   */
  public Page assemble() {
    if (!isComplete()) {
      throw new IllegalStateException(
          "Cannot assemble incomplete page: " + count + "/" + slots.length);
    }

    List<Packet> result = List.of(slots);

    return Page.fromPackets(result);
  }

  // =============== Equality ===============

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PageReassembler other)) {
      return false;
    }
    return pageId == other.pageId;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(pageId);
  }

  // =============== toString ===============

  @Override
  public String toString() {
    return String.format(
        "PageReassembler{ID: %d | srcIp: %s | %d/%d packets | Expiry: %d}",
        pageId, srcIp, count, slots.length, expiryTick);
  }
}
