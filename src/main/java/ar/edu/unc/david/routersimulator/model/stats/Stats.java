package ar.edu.unc.david.routersimulator.model.stats;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.nodes.Router;
import ar.edu.unc.david.routersimulator.model.nodes.Terminal;

/**
 * A common interface for stats snapshots from both {@link Router}s and {@link Terminal}s, to be
 * used in the UI and elsewhere when only the common fields are relevant.
 */
public interface Stats {
  IpAddress ip();

  long packetsReceived();
}
