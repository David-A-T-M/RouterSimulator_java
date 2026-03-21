package ar.edu.unc.david.routersimulator.model.nodes;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.Packet;
import ar.edu.unc.david.routersimulator.model.Page;
import ar.edu.unc.david.routersimulator.model.infrastructure.PacketBuffer;
import ar.edu.unc.david.routersimulator.model.stats.TerminalStats;
import ar.edu.unc.david.routersimulator.service.PageReassembler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * An end-host device connected to a single parent router via {@link PacketReceiver}.
 *
 * <p>Each tick it: expires quarantine entries, cleans up stale reassemblers, drains its output
 * buffer toward the router, processes its input buffer, and optionally generates new traffic.
 */
public class Terminal implements NetworkNode {

  // =============== Defaults ===============
  /** Default processing capacity for incoming packets per tick. */
  public static final int DEF_INPUT_PROC = 10;

  /** Default output bandwidth in packets per tick. */
  public static final int DEF_OUTPUT_BW = 5;

  /** Default capacity for output buffer in packets. 0 means unbounded. */
  public static final int DEF_OUT_BUF_CAP = 0;

  /** Default capacity for input buffer in packets. 0 means unbounded. */
  public static final int DEF_IN_BUF_CAP = 0;

  // =============== Internal types ===============
  /**
   * Immutable config record for terminal parameters. Provides defaults for all fields, so can be
   * instantiated with no args.
   */
  public record Config(int inBufferCap, int inProcCap, int outBufferCap, int outputBw) {
    /** Validates that all parameters are non-negative. */
    public Config {
      if (inBufferCap < 0 || inProcCap < 0 || outBufferCap < 0 || outputBw < 0) {
        throw new IllegalArgumentException("Negative values not allowed");
      }
    }

    /** No-arg constructor that sets all parameters to their defaults. */
    public Config() {
      this(DEF_IN_BUF_CAP, DEF_INPUT_PROC, DEF_OUT_BUF_CAP, DEF_OUTPUT_BW);
    }
  }

  /** Represents a page which has been quarantined due to reassembly timeout. */
  private record QuarantinedId(long pageId, long expiryTick) {}

  // =============== Fields ===============
  private final IpAddress terminalIp;
  private final PacketReceiver rtrConn;
  private final PacketBuffer inBuffer;
  private final PacketBuffer outBuffer;
  private int inProcCap;
  private int outBw;

  private final List<PageReassembler> reassemblers = new ArrayList<>();
  private final List<QuarantinedId> quarantine = new ArrayList<>();

  private long nextPageId = 0;

  private List<IpAddress> addressBook = null;
  private double trafficProb = 0.0;
  private int maxPageLen = 2;
  private Random rng = null;

  private long pagesCreated = 0;
  private long pagesSent = 0;
  private long pagesOutDropped = 0;
  private long pagesCompleted = 0;
  private long pagesTimedOut = 0;
  private long packetsGenerated = 0;
  private long packetsSent = 0;
  private long packetsOutDropped = 0;
  private long packetsOutTimedOut = 0;
  private long packetsReceived = 0;
  private long packetsInTimedOut = 0;
  private long packetsInDropped = 0;
  private long packetsSuccProcessed = 0;

  // =============== Constructors ===============
  /**
   * Creates a terminal with the given parameters. Terminal ID must be > 0, as 0 is reserved for the
   * parent router.
   */
  public Terminal(PacketReceiver router, int routerId, int terminalId, Config cfg) {
    if (terminalId == 0) {
      throw new IllegalArgumentException("Terminal ID must be > 0");
    }

    this.terminalIp = new IpAddress(routerId, terminalId);
    this.rtrConn = router;
    this.inBuffer = new PacketBuffer(cfg.inBufferCap());
    this.outBuffer = new PacketBuffer(cfg.outBufferCap());
    this.inProcCap = cfg.inProcCap();
    this.outBw = cfg.outputBw();
  }

  /**
   * Creates a terminal with default config. Terminal ID must be > 0, as 0 is reserved for the
   * parent router.
   */
  public Terminal(PacketReceiver router, int routerId, int terminalId) {
    this(router, routerId, terminalId, new Config());
  }

