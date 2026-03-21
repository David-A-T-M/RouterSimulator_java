package ar.edu.unc.david.routersimulator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutingTableTest {

  private RoutingTable routingTable;
  private static final IpAddress DEST_IP = new IpAddress(1, 2);
  private static final IpAddress NEXT_HOP = new IpAddress(1, 3);

  @BeforeEach
  void setUp() {
    routingTable = new RoutingTable();
  }

  @Test
  void testSetAndGetNextHop() {
    routingTable.setNextHopIp(DEST_IP, NEXT_HOP);

    IpAddress result = routingTable.getNextHopIp(DEST_IP);

    assertNotNull(result);
    assertEquals(NEXT_HOP, result);
  }

  @Test
  void testGetNextHopNotFound() {
    IpAddress unknownIp = new IpAddress(5, 5);

    IpAddress result = routingTable.getNextHopIp(unknownIp);

    assertNull(result);
  }

  @Test
  void testUpdateExistingRoute() {
    IpAddress newNextHop = new IpAddress(2, 3);

    routingTable.setNextHopIp(DEST_IP, NEXT_HOP);
    routingTable.setNextHopIp(DEST_IP, newNextHop); // Sobrescribimos

    assertEquals(1, routingTable.size());
    assertEquals(newNextHop, routingTable.getNextHopIp(DEST_IP));
  }

  @Test
  void testTableSize() {
    assertEquals(0, routingTable.size());

    routingTable.setNextHopIp(DEST_IP, NEXT_HOP);
    routingTable.setNextHopIp(new IpAddress(2, 1), new IpAddress(1, 3));

    assertEquals(2, routingTable.size());
  }
}
