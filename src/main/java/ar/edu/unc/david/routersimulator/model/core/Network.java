package ar.edu.unc.david.routersimulator.model.core;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.nodes.Router;
import ar.edu.unc.david.routersimulator.model.nodes.Terminal;
import ar.edu.unc.david.routersimulator.model.routing.DijkstraAlgorithm;
import ar.edu.unc.david.routersimulator.model.stats.NetworkStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents a simulated computer network for experimenting with network topologies, traffic
 * generation, and routing algorithms. The network is composed of routers, terminals, and
 * connections, enabling simulation and analysis of various network behaviors.
 */
public class Network {

  public static final int DEF_ROUTERS_COUNT = 20;
  public static final int DEF_MAX_TERMINALS = 10;
  public static final int DEF_COMPLEXITY = 5;
  public static final float DEF_PROBABILITY = 0.5f;
  public static final int DEF_MAX_PAGE_LEN = 10;

  /**
   * Immutable configuration for {@link Network} construction.
   *
   * @param routerCount Number of routers to create (0–255).
   * @param maxTerminalCount Max terminals per router (0–255).
   * @param complexity Extra random links added per router.
   * @param trafficProbability Probability [0,1] that a terminal generates traffic each tick.
   * @param maxPageLen Maximum number of packets per page.
   */
  public record Config(
      int routerCount,
      int maxTerminalCount,
      int complexity,
      float trafficProbability,
      int maxPageLen) {

    /** Validates that all numeric parameters are within sensible bounds. */
    public Config {
      if (routerCount < 0 || routerCount > 255) {
        throw new IllegalArgumentException("routerCount must be 0-255");
      }
      if (maxTerminalCount < 0 || maxTerminalCount > 255) {
        throw new IllegalArgumentException("maxTerminalCount must be 0-255");
      }
      if (complexity < 0) {
        throw new IllegalArgumentException("complexity must be >= 0");
      }
      if (trafficProbability < 0f || trafficProbability > 1f) {
        throw new IllegalArgumentException("trafficProbability must be in [0, 1]");
      }
      if (maxPageLen <= 0) {
        throw new IllegalArgumentException("maxPageLen must be > 0");
      }
    }

    /** Default constructor using the class-level constants. */
    public Config() {
      this(DEF_ROUTERS_COUNT, DEF_MAX_TERMINALS, DEF_COMPLEXITY, DEF_PROBABILITY, DEF_MAX_PAGE_LEN);
    }
  }

  /** Owned routers. */
  private final List<Router> routers = new ArrayList<>();

  /** All terminal IP addresses across the network, shared with every terminal. */
  private final List<IpAddress> addressBook = new ArrayList<>();

  private long currentTick;
  private final Random rng;

  /** Creates a network with a default configuration and a random seed. */
  public Network() {
    this(new Config());
  }

  /** Creates a network from the given configuration and a random seed. */
  public Network(Config config) {
    this(config, new Random());
  }

  /**
   * Constructs a new {@code Network} instance based on the given configuration and random seed.
   * Initializes the network topology with routers, terminals, and random connections based on the
   * parameters extracted from the provided {@code Config} object. After the network is generated,
   * all routing tables are recalculated.
   *
   * @param config The configuration specifying the network details, such as router count, maximum
   *     terminal count, link complexity, traffic generation probability, and maximum page length.
   * @param rng The random number generator used to create the network's random topology and
   *     connections.
   */
  public Network(Config config, Random rng) {
    this.rng = rng;
    this.currentTick = 1;
    generateRandomNetwork(
        config.routerCount(),
        config.maxTerminalCount(),
        config.complexity(),
        config.trafficProbability(),
        config.maxPageLen());
    recalculateAllRoutes();
  }

  /**
   * Builds the network topology: creates routers with terminals, collects the global address book,
   * forms a spanning tree, and adds extra links.
   */
  public void generateRandomNetwork(
      int routerCount, int terminalCount, int complexity, float probability, int pageLen) {

    for (int i = 0; i < routerCount; i++) {
      addRouter(i, terminalCount, probability, pageLen);
    }

    for (Router rtr : routers) {
      addressBook.addAll(rtr.getTerminalIps());
    }

    connectMinimal(routerCount);
    addAdditionalConnections(complexity);
  }

