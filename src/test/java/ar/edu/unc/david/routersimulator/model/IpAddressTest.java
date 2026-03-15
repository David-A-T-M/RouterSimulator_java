package ar.edu.unc.david.routersimulator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IpAddressTest {

  @Test
  void constructor_storesRouterAndTerminalId() {
    var ip = new IpAddress(10, 5);
    assertEquals(10, ip.routerId());
    assertEquals(5, ip.terminalId());
  }

  @Test
  void constructor_throwsOnInvalidRange() {
    assertThrows(IllegalArgumentException.class, () -> new IpAddress(256, 0));
    assertThrows(IllegalArgumentException.class, () -> new IpAddress(-1, 0));

    assertThrows(IllegalArgumentException.class, () -> new IpAddress(0, 256));
    assertThrows(IllegalArgumentException.class, () -> new IpAddress(0, -1));
  }

  @Test
  void invalidConstant_isConfiguredCorrectly() {
    assertEquals(0, IpAddress.INVALID.routerId());
    assertEquals(0, IpAddress.INVALID.terminalId());

    assertFalse(IpAddress.INVALID.isValid());

    IpAddress manualZero = new IpAddress(0, 0);
    assertEquals(IpAddress.INVALID, manualZero);
  }

  @Test
  void ofTerminal_createsCorrectAddress() {
    var routerId = 10;
    var terminalId = 5;
    var ip = IpAddress.ofTerminal(routerId, terminalId);

    assertEquals(routerId, ip.routerId());
    assertEquals(terminalId, ip.terminalId());

    // Verificamos que NO sea detectada como un router (porque terminalId != 0)
    assertFalse(ip.isRouter(), "Una terminal no debería ser detectada como router");
  }

  @Test
  void ofRouter_createsCorrectAddress() {
    var routerId = 10;
    var ip = IpAddress.ofRouter(routerId);

    assertEquals(routerId, ip.routerId());
    assertEquals(0, ip.terminalId());
    assertTrue(ip.isRouter());
  }

  @Test
  void ofTerminal_withZeroId_isEquivalentToOfRouter() {
    var id = 42;
    var ipFromTerminal = IpAddress.ofTerminal(id, 0);
    var ipFromRouter = IpAddress.ofRouter(id);

    assertEquals(ipFromRouter, ipFromTerminal, "ofTerminal con ID 0 debería ser igual a ofRouter");
    assertTrue(ipFromTerminal.isRouter());
  }

  @Test
  void fromRaw_roundTrip() {
    var original = new IpAddress(42, 7);
    var recovered = IpAddress.fromRaw(original.rawAddress());
    assertEquals(original, recovered);
  }

  @Test
  void isRouter_trueWhenTerminalIdIsZero() {
    assertTrue(IpAddress.ofRouter(5).isRouter());
    assertFalse(new IpAddress(5, 3).isRouter());
  }

  @Test
  void isValid_falseOnlyFor_0_0() {
    assertFalse(IpAddress.INVALID.isValid());
    assertTrue(new IpAddress(0, 1).isValid());
    assertTrue(new IpAddress(1, 0).isValid());
  }

  @Test
  void toString_formatsWithLeadingZeros() {
    assertEquals("010.005", new IpAddress(10, 5).toString());
    assertEquals("000.000", IpAddress.INVALID.toString());
  }

  @Test
  void compareTo_ordersByRawAddress() {
    var low = new IpAddress(1, 0);
    var high = new IpAddress(2, 0);
    assertTrue(low.compareTo(high) < 0);
    assertTrue(high.compareTo(low) > 0);
    assertEquals(0, low.compareTo(new IpAddress(1, 0)));
  }

  @ParameterizedTest
  @CsvSource({"1, 0, true", "1, 1, false", "5, 0, true"})
  void isRouter_parametrized(int router, int terminal, boolean expected) {
    assertEquals(expected, new IpAddress(router, terminal).isRouter());
  }
}
