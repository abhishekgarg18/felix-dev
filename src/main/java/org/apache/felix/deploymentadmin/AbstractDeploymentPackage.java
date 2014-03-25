/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.deploymentadmin;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.BundleInfo;
import org.osgi.service.deploymentadmin.DeploymentException;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;

/**
 * Base class for various types of deployment packages. Indifferent in regard to
 * how the deployment package data is obtained, this should be handled by
 * extending classes.
 */
public abstract class AbstractDeploymentPackage implements DeploymentPackage {
    /**
     * Represents an empty deployment package.
     */
    private static final class EmptyDeploymentPackage extends AbstractDeploymentPackage {
        private static final String[] STRINGS = new String[] {};
        private static final ResourceInfoImpl[] RESOURCE_INFO_IMPLS = new ResourceInfoImpl[] {};
        private static final BundleInfoImpl[] BUNDLE_INFO_IMPLS = new BundleInfoImpl[] {};

        public String getHeader(String header) {
            if (Constants.DEPLOYMENTPACKAGE_SYMBOLICMAME.equals(header)) {
                return "";
            } else if (Constants.DEPLOYMENTPACKAGE_VERSION.equals(header)) {
                return Version.emptyVersion.toString();
            } else {
                return null;
            }
        }

        public Bundle getBundle(String symbolicName) {
            return null;
        }

        public BundleInfo[] getBundleInfos() {
            return BUNDLE_INFO_IMPLS;
        }

        public BundleInfoImpl[] getBundleInfoImpls() {
            return BUNDLE_INFO_IMPLS;
        }

        public ResourceInfoImpl[] getResourceInfos() {
            return RESOURCE_INFO_IMPLS;
        }

        public String getName() {
            return "";
        }

        public String getResourceHeader(String resource, String header) {
            return null;
        }

        public ServiceReference getResourceProcessor(String resource) {
            return null;
        }

        public String[] getResources() {
            return STRINGS;
        }

        public Version getVersion() {
            return Version.emptyVersion;
        }

        public boolean isStale() {
            return true;
        }

        public void uninstall() throws DeploymentException {
            throw new IllegalStateException("Can not uninstall stale DeploymentPackage");
        }

        public boolean uninstallForced() throws DeploymentException {
            throw new IllegalStateException("Can not uninstall stale DeploymentPackage");
        }

        public InputStream getBundleStream(String symbolicName) throws IOException {
            return null;
        }

        public BundleInfoImpl[] getOrderedBundleInfos() {
            return BUNDLE_INFO_IMPLS;
        }

        public ResourceInfoImpl[] getOrderedResourceInfos() {
            return RESOURCE_INFO_IMPLS;
        }

        public InputStream getCurrentEntryStream() {
            throw new UnsupportedOperationException();
        }

        public AbstractInfo getNextEntry() throws IOException {
            throw new UnsupportedOperationException();
        }

        public String getDisplayName() {
            return "";
        }

        public URL getIcon() {
            return null;
        }
    }

    protected static final AbstractDeploymentPackage EMPTY_PACKAGE = new EmptyDeploymentPackage();

    private final BundleContext m_bundleContext;
    private final DeploymentAdminImpl m_deploymentAdmin;
    private final DeploymentPackageManifest m_manifest;
    private final Map m_nameToBundleInfo = new HashMap();
    private final Map m_pathToEntry = new HashMap();
    private final BundleInfoImpl[] m_bundleInfos;
    private final ResourceInfoImpl[] m_resourceInfos;
    private final String[] m_resourcePaths;
    private final boolean m_isFixPackage;
    private boolean m_isStale;

    /* Constructor only for use by the emptyPackage static variable */
    private AbstractDeploymentPackage() {
        m_bundleContext = null;
        m_manifest = null;
        m_bundleInfos = null;
        m_resourceInfos = null;
        m_resourcePaths = null;
        m_isFixPackage = false;
        m_deploymentAdmin = null;
    }

