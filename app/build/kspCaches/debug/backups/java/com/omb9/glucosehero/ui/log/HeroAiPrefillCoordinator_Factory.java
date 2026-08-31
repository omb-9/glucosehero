package com.omb9.glucosehero.ui.log;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class HeroAiPrefillCoordinator_Factory implements Factory<HeroAiPrefillCoordinator> {
  @Override
  public HeroAiPrefillCoordinator get() {
    return newInstance();
  }

  public static HeroAiPrefillCoordinator_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static HeroAiPrefillCoordinator newInstance() {
    return new HeroAiPrefillCoordinator();
  }

  private static final class InstanceHolder {
    static final HeroAiPrefillCoordinator_Factory INSTANCE = new HeroAiPrefillCoordinator_Factory();
  }
}
