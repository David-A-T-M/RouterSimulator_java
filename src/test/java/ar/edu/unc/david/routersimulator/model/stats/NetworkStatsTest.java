package ar.edu.unc.david.routersimulator.model.stats;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NetworkStats")
class NetworkStatsTest {

  @Nested
  @DisplayName("Builder")
  class BuilderTests {

    @Test
    @DisplayName("Empty builder produces all-zero record")
    void emptyBuilderAllZeros() {
      NetworkStats s = new NetworkStats.Builder().build();
      assertAll(
          () -> assertEquals(0, s.currentTick()),
          () -> assertEquals(0, s.totalRouters()),
          () -> assertEquals(0, s.totalTerminals()),
          () -> assertEquals(0, s.packetsGenerated()),
          () -> assertEquals(0, s.packetsSent()),
          () -> assertEquals(0, s.packetsDelivered()),
          () -> assertEquals(0, s.packetsDropped()),
          () -> assertEquals(0, s.packetsTimedOut()),
          () -> assertEquals(0, s.packetsInFlight()),
          () -> assertEquals(0, s.pagesCreated()),
          () -> assertEquals(0, s.pagesCompleted()),
          () -> assertEquals(0, s.pagesDropped()),
          () -> assertEquals(0, s.pagesTimedOut()));
    }

    @Test
    @DisplayName("Additive methods accumulate correctly")
    void additiveMethods() {
      NetworkStats s =
          new NetworkStats.Builder()
              .currentTick(10)
              .totalRouters(3)
              .addTerminals(5)
              .addTerminals(3)
              .addPacketsGenerated(200)
              .addPacketsSent(100)
              .addPacketsSent(50)
              .addPacketsDelivered(120)
              .addPacketsDropped(20)
              .addPacketsTimedOut(10)
              .addPacketsInFlight(5)
              .addPagesCreated(30)
              .addPagesCompleted(25)
              .addPagesDropped(3)
              .addPagesTimedOut(2)
              .build();

      assertAll(
          () -> assertEquals(10, s.currentTick()),
          () -> assertEquals(3, s.totalRouters()),
          () -> assertEquals(8, s.totalTerminals()),
          () -> assertEquals(200, s.packetsGenerated()),
          () -> assertEquals(150, s.packetsSent()),
          () -> assertEquals(120, s.packetsDelivered()),
          () -> assertEquals(20, s.packetsDropped()),
          () -> assertEquals(10, s.packetsTimedOut()),
          () -> assertEquals(5, s.packetsInFlight()),
          () -> assertEquals(30, s.pagesCreated()),
          () -> assertEquals(25, s.pagesCompleted()),
          () -> assertEquals(3, s.pagesDropped()),
          () -> assertEquals(2, s.pagesTimedOut()));
    }

    @Test
    @DisplayName("build() can be called multiple times producing independent records")
    void buildIsIdempotent() {
      NetworkStats.Builder b = new NetworkStats.Builder().addPacketsSent(10);
      NetworkStats s1 = b.build();
      b.addPacketsSent(5);
      NetworkStats s2 = b.build();

      assertEquals(10, s1.packetsSent());
      assertEquals(15, s2.packetsSent());
    }
  }

  @Nested
  @DisplayName("deliveryRate()")
  class DeliveryRateTests {

    @Test
    @DisplayName("Returns 0 when nothing was sent")
    void zeroWhenNothingSent() {
      NetworkStats s = new NetworkStats.Builder().build();
      assertEquals(0f, s.deliveryRate());
    }

    @Test
    @DisplayName("Returns 1.0 when all sent packets were delivered")
    void oneWhenAllDelivered() {
      NetworkStats s =
          new NetworkStats.Builder().addPacketsSent(100).addPacketsDelivered(100).build();
      assertEquals(1.0f, s.deliveryRate(), 1e-6f);
    }

    @Test
    @DisplayName("Returns correct fraction for partial delivery")
    void partialDelivery() {
      NetworkStats s =
          new NetworkStats.Builder().addPacketsSent(200).addPacketsDelivered(150).build();
      assertEquals(0.75f, s.deliveryRate(), 1e-6f);
    }
  }

  @Nested
  @DisplayName("dropRate()")
  class DropRateTests {

    @Test
    @DisplayName("Returns 0 when nothing was sent")
    void zeroWhenNothingSent() {
      NetworkStats s = new NetworkStats.Builder().build();
      assertEquals(0f, s.dropRate());
    }

    @Test
    @DisplayName("Returns correct fraction for partial drops")
    void partialDrops() {
      NetworkStats s = new NetworkStats.Builder().addPacketsSent(100).addPacketsDropped(25).build();
      assertEquals(0.25f, s.dropRate(), 1e-6f);
    }

    @Test
    @DisplayName("deliveryRate + dropRate can exceed 1.0 (timed-out packets are separate)")
    void ratesAreIndependent() {
      NetworkStats s =
          new NetworkStats.Builder()
              .addPacketsSent(100)
              .addPacketsDelivered(60)
              .addPacketsDropped(30)
              .build();
      assertEquals(0.60f, s.deliveryRate(), 1e-6f);
      assertEquals(0.30f, s.dropRate(), 1e-6f);
    }
  }

  @Nested
  @DisplayName("successRate()")
  class SuccessRateTests {

    @Test
    @DisplayName("Returns 0 when no pages finished")
    void zeroWhenNoPagesFinished() {
      NetworkStats s = new NetworkStats.Builder().build();
      assertEquals(0f, s.successRate());
    }

    @Test
    @DisplayName("Returns 1.0 when all finished pages completed")
    void oneWhenAllCompleted() {
      NetworkStats s = new NetworkStats.Builder().addPagesCompleted(50).build();
      assertEquals(1.0f, s.successRate(), 1e-6f);
    }

    @Test
    @DisplayName("Counts completed / (completed + dropped + timedOut)")
    void mixedOutcomes() {
      NetworkStats s =
          new NetworkStats.Builder()
              .addPagesCompleted(6)
              .addPagesDropped(2)
              .addPagesTimedOut(2)
              .build();
      assertEquals(0.6f, s.successRate(), 1e-6f);
    }

    @Test
    @DisplayName("pagesCreated does not count as a finished page")
    void createdDoesNotInfluenceSuccessRate() {
      NetworkStats s =
          new NetworkStats.Builder().addPagesCreated(100).addPagesCompleted(10).build();
      assertEquals(1.0f, s.successRate(), 1e-6f);
    }
  }
}
