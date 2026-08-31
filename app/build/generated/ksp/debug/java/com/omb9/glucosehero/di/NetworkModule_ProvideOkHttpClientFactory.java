package com.omb9.glucosehero.di;

import com.omb9.glucosehero.data.remote.DynamicApiInterceptor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class NetworkModule_ProvideOkHttpClientFactory implements Factory<OkHttpClient> {
  private final Provider<DynamicApiInterceptor> dynamicApiInterceptorProvider;

  private NetworkModule_ProvideOkHttpClientFactory(
      Provider<DynamicApiInterceptor> dynamicApiInterceptorProvider) {
    this.dynamicApiInterceptorProvider = dynamicApiInterceptorProvider;
  }

  @Override
  public OkHttpClient get() {
    return provideOkHttpClient(dynamicApiInterceptorProvider.get());
  }

  public static NetworkModule_ProvideOkHttpClientFactory create(
      Provider<DynamicApiInterceptor> dynamicApiInterceptorProvider) {
    return new NetworkModule_ProvideOkHttpClientFactory(dynamicApiInterceptorProvider);
  }

  public static OkHttpClient provideOkHttpClient(DynamicApiInterceptor dynamicApiInterceptor) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideOkHttpClient(dynamicApiInterceptor));
  }
}
