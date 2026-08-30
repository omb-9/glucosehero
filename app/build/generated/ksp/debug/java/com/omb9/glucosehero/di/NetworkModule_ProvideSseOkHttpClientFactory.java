package com.omb9.glucosehero.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class NetworkModule_ProvideSseOkHttpClientFactory implements Factory<OkHttpClient> {
  @Override
  public OkHttpClient get() {
    return provideSseOkHttpClient();
  }

  public static NetworkModule_ProvideSseOkHttpClientFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static OkHttpClient provideSseOkHttpClient() {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideSseOkHttpClient());
  }

  private static final class InstanceHolder {
    static final NetworkModule_ProvideSseOkHttpClientFactory INSTANCE = new NetworkModule_ProvideSseOkHttpClientFactory();
  }
}
