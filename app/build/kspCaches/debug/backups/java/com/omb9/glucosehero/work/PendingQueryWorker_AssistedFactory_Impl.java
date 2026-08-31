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
public final class PendingQueryWorker_AssistedFactory_Impl implements PendingQueryWorker_AssistedFactory {
  private final PendingQueryWorker_Factory delegateFactory;

  PendingQueryWorker_AssistedFactory_Impl(PendingQueryWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public PendingQueryWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<PendingQueryWorker_AssistedFactory> create(
      PendingQueryWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PendingQueryWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<PendingQueryWorker_AssistedFactory> createFactoryProvider(
      PendingQueryWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PendingQueryWorker_AssistedFactory_Impl(delegateFactory));
  }
}
