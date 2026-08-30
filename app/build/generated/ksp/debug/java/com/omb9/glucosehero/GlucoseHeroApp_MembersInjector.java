package com.omb9.glucosehero;

import androidx.hilt.work.HiltWorkerFactory;
import com.omb9.glucosehero.work.InsightNotifier;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class GlucoseHeroApp_MembersInjector implements MembersInjector<GlucoseHeroApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  private final Provider<InsightNotifier> insightNotifierProvider;

  private GlucoseHeroApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<InsightNotifier> insightNotifierProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
    this.insightNotifierProvider = insightNotifierProvider;
  }

  @Override
  public void injectMembers(GlucoseHeroApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
    injectInsightNotifier(instance, insightNotifierProvider.get());
  }

  public static MembersInjector<GlucoseHeroApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<InsightNotifier> insightNotifierProvider) {
    return new GlucoseHeroApp_MembersInjector(workerFactoryProvider, insightNotifierProvider);
  }

  @InjectedFieldSignature("com.omb9.glucosehero.GlucoseHeroApp.workerFactory")
  public static void injectWorkerFactory(GlucoseHeroApp instance, HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }

  @InjectedFieldSignature("com.omb9.glucosehero.GlucoseHeroApp.insightNotifier")
  public static void injectInsightNotifier(GlucoseHeroApp instance,
      InsightNotifier insightNotifier) {
    instance.insightNotifier = insightNotifier;
  }
}
