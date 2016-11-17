/**
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.react.testing;

import javax.annotation.Nullable;

import java.util.concurrent.Callable;

import android.app.Instrumentation;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.CatalystInstance;
import com.facebook.react.cxxbridge.CatalystInstanceImpl;
import com.facebook.react.cxxbridge.JSBundleLoader;
import com.facebook.react.cxxbridge.NativeModuleRegistry;
import com.facebook.react.cxxbridge.JSCJavaScriptExecutor;
import com.facebook.react.cxxbridge.JavaScriptExecutor;
import com.facebook.react.bridge.JavaScriptModuleRegistry;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.NativeModuleCallExceptionHandler;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.queue.ReactQueueConfigurationSpec;
import com.facebook.react.common.TestIdUtil;
import com.facebook.react.cxxbridge.CatalystInstanceImpl;
import com.facebook.react.cxxbridge.JSBundleLoader;
import com.facebook.react.cxxbridge.JSCJavaScriptExecutor;
import com.facebook.react.cxxbridge.JavaScriptExecutor;
import com.facebook.react.cxxbridge.NativeModuleRegistry;
import com.facebook.react.module.model.ReactModuleInfo;

import com.android.internal.util.Predicate;

public class ReactTestHelper {
  private static class DefaultReactTestFactory implements ReactTestFactory {
    private static class ReactInstanceEasyBuilderImpl implements ReactInstanceEasyBuilder {
      private @Nullable Context mContext;
      private final NativeModuleRegistry.Builder mNativeModuleRegistryBuilder =
        new NativeModuleRegistry.Builder();
      private final JavaScriptModuleRegistry.Builder mJSModuleRegistryBuilder =
        new JavaScriptModuleRegistry.Builder();

      @Override
      public ReactInstanceEasyBuilder setContext(Context context) {
        mContext = context;
        return this;
      }

      @Override
      public ReactInstanceEasyBuilder addNativeModule(NativeModule module) {
        mNativeModuleRegistryBuilder.add(module);
        return this;
      }

      @Override
      public ReactInstanceEasyBuilder addJSModule(Class moduleInterfaceClass) {
        mJSModuleRegistryBuilder.add(moduleInterfaceClass);
        return this;
      }

      @Override
      public CatalystInstance build() {
        JavaScriptExecutor executor = null;
        try {
          executor = new JSCJavaScriptExecutor.Factory(new WritableNativeMap()).create();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return new CatalystInstanceImpl.Builder()
          .setReactQueueConfigurationSpec(ReactQueueConfigurationSpec.createDefault())
          .setJSExecutor(executor)
          .setRegistry(mNativeModuleRegistryBuilder.build())
          .setJSModuleRegistry(mJSModuleRegistryBuilder.build())
          .setJSBundleLoader(JSBundleLoader.createAssetLoader(
                               mContext,
                               "assets://AndroidTestBundle.js"))
          .setNativeModuleCallExceptionHandler(
            new NativeModuleCallExceptionHandler() {
                @Override
                public void handleException(Exception e) {
                  throw new RuntimeException(e);
                }
            })
          .build();
      }
    }

    @Override
    public ReactInstanceEasyBuilder getCatalystInstanceBuilder() {
      return new ReactInstanceEasyBuilderImpl();
    }

    @Override
    public ReactInstanceManager.Builder getReactInstanceManagerBuilder() {
      return ReactInstanceManager.builder();
    }
  }

  public static ReactTestFactory getReactTestFactory() {
    Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    if (!(inst instanceof ReactTestFactory)) {
      return new DefaultReactTestFactory();
    }

    return (ReactTestFactory) inst;
  }

  public static ReactTestFactory.ReactInstanceEasyBuilder catalystInstanceBuilder(
      final ReactIntegrationTestCase testCase) {
    final ReactTestFactory.ReactInstanceEasyBuilder builder =
      getReactTestFactory().getCatalystInstanceBuilder();
    ReactTestFactory.ReactInstanceEasyBuilder postBuilder =
      new ReactTestFactory.ReactInstanceEasyBuilder() {
        @Override
        public ReactTestFactory.ReactInstanceEasyBuilder setContext(Context context) {
          builder.setContext(context);
          return this;
        }

        @Override
        public ReactTestFactory.ReactInstanceEasyBuilder addNativeModule(NativeModule module) {
          builder.addNativeModule(module);
          return this;
        }

        @Override
        public ReactTestFactory.ReactInstanceEasyBuilder addJSModule(Class moduleInterfaceClass) {
          builder.addJSModule(moduleInterfaceClass);
          return this;
        }

        @Override
        public CatalystInstance build() {
          final CatalystInstance instance = builder.build();
          try {
            instance.getReactQueueConfiguration().getJSQueueThread().callOnQueue(
              new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                  testCase.initializeWithInstance(instance);
                  instance.runJSBundle();
                  return null;
                }
              }).get();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
              @Override
              public void run() {
                instance.initialize();
              }
            });
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          testCase.waitForBridgeAndUIIdle();
          return instance;
        }
      };

    postBuilder.setContext(testCase.getContext());
    return postBuilder;
  }

  /**
   * Gets the view at given path in the UI hierarchy, ignoring modals.
   */
  public static <T extends View> T getViewAtPath(ViewGroup rootView, int... path) {
    // The application root element is wrapped in a helper view in order
    // to be able to display modals. See renderApplication.js.
    ViewGroup appWrapperView = rootView;
    View view = appWrapperView.getChildAt(0);
    for (int i = 0; i < path.length; i++) {
      view = ((ViewGroup) view).getChildAt(path[i]);
    }
    return (T) view;
  }

  /**
   * Gets the view with a given react test ID in the UI hierarchy. React test ID is currently
   * propagated into view content description.
   */
  public static View getViewWithReactTestId(View rootView, String testId) {
    return rootView.findViewById(TestIdUtil.getTestId(testId));
  }
}
