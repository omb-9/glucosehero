package com.omb9.glucosehero.di;

import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase;
import com.omb9.glucosehero.data.local.db.SupplyDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideSupplyDaoFactory implements Factory<SupplyDao> {
  private final Provider<GlucoseHeroDatabase> dbProvider;

  private DatabaseModule_ProvideSupplyDaoFactory(Provider<GlucoseHeroDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public SupplyDao get() {
    return provideSupplyDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideSupplyDaoFactory create(
      Provider<GlucoseHeroDatabase> dbProvider) {
    return new DatabaseModule_ProvideSupplyDaoFactory(dbProvider);
  }

  public static SupplyDao provideSupplyDao(GlucoseHeroDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideSupplyDao(db));
  }
}
