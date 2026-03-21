package ar.edu.unc.david.routersimulator.model.stats;

import ar.edu.unc.david.routersimulator.model.IpAddress;
import ar.edu.unc.david.routersimulator.model.nodes.Terminal;

/** Represents a snapshot of the stats of a {@link Terminal} at a given tick. */
public record TerminalStats(
    IpAddress ip,
    long pagesCreated,
    long pagesSent,
    long pagesOutDropped,
    long pagesCompleted,
    long pagesTimedOut,
    long packetsGenerated,
    long packetsSent,
    long packetsOutDropped,
    long packetsOutTimedOut,
    long packetsReceived,
    long packetsInTimedOut,
    long packetsInDropped,
    long packetsSuccProcessed,
    int activeReassemblers,
    int quarantineSize)
    implements Stats {}
