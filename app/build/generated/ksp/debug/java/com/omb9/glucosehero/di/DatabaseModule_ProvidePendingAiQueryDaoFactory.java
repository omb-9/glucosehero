package com.omb9.glucosehero.di;

import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase;
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao;
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
public final class DatabaseModule_ProvidePendingAiQueryDaoFactory implements Factory<PendingAiQueryDao> {
  private final Provider<GlucoseHeroDatabase> dbProvider;

  private DatabaseModule_ProvidePendingAiQueryDaoFactory(Provider<GlucoseHeroDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public PendingAiQueryDao get() {
    return providePendingAiQueryDao(dbProvider.get());
  }

  public static DatabaseModule_ProvidePendingAiQueryDaoFactory create(
      Provider<GlucoseHeroDatabase> dbProvider) {
    return new DatabaseModule_ProvidePendingAiQueryDaoFactory(dbProvider);
  }

  public static PendingAiQueryDao providePendingAiQueryDao(GlucoseHeroDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.providePendingAiQueryDao(db));
  }
}
