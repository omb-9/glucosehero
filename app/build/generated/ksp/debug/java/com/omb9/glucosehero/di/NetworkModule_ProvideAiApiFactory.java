package com.omb9.glucosehero.di;

import com.omb9.glucosehero.data.remote.AiApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import retrofit2.Retrofit;

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
public final class NetworkModule_ProvideAiApiFactory implements Factory<AiApi> {
  private final Provider<Retrofit> retrofitProvider;

  private NetworkModule_ProvideAiApiFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public AiApi get() {
    return provideAiApi(retrofitProvider.get());
  }

  public static NetworkModule_ProvideAiApiFactory create(Provider<Retrofit> retrofitProvider) {
    return new NetworkModule_ProvideAiApiFactory(retrofitProvider);
  }

  public static AiApi provideAiApi(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideAiApi(retrofit));
  }
}
