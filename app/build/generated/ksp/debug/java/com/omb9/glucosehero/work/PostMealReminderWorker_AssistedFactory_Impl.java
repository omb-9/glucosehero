package com.omb9.glucosehero.work;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class PostMealReminderWorker_AssistedFactory_Impl implements PostMealReminderWorker_AssistedFactory {
  private final PostMealReminderWorker_Factory delegateFactory;

  PostMealReminderWorker_AssistedFactory_Impl(PostMealReminderWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public PostMealReminderWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<PostMealReminderWorker_AssistedFactory> create(
      PostMealReminderWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PostMealReminderWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<PostMealReminderWorker_AssistedFactory> createFactoryProvider(
      PostMealReminderWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PostMealReminderWorker_AssistedFactory_Impl(delegateFactory));
  }
}
