package com.omb9.glucosehero.data.remote;

import com.omb9.glucosehero.domain.repository.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class DynamicApiInterceptor_Factory implements Factory<DynamicApiInterceptor> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private DynamicApiInterceptor_Factory(Provider<SettingsRepository> settingsRepositoryProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public DynamicApiInterceptor get() {
    return newInstance(settingsRepositoryProvider.get());
  }

  public static DynamicApiInterceptor_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new DynamicApiInterceptor_Factory(settingsRepositoryProvider);
  }

  public static DynamicApiInterceptor newInstance(SettingsRepository settingsRepository) {
    return new DynamicApiInterceptor(settingsRepository);
  }
}
