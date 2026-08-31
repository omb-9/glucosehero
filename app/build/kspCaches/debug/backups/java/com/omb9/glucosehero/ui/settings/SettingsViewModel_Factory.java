package com.omb9.glucosehero.ui.settings;

import com.omb9.glucosehero.data.billing.BillingRepository;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<BillingRepository> billingRepositoryProvider;

  private SettingsViewModel_Factory(Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<BillingRepository> billingRepositoryProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.billingRepositoryProvider = billingRepositoryProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(settingsRepositoryProvider.get(), billingRepositoryProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<BillingRepository> billingRepositoryProvider) {
    return new SettingsViewModel_Factory(settingsRepositoryProvider, billingRepositoryProvider);
  }

  public static SettingsViewModel newInstance(SettingsRepository settingsRepository,
      BillingRepository billingRepository) {
    return new SettingsViewModel(settingsRepository, billingRepository);
  }
}
