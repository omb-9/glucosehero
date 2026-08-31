package com.omb9.glucosehero.data.remote.sse;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class SseChatClient_Factory implements Factory<SseChatClient> {
  private final Provider<OkHttpClient> clientProvider;

  private SseChatClient_Factory(Provider<OkHttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public SseChatClient get() {
    return newInstance(clientProvider.get());
  }

  public static SseChatClient_Factory create(Provider<OkHttpClient> clientProvider) {
    return new SseChatClient_Factory(clientProvider);
  }

  public static SseChatClient newInstance(OkHttpClient client) {
    return new SseChatClient(client);
  }
}
