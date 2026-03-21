package ar.edu.unc.david.routersimulator;

import ar.edu.unc.david.routersimulator.model.core.Admin;
import ar.edu.unc.david.routersimulator.model.core.Network;

/**
 * The Main class serves as the entry point for the program execution. It initializes the necessary
 * components and orchestrates the simulation run.
 *
 * <p>Responsibilities of this class: - Create and configure the Network instance. - Initiate and
 * manage the Admin instance to control the simulation. - Display the final report after the
 * simulation is completed.
 */
public class Main {

  /**
   * The main method serves as the entry point for the program execution. It initializes the
   * simulation environment, runs the simulation, and outputs the final report.
   *
   * @param args command-line arguments passed to the program
   */
  public static void main(String[] args) {
    Network network = new Network();
    Admin admin = new Admin(network);

    admin.runFor(100, 10);

    System.out.println("\n=== FINAL REPORT ===");
    admin.printReport();
  }
}
