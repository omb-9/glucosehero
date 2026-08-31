package com.omb9.glucosehero.ui.glance;

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
public final class WidgetRefresher_Factory implements Factory<WidgetRefresher> {
  private final Provider<Context> contextProvider;

  private WidgetRefresher_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public WidgetRefresher get() {
    return newInstance(contextProvider.get());
  }

  public static WidgetRefresher_Factory create(Provider<Context> contextProvider) {
    return new WidgetRefresher_Factory(contextProvider);
  }

  public static WidgetRefresher newInstance(Context context) {
    return new WidgetRefresher(context);
  }
}
