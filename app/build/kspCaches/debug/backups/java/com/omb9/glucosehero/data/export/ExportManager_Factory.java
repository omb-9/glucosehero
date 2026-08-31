package com.omb9.glucosehero.data.export;

import android.content.Context;
import com.omb9.glucosehero.domain.repository.EntryRepository;
import com.omb9.glucosehero.domain.repository.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ExportManager_Factory implements Factory<ExportManager> {
  private final Provider<Context> contextProvider;

  private final Provider<EntryRepository> entryRepositoryProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private ExportManager_Factory(Provider<Context> contextProvider,
      Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.entryRepositoryProvider = entryRepositoryProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public ExportManager get() {
    return newInstance(contextProvider.get(), entryRepositoryProvider.get(), settingsRepositoryProvider.get());
  }

  public static ExportManager_Factory create(Provider<Context> contextProvider,
      Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new ExportManager_Factory(contextProvider, entryRepositoryProvider, settingsRepositoryProvider);
  }

  public static ExportManager newInstance(Context context, EntryRepository entryRepository,
      SettingsRepository settingsRepository) {
    return new ExportManager(context, entryRepository, settingsRepository);
  }
}
