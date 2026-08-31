package com.omb9.glucosehero.work;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = PostMealReminderWorker.class
)
public interface PostMealReminderWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.omb9.glucosehero.work.PostMealReminderWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(
      PostMealReminderWorker_AssistedFactory factory);
}