  /**
   * Establishes initial minimal connections between routers to form a spanning tree. Ensures each
   * router is connected to at least one other router, creating a connected network topology with
   * minimal links.
   *
   * @param routerCount The total number of routers in the network. Determines the number of
   *     connections to be created.
   */
  private void connectMinimal(int routerCount) {
    for (int i = 1; i < routerCount; i++) {
      int targetIndex = rng.nextInt(i); // [0, i)
      establishLink(routers.get(i), routers.get(targetIndex));
    }
  }

  /**
   * Adds additional connections between routers in the network based on the given complexity. For
   * each router, a number of random connections to other routers are established, determined by the
   * specified complexity.
   *
   * @param complexity The number of additional connections to attempt for each router. This defines
   *     the degree of random links added to the network. If set to 0 or if the routers list is
   *     empty, no additional connections are created.
   */
  public void addAdditionalConnections(int complexity) {
    if (complexity == 0 || routers.isEmpty()) {
      return;
    }
    for (Router source : routers) {
      for (int c = 0; c < complexity; c++) {
        Router target = routers.get(rng.nextInt(routers.size()));
        establishLink(source, target);
      }
    }
  }

  /**
   * Establishes a bidirectional link between two routers. Does nothing if both references point to
   * the same router.
   *
   * @param rtrA First router.
   * @param rtrB Second router.
   */
  public static void establishLink(Router rtrA, Router rtrB) {
    if (rtrA == rtrB) {
      return;
    }
    rtrA.connectRouter(rtrB);
    rtrB.connectRouter(rtrA);
  }

  /**
   * Runs the simulation for the specified number of ticks, recalculating all routing tables every 5
   * ticks and once more at the end.
   *
   * @param ticks Number of simulation steps to execute.
   */
  public void simulate(int ticks) {
    for (int i = 0; i < ticks; i++) {
      tick();
      if (i % 5 == 0) {
        recalculateAllRoutes();
      }
    }
    recalculateAllRoutes();
  }

  /**
   * Aggregates statistics from all routers and their terminals into a single {@link NetworkStats}
   * snapshot.
   *
   * @return Immutable stats record for the current simulation state.
   */
  public NetworkStats getStats() {
    NetworkStats.Builder b =
        new NetworkStats.Builder().currentTick(currentTick - 1).totalRouters(routers.size());

    for (Router rtr : routers) {
      b.addTerminals(rtr.terminalCount());
      b.addPacketsInFlight(rtr.packetsInPending());
      b.addPacketsInFlight(rtr.getPacketsOutPending());
      b.addPacketsInFlight(rtr.packetsLocPending());

      var routerStats = rtr.collectStats();
      b.addPacketsDropped(routerStats.packetsDropped());
      b.addPacketsTimedOut(routerStats.packetsTimedOut());

      for (Terminal terminal : rtr.getTerminals()) {
        var terminalStats = terminal.collectStats();
        b.addPagesCreated(terminalStats.pagesCreated());
        b.addPagesDropped(terminalStats.pagesOutDropped());
        b.addPagesCompleted(terminalStats.pagesCompleted());
        b.addPagesTimedOut(terminalStats.pagesTimedOut());
        b.addPacketsGenerated(terminalStats.packetsGenerated());
        b.addPacketsSent(terminalStats.packetsSent());
        b.addPacketsDropped(terminalStats.packetsInDropped());
        b.addPacketsDropped(terminalStats.packetsOutDropped());
        b.addPacketsTimedOut(terminalStats.packetsOutTimedOut());
        b.addPacketsTimedOut(terminalStats.packetsInTimedOut());
        b.addPacketsInFlight(terminal.inputBufferPending());
        b.addPacketsInFlight(terminal.outBufferPending());
        b.addPacketsDelivered(terminalStats.packetsSuccProcessed());
      }
    }

    return b.build();
  }

  public List<Router> getRouters() {
    return routers;
  }

  private void addRouter(int routerId, int terminalCount, float probability, int pageLen) {
    IpAddress ip = IpAddress.ofRouter(routerId);
    Router rtr = new Router(ip, terminalCount);
    rtr.shareAddressBook(addressBook);
    rtr.shareRandomGenerator(rng);
    rtr.shareTrafficProbability(probability);
    rtr.shareMaxPageLength(pageLen);
    routers.add(rtr);
  }

  private void recalculateAllRoutes() {
    for (Router rtr : routers) {
      rtr.setRoutingTable(DijkstraAlgorithm.computeRoutingTable(routers, rtr.ip()));
    }
  }

  private void tick() {
    for (Router router : routers) {
      router.tick(currentTick);
    }
    currentTick++;
  }
}