  // =============== core public API ===============

  /**
   * Executes one simulation tick for the terminal, performing a series of operations to update
   * internal state, process buffers, and generate traffic.
   *
   * @param currentTick The current simulation tick, used for tracking time-sensitive operations
   *     such as packet expiration and quarantine updates.
   */
  @Override
  public void tick(long currentTick) {
    updateQuarantine(currentTick);
    cleanupReassemblers(currentTick);
    processOutputBuffer(currentTick);
    processInputBuffer(currentTick);
    generateTraffic(currentTick);
  }

  /**
   * Sends a page from the terminal to a destination IP address. The page is split into smaller
   * packets, which are enqueued into the terminal's output buffer. If the buffer does not have
   * enough space for all the packets, the page is dropped.
   *
   * @param length The length of the page to be sent, in bytes.
   * @param destIp The destination IP address to which the page should be sent.
   * @param currentTick The current simulation tick, used for timestamping packets.
   * @return {@code true} if the page was successfully sent; {@code false} if the page was dropped
   *     due to insufficient buffer space.
   */
  public boolean sendPage(int length, IpAddress destIp, long currentTick) {
    Page page = new Page(nextPageId++, length, terminalIp, destIp);
    List<Packet> packets = page.toPackets(currentTick);
    final int numPackets = packets.size();
    pagesCreated++;
    packetsGenerated += numPackets;

    if (outBuffer.availableSpace() < numPackets) {
      pagesOutDropped++;
      packetsOutDropped += numPackets;
      return false;
    }

    packets.forEach(outBuffer::enqueue);
    pagesSent++;
    return true;
  }

  /**
   * Receives a packet into the terminal's input buffer. If the packet's page ID is currently
   * quarantined, or if the input buffer is full, the packet is dropped.
   *
   * @param packet The packet to be received.
   * @return {@code true} if the packet was successfully received; {@code false} if the packet was
   *     dropped due to quarantine or buffer overflow.
   */
  @Override
  public boolean receivePacket(Packet packet) {
    packetsReceived++;

    if (isQuarantined(packet.pageId())) {
      packetsInTimedOut++;
      return false;
    }

    if (!inBuffer.enqueue(packet)) {
      packetsInDropped++;
      return false;
    }
    return true;
  }

  // =============== config public API ===============

  /**
   * Sets the address book for the terminal. The address book is a list of {@code IpAddress} objects
   * representing the destinations to which the terminal can communicate. If a {@code null} value is
   * provided, an {@code IllegalArgumentException} is thrown.
   *
   * @param book The list of {@code IpAddress} objects representing the address book. Must be
   *     non-null.
   * @throws IllegalArgumentException If the provided list is {@code null}.
   */
  public void setAddressBook(List<IpAddress> book) {
    if (book == null) {
      throw new IllegalArgumentException("Address book cannot be null");
    }
    this.addressBook = book;
  }

  /**
   * Sets the random number generator (RNG) for the terminal. The RNG is used in various
   * probabilistic operations, such as generating random traffic.
   *
   * @param rng The {@code Random} instance to be set as the terminal's random number generator.
   *     Must not be {@code null}.
   * @throws IllegalArgumentException If the provided {@code Random} instance is {@code null}.
   */
  public void setRng(Random rng) {
    if (rng == null) {
      throw new IllegalArgumentException("Random number generator cannot be null");
    }
    this.rng = rng;
  }

  /**
   * Sets the traffic probability for the terminal. The traffic probability is a value between 0 and
   * 1 that determines the likelihood of this terminal generating new traffic during a simulation
   * tick.
   *
   * @param prob The desired traffic probability. Must be a value in the range [0.0, 1.0]. If the
   *     value is outside this range, an {@code IllegalArgumentException} is thrown.
   */
  public void setTrafficProbability(double prob) {
    if (prob < 0.0 || prob > 1.0) {
      throw new IllegalArgumentException("Traffic probability must be between 0 and 1");
    }
    this.trafficProb = prob;
  }

