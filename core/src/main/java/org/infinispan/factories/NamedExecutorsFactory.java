package org.infinispan.factories;

import static org.infinispan.factories.KnownComponentNames.ASYNC_NOTIFICATION_EXECUTOR;
import static org.infinispan.factories.KnownComponentNames.ASYNC_OPERATIONS_EXECUTOR;
import static org.infinispan.factories.KnownComponentNames.ASYNC_TRANSPORT_EXECUTOR;
import static org.infinispan.factories.KnownComponentNames.EXPIRATION_SCHEDULED_EXECUTOR;
import static org.infinispan.factories.KnownComponentNames.PERSISTENCE_EXECUTOR;
import static org.infinispan.factories.KnownComponentNames.REMOTE_COMMAND_EXECUTOR;
import static org.infinispan.factories.KnownComponentNames.STATE_TRANSFER_EXECUTOR;
import static org.infinispan.factories.KnownComponentNames.TIMEOUT_SCHEDULE_EXECUTOR;
import static org.infinispan.factories.KnownComponentNames.getDefaultThreadPrio;
import static org.infinispan.factories.KnownComponentNames.shortened;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import org.infinispan.commons.CacheConfigurationException;
import org.infinispan.commons.executors.BlockingThreadPoolExecutorFactory;
import org.infinispan.commons.executors.ScheduledThreadPoolExecutorFactory;
import org.infinispan.commons.executors.ThreadPoolExecutorFactory;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.ThreadPoolConfiguration;
import org.infinispan.executors.LazyInitializingBlockingTaskAwareExecutorService;
import org.infinispan.executors.LazyInitializingExecutorService;
import org.infinispan.executors.LazyInitializingScheduledExecutorService;
import org.infinispan.factories.annotations.DefaultFactoryFor;
import org.infinispan.factories.threads.DefaultNonBlockingThreadFactory;
import org.infinispan.factories.threads.DefaultThreadFactory;

/**
 * A factory that specifically knows how to create named executors.
 *
 * @author Manik Surtani
 * @author Pedro Ruivo
 * @since 4.0
 */
@DefaultFactoryFor(names = {ASYNC_TRANSPORT_EXECUTOR, ASYNC_NOTIFICATION_EXECUTOR, PERSISTENCE_EXECUTOR, ASYNC_OPERATIONS_EXECUTOR,
                             EXPIRATION_SCHEDULED_EXECUTOR, REMOTE_COMMAND_EXECUTOR, STATE_TRANSFER_EXECUTOR, TIMEOUT_SCHEDULE_EXECUTOR})
public class NamedExecutorsFactory extends AbstractComponentFactory implements AutoInstantiableFactory {
   @Override
   public Object construct(String componentName) {
      try {
         // Construction happens only on startup of either CacheManager, or Cache, so
         // using synchronized protection does not have a great impact on app performance.
         if (componentName.equals(ASYNC_NOTIFICATION_EXECUTOR)) {
            return createExecutorService(
                        globalConfiguration.listenerThreadPool(),
                        ASYNC_NOTIFICATION_EXECUTOR,
                        ExecutorServiceType.DEFAULT);
         } else if (componentName.equals(PERSISTENCE_EXECUTOR)) {
            return createExecutorService(
                        globalConfiguration.persistenceThreadPool(),
                        PERSISTENCE_EXECUTOR,
                        ExecutorServiceType.DEFAULT);
         } else if (componentName.equals(ASYNC_TRANSPORT_EXECUTOR)) {
            return createExecutorService(
                        globalConfiguration.transport().transportThreadPool(),
                        ASYNC_TRANSPORT_EXECUTOR,
                        ExecutorServiceType.DEFAULT);
         } else if (componentName.equals(EXPIRATION_SCHEDULED_EXECUTOR)) {
            return createExecutorService(
                        globalConfiguration.expirationThreadPool(),
                        EXPIRATION_SCHEDULED_EXECUTOR,
                        ExecutorServiceType.SCHEDULED);
         } else if (componentName.equals(REMOTE_COMMAND_EXECUTOR)) {
            return createExecutorService(
                        globalConfiguration.transport().remoteCommandThreadPool(),
                        REMOTE_COMMAND_EXECUTOR,
                        ExecutorServiceType.REMOTE_BLOCKING);
         } else if (componentName.equals(STATE_TRANSFER_EXECUTOR)) {
            return createExecutorService(
                        globalConfiguration.stateTransferThreadPool(),
                        STATE_TRANSFER_EXECUTOR,
                        ExecutorServiceType.DEFAULT);
         } else if (componentName.equals(ASYNC_OPERATIONS_EXECUTOR)) {
            return createExecutorService(
                        globalConfiguration.asyncThreadPool(),
                        ASYNC_OPERATIONS_EXECUTOR, ExecutorServiceType.NON_BLOCKING);
         } else if (componentName.endsWith(TIMEOUT_SCHEDULE_EXECUTOR)) {
            return createExecutorService(null, TIMEOUT_SCHEDULE_EXECUTOR, ExecutorServiceType.SCHEDULED);
         } else {
            throw new CacheConfigurationException("Unknown named executor " + componentName);
         }
      } catch (CacheConfigurationException ce) {
         throw ce;
      } catch (Exception e) {
         throw new CacheConfigurationException("Unable to instantiate ExecutorFactory for named component " + componentName, e);
      }
   }

