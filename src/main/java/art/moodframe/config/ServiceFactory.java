package art.moodframe.config;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The singleton service locator of the application, not a DI container: the composition root
 * ({@code MoodframeApplication}) registers the services and lazy providers and hands every service its dependencies
 * in the constructor; only the coordinators of the user interface look services up here (see ARCHITECTURE.md).
 *
 * <p>It supports both eager and lazy initialization through service providers and maintains thread-safe access to
 * the registered services. A lookup of a service that is not registered fails at once.</p>
 * 
 * <p><strong>Thread Safety:</strong> Service registration uses concurrent maps. Creating a service
 * from its provider is synchronized, so a provider runs at most once per service even if
 * several threads ask for it at the same time.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Register a service
 * ServiceFactory.getInstance().registerService(ApplicationConfig.class, new ApplicationConfig(paths));
 *
 * // Register a lazy provider
 * ServiceFactory.getInstance().registerProvider(FrameService.class,
 *     () -> new FrameService(new FrameRepository(database), events));
 *
 * // Retrieve a service
 * FrameService frames = ServiceFactory.getInstance().getService(FrameService.class);
 * }</pre>
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Service_locator_pattern">Service Locator Pattern</a>
 * @since 1.0.0
 */
public class ServiceFactory {
    
    private static ServiceFactory instance;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final Map<Class<?>, ServiceProvider<?>> providers = new ConcurrentHashMap<>();
    
    private ServiceFactory() {
        // Private constructor for Singleton
    }
    
    /**
     * Returns the singleton instance of the ServiceFactory.
     * 
     * <p>The instance is created on the first call; the method is synchronized, so every thread gets the same
     * one.</p>
     * 
     * @return the singleton ServiceFactory instance
     * @since 1.0.0
     */
    public static synchronized ServiceFactory getInstance() {
        if (instance == null) {
            instance = new ServiceFactory();
        }
        return instance;
    }
    
    /**
     * Registers a service instance with the factory.
     * 
     * <p>Once registered, the service can be retrieved using {@link #getService(Class)}.
     * If a service of the same type is already registered, it will be replaced.</p>
     * 
     * @param <T> the service type
     * @param serviceClass the class object representing the service type
     * @param serviceInstance the service instance to register
     * @throws NullPointerException if serviceClass or serviceInstance is null
     * @since 1.0.0
     */
    public <T> void registerService(Class<T> serviceClass, T serviceInstance) {
        services.put(serviceClass, serviceInstance);
    }
    
    /**
     * Registers a service provider for lazy initialization.
     * 
     * <p>The provider's {@link ServiceProvider#create()} method will be called
     * only when the service is first requested via {@link #getService(Class)}.
     * This enables deferred initialization of expensive resources.</p>
     * 
     * @param <T> the service type
     * @param serviceClass the class object representing the service type
     * @param provider the service provider that creates the service instance
     * @throws NullPointerException if serviceClass or provider is null
     * @since 1.0.0
     */
    public <T> void registerProvider(Class<T> serviceClass, ServiceProvider<T> provider) {
        providers.put(serviceClass, provider);
    }
    
    /**
     * Retrieves a service instance from the factory.
     * 
     * <p>If the service is already instantiated, returns the cached instance.
     * If only a provider is registered, creates the service using the provider
     * and caches the result for future calls.</p>
     * 
     * @param <T> the service type
     * @param serviceClass the class object representing the service type
     * @return the service instance, never null
     * @throws IllegalStateException if no service or provider is registered or the provider returned null, so a
     *         missing registration fails at the lookup instead of later
     * @throws RuntimeException if the service provider throws an exception
     * @since 1.0.0
     */
    public <T> T getService(Class<T> serviceClass) {
        return findService(serviceClass).orElseThrow(
                () -> new IllegalStateException("No service registered for " + serviceClass.getName()));
    }

    /**
     * Retrieves a service the caller can do without, like the generation, whose provider gives none if its parts
     * cannot be created; creates it from its provider like {@link #getService(Class)}.
     *
     * @param <T> the service type
     * @param serviceClass the class object representing the service type
     * @return the service, empty if no service or provider is registered or the provider returned null (which is
     *         not cached, the next call asks the provider again)
     * @throws RuntimeException if the service provider throws an exception
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> findService(Class<T> serviceClass) {
        T service = (T) services.get(serviceClass);
        if (service != null || !providers.containsKey(serviceClass))
            return Optional.ofNullable(service);

        /*  A provider may ask for other services, the lock is reentrant  */
        synchronized (this) {
            service = (T) services.get(serviceClass);
            ServiceProvider<T> provider = (ServiceProvider<T>) providers.get(serviceClass);
            if (service == null && provider != null) {
                service = provider.create();
                if (service != null)
                    services.put(serviceClass, service);
            }
        }
        return Optional.ofNullable(service);
    }
    
    /**
     * Retrieves a service only if it exists already, without asking its provider to create it.
     *
     * <p>For the shutdown, which stops what was started and creates nothing.</p>
     *
     * @param <T> the service type
     * @param serviceClass the class object representing the service type
     * @return the service instance, or null if it was not created
     */
    @SuppressWarnings("unchecked")
    public <T> T getCreatedService(Class<T> serviceClass) {
        return (T) services.get(serviceClass);
    }

    /**
     * Clears all registered services and providers.
     * 
     * <p>The application calls it on shutdown, after it stopped the services; tests call it to start from an empty
     * factory. A service looked up afterwards is not found, so nothing that still needs one may run then.</p>
     * 
     * @since 1.0.0
     */
    public void clearServices() {
        services.clear();
        providers.clear();
    }
    
    /**
     * Functional interface for creating service instances.
     * 
     * <p>Service providers enable lazy initialization of services, allowing
     * expensive resources to be created only when first needed.</p>
     * 
     * @param <T> the type of service this provider creates
     * @since 1.0.0
     */
    @FunctionalInterface
    public interface ServiceProvider<T> {
        T create();
    }
}