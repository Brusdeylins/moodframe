package art.moodframe.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central service factory for dependency injection using the Singleton pattern.
 * 
 * <p>This factory provides a centralized registry for services and supports
 * both eager and lazy initialization through service providers. The factory
 * maintains thread-safe access to registered services and provides test mode
 * support for unit testing scenarios.</p>
 * 
 * <p><strong>Thread Safety:</strong> Service registration uses concurrent maps. Creating a service
 * from its provider is synchronized, so a provider runs at most once per service even if
 * several threads ask for it at the same time.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Register a service
 * ServiceFactory.getInstance().registerService(ApiService.class, new BflApiService());
 * 
 * // Register a lazy provider
 * ServiceFactory.getInstance().registerProvider(DatabaseService.class, 
 *     () -> new DatabaseService(config.getDatabasePath()));
 * 
 * // Retrieve a service
 * ApiService apiService = ServiceFactory.getInstance().getService(ApiService.class);
 * }</pre>
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Dependency_injection">Dependency Injection Pattern</a>
 * @since 1.0.0
 */
public class ServiceFactory {
    
    private static ServiceFactory instance;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final Map<Class<?>, ServiceProvider<?>> providers = new ConcurrentHashMap<>();
    private final Map<Class<?>, Class<?>> interfaceImplementations = new ConcurrentHashMap<>();
    private boolean testMode = false;
    
    private ServiceFactory() {
        // Private constructor for Singleton
    }
    
    /**
     * Returns the singleton instance of the ServiceFactory.
     * 
     * <p>This method implements lazy initialization with double-checked locking
     * to ensure thread safety while minimizing synchronization overhead.</p>
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
     * @return the service instance, or null if no service or provider is registered or the provider
     *         returned null (a null result is not cached, the next call asks the provider again)
     * @throws RuntimeException if the service provider throws an exception
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        T service = (T) services.get(serviceClass);
        if (service != null || !providers.containsKey(serviceClass))
            return service;

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
        return service;
    }
    
    /**
     * Sets the test mode flag.
     * 
     * <p>When test mode is enabled, services can behave differently to support
     * unit testing scenarios (e.g., using in-memory databases, mock services).</p>
     * 
     * @param testMode true to enable test mode, false to disable
     * @since 1.0.0
     */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
    
    /**
     * Returns whether test mode is currently active.
     * 
     * @return true if test mode is enabled, false otherwise
     * @since 1.0.0
     */
    public boolean isTestMode() {
        return testMode;
    }
    
    /**
     * Clears all registered services and providers.
     * 
     * <p><strong>Warning:</strong> This method is intended for testing purposes only.
     * It removes all registered services and providers from the factory, which may
     * cause application failures if called in production code.</p>
     * 
     * @since 1.0.0
     */
    public void clearServices() {
        services.clear();
        providers.clear();
        interfaceImplementations.clear();
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