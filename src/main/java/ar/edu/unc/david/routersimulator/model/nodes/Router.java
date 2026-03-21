package ar.edu.unc.david.routersimulator.model.nodes;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.Packet;
import ar.edu.unc.david.routersimulator.model.RoutingTable;
import ar.edu.unc.david.routersimulator.model.infrastructure.PacketBuffer;
import ar.edu.unc.david.routersimulator.model.stats.RouterStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Represents a network router that routes packets between terminals and other routers. The Router
 * class manages input and output buffers, maintains connections to terminals and neighboring
 * routers, and facilitates the simulation of packet routing and delivery. It supports various
 * configuration and operational methods, enabling the management of terminals, routing logic, and
 * buffer capacities.
 */
public class Router implements NetworkNode {

  public static final int DEF_INPUT_PROC = 10;
  public static final int DEF_OUTPUT_BW = 5;
  public static final int DEF_LOC_BW = 10;
  public static final int DEF_OUT_BUF_CAP = 0;
  public static final int DEF_IN_BUF_CAP = 0;
  public static final int DEF_LOC_BUF_CAP = 0;

  /**
   * The Config record represents the configuration settings for a router's internal buffers,
   * bandwidth capacities, and processing capabilities. It ensures that all the parameters provided
   * are non-negative.
   *
   * <p>This configuration record is primarily used to initialize and manage the settings for
   * various router components such as input buffers, processing capacity, local buffers,
   * bandwidths, and output buffers.
   *
   * @param inBufferCap The capacity of the input buffer.
   * @param inProcCap The maximum number of packets that can be processed per tick in the input
   *     stage.
   * @param locBufferCap The capacity of the local buffer.
   * @param locBw The bandwidth of the local buffer.
   * @param outBufferCap The capacity of the output buffer.
   * @param outBw The bandwidth of the output buffer.
   */
  public record Config(
      int inBufferCap, int inProcCap, int locBufferCap, int locBw, int outBufferCap, int outBw) {

    /**
     * Creates an instance of the Config record, validating that all configuration parameters are
     * non-negative. If any parameter is negative, an {@code IllegalArgumentException} is thrown.
     *
     * @throws IllegalArgumentException if any of the configuration parameters are negative.
     */
    public Config {
      if (inBufferCap < 0
          || inProcCap < 0
          || locBufferCap < 0
          || locBw < 0
          || outBufferCap < 0
          || outBw < 0) {
        throw new IllegalArgumentException("All config parameters must be non-negative");
      }
    }

    /**
     * Default constructor for the Config record that initializes the configuration parameters using
     * predefined default values. This constructor delegates to the primary constructor of the
     * Config record with these default values.
     */
    public Config() {
      this(
          DEF_IN_BUF_CAP,
          DEF_INPUT_PROC,
          DEF_LOC_BUF_CAP,
          DEF_LOC_BW,
          DEF_OUT_BUF_CAP,
          DEF_OUTPUT_BW);
    }
  }

  private record RtrConnection(Router neighborRouter, PacketBuffer outBuffer) {
    private RtrConnection {
      java.util.Objects.requireNonNull(neighborRouter, "Neighbor router cannot be null");
      java.util.Objects.requireNonNull(outBuffer, "Output buffer cannot be null");
    }

    private RtrConnection(Router neighborRouter, int capacity) {
      this(neighborRouter, new PacketBuffer(capacity));
    }
  }

  private final IpAddress routerIp;
  private RoutingTable routingTable;

  private final Map<IpAddress, Terminal> terminals;
  private final Map<IpAddress, RtrConnection> connections;

  private final int outBufferCap;
  private int outBufferBw;
  private final PacketBuffer inBuffer;
  private int inProcCap;
  private final PacketBuffer locBuffer;
  private int locBufferBw;

  private int packetsReceived;
  private int packetsDropped;
  private int packetsTimedOut;
  private int packetsForwarded;
  private int packetsDelivered;

  public Router(IpAddress ip) {
    this(ip, 0, new Config());
  }

  public Router(IpAddress ip, int terminalCount) {
    this(ip, terminalCount, new Config());
  }