    /**
     * Creates an instance of this class.
     *
     * @param manifest The manifest of the deployment package.
     * @param bundleContext The bundle context.
     * @throws DeploymentException Thrown if the specified manifest does not
     *         describe a valid deployment package.
     */
    public AbstractDeploymentPackage(Manifest manifest, BundleContext bundleContext, DeploymentAdminImpl deploymentAdmin) throws DeploymentException {
        m_manifest = new DeploymentPackageManifest(manifest);
        m_isFixPackage = m_manifest.getFixPackage() != null;
        m_bundleContext = bundleContext;
        m_deploymentAdmin = deploymentAdmin;

        List bundleInfos = m_manifest.getBundleInfos();
        m_bundleInfos = (BundleInfoImpl[]) bundleInfos.toArray(new BundleInfoImpl[bundleInfos.size()]);

        for (int i = 0; i < m_bundleInfos.length; i++) {
            String bsn = m_bundleInfos[i].getSymbolicName();
            if (m_nameToBundleInfo.put(bsn, m_bundleInfos[i]) != null) {
                // FELIX-4463: make sure that the DP is consistent...
                throw new DeploymentException(DeploymentException.CODE_OTHER_ERROR, "Duplicate bundle present in deployment package: " + bsn);
            }

            String path = m_bundleInfos[i].getPath();
            if (m_pathToEntry.put(path, m_bundleInfos[i]) != null) {
                // FELIX-4463: make sure that the DP is consistent...
                throw new DeploymentException(DeploymentException.CODE_OTHER_ERROR, "Non-unique path present in deployment package: " + path);
            }
        }

        List resourceInfos = m_manifest.getResourceInfos();
        m_resourceInfos = (ResourceInfoImpl[]) resourceInfos.toArray(new ResourceInfoImpl[resourceInfos.size()]);

        for (int i = 0; i < m_resourceInfos.length; i++) {
            String path = m_resourceInfos[i].getPath();
            if (m_pathToEntry.put(path, m_resourceInfos[i]) != null) {
                // FELIX-4463: make sure that the DP is consistent...
                throw new DeploymentException(DeploymentException.CODE_OTHER_ERROR, "Non-unique path present in deployment package: " + path);
            }
        }
        m_resourcePaths = (String[]) m_pathToEntry.keySet().toArray(new String[m_pathToEntry.size()]);
    }

    public Bundle getBundle(String symbolicName) {
        if (isStale()) {
            throw new IllegalStateException("Can not get bundle from stale deployment package.");
        }

        BundleInfo bundleInfo = (BundleInfo) m_nameToBundleInfo.get(symbolicName);
        if (bundleInfo != null) {
            Version version = bundleInfo.getVersion();

            Bundle[] bundles = m_bundleContext.getBundles();
            for (int i = 0; i < bundles.length; i++) {
                if (symbolicName.equals(bundles[i].getSymbolicName()) && version.equals(bundles[i].getVersion())) {
                    return bundles[i];
                }
            }
        }
        return null;
    }

    public BundleInfo[] getBundleInfos() {
        return (BundleInfo[]) m_bundleInfos.clone();
    }

    /**
     * Returns the bundles of this deployment package as an array of
     * <code>BundleInfoImpl</code> objects.
     *
     * @return Array containing <code>BundleInfoImpl</code> objects for each
     *         bundle this deployment package.
     */
    public BundleInfoImpl[] getBundleInfoImpls() {
        return (BundleInfoImpl[]) m_bundleInfos.clone();
    }

    /**
     * Returns the processed resources of this deployment package as an array of
     * <code>ResourceInfoImpl</code> objects.
     *
     * @return Array containing <code>ResourceInfoImpl</code> objects for each
     *         processed resource of this deployment package.
     */
    public ResourceInfoImpl[] getResourceInfos() {
        return (ResourceInfoImpl[]) m_resourceInfos.clone();
    }

    /**
     * Determines whether this deployment package is a fix package.
     *
     * @return True if this deployment package is a fix package, false
     *         otherwise.
     */
    public boolean isFixPackage() {
        return m_isFixPackage;
    }

    public String getHeader(String header) {
        return m_manifest.getHeader(header);
    }

    public String getName() {
        return m_manifest.getSymbolicName();
    }

    public String getDisplayName() {
        return getHeader("DeploymentPackage-Name");
    }

    public URL getIcon() {
        String icon = getHeader("DeploymentPackage-Icon");
        if (icon == null) {
            return null;
        } else {
            try {
                // TODO spec states this must be a local resource, but we don't
// make
                // sure of that yet
                return new URL(icon);
            }
            catch (MalformedURLException e) {
                return null;
            }
        }
    }

    public String getResourceHeader(String resource, String header) {
        AbstractInfo info = (AbstractInfo) m_pathToEntry.get(resource);
        if (info != null) {
            return info.getHeader(header);
        }
        return null;
    }

    public ServiceReference getResourceProcessor(String resource) {
        if (isStale()) {
            throw new IllegalStateException("Can not get bundle from stale deployment package.");
        }
        AbstractInfo info = (AbstractInfo) m_pathToEntry.get(resource);
        if (info instanceof ResourceInfoImpl) {
            String processor = ((ResourceInfoImpl) info).getResourceProcessor();
            if (processor != null) {
                try {
                    ServiceReference[] services = m_bundleContext.getServiceReferences(ResourceProcessor.class.getName(), "(" + org.osgi.framework.Constants.SERVICE_PID + "=" + processor + ")");
                    if (services != null && services.length > 0) {
                        return services[0];
                    } else {
                        return null;
                    }
                }
                catch (InvalidSyntaxException e) {
                    // TODO: log this
                    return null;
                }
            }
        }
        return null;
    }

    public String[] getResources() {
        return (String[]) m_resourcePaths.clone();
    }

    public Version getVersion() {
        return m_manifest.getVersion();
    }

    /**
     * If this deployment package is a fix package this method determines the
     * version range this deployment package can be applied to.
     *
     * @return <code>VersionRange</code> the fix package can be applied to or
     *         <code>null</code> if it is not a fix package.
     */
    public VersionRange getVersionRange() {
        return m_manifest.getFixPackage();
    }

