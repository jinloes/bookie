package com.bookie.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.OutlookToken;
import com.bookie.repository.OutlookTokenRepository;
import com.microsoft.aad.msal4j.ITokenCache;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class H2TokenCacheAspectTest {

  @Mock private OutlookTokenRepository repository;
  @Mock private TokenCacheCrypto tokenCacheCrypto;
  @Mock private ITokenCacheAccessContext context;
  @Mock private ITokenCache tokenCache;

  @Nested
  class BeforeCacheAccess {

    @Test
    void decryptsPersistedCacheBeforeDeserialize() {
      when(repository.findById(1L)).thenReturn(Optional.of(new OutlookToken(1L, "encrypted")));
      when(tokenCacheCrypto.decrypt("encrypted")).thenReturn("{\"cache\":\"json\"}");
      when(context.tokenCache()).thenReturn(tokenCache);

      H2TokenCacheAspect aspect = new H2TokenCacheAspect(repository, tokenCacheCrypto);
      aspect.beforeCacheAccess(context);

      verify(tokenCacheCrypto).decrypt("encrypted");
      verify(tokenCache).deserialize("{\"cache\":\"json\"}");
    }
  }

  @Nested
  class AfterCacheAccess {

    @Test
    void encryptsSerializedCacheBeforePersisting() {
      when(context.hasCacheChanged()).thenReturn(true);
      when(context.tokenCache()).thenReturn(tokenCache);
      when(tokenCache.serialize()).thenReturn("{\"cache\":\"json\"}");
      when(tokenCacheCrypto.encrypt("{\"cache\":\"json\"}")).thenReturn("enc:v1:payload");

      H2TokenCacheAspect aspect = new H2TokenCacheAspect(repository, tokenCacheCrypto);
      aspect.afterCacheAccess(context);

      ArgumentCaptor<OutlookToken> tokenCaptor = ArgumentCaptor.forClass(OutlookToken.class);
      verify(repository).save(tokenCaptor.capture());
      OutlookToken saved = tokenCaptor.getValue();
      org.assertj.core.api.Assertions.assertThat(saved.getId()).isEqualTo(1L);
      org.assertj.core.api.Assertions.assertThat(saved.getCacheData()).isEqualTo("enc:v1:payload");
    }

    @Test
    void skipsPersistWhenCacheUnchanged() {
      when(context.hasCacheChanged()).thenReturn(false);

      H2TokenCacheAspect aspect = new H2TokenCacheAspect(repository, tokenCacheCrypto);
      aspect.afterCacheAccess(context);

      verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
  }
}