  /**
   * Constructs a Router object with a specific IP address, terminal count, and configuration
   * settings. This constructor ensures that the provided IP address is valid for a router (terminal
   * ID must be 0) and initializes buffers, bandwidth, and terminals for the router based on the
   * supplied configuration.
   *
   * @param ip The IP address of the router. Must have a terminal ID of 0, indicating it is a
   *     router.
   * @param terminalCount The number of terminals directly connected to this router.
   * @param cfg The configuration object containing buffer capacities, bandwidths, and other router
   *     settings.
   * @throws IllegalArgumentException if the IP address is not valid for a router (terminal ID is
   *     not 0).
   */
  public Router(IpAddress ip, int terminalCount, Config cfg) {
    if (!ip.isRouter()) {
      throw new IllegalArgumentException("Router Ip must have terminalID = 0");
    }

    this.routerIp = ip;
    this.outBufferCap = cfg.outBufferCap;
    this.inBuffer = new PacketBuffer(cfg.inBufferCap);
    this.inProcCap = cfg.inProcCap;
    this.locBuffer = new PacketBuffer(cfg.locBufferCap);
    this.locBufferBw = cfg.locBw;
    this.outBufferBw = cfg.outBw;

    this.terminals = new LinkedHashMap<>();
    this.connections = new LinkedHashMap<>();

    initializeTerminals(terminalCount);
  }

  /**
   * Connects a terminal to the router. This method ensures that the terminal belongs to the same
   * router, is not connected, and is a valid terminal. Upon successful connection, the terminal is
   * added to the router's internal terminal collection.
   *
   * @param terminal The terminal to be connected to the router. Must not be null, must belong to
   *     the router, and must not be connected.
   * @return {@code true} if the terminal was successfully connected.
   * @throws IllegalArgumentException if the terminal is null, is already connected, or does not
   *     belong to this router.
   */
  public boolean connectTerminal(Terminal terminal) {
    if (terminal == null) {
      throw new IllegalArgumentException("Terminal cannot be null");
    }
    if (terminalIsConnected(terminal.ip())) {
      throw new IllegalArgumentException("Terminal already connected");
    }
    if (!(terminal.ip().routerId() == routerIp.routerId())) {
      throw new IllegalArgumentException("Terminal does not belong to this router");
    }

    terminals.put(terminal.ip(), terminal);
    return true;
  }

  /**
   * Connects the current router to a neighboring router. This method establishes a connection
   * between the two routers if the neighbor is valid (non-null, different router, and not already
   * connected). The connection is tracked using the neighbor's IP address and involves initializing
   * the output buffer for the connection.
   *
   * @param neighbor The neighboring router to connect to. Must not be null and must not be
   *     connected to this router.
   * @return {@code true} if the connection was successfully established, or {@code false} if the
   *     neighbor is the same router or is already connected.
   * @throws IllegalArgumentException if the neighbor
   */
  public boolean connectRouter(Router neighbor) {
    if (neighbor == null) {
      throw new IllegalArgumentException("Neighbor router cannot be null");
    }
    if (neighbor == this || routerIsConnected(neighbor.ip())) {
      return false;
    }
    connections.put(neighbor.ip(), new RtrConnection(neighbor, outBufferCap));
    return true;
  }

  /**
   * Receives a packet and attempts to enqueue it into the input buffer of the router. If the input
   * buffer is full, the packet is dropped.
   *
   * @param packet The packet to be received. Must not be null.
   * @return {@code true} if the packet was successfully enqueued into the input buffer; {@code
   *     false} if the packet was dropped due to a full input buffer.
   */
  public boolean receivePacket(Packet packet) {
    packetsReceived++;
    if (!inBuffer.enqueue(packet)) {
      packetsDropped++;
      return false;
    }
    return true;
  }