    public boolean isStale() {
        return m_isStale;
    }

    /**
     * @return <code>true</code> if this package is actually an empty package
     *         used for installing new deployment packages, <code>false</code>
     *         otherwise.
     */
    public boolean isNew() {
        return this == EMPTY_PACKAGE;
    }

    public void setStale(boolean isStale) {
        m_isStale = isStale;
    }

    public void uninstall() throws DeploymentException {
        if (isStale()) {
            throw new IllegalStateException("Deployment package is stale, cannot uninstall.");
        }
        try {
            m_deploymentAdmin.uninstallDeploymentPackage(this, false /* force */);
        }
        finally {
            setStale(true);
        }
    }

    public boolean uninstallForced() throws DeploymentException {
        if (isStale()) {
            throw new IllegalStateException("Deployment package is stale, cannot uninstall.");
        }
        try {
            m_deploymentAdmin.uninstallDeploymentPackage(this, true /* force */);
        }
        finally {
            setStale(true);
        }
        return true;
    }

    /**
     * Determines the bundles of this deployment package in the order in which
     * they were originally received.
     *
     * @return Array containing <code>BundleInfoImpl</code> objects of the
     *         bundles in this deployment package, ordered in the way they
     *         appeared when the deployment package was first received.
     */
    public abstract BundleInfoImpl[] getOrderedBundleInfos();

    /**
     * Determines the resources of this deployment package in the order in which
     * they were originally received.
     *
     * @return Array containing <code>ResourceInfoImpl</code> objects of all
     *         processed resources in this deployment package, ordered in the
     *         way they appeared when the deployment package was first received
     */
    public abstract ResourceInfoImpl[] getOrderedResourceInfos();

    /**
     * Determines the info about a processed resource based on it's
     * path/resource-id.
     *
     * @param path String containing a (processed) resource path
     * @return <code>ResourceInfoImpl</code> for the resource identified by the
     *         specified path or null if the path is unknown or does not
     *         describe a processed resource
     */
    public ResourceInfoImpl getResourceInfoByPath(String path) {
        AbstractInfo info = (AbstractInfo) m_pathToEntry.get(path);
        if (info instanceof ResourceInfoImpl) {
            return (ResourceInfoImpl) info;
        }
        return null;
    }

    /**
     * Determines the info about either a bundle or processed resource based on
     * it's path/resource-id.
     *
     * @param path String containing a resource path (either bundle or processed
     *        resource)
     * @return <code>AbstractInfoImpl</code> for the resource identified by the
     *         specified path or null if the path is unknown
     */
    protected AbstractInfo getAbstractInfoByPath(String path) {
        return (AbstractInfo) m_pathToEntry.get(path);
    }

    /**
     * Determines the info about a bundle based on it's path/resource-id.
     *
     * @param path String containing a bundle path
     * @return <code>BundleInfoImpl</code> for the bundle resource identified by
     *         the specified path or null if the path is unknown or does not
     *         describe a bundle resource
     */
    public BundleInfoImpl getBundleInfoByPath(String path) {
        AbstractInfo info = (AbstractInfo) m_pathToEntry.get(path);
        if (info instanceof BundleInfoImpl) {
            return (BundleInfoImpl) info;
        }
        return null;
    }

    /**
     * Determines the info about a bundle resource based on the bundle symbolic
     * name.
     *
     * @param symbolicName String containing a bundle symbolic name
     * @return <code>BundleInfoImpl</code> for the bundle identified by the
     *         specified symbolic name or null if the symbolic name is unknown
     */
    public BundleInfoImpl getBundleInfoByName(String symbolicName) {
        return (BundleInfoImpl) m_nameToBundleInfo.get(symbolicName);
    }

    /**
     * Determines the data stream of a bundle resource based on the bundle
     * symbolic name
     *
     * @param symbolicName Bundle symbolic name
     * @return Stream to the bundle identified by the specified symbolic name or
     *         null if no such bundle exists in this deployment package.
     * @throws IOException If the bundle can not be properly offered as an
     *         inputstream
     */
    public abstract InputStream getBundleStream(String symbolicName) throws IOException;

    /**
     * Determines the next resource entry in this deployment package based on
     * the order in which the resources appeared when the package was originally
     * received.
     *
     * @return <code>AbstractInfo</code> describing the next resource entry (as
     *         determined by the order in which the deployment package was
     *         received originally) or null if there is no next entry
     * @throws IOException if the next entry can not be properly determined
     */
    public abstract AbstractInfo getNextEntry() throws IOException;

    /**
     * Determines the data stream to the current entry of this deployment
     * package, use this together with the <code>getNextEntry</code> method.
     *
     * @return Stream to the current resource in the deployment package (as
     *         determined by the order in which the deployment package was
     *         received originally) or null if there is no entry
     */
    public abstract InputStream getCurrentEntryStream();

}
