package com.omb9.glucosehero.data.repository;

import android.content.Context;
import com.omb9.glucosehero.data.local.db.ChatMessageDao;
import com.omb9.glucosehero.data.local.db.EntryDao;
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao;
import com.omb9.glucosehero.data.remote.AiApi;
import com.omb9.glucosehero.data.remote.sse.SseChatClient;
import com.omb9.glucosehero.domain.repository.SettingsRepository;
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
public final class ChatRepositoryImpl_Factory implements Factory<ChatRepositoryImpl> {
  private final Provider<Context> contextProvider;

  private final Provider<ChatMessageDao> chatMessageDaoProvider;

  private final Provider<PendingAiQueryDao> pendingAiQueryDaoProvider;

  private final Provider<EntryDao> entryDaoProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<SseChatClient> sseChatClientProvider;

  private final Provider<AiApi> aiApiProvider;

  private ChatRepositoryImpl_Factory(Provider<Context> contextProvider,
      Provider<ChatMessageDao> chatMessageDaoProvider,
      Provider<PendingAiQueryDao> pendingAiQueryDaoProvider, Provider<EntryDao> entryDaoProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<SseChatClient> sseChatClientProvider, Provider<AiApi> aiApiProvider) {
    this.contextProvider = contextProvider;
    this.chatMessageDaoProvider = chatMessageDaoProvider;
    this.pendingAiQueryDaoProvider = pendingAiQueryDaoProvider;
    this.entryDaoProvider = entryDaoProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.sseChatClientProvider = sseChatClientProvider;
    this.aiApiProvider = aiApiProvider;
  }

  @Override
  public ChatRepositoryImpl get() {
    return newInstance(contextProvider.get(), chatMessageDaoProvider.get(), pendingAiQueryDaoProvider.get(), entryDaoProvider.get(), settingsRepositoryProvider.get(), sseChatClientProvider.get(), aiApiProvider.get());
  }

  public static ChatRepositoryImpl_Factory create(Provider<Context> contextProvider,
      Provider<ChatMessageDao> chatMessageDaoProvider,
      Provider<PendingAiQueryDao> pendingAiQueryDaoProvider, Provider<EntryDao> entryDaoProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<SseChatClient> sseChatClientProvider, Provider<AiApi> aiApiProvider) {
    return new ChatRepositoryImpl_Factory(contextProvider, chatMessageDaoProvider, pendingAiQueryDaoProvider, entryDaoProvider, settingsRepositoryProvider, sseChatClientProvider, aiApiProvider);
  }

  public static ChatRepositoryImpl newInstance(Context context, ChatMessageDao chatMessageDao,
      PendingAiQueryDao pendingAiQueryDao, EntryDao entryDao, SettingsRepository settingsRepository,
      SseChatClient sseChatClient, AiApi aiApi) {
    return new ChatRepositoryImpl(context, chatMessageDao, pendingAiQueryDao, entryDao, settingsRepository, sseChatClient, aiApi);
  }
}
