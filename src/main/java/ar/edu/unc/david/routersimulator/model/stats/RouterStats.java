package ar.edu.unc.david.routersimulator.model.stats;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.nodes.Router;

/** Represents a snapshot of the stats of a {@link Router} at a given tick. */
public record RouterStats(
    IpAddress ip,
    long packetsReceived,
    long packetsDropped,
    long packetsTimedOut,
    long packetsForwarded,
    long packetsDelivered,
    int neighborCount,
    int routingTableSize)
    implements Stats {}
