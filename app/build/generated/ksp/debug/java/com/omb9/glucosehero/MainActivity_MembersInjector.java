package com.omb9.glucosehero;

import com.omb9.glucosehero.domain.repository.SettingsRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private MainActivity_MembersInjector(Provider<SettingsRepository> settingsRepositoryProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectSettingsRepository(instance, settingsRepositoryProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new MainActivity_MembersInjector(settingsRepositoryProvider);
  }

  @InjectedFieldSignature("com.omb9.glucosehero.MainActivity.settingsRepository")
  public static void injectSettingsRepository(MainActivity instance,
      SettingsRepository settingsRepository) {
    instance.settingsRepository = settingsRepository;
  }
}
