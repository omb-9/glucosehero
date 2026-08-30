package com.omb9.glucosehero;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore;
import com.omb9.glucosehero.data.local.db.ChatMessageDao;
import com.omb9.glucosehero.data.local.db.EntryDao;
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase;
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao;
import com.omb9.glucosehero.data.remote.AiApi;
import com.omb9.glucosehero.data.remote.DynamicApiInterceptor;
import com.omb9.glucosehero.data.remote.sse.SseChatClient;
import com.omb9.glucosehero.data.repository.ChatRepositoryImpl;
import com.omb9.glucosehero.data.repository.EntryRepositoryImpl;
import com.omb9.glucosehero.data.repository.SettingsRepositoryImpl;
import com.omb9.glucosehero.data.security.KeystoreManager;
import com.omb9.glucosehero.di.DatabaseModule_ProvideChatMessageDaoFactory;
import com.omb9.glucosehero.di.DatabaseModule_ProvideDatabaseFactory;
import com.omb9.glucosehero.di.DatabaseModule_ProvideEntryDaoFactory;
import com.omb9.glucosehero.di.DatabaseModule_ProvidePendingAiQueryDaoFactory;
import com.omb9.glucosehero.di.NetworkModule_ProvideAiApiFactory;
import com.omb9.glucosehero.di.NetworkModule_ProvideOkHttpClientFactory;
import com.omb9.glucosehero.di.NetworkModule_ProvideRetrofitFactory;
import com.omb9.glucosehero.di.NetworkModule_ProvideSseOkHttpClientFactory;
import com.omb9.glucosehero.ui.chat.ChatViewModel;
import com.omb9.glucosehero.ui.chat.ChatViewModel_HiltModules;
import com.omb9.glucosehero.ui.chat.ChatViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.omb9.glucosehero.ui.chat.ChatViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.omb9.glucosehero.ui.entrydetail.EntryDetailViewModel;
import com.omb9.glucosehero.ui.entrydetail.EntryDetailViewModel_HiltModules;
import com.omb9.glucosehero.ui.entrydetail.EntryDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.omb9.glucosehero.ui.entrydetail.EntryDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.omb9.glucosehero.ui.log.HeroAiPrefillCoordinator;
import com.omb9.glucosehero.ui.log.LogViewModel;
import com.omb9.glucosehero.ui.log.LogViewModel_HiltModules;
import com.omb9.glucosehero.ui.log.LogViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.omb9.glucosehero.ui.log.LogViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.omb9.glucosehero.ui.settings.SettingsViewModel;
import com.omb9.glucosehero.ui.settings.SettingsViewModel_HiltModules;
import com.omb9.glucosehero.ui.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.omb9.glucosehero.ui.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.omb9.glucosehero.ui.stats.StatsViewModel;
import com.omb9.glucosehero.ui.stats.StatsViewModel_HiltModules;
import com.omb9.glucosehero.ui.stats.StatsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.omb9.glucosehero.ui.stats.StatsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.omb9.glucosehero.work.ConnectivityObserver;
import com.omb9.glucosehero.work.InsightNotifier;
import com.omb9.glucosehero.work.PendingQueryWorker;
import com.omb9.glucosehero.work.PendingQueryWorker_AssistedFactory;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

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
public final class DaggerGlucoseHeroApp_HiltComponents_SingletonC {
  private DaggerGlucoseHeroApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public GlucoseHeroApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements GlucoseHeroApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public GlucoseHeroApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements GlucoseHeroApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public GlucoseHeroApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements GlucoseHeroApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public GlucoseHeroApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements GlucoseHeroApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public GlucoseHeroApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements GlucoseHeroApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public GlucoseHeroApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements GlucoseHeroApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public GlucoseHeroApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements GlucoseHeroApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public GlucoseHeroApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends GlucoseHeroApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends GlucoseHeroApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends GlucoseHeroApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends GlucoseHeroApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    Map keySetMapOfClassOfObjectAndBooleanBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, Boolean>newMapBuilder(5);
      mapBuilder.put(ChatViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ChatViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(EntryDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EntryDetailViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(LogViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, LogViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(StatsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, StatsViewModel_HiltModules.KeyModule.provide());
      return mapBuilder.build();
    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(keySetMapOfClassOfObjectAndBooleanBuilder());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectSettingsRepository(instance, singletonCImpl.settingsRepositoryImplProvider.get());
      return instance;
    }
  }

  private static final class ViewModelCImpl extends GlucoseHeroApp_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<ChatViewModel> chatViewModelProvider;

    Provider<EntryDetailViewModel> entryDetailViewModelProvider;

    Provider<LogViewModel> logViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    Provider<StatsViewModel> statsViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    Map hiltViewModelMapMapOfClassOfObjectAndProviderOfViewModelBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(5);
      mapBuilder.put(ChatViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (chatViewModelProvider)));
      mapBuilder.put(EntryDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (entryDetailViewModelProvider)));
      mapBuilder.put(LogViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (logViewModelProvider)));
      mapBuilder.put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider)));
      mapBuilder.put(StatsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (statsViewModelProvider)));
      return mapBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.chatViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.entryDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.logViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.statsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(hiltViewModelMapMapOfClassOfObjectAndProviderOfViewModelBuilder());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.omb9.glucosehero.ui.chat.ChatViewModel
          return (T) new ChatViewModel(singletonCImpl.chatRepositoryImplProvider.get(), singletonCImpl.connectivityObserverProvider.get(), singletonCImpl.heroAiPrefillCoordinatorProvider.get(), singletonCImpl.settingsRepositoryImplProvider.get());

          case 1: // com.omb9.glucosehero.ui.entrydetail.EntryDetailViewModel
          return (T) new EntryDetailViewModel(viewModelCImpl.savedStateHandle, singletonCImpl.entryRepositoryImplProvider.get(), singletonCImpl.settingsRepositoryImplProvider.get());

          case 2: // com.omb9.glucosehero.ui.log.LogViewModel
          return (T) new LogViewModel(singletonCImpl.entryRepositoryImplProvider.get(), singletonCImpl.settingsRepositoryImplProvider.get(), singletonCImpl.heroAiPrefillCoordinatorProvider.get());

          case 3: // com.omb9.glucosehero.ui.settings.SettingsViewModel
          return (T) new SettingsViewModel(singletonCImpl.settingsRepositoryImplProvider.get());

          case 4: // com.omb9.glucosehero.ui.stats.StatsViewModel
          return (T) new StatsViewModel(singletonCImpl.entryRepositoryImplProvider.get(), singletonCImpl.settingsRepositoryImplProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends GlucoseHeroApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends GlucoseHeroApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends GlucoseHeroApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<GlucoseHeroDatabase> provideDatabaseProvider;

    Provider<SettingsDataStore> settingsDataStoreProvider;

    Provider<KeystoreManager> keystoreManagerProvider;

    Provider<SettingsRepositoryImpl> settingsRepositoryImplProvider;

    Provider<OkHttpClient> provideSseOkHttpClientProvider;

    Provider<SseChatClient> sseChatClientProvider;

    Provider<DynamicApiInterceptor> dynamicApiInterceptorProvider;

    Provider<OkHttpClient> provideOkHttpClientProvider;

    Provider<Retrofit> provideRetrofitProvider;

    Provider<AiApi> provideAiApiProvider;

    Provider<ChatRepositoryImpl> chatRepositoryImplProvider;

    Provider<InsightNotifier> insightNotifierProvider;

    Provider<PendingQueryWorker_AssistedFactory> pendingQueryWorker_AssistedFactoryProvider;

    Provider<ConnectivityObserver> connectivityObserverProvider;

    Provider<HeroAiPrefillCoordinator> heroAiPrefillCoordinatorProvider;

    Provider<EntryRepositoryImpl> entryRepositoryImplProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    PendingAiQueryDao pendingAiQueryDao() {
      return DatabaseModule_ProvidePendingAiQueryDaoFactory.providePendingAiQueryDao(provideDatabaseProvider.get());
    }

    ChatMessageDao chatMessageDao() {
      return DatabaseModule_ProvideChatMessageDaoFactory.provideChatMessageDao(provideDatabaseProvider.get());
    }

    EntryDao entryDao() {
      return DatabaseModule_ProvideEntryDaoFactory.provideEntryDao(provideDatabaseProvider.get());
    }

    Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOfListenableWorker(
        ) {
      return Collections.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>singletonMap("com.omb9.glucosehero.work.PendingQueryWorker", ((Provider) (pendingQueryWorker_AssistedFactoryProvider)));
    }

    HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOfListenableWorker());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<GlucoseHeroDatabase>(singletonCImpl, 1));
      this.settingsDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<SettingsDataStore>(singletonCImpl, 4));
      this.keystoreManagerProvider = DoubleCheck.provider(new SwitchingProvider<KeystoreManager>(singletonCImpl, 5));
      this.settingsRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<SettingsRepositoryImpl>(singletonCImpl, 3));
      this.provideSseOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 7));
      this.sseChatClientProvider = DoubleCheck.provider(new SwitchingProvider<SseChatClient>(singletonCImpl, 6));
      this.dynamicApiInterceptorProvider = DoubleCheck.provider(new SwitchingProvider<DynamicApiInterceptor>(singletonCImpl, 11));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 10));
      this.provideRetrofitProvider = DoubleCheck.provider(new SwitchingProvider<Retrofit>(singletonCImpl, 9));
      this.provideAiApiProvider = DoubleCheck.provider(new SwitchingProvider<AiApi>(singletonCImpl, 8));
      this.chatRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ChatRepositoryImpl>(singletonCImpl, 2));
      this.insightNotifierProvider = DoubleCheck.provider(new SwitchingProvider<InsightNotifier>(singletonCImpl, 12));
      this.pendingQueryWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<PendingQueryWorker_AssistedFactory>(singletonCImpl, 0));
      this.connectivityObserverProvider = DoubleCheck.provider(new SwitchingProvider<ConnectivityObserver>(singletonCImpl, 13));
      this.heroAiPrefillCoordinatorProvider = DoubleCheck.provider(new SwitchingProvider<HeroAiPrefillCoordinator>(singletonCImpl, 14));
      this.entryRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<EntryRepositoryImpl>(singletonCImpl, 15));
    }

    @Override
    public void injectGlucoseHeroApp(GlucoseHeroApp glucoseHeroApp) {
      injectGlucoseHeroApp2(glucoseHeroApp);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private GlucoseHeroApp injectGlucoseHeroApp2(GlucoseHeroApp instance) {
      GlucoseHeroApp_MembersInjector.injectWorkerFactory(instance, hiltWorkerFactory());
      GlucoseHeroApp_MembersInjector.injectInsightNotifier(instance, insightNotifierProvider.get());
      return instance;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.omb9.glucosehero.work.PendingQueryWorker_AssistedFactory
          return (T) new PendingQueryWorker_AssistedFactory() {
            @Override
            public PendingQueryWorker create(Context appContext, WorkerParameters workerParams) {
              return new PendingQueryWorker(appContext, workerParams, singletonCImpl.pendingAiQueryDao(), singletonCImpl.chatMessageDao(), singletonCImpl.chatRepositoryImplProvider.get(), singletonCImpl.insightNotifierProvider.get());
            }
          };

          case 1: // com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
          return (T) DatabaseModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 2: // com.omb9.glucosehero.data.repository.ChatRepositoryImpl
          return (T) new ChatRepositoryImpl(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.chatMessageDao(), singletonCImpl.pendingAiQueryDao(), singletonCImpl.entryDao(), singletonCImpl.settingsRepositoryImplProvider.get(), singletonCImpl.sseChatClientProvider.get(), singletonCImpl.provideAiApiProvider.get());

          case 3: // com.omb9.glucosehero.data.repository.SettingsRepositoryImpl
          return (T) new SettingsRepositoryImpl(singletonCImpl.settingsDataStoreProvider.get(), singletonCImpl.keystoreManagerProvider.get());

          case 4: // com.omb9.glucosehero.data.local.datastore.SettingsDataStore
          return (T) new SettingsDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 5: // com.omb9.glucosehero.data.security.KeystoreManager
          return (T) new KeystoreManager();

          case 6: // com.omb9.glucosehero.data.remote.sse.SseChatClient
          return (T) new SseChatClient(singletonCImpl.provideSseOkHttpClientProvider.get());

          case 7: // @javax.inject.Named("sse") okhttp3.OkHttpClient
          return (T) NetworkModule_ProvideSseOkHttpClientFactory.provideSseOkHttpClient();

          case 8: // com.omb9.glucosehero.data.remote.AiApi
          return (T) NetworkModule_ProvideAiApiFactory.provideAiApi(singletonCImpl.provideRetrofitProvider.get());

          case 9: // retrofit2.Retrofit
          return (T) NetworkModule_ProvideRetrofitFactory.provideRetrofit(singletonCImpl.provideOkHttpClientProvider.get());

          case 10: // okhttp3.OkHttpClient
          return (T) NetworkModule_ProvideOkHttpClientFactory.provideOkHttpClient(singletonCImpl.dynamicApiInterceptorProvider.get());

          case 11: // com.omb9.glucosehero.data.remote.DynamicApiInterceptor
          return (T) new DynamicApiInterceptor(singletonCImpl.settingsRepositoryImplProvider.get());

          case 12: // com.omb9.glucosehero.work.InsightNotifier
          return (T) new InsightNotifier(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 13: // com.omb9.glucosehero.work.ConnectivityObserver
          return (T) new ConnectivityObserver(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 14: // com.omb9.glucosehero.ui.log.HeroAiPrefillCoordinator
          return (T) new HeroAiPrefillCoordinator();

          case 15: // com.omb9.glucosehero.data.repository.EntryRepositoryImpl
          return (T) new EntryRepositoryImpl(singletonCImpl.entryDao());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
