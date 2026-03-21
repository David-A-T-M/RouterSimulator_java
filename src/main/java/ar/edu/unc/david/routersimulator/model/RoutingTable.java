package ar.edu.unc.david.routersimulator.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a routing table for managing and retrieving next-hop IP addresses for given
 * destination IP addresses in a simulated network environment.
 *
 * <p>This class uses a {@code Map} to store routing entries, where the key is the destination IP
 * address and the value is a {@code RouteEntry} object containing information about the destination
 * and the associated next hop.
 */
public class RoutingTable {

  /**
   * Represents a single routing table entry containing the destination router IP and the next-hop
   * IP.
   *
   * @param destRouterIp The IP address of the destination router for this route entry.
   * @param nextHopIp The IP address of the next hop to reach the destination router.
   */
  public record RouteEntry(IpAddress destRouterIp, IpAddress nextHopIp) {}

  private final Map<IpAddress, RouteEntry> entries = new HashMap<>();

  /**
   * Retrieves the next-hop IP address for the given destination IP address based on the routing
   * table.
   *
   * @param destIp The destination IP address for which to retrieve the next-hop IP address.
   * @return The next-hop IP address if a route entry exists for the destination IP address, or
   *     {@code null} if no route is found.
   */
  public IpAddress getNextHopIp(IpAddress destIp) {
    RouteEntry entry = entries.get(destIp);
    return (entry != null) ? entry.nextHopIp() : null;
  }

  public void setNextHopIp(IpAddress destIp, IpAddress nextHop) {
    entries.put(destIp, new RouteEntry(destIp, nextHop));
  }

  public int size() {
    return entries.size();
  }
}
