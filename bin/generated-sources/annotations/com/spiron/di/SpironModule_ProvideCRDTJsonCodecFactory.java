package com.spiron.di;

import com.spiron.serialization.CRDTJsonCodec;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
    "cast"
})
public final class SpironModule_ProvideCRDTJsonCodecFactory implements Factory<CRDTJsonCodec> {
  private final SpironModule module;

  public SpironModule_ProvideCRDTJsonCodecFactory(SpironModule module) {
    this.module = module;
  }

  @Override
  public CRDTJsonCodec get() {
    return provideCRDTJsonCodec(module);
  }

  public static SpironModule_ProvideCRDTJsonCodecFactory create(SpironModule module) {
    return new SpironModule_ProvideCRDTJsonCodecFactory(module);
  }

  public static CRDTJsonCodec provideCRDTJsonCodec(SpironModule instance) {
    return Preconditions.checkNotNullFromProvides(instance.provideCRDTJsonCodec());
  }
}