  /**
   * Sets the maximum page length for the terminal. This value determines the largest size (in
   * packets) that a page can have during traffic generation or other operations. The length must be
   * at least 2 packets; otherwise, the method throws an {@code IllegalArgumentException}.
   *
   * @param len The desired maximum page length. Must be greater than or equal to 2. If a value less
   *     than 2 is provided, the method throws an exception.
   */
  public void setMaxPageLength(int len) {
    if (len < 2) {
      throw new IllegalArgumentException("Max page length must be at least 2");
    }
    this.maxPageLen = len;
  }

  /**
   * Sets the output bandwidth for the terminal. The output bandwidth defines the maximum number of
   * packets that can be sent per simulation tick.
   *
   * @param bw The desired output bandwidth. Must be a non-negative integer. If a negative value is
   *     provided, the method throws an {@code IllegalArgumentException}.
   */
  public void setOutputBw(int bw) {
    if (bw < 0) {
      throw new IllegalArgumentException("Output bandwidth cannot be negative");
    }
    this.outBw = bw;
  }

  /**
   * Sets the input processing capacity for the terminal. The input processing capacity determines
   * the maximum number of packets that can be processed from the input buffer per simulation tick.
   *
   * @param cap The desired input processing capacity. Must be a non-negative integer. If a negative
   *     value is provided, the method throws an {@code IllegalArgumentException}.
   */
  public void setInProcCap(int cap) {
    if (cap < 0) {
      throw new IllegalArgumentException("Input processing capacity cannot be negative");
    }
    this.inProcCap = cap;
  }

  /** Collects a snapshot of the terminal's stats at the current tick. */
  @Override
  public TerminalStats collectStats() {
    return new TerminalStats(
        terminalIp,
        pagesCreated,
        pagesSent,
        pagesOutDropped,
        pagesCompleted,
        pagesTimedOut,
        packetsGenerated,
        packetsSent,
        packetsOutDropped,
        packetsOutTimedOut,
        packetsReceived,
        packetsInTimedOut,
        packetsInDropped,
        packetsSuccProcessed,
        reassemblers.size(),
        quarantine.size());
  }

  /** Returns the terminal's IP address. */
  @Override
  public IpAddress ip() {
    return terminalIp;
  }

  public long pagesCreated() {
    return pagesCreated;
  }

  public long pagesSent() {
    return pagesSent;
  }

  public long pagesOutDropped() {
    return pagesOutDropped;
  }

  public long pagesCompleted() {
    return pagesCompleted;
  }

  public long pagesTimedOut() {
    return pagesTimedOut;
  }

  public long packetsGenerated() {
    return packetsGenerated;
  }

  public long packetsSent() {
    return packetsSent;
  }

  public long packetsOutDropped() {
    return packetsOutDropped;
  }

  public long packetsOutTimedOut() {
    return packetsOutTimedOut;
  }

  public long packetsReceived() {
    return packetsReceived;
  }

  public long packetsInTimedOut() {
    return packetsInTimedOut;
  }

  public long packetsInDropped() {
    return packetsInDropped;
  }

  public long packetsSuccProcessed() {
    return packetsSuccProcessed;
  }

  public int outBw() {
    return outBw;
  }

  public int inProcCap() {
    return inProcCap;
  }

  public int outBufferPending() {
    return outBuffer.size();
  }

  /**
   * Determines the number of packets currently pending in the terminal's input buffer. The method
   * does not include packets being tracked by reassemblers.
   *
   * @return The count of packets in the input buffer.
   */
  public int inputBufferPending() {
    int pendingInReassemblers = reassemblers.stream().mapToInt(PageReassembler::count).sum();
    return pendingInReassemblers + inBuffer.size();
  }

  List<IpAddress> addressBook() {
    return addressBook;
  }

  Random rng() {
    return rng;
  }

  double trafficProb() {
    return trafficProb;
  }

  int maxPageLen() {
    return maxPageLen;
  }

  @Override
  public String toString() {
    return String.format(
        "Terminal{Ip: %s | Sent: %d | Reassembled: %d | Pending: %d}",
        terminalIp, pagesSent, pagesCompleted, reassemblers.size());
  }

  /** Removes expired page IDs from the quarantine list based on the current tick. */
  private void updateQuarantine(long currentTick) {
    quarantine.removeIf(q -> currentTick >= q.expiryTick());
  }

