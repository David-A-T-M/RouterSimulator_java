package ar.edu.unc.david.routersimulator.model.stats;

/**
 * Immutable snapshot of aggregated network statistics collected at a given simulation tick.
 *
 * <p>Use {@link Builder} to construct instances incrementally while iterating over routers and
 * terminals, then call {@link Builder#build()} to get the final record.
 */
public record NetworkStats(
    long currentTick,
    int totalRouters,
    int totalTerminals,
    long packetsGenerated,
    long packetsSent,
    long packetsDelivered,
    long packetsDropped,
    long packetsTimedOut,
    long packetsInFlight,
    long pagesCreated,
    long pagesCompleted,
    long pagesDropped,
    long pagesTimedOut) {

  /** Fraction of sent packets that were successfully delivered (0.0 if nothing was sent). */
  public float deliveryRate() {
    return packetsSent > 0 ? (float) packetsDelivered / packetsSent : 0f;
  }

  /** Fraction of sent packets that were dropped (0.0 if nothing was sent). */
  public float dropRate() {
    return packetsSent > 0 ? (float) packetsDropped / packetsSent : 0f;
  }

  /** Fraction of finished pages that completed successfully. */
  public float successRate() {
    long finished = pagesCompleted + pagesDropped + pagesTimedOut;
    return finished > 0 ? (float) pagesCompleted / finished : 0f;
  }

  /**
   * Mutable accumulator for building a {@link NetworkStats} instance while iterating over routers
   * and terminals.
   */
  public static final class Builder {

    private long currentTick;
    private int totalRouters;
    private int totalTerminals;
    private long packetsGenerated;
    private long packetsSent;
    private long packetsDelivered;
    private long packetsDropped;
    private long packetsTimedOut;
    private long packetsInFlight;
    private long pagesCreated;
    private long pagesCompleted;
    private long pagesDropped;
    private long pagesTimedOut;

    public Builder currentTick(long v) {
      currentTick = v;
      return this;
    }

    public Builder totalRouters(int v) {
      totalRouters = v;
      return this;
    }

    public Builder addTerminals(int v) {
      totalTerminals += v;
      return this;
    }

    public Builder addPacketsGenerated(long v) {
      packetsGenerated += v;
      return this;
    }

    public Builder addPacketsSent(long v) {
      packetsSent += v;
      return this;
    }

    public Builder addPacketsDelivered(long v) {
      packetsDelivered += v;
      return this;
    }

    public Builder addPacketsDropped(long v) {
      packetsDropped += v;
      return this;
    }

    public Builder addPacketsTimedOut(long v) {
      packetsTimedOut += v;
      return this;
    }

    public Builder addPacketsInFlight(long v) {
      packetsInFlight += v;
      return this;
    }

    public Builder addPagesCreated(long v) {
      pagesCreated += v;
      return this;
    }

    public Builder addPagesCompleted(long v) {
      pagesCompleted += v;
      return this;
    }

    public Builder addPagesDropped(long v) {
      pagesDropped += v;
      return this;
    }

    public Builder addPagesTimedOut(long v) {
      pagesTimedOut += v;
      return this;
    }

    /**
     * Finalizes the accumulation of network statistics and constructs an immutable {@link
     * NetworkStats} instance.
     *
     * @return a new {@link NetworkStats} instance containing the aggregated values for the
     *     collected metrics, including the current simulation tick, counts of routers and
     *     terminals, packets, and pages tracked during simulation.
     */
    public NetworkStats build() {
      return new NetworkStats(
          currentTick,
          totalRouters,
          totalTerminals,
          packetsGenerated,
          packetsSent,
          packetsDelivered,
          packetsDropped,
          packetsTimedOut,
          packetsInFlight,
          pagesCreated,
          pagesCompleted,
          pagesDropped,
          pagesTimedOut);
    }
  }
}
