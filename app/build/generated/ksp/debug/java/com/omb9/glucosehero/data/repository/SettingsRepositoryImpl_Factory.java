package com.omb9.glucosehero.data.repository;

import com.omb9.glucosehero.data.local.datastore.SettingsDataStore;
import com.omb9.glucosehero.data.security.KeystoreManager;
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
public final class SettingsRepositoryImpl_Factory implements Factory<SettingsRepositoryImpl> {
  private final Provider<SettingsDataStore> dataStoreProvider;

  private final Provider<KeystoreManager> keystoreManagerProvider;

  private SettingsRepositoryImpl_Factory(Provider<SettingsDataStore> dataStoreProvider,
      Provider<KeystoreManager> keystoreManagerProvider) {
    this.dataStoreProvider = dataStoreProvider;
    this.keystoreManagerProvider = keystoreManagerProvider;
  }

  @Override
  public SettingsRepositoryImpl get() {
    return newInstance(dataStoreProvider.get(), keystoreManagerProvider.get());
  }

  public static SettingsRepositoryImpl_Factory create(Provider<SettingsDataStore> dataStoreProvider,
      Provider<KeystoreManager> keystoreManagerProvider) {
    return new SettingsRepositoryImpl_Factory(dataStoreProvider, keystoreManagerProvider);
  }

  public static SettingsRepositoryImpl newInstance(SettingsDataStore dataStore,
      KeystoreManager keystoreManager) {
    return new SettingsRepositoryImpl(dataStore, keystoreManager);
  }
}
