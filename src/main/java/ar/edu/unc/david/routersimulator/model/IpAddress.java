package ar.edu.unc.david.routersimulator.model;

import org.jetbrains.annotations.NotNull;

/**
 * Represents an IP address in the simulator, consisting of a router ID and a terminal ID. Both IDs
 * are integers in the range 0-255, allowing us to pack them into a single 16-bit integer if needed.
 *
 * <p>This class is immutable and implements Comparable for easy sorting. We use a Java record to
 * automatically generate boilerplate code like constructors, getters, equals, hashCode, and
 * toString.
 */
public record IpAddress(int routerId, int terminalId) implements Comparable<IpAddress> {

  /** Compact constructor that validates the input values for routerId and terminalId. */
  public IpAddress {
    if (routerId < 0 || routerId > 255) {
      throw new IllegalArgumentException("routerId must be 0-255, got: " + routerId);
    }
    if (terminalId < 0 || terminalId > 255) {
      throw new IllegalArgumentException("terminalId must be 0-255, got: " + terminalId);
    }
  }

  /** An invalid IP address, used as a sentinel value. */
  public static final IpAddress INVALID = new IpAddress(0, 0);

  /** Creates the address of a router (terminalId is always 0 for routers). */
  public static IpAddress ofRouter(int routerId) {
    return new IpAddress(routerId, 0);
  }

  /** Creates the address of a terminal given its router ID and terminal ID. */
  public static IpAddress ofTerminal(int routerId, int terminalId) {
    return new IpAddress(routerId, terminalId);
  }

  /**
   * Converts a raw 16-bit integer back into an IPAddress, extracting the routerId and terminalId.
   */
  public static IpAddress fromRaw(int raw) {
    return new IpAddress((raw >> 8) & 0xFF, raw & 0xFF);
  }

  /**
   * Packs the routerId and terminalId into a single 16-bit integer, with routerId in the high byte
   * and terminalId in the low byte.
   */
  public int rawAddress() {
    return (routerId << 8) | terminalId;
  }

  /** Determines if this IP address corresponds to a router (terminalId is 0). */
  public boolean isRouter() {
    return terminalId == 0;
  }

  /** Checks if the IP address is valid (not the INVALID sentinel). */
  public boolean isValid() {
    return this != INVALID;
  }

  /**
   * Compares this IP address to another based on their raw integer representation, allowing for
   * natural ordering.
   */
  @Override
  public int compareTo(IpAddress other) {
    return Integer.compare(this.rawAddress(), other.rawAddress());
  }

  /**
   * Returns a string representation of the IP address in the format "RRR.TTT", where RRR is the
   * routerId and TTT is the terminalId, both zero-padded to three digits.
   */
  @Override
  public @NotNull String toString() {
    return String.format("%03d.%03d", routerId, terminalId);
  }
}
