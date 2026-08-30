package com.omb9.glucosehero.data.security;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class KeystoreManager_Factory implements Factory<KeystoreManager> {
  @Override
  public KeystoreManager get() {
    return newInstance();
  }

  public static KeystoreManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static KeystoreManager newInstance() {
    return new KeystoreManager();
  }

  private static final class InstanceHolder {
    static final KeystoreManager_Factory INSTANCE = new KeystoreManager_Factory();
  }
}
