package com.omb9.glucosehero.ui.entrydetail;

import androidx.lifecycle.SavedStateHandle;
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
public final class EntryDetailViewModel_Factory implements Factory<EntryDetailViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<EntryRepository> entryRepositoryProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private EntryDetailViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.entryRepositoryProvider = entryRepositoryProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public EntryDetailViewModel get() {
    return newInstance(savedStateHandleProvider.get(), entryRepositoryProvider.get(), settingsRepositoryProvider.get());
  }

  public static EntryDetailViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new EntryDetailViewModel_Factory(savedStateHandleProvider, entryRepositoryProvider, settingsRepositoryProvider);
  }

  public static EntryDetailViewModel newInstance(SavedStateHandle savedStateHandle,
      EntryRepository entryRepository, SettingsRepository settingsRepository) {
    return new EntryDetailViewModel(savedStateHandle, entryRepository, settingsRepository);
  }
}
