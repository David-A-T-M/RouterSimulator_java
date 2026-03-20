package ar.edu.unc.david.routersimulator.model.nodes;

/**
 * Represents an entity that can be processed or updated in discrete time intervals or "ticks".
 * Implementing classes define specific behavior to be executed during each tick.
 */
public interface Tickable {
  void tick(long currentTick);
}