   @SuppressWarnings("unchecked")
   private <T extends ExecutorService> T createExecutorService(ThreadPoolConfiguration threadPoolConfiguration,
                                                               String componentName, ExecutorServiceType type) {
      ThreadFactory threadFactory;
      ThreadPoolExecutorFactory executorFactory;
      if (threadPoolConfiguration != null) {
         threadFactory = threadPoolConfiguration.threadFactory() != null
               ? threadPoolConfiguration.threadFactory()
               : createThreadFactoryWithDefaults(globalConfiguration, componentName, type);

         ThreadPoolExecutorFactory threadPoolFactory = threadPoolConfiguration.threadPoolFactory();
         if (threadPoolFactory != null) {
            executorFactory = threadPoolConfiguration.threadPoolFactory();
            if (type == ExecutorServiceType.NON_BLOCKING && !executorFactory.createsNonBlockingThreads()) {
               throw log.threadPoolFactoryIsBlocking(componentName);
            }
         } else {
            executorFactory = createThreadPoolFactoryWithDefaults(componentName, type);
         }
      } else {
         threadFactory = createThreadFactoryWithDefaults(globalConfiguration, componentName, type);
         executorFactory = createThreadPoolFactoryWithDefaults(componentName, type);
      }

      switch (type) {
         case SCHEDULED:
            return (T) new LazyInitializingScheduledExecutorService(executorFactory, threadFactory);
         case REMOTE_BLOCKING:
            final String controllerName = "Controller-" + shortened(componentName) + "-" +
                  globalConfiguration.transport().nodeName();
            return (T) new LazyInitializingBlockingTaskAwareExecutorService(executorFactory, threadFactory,
                                                                        globalComponentRegistry.getTimeService(),
                                                                        controllerName);
         default:
            return (T) new LazyInitializingExecutorService(executorFactory, threadFactory);
      }
   }

   private ThreadFactory createThreadFactoryWithDefaults(GlobalConfiguration globalCfg, final String componentName,
                                                         ExecutorServiceType type) {
      if (type.isNonBlocking()) {
         return new DefaultNonBlockingThreadFactory(null, getDefaultThreadPrio(componentName),
               DefaultThreadFactory.DEFAULT_PATTERN, globalCfg.transport().nodeName(), shortened(componentName));
      }
      // Use defaults
      return new DefaultThreadFactory(null, getDefaultThreadPrio(componentName), DefaultThreadFactory.DEFAULT_PATTERN,
            globalCfg.transport().nodeName(), shortened(componentName));
   }

   private ThreadPoolExecutorFactory createThreadPoolFactoryWithDefaults(
         final String componentName, ExecutorServiceType type) {
      switch (type) {
         case SCHEDULED:
            return ScheduledThreadPoolExecutorFactory.create();
         default:
            int defaultQueueSize = KnownComponentNames.getDefaultQueueSize(componentName);
            int defaultMaxThreads = KnownComponentNames.getDefaultThreads(componentName);
            return BlockingThreadPoolExecutorFactory.create(defaultMaxThreads, defaultQueueSize,
                  type == ExecutorServiceType.NON_BLOCKING);
      }
   }

   private enum ExecutorServiceType {
      // This type can be blocking
      DEFAULT,
      SCHEDULED,
      // This is a special type that allows for blocking remote operations to be enqueued
      REMOTE_BLOCKING,
      // This type of pool means that nothing should ever be executed upon it that may block
      NON_BLOCKING,
      ;

      boolean isNonBlocking() {
         return this == NON_BLOCKING;
      }
   }

}
