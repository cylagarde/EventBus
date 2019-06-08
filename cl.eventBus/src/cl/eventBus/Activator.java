package cl.eventBus;

import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator
{
  private ServiceRegistration<IEventBroker> registerService = null;

  @Override
  public void start(BundleContext bundleContext) throws Exception
  {
    // register eventBroker as service
    if (bundleContext.getServiceReference(IEventBroker.class) == null)
    {
      IEclipseContext eclipseCtx = EclipseContextFactory.getServiceContext(bundleContext);
      IEventBroker eventBroker = eclipseCtx.get(IEventBroker.class);
      registerService = bundleContext.registerService(IEventBroker.class, eventBroker, null);
    }
  }

  @Override
  public void stop(BundleContext bundleContext) throws Exception
  {
    if (registerService != null)
      registerService.unregister();
  }
}
