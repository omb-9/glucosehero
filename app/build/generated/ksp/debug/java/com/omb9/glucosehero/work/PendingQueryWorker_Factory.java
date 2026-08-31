package com.omb9.glucosehero.work;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.omb9.glucosehero.data.local.db.ChatMessageDao;
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao;
import com.omb9.glucosehero.domain.repository.ChatRepository;
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
public final class PendingQueryWorker_Factory {
  private final Provider<PendingAiQueryDao> pendingAiQueryDaoProvider;

  private final Provider<ChatMessageDao> chatMessageDaoProvider;

  private final Provider<ChatRepository> chatRepositoryProvider;

  private final Provider<InsightNotifier> insightNotifierProvider;

  private PendingQueryWorker_Factory(Provider<PendingAiQueryDao> pendingAiQueryDaoProvider,
      Provider<ChatMessageDao> chatMessageDaoProvider,
      Provider<ChatRepository> chatRepositoryProvider,
      Provider<InsightNotifier> insightNotifierProvider) {
    this.pendingAiQueryDaoProvider = pendingAiQueryDaoProvider;
    this.chatMessageDaoProvider = chatMessageDaoProvider;
    this.chatRepositoryProvider = chatRepositoryProvider;
    this.insightNotifierProvider = insightNotifierProvider;
  }

  public PendingQueryWorker get(Context appContext, WorkerParameters workerParams) {
    return newInstance(appContext, workerParams, pendingAiQueryDaoProvider.get(), chatMessageDaoProvider.get(), chatRepositoryProvider.get(), insightNotifierProvider.get());
  }

  public static PendingQueryWorker_Factory create(
      Provider<PendingAiQueryDao> pendingAiQueryDaoProvider,
      Provider<ChatMessageDao> chatMessageDaoProvider,
      Provider<ChatRepository> chatRepositoryProvider,
      Provider<InsightNotifier> insightNotifierProvider) {
    return new PendingQueryWorker_Factory(pendingAiQueryDaoProvider, chatMessageDaoProvider, chatRepositoryProvider, insightNotifierProvider);
  }

  public static PendingQueryWorker newInstance(Context appContext, WorkerParameters workerParams,
      PendingAiQueryDao pendingAiQueryDao, ChatMessageDao chatMessageDao,
      ChatRepository chatRepository, InsightNotifier insightNotifier) {
    return new PendingQueryWorker(appContext, workerParams, pendingAiQueryDao, chatMessageDao, chatRepository, insightNotifier);
  }
}