  /**
   * Removes reassemblers that have expired based on the current tick, counts their packets as timed
   * out, and adds their page IDs to the quarantine list.
   */
  private void cleanupReassemblers(long currentTick) {
    Iterator<PageReassembler> it = reassemblers.iterator();
    while (it.hasNext()) {
      PageReassembler ra = it.next();
      if (ra.isExpired(currentTick)) {
        pagesTimedOut++;
        packetsInTimedOut += ra.count();
        quarantine.add(new QuarantinedId(ra.pageId(), currentTick + Packet.TTL));
        it.remove();
      }
    }
  }

  /**
   * Drains the output buffer by sending up to outBw packets to the parent router. If a packet is
   * expired, it is dropped and counted as a timeout.
   */
  private void processOutputBuffer(long currentTick) {
    int sent = 0;
    while (sent < outBw && !outBuffer.isEmpty()) {
      Packet p = outBuffer.dequeue();
      if (p.isExpired(currentTick)) {
        packetsOutTimedOut++;
        continue;
      }
      rtrConn.receivePacket(p);
      packetsSent++;
      sent++;
    }
  }

  /**
   * Processes incoming packets in the input buffer, up to the configured processing capacity. For
   * each packet, it checks for expiration and destination validity, then tries to add it to the
   * corresponding reassembler. If the page is complete after adding the packet, it handles the
   * completed page.
   *
   * @param currentTick The current simulation tick, used for checking packet expiration.
   */
  private void processInputBuffer(long currentTick) {
    for (int processed = 0; processed < inProcCap && !inBuffer.isEmpty(); processed++) {
      Packet p = inBuffer.dequeue();

      if (p.isExpired(currentTick)) {
        packetsInTimedOut++;
        continue;
      }
      if (!p.dstIp().equals(terminalIp)) {
        packetsInDropped++;
        continue;
      }

      PageReassembler ra = findOrCreateReassembler(p.pageId(), p.srcIp(), p.pageLen(), currentTick);

      if (ra == null) {
        packetsInTimedOut++;
        continue;
      }
      if (!ra.addPacket(p)) {
        packetsInDropped++;
        continue;
      }
      if (ra.isComplete()) {
        packetsSuccProcessed += ra.total();
        handleCompletedPage(ra.pageId(), ra.srcIp());
      }
    }
  }

  /**
   * Generates new traffic from the terminal to a random destination in the address book, based on
   * the configured traffic probability. The generated page has a random length between 2 and the
   * configured maximum page length. If the output buffer does not have enough space for the new
   * page, it is dropped.
   *
   * @param currentTick The current simulation tick, used for timestamping packets.
   */
  private void generateTraffic(long currentTick) {
    if (addressBook == null || addressBook.isEmpty() || rng == null) {
      return;
    }
    if (rng.nextDouble() >= trafficProb) {
      return;
    }

    IpAddress dest = addressBook.get(rng.nextInt(addressBook.size()));
    if (dest.equals(terminalIp)) {
      return;
    }

    int len = 2 + rng.nextInt(maxPageLen - 1);
    sendPage(len, dest, currentTick);
  }

  private PageReassembler findOrCreateReassembler(
      long pageId, IpAddress srcIp, int pageLen, long currentTick) {
    for (PageReassembler ra : reassemblers) {
      if (ra.pageId() == pageId && ra.srcIp().equals(srcIp)) {
        return ra.total() == pageLen ? ra : null;
      }
    }
    PageReassembler ra = new PageReassembler(pageId, srcIp, pageLen, currentTick);
    reassemblers.add(ra);
    return ra;
  }

  private void handleCompletedPage(long pageId, IpAddress srcIp) {
    Iterator<PageReassembler> it = reassemblers.iterator();
    while (it.hasNext()) {
      PageReassembler ra = it.next();
      if (ra.pageId() == pageId && ra.srcIp().equals(srcIp)) {
        ra.assemble();
        pagesCompleted++;
        it.remove();
        return;
      }
    }
  }

  private boolean isQuarantined(long pageId) {
    return quarantine.stream().anyMatch(q -> q.pageId() == pageId);
  }
}
