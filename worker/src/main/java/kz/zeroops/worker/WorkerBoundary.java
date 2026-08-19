package kz.zeroops.worker;

import org.springframework.stereotype.Component;

@Component
public class WorkerBoundary {
  /** Deployment execution is intentionally absent until a restricted server-agent protocol exists. */
  public boolean acceptsSignedAllowlistedManifest(boolean signatureValid, boolean allowlisted) {
    return signatureValid && allowlisted;
  }
}
