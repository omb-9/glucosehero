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
public final class PostMealReminderNotifier_Factory implements Factory<PostMealReminderNotifier> {
  private final Provider<Context> contextProvider;

  private PostMealReminderNotifier_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PostMealReminderNotifier get() {
    return newInstance(contextProvider.get());
  }

  public static PostMealReminderNotifier_Factory create(Provider<Context> contextProvider) {
    return new PostMealReminderNotifier_Factory(contextProvider);
  }

  public static PostMealReminderNotifier newInstance(Context context) {
    return new PostMealReminderNotifier(context);
  }
}
