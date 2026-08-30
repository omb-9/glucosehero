package com.omb9.glucosehero.di;

import com.omb9.glucosehero.data.local.db.EntryDao;
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase;
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
public final class DatabaseModule_ProvideEntryDaoFactory implements Factory<EntryDao> {
  private final Provider<GlucoseHeroDatabase> dbProvider;

  private DatabaseModule_ProvideEntryDaoFactory(Provider<GlucoseHeroDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public EntryDao get() {
    return provideEntryDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideEntryDaoFactory create(
      Provider<GlucoseHeroDatabase> dbProvider) {
    return new DatabaseModule_ProvideEntryDaoFactory(dbProvider);
  }

  public static EntryDao provideEntryDao(GlucoseHeroDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideEntryDao(db));
  }
}
