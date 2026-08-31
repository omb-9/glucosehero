package com.omb9.glucosehero.data.repository;

import com.omb9.glucosehero.data.local.db.EntryDao;
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
public final class EntryRepositoryImpl_Factory implements Factory<EntryRepositoryImpl> {
  private final Provider<EntryDao> entryDaoProvider;

  private EntryRepositoryImpl_Factory(Provider<EntryDao> entryDaoProvider) {
    this.entryDaoProvider = entryDaoProvider;
  }

  @Override
  public EntryRepositoryImpl get() {
    return newInstance(entryDaoProvider.get());
  }

  public static EntryRepositoryImpl_Factory create(Provider<EntryDao> entryDaoProvider) {
    return new EntryRepositoryImpl_Factory(entryDaoProvider);
  }

  public static EntryRepositoryImpl newInstance(EntryDao entryDao) {
    return new EntryRepositoryImpl(entryDao);
  }
}
