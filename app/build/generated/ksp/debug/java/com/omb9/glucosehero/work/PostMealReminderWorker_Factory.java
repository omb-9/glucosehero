package com.omb9.glucosehero.work;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.omb9.glucosehero.domain.repository.SettingsRepository;
import dagger.internal.DaggerGenerated;
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
public final class PostMealReminderWorker_Factory {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<PostMealReminderNotifier> notifierProvider;

  private PostMealReminderWorker_Factory(Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<PostMealReminderNotifier> notifierProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.notifierProvider = notifierProvider;
  }

  public PostMealReminderWorker get(Context appContext, WorkerParameters workerParams) {
    return newInstance(appContext, workerParams, settingsRepositoryProvider.get(), notifierProvider.get());
  }

  public static PostMealReminderWorker_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<PostMealReminderNotifier> notifierProvider) {
    return new PostMealReminderWorker_Factory(settingsRepositoryProvider, notifierProvider);
  }

  public static PostMealReminderWorker newInstance(Context appContext,
      WorkerParameters workerParams, SettingsRepository settingsRepository,
      PostMealReminderNotifier notifier) {
    return new PostMealReminderWorker(appContext, workerParams, settingsRepository, notifier);
  }
}
