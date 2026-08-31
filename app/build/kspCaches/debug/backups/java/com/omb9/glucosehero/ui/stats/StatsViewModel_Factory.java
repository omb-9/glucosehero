package com.omb9.glucosehero.ui.stats;

import com.omb9.glucosehero.data.export.ExportManager;
import com.omb9.glucosehero.domain.repository.EntryRepository;
import com.omb9.glucosehero.domain.repository.SettingsRepository;
import com.omb9.glucosehero.domain.repository.SupplyRepository;
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

  private final Provider<SupplyRepository> supplyRepositoryProvider;

  private final Provider<ExportManager> exportManagerProvider;

  private StatsViewModel_Factory(Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<SupplyRepository> supplyRepositoryProvider,
      Provider<ExportManager> exportManagerProvider) {
    this.entryRepositoryProvider = entryRepositoryProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.supplyRepositoryProvider = supplyRepositoryProvider;
    this.exportManagerProvider = exportManagerProvider;
  }

  @Override
  public StatsViewModel get() {
    return newInstance(entryRepositoryProvider.get(), settingsRepositoryProvider.get(), supplyRepositoryProvider.get(), exportManagerProvider.get());
  }

  public static StatsViewModel_Factory create(Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<SupplyRepository> supplyRepositoryProvider,
      Provider<ExportManager> exportManagerProvider) {
    return new StatsViewModel_Factory(entryRepositoryProvider, settingsRepositoryProvider, supplyRepositoryProvider, exportManagerProvider);
  }

  public static StatsViewModel newInstance(EntryRepository entryRepository,
      SettingsRepository settingsRepository, SupplyRepository supplyRepository,
      ExportManager exportManager) {
    return new StatsViewModel(entryRepository, settingsRepository, supplyRepository, exportManager);
  }
}