  /**
   * Processes the output buffers of all router connections and forwards packets to neighboring
   * routers. Packets are dequeued from the output buffer and either forwarded, dropped, or
   * considered timed out based on their expiry and the availability of neighboring routers.
   *
   * @param currentTick The current simulation tick used to determine packet expiration.
   * @return The total number of packets successfully forwarded to neighboring routers.
   */
  int processOutputBuffers(long currentTick) {
    int totalSent = 0;

    for (RtrConnection conn : connections.values()) {
      int sent = 0;
      while (sent < outBufferBw && !conn.outBuffer.isEmpty()) {
        Packet packet = conn.outBuffer.dequeue();

        if (packet.expiryTick() <= currentTick) {
          packetsTimedOut++;
          continue;
        }

        conn.neighborRouter.receivePacket(packet);
        sent++;
        packetsForwarded++;
      }
      totalSent += sent;
    }

    return totalSent;
  }

  /**
   * Processes packets stored in the local buffer of the router, delivering them to their
   * corresponding terminals if possible. Packets that have expired or cannot be delivered are
   * tracked accordingly. The method ensures that packet processing does not exceed the local buffer
   * bandwidth.
   *
   * @param currentTick The current simulation tick used to determine packet expiration.
   * @return The number of packets successfully delivered from the local buffer during this
   *     execution.
   */
  public int processLocalBuffer(long currentTick) {
    int delivered = 0;

    while (delivered < locBufferBw && !locBuffer.isEmpty()) {
      Packet packet = locBuffer.dequeue();

      if (packet.expiryTick() <= currentTick) {
        packetsTimedOut++;
        continue;
      }

      Terminal dest = terminals.get(packet.dstIp());
      if (dest != null) {
        dest.receivePacket(packet);
        packetsDelivered++;
        delivered++;
      } else {
        packetsDropped++;
      }
    }

    return delivered;
  }

  /**
   * Updates the state of all terminals connected to the router for the given simulation tick. This
   * method iterates through all terminals and invokes their tick method, passing the current tick
   * as an argument.
   *
   * @param currentTick The current simulation tick, used for updating the state of each terminal.
   */
  public void tickTerminals(long currentTick) {
    for (Terminal t : terminals.values()) {
      t.tick(currentTick);
    }
  }

  /**
   * Processes packets in the input buffer of the router. This method dequeues packets from the
   * input buffer up to the maximum processing capacity or until the input buffer is empty. For each
   * dequeued packet, it checks whether the packet has timed out based on the current simulation
   * tick. Timed-out packets are discarded, while valid packets are routed to their next
   * destination.
   *
   * @param currentTick The current simulation tick used to determine packet expiration.
   * @return The number of packets successfully processed from the input buffer during this method
   *     execution.
   */
  int processInputBuffer(long currentTick) {
    int processed = 0;

    while (processed < inProcCap && !inBuffer.isEmpty()) {
      processed++;
      Packet packet = inBuffer.dequeue();

      if (packet.expiryTick() <= currentTick) {
        packetsTimedOut++;
        continue;
      }

      routePacket(packet);
    }

    return processed;
  }

  /**
   * Executes the main tick operation for the router, processing packets in the correct order
   * through output buffers, local buffers, terminals, and input buffers. This method serves as the
   * primary simulation step for the router, ensuring that all buffered packets and connected
   * terminals are updated within a single tick.
   *
   * @param currentTick The current simulation tick, used for processing packets and updating the
   *     state of the router and its terminals.
   */
  public void tick(long currentTick) {
    processOutputBuffers(currentTick);
    processLocalBuffer(currentTick);
    tickTerminals(currentTick);
    processInputBuffer(currentTick);
  }

  public int getPacketsOutPending() {
    return connections.values().stream().mapToInt(c -> c.outBuffer.size()).sum();
  }

  public int getNeighborBufferUsage(IpAddress neighborIp) {
    RtrConnection conn = connections.get(neighborIp);
    return conn != null ? conn.outBuffer.size() : 0;
  }

  public Terminal getTerminal(IpAddress ip) {
    return terminals.get(ip);
  }

  public List<Terminal> getTerminals() {
    return new ArrayList<>(terminals.values());
  }

  public List<IpAddress> getNeighborIps() {
    return new ArrayList<>(connections.keySet());
  }

  public List<IpAddress> getTerminalIps() {
    return new ArrayList<>(terminals.keySet());
  }

