package com.omb9.glucosehero.work;

import android.content.Context;
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
public final class InsightNotifier_Factory implements Factory<InsightNotifier> {
  private final Provider<Context> contextProvider;

  private InsightNotifier_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public InsightNotifier get() {
    return newInstance(contextProvider.get());
  }

  public static InsightNotifier_Factory create(Provider<Context> contextProvider) {
    return new InsightNotifier_Factory(contextProvider);
  }

  public static InsightNotifier newInstance(Context context) {
    return new InsightNotifier(context);
  }
}
