package ar.edu.unc.david.routersimulator.model.nodes;

import ar.edu.unc.david.routersimulator.model.Packet;

/** Any entity that accepts incoming {@link Packet}s into an input buffer. */
public interface PacketReceiver {
  boolean receivePacket(Packet packet);
}
