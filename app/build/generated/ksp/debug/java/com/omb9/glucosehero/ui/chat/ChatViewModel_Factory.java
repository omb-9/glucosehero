package com.omb9.glucosehero.ui.chat;

import com.omb9.glucosehero.domain.repository.ChatRepository;
import com.omb9.glucosehero.domain.repository.SettingsRepository;
import com.omb9.glucosehero.ui.log.HeroAiPrefillCoordinator;
import com.omb9.glucosehero.work.ConnectivityObserver;
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
public final class ChatViewModel_Factory implements Factory<ChatViewModel> {
  private final Provider<ChatRepository> chatRepositoryProvider;

  private final Provider<ConnectivityObserver> connectivityObserverProvider;

  private final Provider<HeroAiPrefillCoordinator> heroAiPrefillCoordinatorProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private ChatViewModel_Factory(Provider<ChatRepository> chatRepositoryProvider,
      Provider<ConnectivityObserver> connectivityObserverProvider,
      Provider<HeroAiPrefillCoordinator> heroAiPrefillCoordinatorProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    this.chatRepositoryProvider = chatRepositoryProvider;
    this.connectivityObserverProvider = connectivityObserverProvider;
    this.heroAiPrefillCoordinatorProvider = heroAiPrefillCoordinatorProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public ChatViewModel get() {
    return newInstance(chatRepositoryProvider.get(), connectivityObserverProvider.get(), heroAiPrefillCoordinatorProvider.get(), settingsRepositoryProvider.get());
  }

  public static ChatViewModel_Factory create(Provider<ChatRepository> chatRepositoryProvider,
      Provider<ConnectivityObserver> connectivityObserverProvider,
      Provider<HeroAiPrefillCoordinator> heroAiPrefillCoordinatorProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new ChatViewModel_Factory(chatRepositoryProvider, connectivityObserverProvider, heroAiPrefillCoordinatorProvider, settingsRepositoryProvider);
  }

  public static ChatViewModel newInstance(ChatRepository chatRepository,
      ConnectivityObserver connectivityObserver, HeroAiPrefillCoordinator heroAiPrefillCoordinator,
      SettingsRepository settingsRepository) {
    return new ChatViewModel(chatRepository, connectivityObserver, heroAiPrefillCoordinator, settingsRepository);
  }
}
