package kz.zeroops.worker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
class WorkerBoundaryTest {
  private final WorkerBoundary boundary = new WorkerBoundary();
  @Test void onlyAcceptsVerifiedAllowlistedWork() { assertTrue(boundary.acceptsSignedAllowlistedManifest(true, true)); assertFalse(boundary.acceptsSignedAllowlistedManifest(false, true)); }
}
