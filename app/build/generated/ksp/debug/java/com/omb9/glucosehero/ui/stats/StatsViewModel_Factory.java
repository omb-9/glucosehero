package com.omb9.glucosehero.ui.stats;

import com.omb9.glucosehero.domain.repository.EntryRepository;
import com.omb9.glucosehero.domain.repository.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class StatsViewModel_Factory implements Factory<StatsViewModel> {
  private final Provider<EntryRepository> entryRepositoryProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private StatsViewModel_Factory(Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    this.entryRepositoryProvider = entryRepositoryProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public StatsViewModel get() {
    return newInstance(entryRepositoryProvider.get(), settingsRepositoryProvider.get());
  }

  public static StatsViewModel_Factory create(Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new StatsViewModel_Factory(entryRepositoryProvider, settingsRepositoryProvider);
  }

  public static StatsViewModel newInstance(EntryRepository entryRepository,
      SettingsRepository settingsRepository) {
    return new StatsViewModel(entryRepository, settingsRepository);
  }
}
