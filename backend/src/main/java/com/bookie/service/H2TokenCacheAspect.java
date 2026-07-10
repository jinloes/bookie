package com.bookie.service;

import com.bookie.model.OutlookToken;
import com.bookie.repository.OutlookTokenRepository;
import com.microsoft.aad.msal4j.ITokenCacheAccessAspect;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;

/**
 * Persists the MSAL4J token cache to H2 after each update, and reloads it before each access. This
 * allows the app to survive restarts without requiring re-authentication.
 */
class H2TokenCacheAspect implements ITokenCacheAccessAspect {

  private static final long CACHE_ID = 1L;
  private final OutlookTokenRepository repository;
  private final TokenCacheCrypto tokenCacheCrypto;

  H2TokenCacheAspect(OutlookTokenRepository repository, TokenCacheCrypto tokenCacheCrypto) {
    this.repository = repository;
    this.tokenCacheCrypto = tokenCacheCrypto;
  }

  @Override
  public void beforeCacheAccess(ITokenCacheAccessContext ctx) {
    repository
        .findById(CACHE_ID)
        .ifPresent(
            token -> {
              String decrypted = tokenCacheCrypto.decrypt(token.getCacheData());
              ctx.tokenCache().deserialize(decrypted);
            });
  }

  @Override
  public void afterCacheAccess(ITokenCacheAccessContext ctx) {
    if (ctx.hasCacheChanged()) {
      String encrypted = tokenCacheCrypto.encrypt(ctx.tokenCache().serialize());
      repository.save(new OutlookToken(CACHE_ID, encrypted));
    }
  }
}
