package com.dotcms.plugin.menulink;

import com.dotcms.plugin.menulink.rest.MenuLinkResource;
import com.dotcms.rest.config.RestServiceUtil;
import com.dotmarketing.util.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * OSGi bundle activator for the Menu Link REST API plugin.
 *
 * <p>Registers {@link MenuLinkResource} with dotCMS's Jersey container on start,
 * and removes it cleanly on stop so that hot-redeploy works correctly.
 */
public class Activator implements BundleActivator {

    @Override
    public void start(final BundleContext context) throws Exception {
        Logger.info(this, "Starting Menu Link REST API plugin");
        RestServiceUtil.addResource(MenuLinkResource.class);
        Logger.info(this, "Registered /api/v1/menulinks");
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        Logger.info(this, "Stopping Menu Link REST API plugin");
        RestServiceUtil.removeResource(MenuLinkResource.class);
        Logger.info(this, "Unregistered /api/v1/menulinks");
    }
}
