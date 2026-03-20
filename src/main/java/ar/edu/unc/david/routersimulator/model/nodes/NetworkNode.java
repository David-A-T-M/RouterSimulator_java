package ar.edu.unc.david.routersimulator.model.nodes;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.stats.Stats;

/**
 * A node in the simulated network — either a {@link Router} or a {@link Terminal}.
 *
 * <p>Every node has an IP, can receive packets, advances per tick, and reports a common {@link
 * Stats} snapshot. Node-specific stats are available by casting to {@link TerminalStats} or {@link
 * RouterStats}.
 */
public interface NetworkNode extends PacketReceiver, Tickable {
  IpAddress ip();

  Stats collectStats();
}
