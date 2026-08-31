package com.omb9.glucosehero.data.repository;

import com.omb9.glucosehero.data.local.db.SupplyDao;
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
public final class SupplyRepositoryImpl_Factory implements Factory<SupplyRepositoryImpl> {
  private final Provider<SupplyDao> supplyDaoProvider;

  private SupplyRepositoryImpl_Factory(Provider<SupplyDao> supplyDaoProvider) {
    this.supplyDaoProvider = supplyDaoProvider;
  }

  @Override
  public SupplyRepositoryImpl get() {
    return newInstance(supplyDaoProvider.get());
  }

  public static SupplyRepositoryImpl_Factory create(Provider<SupplyDao> supplyDaoProvider) {
    return new SupplyRepositoryImpl_Factory(supplyDaoProvider);
  }

  public static SupplyRepositoryImpl newInstance(SupplyDao supplyDao) {
    return new SupplyRepositoryImpl(supplyDao);
  }
}