  /**
   * Shares the address book containing the list of terminal IP addresses with all connected
   * terminals. This is achieved by iterating through the router's terminals and setting their
   * address book to the provided list of terminal IPs.
   *
   * @param terminalIps The list of terminal IP addresses to be shared with all connected terminals.
   */
  public void shareAddressBook(List<IpAddress> terminalIps) {
    for (Terminal t : terminals.values()) {
      t.setAddressBook(terminalIps);
    }
  }

  /**
   * Shares a given Random generator instance with all Terminal objects in the terminals collection
   * by setting the generator for each terminal.
   *
   * @param rgen the Random generator instance to be shared with all Terminal objects
   */
  public void shareRandomGenerator(Random rgen) {
    for (Terminal t : terminals.values()) {
      t.setRng(rgen);
    }
  }

  /**
   * Distributes the given traffic probability to all terminals.
   *
   * @param probability the traffic probability to be shared with all terminals
   */
  public void shareTrafficProbability(double probability) {
    for (Terminal t : terminals.values()) {
      t.setTrafficProbability(probability);
    }
  }

  /**
   * Shares the specified maximum page length across all terminals.
   *
   * @param pageLen the maximum page length to be set for each terminal
   */
  public void shareMaxPageLength(int pageLen) {
    for (Terminal t : terminals.values()) {
      t.setMaxPageLength(pageLen);
    }
  }

  private void initializeTerminals(int count) {
    for (int i = 1; i <= count; i++) {
      Terminal terminal = new Terminal(this, this.routerIp.routerId(), i);
      IpAddress terminalIp = new IpAddress(routerIp.routerId(), i);
      terminals.put(terminalIp, terminal);
    }
  }

  private PacketBuffer getOutputBuffer(IpAddress nextIp) {
    RtrConnection conn = connections.get(nextIp);
    return conn != null ? conn.outBuffer : null;
  }

  private void routePacket(Packet packet) {
    IpAddress destIp = IpAddress.ofRouter(packet.dstIp().routerId());

    if (destIp.routerId() == routerIp.routerId()) {
      if (locBuffer.enqueue(packet)) {
        return;
      }
      packetsDropped++;
      return;
    }

    IpAddress nextHopIp = routingTable.getNextHopIp(destIp);
    PacketBuffer outBuffer = getOutputBuffer(nextHopIp);

    if (outBuffer == null) {
      packetsDropped++;
      return;
    }

    if (outBuffer.enqueue(packet)) {
      return;
    }

    packetsDropped++;
  }

  private boolean routerIsConnected(IpAddress neighborIp) {
    return connections.containsKey(neighborIp);
  }

  private boolean terminalIsConnected(IpAddress terminalIp) {
    return terminals.containsKey(terminalIp);
  }

  @Override
  public IpAddress ip() {
    return routerIp;
  }

  @Override
  public RouterStats collectStats() {
    return new RouterStats(
        routerIp,
        packetsReceived(),
        packetsDropped(),
        packetsTimedOut(),
        packetsForwarded(),
        packetsDelivered(),
        getNeighborIps().size(),
        routingTable.size());
  }

  public void setInProcCap(int proCap) {
    this.inProcCap = proCap;
  }

  public void setLocBufferBw(int bw) {
    this.locBufferBw = bw;
  }

  public void setOutBufferBw(int bw) {
    this.outBufferBw = bw;
  }

  public void setRoutingTable(RoutingTable t) {
    this.routingTable = t;
  }

  public int terminalCount() {
    return terminals.size();
  }

  public int routerCount() {
    return connections.size();
  }

  public int inProcCap() {
    return inProcCap;
  }

  public int locBufferBw() {
    return locBufferBw;
  }

  public int outBufferBw() {
    return outBufferBw;
  }

  public int packetsReceived() {
    return packetsReceived;
  }

  public int packetsDropped() {
    return packetsDropped;
  }

  public int packetsTimedOut() {
    return packetsTimedOut;
  }

  public int packetsForwarded() {
    return packetsForwarded;
  }

  public int packetsDelivered() {
    return packetsDelivered;
  }

  public int packetsInPending() {
    return inBuffer.size();
  }

  public int packetsLocPending() {
    return locBuffer.size();
  }

  public int localBufferUsage() {
    return locBuffer.size();
  }
}
