package ar.edu.unc.david.routersimulator.model;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a single transmission unit on the network — a fragment of a {@link Page}.
 *
 * <p>A Packet is identified by its {@code pageId} and its position within the page ({@code
 * pagePos}). The {@code expiryTick} is set at creation time as {@code currentTick + PACKET_TTL} and
 * never changes — routers compare the simulation's current tick against it to detect expiry.
 */
public record Packet(
    long pageId, int pagePos, int pageLen, long expiryTick, IpAddress srcIp, IpAddress dstIp) {
  public static final int TTL = 100;

  /** Compact constructor that validates the input values for pagePos and pageLen. */
  public Packet {
    if (pageId < 0) {
      throw new IllegalArgumentException("pageId must be non-negative, got: " + pageId);
    }
    if (pageLen <= 0) {
      throw new IllegalArgumentException("pageLen must be positive, got: " + pageLen);
    }
    if (pagePos < 0 || pagePos >= pageLen) {
      throw new IllegalArgumentException(
          "pagePos must be in [0, pageLen), got: " + pagePos + " with pageLen: " + pageLen);
    }
    if (expiryTick < 0) {
      throw new IllegalArgumentException("expiryTick must be non-negative, got: " + expiryTick);
    }
    if (!srcIp.isValid()) {
      throw new IllegalArgumentException("srcIp must be valid (not 0.0)");
    }
    if (!dstIp.isValid()) {
      throw new IllegalArgumentException("dstIp must be valid (not 0.0)");
    }
  }

  /** Preferred way to create a Packet — calculates expiryTick from the current simulation tick. */
  public static Packet create(
      long pageId, int pagePos, int pageLen, IpAddress srcIp, IpAddress dstIp, long currentTick) {
    return new Packet(pageId, pagePos, pageLen, currentTick + TTL, srcIp, dstIp);
  }

  // =============== Query methods ===============

  /** Returns true if this packet is the first fragment of its page (i.e., pagePos == 0). */
  public boolean isFirstPacket() {
    return pagePos == 0;
  }

  /**
   * Returns true if this packet is the last fragment of its page. (i.e., pagePos == pageLen - 1).
   */
  public boolean isLastPacket() {
    return pagePos == pageLen - 1;
  }

  /**
   * Returns true if the current simulation tick is greater than or equal to this packet's
   * expiryTick.
   */
  public boolean isExpired(long currentTick) {
    return currentTick >= expiryTick;
  }

  // =============== Equality ===============

  /**
   * Two packets are considered equal if they belong to the same page (same pageId) and have the
   * same position within that page (same pagePos). The other fields (expiryTick, srcIp, dstIp) are
   * not considered for equality.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Packet other)) {
      return false;
    }
    return pageId == other.pageId && pagePos == other.pagePos;
  }

  /** Returns a hash code based on the pageId and pagePos. */
  @Override
  public int hashCode() {
    return Long.hashCode(pageId) * 31 + pagePos;
  }

  // =============== toString ===============
  /**
   * Returns a string representation of the packet, including source/destination IPs and page info.
   */
  @Override
  public @NotNull String toString() {
    return String.format(
        "Src: %s -> Dst: %s | ID: %06d-%d/%d", srcIp, dstIp, pageId, pagePos, pageLen);
  }
}
