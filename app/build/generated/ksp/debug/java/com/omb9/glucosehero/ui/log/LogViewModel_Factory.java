package com.omb9.glucosehero.ui.log;

import com.omb9.glucosehero.domain.repository.EntryRepository;
import com.omb9.glucosehero.domain.repository.SettingsRepository;
import com.omb9.glucosehero.ui.glance.WidgetRefresher;
import com.omb9.glucosehero.work.ReminderScheduler;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class LogViewModel_Factory implements Factory<LogViewModel> {
  private final Provider<EntryRepository> entryRepositoryProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<HeroAiPrefillCoordinator> heroAiPrefillCoordinatorProvider;

  private final Provider<ReminderScheduler> reminderSchedulerProvider;

  private final Provider<WidgetRefresher> widgetRefresherProvider;

  private LogViewModel_Factory(Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<HeroAiPrefillCoordinator> heroAiPrefillCoordinatorProvider,
      Provider<ReminderScheduler> reminderSchedulerProvider,
      Provider<WidgetRefresher> widgetRefresherProvider) {
    this.entryRepositoryProvider = entryRepositoryProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.heroAiPrefillCoordinatorProvider = heroAiPrefillCoordinatorProvider;
    this.reminderSchedulerProvider = reminderSchedulerProvider;
    this.widgetRefresherProvider = widgetRefresherProvider;
  }

  @Override
  public LogViewModel get() {
    return newInstance(entryRepositoryProvider.get(), settingsRepositoryProvider.get(), heroAiPrefillCoordinatorProvider.get(), reminderSchedulerProvider.get(), widgetRefresherProvider.get());
  }

  public static LogViewModel_Factory create(Provider<EntryRepository> entryRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<HeroAiPrefillCoordinator> heroAiPrefillCoordinatorProvider,
      Provider<ReminderScheduler> reminderSchedulerProvider,
      Provider<WidgetRefresher> widgetRefresherProvider) {
    return new LogViewModel_Factory(entryRepositoryProvider, settingsRepositoryProvider, heroAiPrefillCoordinatorProvider, reminderSchedulerProvider, widgetRefresherProvider);
  }

  public static LogViewModel newInstance(EntryRepository entryRepository,
      SettingsRepository settingsRepository, HeroAiPrefillCoordinator heroAiPrefillCoordinator,
      ReminderScheduler reminderScheduler, WidgetRefresher widgetRefresher) {
    return new LogViewModel(entryRepository, settingsRepository, heroAiPrefillCoordinator, reminderScheduler, widgetRefresher);
  }
}
