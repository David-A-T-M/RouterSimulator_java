package ar.edu.unc.david.routersimulator.model;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a logical message — the unit of data that a {@link Terminal} generates and sends.
 *
 * <p>A Page is fragmented into {@link Packet}s for transmission and reassembled at the destination.
 * Equality is based solely on {@code pageId}
 */
public record Page(long pageId, int pageLen, IpAddress srcIp, IpAddress dstIp)
    implements Comparable<Page> {

  // =============== Compact Constructor ===============

  /** Compact constructor that validates the input values for pageLen and srcIp/dstIp. */
  public Page {
    if (pageId < 0) {
      throw new IllegalArgumentException("pageId must be non-negative, got: " + pageId);
    }
    if (pageLen <= 0) {
      throw new IllegalArgumentException("pageLen must be > 0, got: " + pageLen);
    }
    if (!srcIp.isValid()) {
      throw new IllegalArgumentException("srcIp must be valid (not 0.0)");
    }
    if (!dstIp.isValid()) {
      throw new IllegalArgumentException("dstIp must be valid (not 0.0)");
    }
  }

  // =============== Factory methods ===============

  /**
   * Rebuilds a Page from a list of Packets. Validates that all packets have consistent pageId,
   * pageLen, srcIp, dstIp, and that their pagePos values are correct.
   */
  public static Page fromPackets(List<Packet> packets) {
    if (packets == null || packets.isEmpty()) {
      throw new IllegalArgumentException("Cannot create Page from empty packet list");
    }

    Packet first = packets.getFirst();
    long pageId = first.pageId();
    int pageLen = first.pageLen();
    IpAddress src = first.srcIp();
    IpAddress dst = first.dstIp();

    if (packets.size() != pageLen) {
      throw new IllegalArgumentException(
          "Packet count (" + packets.size() + ") does not match pageLen (" + pageLen + ")");
    }

    for (int i = 0; i < packets.size(); i++) {
      Packet p = packets.get(i);

      if (p.pageId() != pageId) {
        throw new IllegalArgumentException(
            "Packet " + i + " has inconsistent pageId: " + p.pageId() + " vs " + pageId);
      }
      if (p.pageLen() != pageLen) {
        throw new IllegalArgumentException("Packet " + i + " has inconsistent pageLen");
      }
      if (!p.srcIp().equals(src)) {
        throw new IllegalArgumentException("Packet " + i + " has inconsistent srcIp");
      }
      if (!p.dstIp().equals(dst)) {
        throw new IllegalArgumentException("Packet " + i + " has inconsistent dstIp");
      }
      if (p.pagePos() != i) {
        throw new IllegalArgumentException(
            "Packet at index " + i + " has wrong pagePos: " + p.pagePos());
      }
    }

    return new Page(pageId, pageLen, src, dst);
  }

  // =============== toPackets ===============

  /**
   * Fragments this Page into a list of Packets for transmission. Each Packet will have the same
   * pageId, pageLen, srcIp, dstIp, and a unique pagePos from 0 to pageLen-1. The currentTick is
   * included in each Packet for timing purposes.
   */
  public List<Packet> toPackets(long currentTick) {
    List<Packet> packets = new ArrayList<>(pageLen);
    for (int pos = 0; pos < pageLen; pos++) {
      packets.add(Packet.create(pageId, pos, pageLen, srcIp, dstIp, currentTick));
    }
    return packets;
  }

  // =============== Equality ===============

  /** Equality is based solely on pageId. */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Page other)) {
      return false;
    }
    return pageId == other.pageId;
  }

  /** Returns a hash code based on the pageId. */
  @Override
  public int hashCode() {
    return Long.hashCode(pageId);
  }

  /** Natural ordering based on pageId. */
  @Override
  public int compareTo(Page other) {
    return Long.compare(this.pageId, other.pageId);
  }

  // =============== toString ===============

  /** Returns a string representation of the Page, including pageId, pageLen, and IP addresses. */
  @Override
  public @NotNull String toString() {
    return String.format("Page{ID: %d | Len: %d | %s -> %s}", pageId, pageLen, srcIp, dstIp);
  }
}
