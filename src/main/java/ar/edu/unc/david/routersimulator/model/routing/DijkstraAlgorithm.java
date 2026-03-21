package ar.edu.unc.david.routersimulator.model.routing;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.RoutingTable;
import ar.edu.unc.david.routersimulator.model.nodes.Router;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless utility class that implements Dijkstra's shortest-path algorithm over the simulated
 * router network.
 *
 * <p>Edge weight = the current output-buffer load on the link from the current router to its
 * neighbor, so the algorithm naturally prefers less-congested paths.
 */
public final class DijkstraAlgorithm {

  private static final long INF = Long.MAX_VALUE / 2;

  private DijkstraAlgorithm() {}

  private static final class DistanceInfo {
    long distance = INF;
    IpAddress parent = IpAddress.INVALID;
    boolean visited = false;
  }

  /**
   * Computes the routing table for {@code sourceIp} given the full list of routers.
   *
   * <p>The algorithm builds a shortest-path tree rooted at {@code sourceIp} where the cost of
   * traversing a link equals the buffer load reported by {@link Router#getNeighborBufferUsage}. The
   * next-hop stored in the table is the first router on the shortest path from source to each
   * reachable destination.
   *
   * @param routers All routers in the network (read-only view).
   * @param sourceIp IP address of the router for which the table is being computed.
   * @return A populated {@link RoutingTable} for {@code sourceIp}.
   * @throws IllegalArgumentException if {@code sourceIp} is not found in {@code routers}.
   */
  public static RoutingTable computeRoutingTable(List<Router> routers, IpAddress sourceIp) {
    java.util.Objects.requireNonNull(routers, "routers list cannot be null");
    java.util.Objects.requireNonNull(sourceIp, "sourceIp cannot be null");
    if (routers.size() <= 1) {
      return new RoutingTable();
    }
    final int n = routers.size();

    // Initialise distance array
    List<DistanceInfo> distances = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      distances.add(new DistanceInfo());
    }

    int sourceIndex = getRouterIndex(routers, sourceIp);
    distances.get(sourceIndex).distance = 0;
    distances.get(sourceIndex).parent = sourceIp;

    // Main Dijkstra loop
    for (int i = 0; i < n; i++) {
      int current = findMinDistance(distances, n);
      if (current == -1) {
        break; // all reachable routers visited
      }

      distances.get(current).visited = true;

      Router currentRouter = routers.get(current);
      for (IpAddress neighborIp : currentRouter.getNeighborIps()) {
        int neighborIndex = getRouterIndex(routers, neighborIp);
        DistanceInfo neighborInfo = distances.get(neighborIndex);

        if (neighborInfo.visited) {
          continue;
        }

        long bufferLoad = currentRouter.getNeighborBufferUsage(neighborIp);
        long newDist = distances.get(current).distance + bufferLoad;

        if (newDist < neighborInfo.distance) {
          neighborInfo.distance = newDist;
          neighborInfo.parent = currentRouter.ip();
        }
      }
    }

    // Build routing table
    RoutingTable routingTable = new RoutingTable();

    for (int i = 0; i < n; i++) {
      if (i == sourceIndex || distances.get(i).distance == INF) {
        continue; // skip the source itself and unreachable routers
      }

      IpAddress destIp = routers.get(i).ip();
      IpAddress currentIp = destIp;
      IpAddress parentIp = distances.get(i).parent;

      // Walk back from destination toward a source to find the next hop
      while (!parentIp.equals(sourceIp)) {
        currentIp = parentIp;
        int parentIdx = getRouterIndex(routers, parentIp);
        parentIp = distances.get(parentIdx).parent;
      }

      routingTable.setNextHopIp(destIp, currentIp);
    }

    return routingTable;
  }

  /**
   * Convenience method: computes and returns one {@link RoutingTable} per router, in the same order
   * as {@code routers}.
   *
   * @param routers All routers in the network.
   * @return A list of routing tables, one per router.
   */
  public static List<RoutingTable> computeAllRoutingTables(List<Router> routers) {
    List<RoutingTable> tables = new ArrayList<>(routers.size());
    for (Router router : routers) {
      tables.add(computeRoutingTable(routers, router.ip()));
    }
    return tables;
  }

  // Private helpers

  /**
   * Returns the index in {@code routers} of the router whose IP equals {@code routerIp}.
   *
   * @throws IllegalArgumentException if no such router exists.
   */
  private static int getRouterIndex(List<Router> routers, IpAddress routerIp) {
    for (int i = 0; i < routers.size(); i++) {
      if (routers.get(i).ip().equals(routerIp)) {
        return i;
      }
    }
    throw new IllegalArgumentException("No router found with IP: " + routerIp);
  }

  /**
   * Returns the index of the unvisited router with the smallest tentative distance, or {@code -1}
   * if all reachable routers have already been visited (mirrors the C++ sentinel {@code
   * numeric_limits<size_t>::max()}).
   */
  private static int findMinDistance(List<DistanceInfo> distances, int n) {
    long minDistance = INF;
    int currentIndex = -1;

    for (int j = 0; j < n; j++) {
      DistanceInfo info = distances.get(j);
      if (!info.visited && info.distance < minDistance) {
        minDistance = info.distance;
        currentIndex = j;
      }
    }

    return currentIndex;
  }
}
