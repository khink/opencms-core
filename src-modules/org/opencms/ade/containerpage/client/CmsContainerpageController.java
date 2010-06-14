/*
 * File   : $Source: /alkacon/cvs/opencms/src-modules/org/opencms/ade/containerpage/client/Attic/CmsContainerpageController.java,v $
 * Date   : $Date: 2010/06/14 14:32:42 $
 * Version: $Revision: 1.14 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (C) 2002 - 2009 Alkacon Software (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ade.containerpage.client;

import org.opencms.ade.containerpage.client.draganddrop.CmsDragContainerElement;
import org.opencms.ade.containerpage.client.draganddrop.CmsDragSubcontainer;
import org.opencms.ade.containerpage.client.draganddrop.CmsDragTargetContainer;
import org.opencms.ade.containerpage.client.draganddrop.I_CmsDragTargetContainer;
import org.opencms.ade.containerpage.client.ui.CmsLeavePageDialog;
import org.opencms.ade.containerpage.client.ui.I_CmsToolbarButton;
import org.opencms.ade.containerpage.shared.CmsCntPageData;
import org.opencms.ade.containerpage.shared.CmsContainer;
import org.opencms.ade.containerpage.shared.CmsContainerElement;
import org.opencms.ade.containerpage.shared.CmsContainerElementData;
import org.opencms.ade.containerpage.shared.rpc.I_CmsContainerpageService;
import org.opencms.ade.containerpage.shared.rpc.I_CmsContainerpageServiceAsync;
import org.opencms.gwt.client.CmsCoreProvider;
import org.opencms.gwt.client.rpc.CmsRpcAction;
import org.opencms.gwt.client.rpc.CmsRpcPrefetcher;
import org.opencms.gwt.client.util.CmsDebugLog;
import org.opencms.gwt.client.util.CmsDomUtil;
import org.opencms.gwt.client.util.I_CmsSimpleCallback;
import org.opencms.util.CmsStringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.Window.ClosingEvent;
import com.google.gwt.user.client.Window.ClosingHandler;
import com.google.gwt.user.client.ui.Widget;

/**
 * Data provider for the container-page editor. All data concerning the container-page is requested and maintained by this provider.<p>
 * 
 * @author Tobias Herrmann
 * 
 * @version $Revision: 1.14 $
 * 
 * @since 8.0.0
 */
public final class CmsContainerpageController {

    /**
     * A RPC action implementation used to request the data for container-page elements.<p>
     */
    private class MultiElementAction extends CmsRpcAction<Map<String, CmsContainerElementData>> {

        /** Call-back executed on response. */
        private I_CmsSimpleCallback<Map<String, CmsContainerElementData>> m_callback;

        /** The requested client id's. */
        private Set<String> m_clientIds;

        /**
         * Constructor.<p>
         * 
         * @param clientIds the client id's
         * @param callback the call-back
         */
        public MultiElementAction(
            Set<String> clientIds,
            I_CmsSimpleCallback<Map<String, CmsContainerElementData>> callback) {

            super();
            m_clientIds = clientIds;
            m_callback = callback;
        }

        /**
         * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
         */
        @Override
        public void execute() {

            Map<String, CmsContainerElementData> result = new HashMap<String, CmsContainerElementData>();
            List<String> neededIds = new ArrayList<String>();
            Iterator<String> it = m_clientIds.iterator();
            while (it.hasNext()) {
                String clientId = it.next();
                if (m_elements.containsKey(clientId)) {
                    result.put(clientId, m_elements.get(clientId));
                } else {
                    neededIds.add(clientId);
                }
            }
            if (neededIds.size() == 0) {
                m_callback.execute(result);
            } else {
                getContainerpageService().getElementsData(
                    CmsContainerpageController.getCurrentUri(),
                    null,
                    m_clientIds,
                    m_containerTypes,
                    this);
            }

        }

        /**
         * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
         */
        @Override
        protected void onResponse(Map<String, CmsContainerElementData> result) {

            if (result != null) {
                addElements(result);
                Map<String, CmsContainerElementData> elements = new HashMap<String, CmsContainerElementData>();
                Iterator<String> it = m_clientIds.iterator();
                while (it.hasNext()) {
                    String clientId = it.next();
                    elements.put(clientId, m_elements.get(clientId));
                }
                m_callback.execute(elements);
                return;
            }
            m_callback.onError("An error occurred while retrieving elements.");

        }

    }

    /**
     * A RPC action implementation used to reload the data for a container-page element.<p>
     */
    private class ReloadElementAction extends CmsRpcAction<Map<String, CmsContainerElementData>> {

        /** The requested client id's. */
        private Set<String> m_clientIds;

        /**
         * Constructor.<p>
         * 
         * @param clientIds the client id's to reload
         */
        public ReloadElementAction(Set<String> clientIds) {

            super();
            m_clientIds = clientIds;
        }

        /**
         * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
         */
        @Override
        public void execute() {

            getContainerpageService().getElementsData(
                CmsContainerpageController.getCurrentUri(),
                null,
                m_clientIds,
                m_containerTypes,
                this);

        }

        /**
         * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
         */
        @Override
        protected void onResponse(Map<String, CmsContainerElementData> result) {

            if (result == null) {
                return;
            }
            addElements(result);
            Iterator<CmsDragContainerElement> it = getAllDragElements().iterator();
            while (it.hasNext()) {
                CmsDragContainerElement dragElement = it.next();
                if (!m_clientIds.contains(dragElement.getClientId())) {
                    continue;
                }
                try {
                    replaceDragElement(dragElement, m_elements.get(dragElement.getClientId()));
                } catch (Exception e) {
                    CmsDebugLog.getInstance().printLine("trying to replace");
                    CmsDebugLog.getInstance().printLine(e.getLocalizedMessage());
                }

            }

        }
    }

    /**
     * A RPC action implementation used to request the data for a single container-page element.<p>
     */
    private class SingleElementAction extends CmsRpcAction<Map<String, CmsContainerElementData>> {

        /** Call-back executed on response. */
        private I_CmsSimpleCallback<CmsContainerElementData> m_callback;

        /** The requested client id. */
        private String m_clientId;

        /**
         * Constructor.<p>
         * 
         * @param clientId the client id
         * @param callback the call-back
         */
        public SingleElementAction(String clientId, I_CmsSimpleCallback<CmsContainerElementData> callback) {

            super();
            m_clientId = clientId;
            m_callback = callback;
        }

        /**
         * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
         */
        @Override
        public void execute() {

            if (m_elements.containsKey(m_clientId)) {
                m_callback.execute(m_elements.get(m_clientId));
            } else {
                List<String> clientIds = new ArrayList<String>();
                clientIds.add(m_clientId);
                getContainerpageService().getElementsData(
                    CmsContainerpageController.getCurrentUri(),
                    null,
                    clientIds,
                    m_containerTypes,
                    this);
            }

        }

        /**
         * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
         */
        @Override
        protected void onResponse(Map<String, CmsContainerElementData> result) {

            if (result != null) {
                addElements(result);
                m_callback.execute(m_elements.get(m_clientId));
            }
            m_callback.onError("An error occurred while retrieving element with id: '" + m_clientId + "'.");

        }
    }

    /** Instance of the data provider. */
    private static CmsContainerpageController INSTANCE;

    /** Closing handler registration. */
    /*DEFAULT*/HandlerRegistration m_closingRegistration;

    /** The container types within this page. */
    /*DEFAULT*/Set<String> m_containerTypes;

    /** The container element data. All requested elements will be cached here.*/
    /*DEFAULT*/Map<String, CmsContainerElementData> m_elements;

    /** Flag if the container-page has changed. */
    /*DEFAULT*/boolean m_pageChanged;

    /** The container-page RPC service. */
    private I_CmsContainerpageServiceAsync m_containerpageService;

    /** The container-page util instance. */
    private CmsContainerpageUtil m_containerpageUtil;

    /** The container data. */
    private Map<String, CmsContainerJso> m_containers;

    /** The prefetched data. */
    private CmsCntPageData m_data;

    /** The currently edited sub-container element. */
    private CmsDragSubcontainer m_editingSubcontainer;

    /** The container-page handler. */
    private CmsContainerpageHandler m_handler;

    /** The drag targets within this page. */
    private Map<String, CmsDragTargetContainer> m_targetContainers;

    /**
     * Constructor.<p>
     */
    public CmsContainerpageController() {

        m_data = (CmsCntPageData)CmsRpcPrefetcher.getSerializedObject(
            getContainerpageService(),
            CmsCntPageData.DICT_NAME);
    }

    /**
     * Returns the data provider instance.<p>
     * 
     * @return the data provider
     */
    public static CmsContainerpageController get() {

        if (INSTANCE == null) {
            CmsDebugLog.getInstance().printLine("WARNING: The data provider has not been initialized!");
            return null;
        }
        return INSTANCE;
    }

    /**
     * Returns the current URI.<p>
     * 
     * @return the current URI
     */
    public static String getCurrentUri() {

        return CmsCoreProvider.get().getUri();

    }

    /**
     * Initializes the data provider. This will also transform the pages containers and elements into the appropriate widgets (done within the private constructor).<p>
     *  
     * @param toolbarButtons the tool-bar buttons of the container-page editor
     */
    public static void init(List<I_CmsToolbarButton> toolbarButtons) {

        if (INSTANCE == null) {
            INSTANCE = new CmsContainerpageController();
        }
    }

    /**
     * Adds an element specified by it's id to the favorite list.<p>
     * 
     * @param clientId the element id
     */
    public void addToFavoriteList(final String clientId) {

        CmsRpcAction<Void> action = new CmsRpcAction<Void>() {

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
             */
            @Override
            public void execute() {

                getContainerpageService().addToFavoriteList(clientId, this);
            }

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
             */
            @Override
            protected void onResponse(Void result) {

                // nothing to do
            }
        };
        action.execute();
    }

    /**
     * Adds an element specified by it's id to the recent list.<p>
     * 
     * @param clientId the element id
     */
    public void addToRecentList(final String clientId) {

        CmsRpcAction<Void> action = new CmsRpcAction<Void>() {

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
             */
            @Override
            public void execute() {

                getContainerpageService().addToRecentList(clientId, this);
            }

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
             */
            @Override
            protected void onResponse(Void result) {

                // nothing to do
            }
        };
        action.execute();
    }

    /**
     * Creates a new resource for crag container elements with the status new and opens the content editor.<p>
     * 
     * @param element the container element
     */
    public void createAndEditNewElement(final CmsDragContainerElement element) {

        if (!element.isNew()) {
            return;
        }
        m_handler.showPageOverlay();
        CmsRpcAction<CmsContainerElement> action = new CmsRpcAction<CmsContainerElement>() {

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
             */
            @Override
            public void execute() {

                getContainerpageService().createNewElement(
                    getCurrentUri(),
                    element.getClientId(),
                    element.getNewType(),
                    this);
            }

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
             */
            @Override
            protected void onResponse(CmsContainerElement result) {

                element.setNewType(null);
                element.setClientId(result.getClientId());
                element.setSitePath(result.getSitePath());
                getHandler().hidePageOverlay();
                getHandler().openEditorForElement(element);
            }
        };
        action.execute();
    }

    /**
     * Returns all drag elements of the page.<p>
     * 
     * @return the drag elements
     */
    public List<CmsDragContainerElement> getAllDragElements() {

        List<CmsDragContainerElement> result = new ArrayList<CmsDragContainerElement>();
        Iterator<CmsDragTargetContainer> it = m_targetContainers.values().iterator();
        while (it.hasNext()) {
            result.addAll(it.next().getAllDragElements());
        }
        if (isSubcontainerEditing()) {
            Iterator<Widget> itSub = m_editingSubcontainer.iterator();
            while (itSub.hasNext()) {
                Widget w = itSub.next();
                if (w instanceof CmsDragContainerElement) {
                    result.add((CmsDragContainerElement)w);
                }
            }
        }
        return result;
    }

    /**
     * Returns the data for the requested element, or <code>null</code> if the element has not been cached yet.<p>
     * 
     * @param clientId the element id
     * 
     * @return the element data
     */
    public CmsContainerElementData getCachedElement(String clientId) {

        if (m_elements.containsKey(clientId)) {
            return m_elements.get(clientId);
        }
        return null;
    }

    /**
     * Returns the container data of container with the given name.
     * 
     * @param containerName the container name
     * 
     * @return the container data
     */
    public CmsContainerJso getContainer(String containerName) {

        return m_containers.get(containerName);
    }

    /**
     * Returns the {@link org.opencms.ade.containerpage.client.CmsContainerpageUtil}.<p>
     *
     * @return the containerpage-util
     */
    public CmsContainerpageUtil getContainerpageUtil() {

        return m_containerpageUtil;
    }

    /**
     * Returns the containers.<p>
     *
     * @return the containers
     */
    public Map<String, CmsContainerJso> getContainers() {

        return m_containers;
    }

    /**
     * Returns the container drag target by name (HTML id attribute).<p>
     * 
     * @param containerName the container name
     * @return the drag target
     */
    public CmsDragTargetContainer getContainerTarget(String containerName) {

        return m_targetContainers.get(containerName);
    }

    /**
     * Returns a map of the container drag targets.<p>
     * 
     * @return the drag targets
     */
    public Map<String, CmsDragTargetContainer> getContainerTargets() {

        return m_targetContainers;
    }

    /**
     * Returns the type of container with the given name.<p>
     * 
     * @param containerName the container name
     * 
     * @return the container type
     */
    public String getContainerType(String containerName) {

        return getContainer(containerName).getType();
    }

    /**
     * Returns the prefetched data.<p>
     *
     * @return the prefetched data
     */
    public CmsCntPageData getData() {

        return m_data;
    }

    /**
     * Requests the data for a container element specified by the client id. The data will be provided to the given call-back function.<p>
     * 
     * @param clientId the element id
     * @param callback the call-back to execute with the requested data
     */
    public void getElement(String clientId, I_CmsSimpleCallback<CmsContainerElementData> callback) {

        if (m_elements.containsKey(clientId)) {
            callback.execute(m_elements.get(clientId));
        } else {
            SingleElementAction action = new SingleElementAction(clientId, callback);
            action.execute();
        }
    }

    /**
     * Requests the data for container elements specified by the client id. The data will be provided to the given call-back function.<p>
     * 
     * @param clientIds the element id's
     * @param callback the call-back to execute with the requested data
     */
    public void getElements(Set<String> clientIds, I_CmsSimpleCallback<Map<String, CmsContainerElementData>> callback) {

        MultiElementAction action = new MultiElementAction(clientIds, callback);
        action.execute();
    }

    /**
     * Retrieves a container element with a given set of properties.<p>
     * 
     * @param clientId the id of the container element
     * @param properties the set of properties
     *  
     * @param callback the callback which should be executed when the element has been loaded 
     */
    public void getElementWithProperties(
        final String clientId,
        final Map<String, String> properties,
        final I_CmsSimpleCallback<CmsContainerElementData> callback) {

        CmsRpcAction<CmsContainerElementData> action = new CmsRpcAction<CmsContainerElementData>() {

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
             */
            @Override
            public void execute() {

                start(200);
                getContainerpageService().getElementWithProperties(
                    CmsContainerpageController.getCurrentUri(),
                    null,
                    clientId,
                    properties,
                    m_containerTypes,
                    this);

            }

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
             */
            @Override
            protected void onResponse(CmsContainerElementData result) {

                stop(false);
                callback.execute(result);
            }

        };
        action.execute();
    }

    /**
     * Returns the container-page handler.<p>
     *
     * @return the container-page handler
     */
    public CmsContainerpageHandler getHandler() {

        return m_handler;
    }

    /**
     * Returns the new resource client id for the given resource type.
     * Returns <code>null</code>, if the type is can not be created.<p>
     * 
     * @param resourceType the resource type name
     * 
     * @return the new resource id
     */
    public String getNewResourceId(String resourceType) {

        return getData().getNewTypes().get(resourceType);
    }

    /**
     * Returns the sub-container element being edited.<p>
     * 
     * @return the sub-container
     */
    public CmsDragSubcontainer getSubcontainer() {

        return m_editingSubcontainer;
    }

    /**
     * Returns the type of the currently edited sub-container.<p>
     * 
     * @return the sub-container type, or <code>null</code> if no editing is taking place
     */
    public String getSubcontainerType() {

        if (m_editingSubcontainer != null) {
            return m_editingSubcontainer.getContainerType();
        }
        return null;
    }

    /**
     * Returns if the page has changed.<p>
     * 
     * @return <code>true</code> if the page has changed
     */
    public boolean hasPageChanged() {

        return m_pageChanged;
    }

    /**
     * Initializes the controller.<p>
     * 
     * @param handler the container-page handler
     * @param containerpageUtil the container-page utility
     */
    public void init(CmsContainerpageHandler handler, CmsContainerpageUtil containerpageUtil) {

        m_containerpageUtil = containerpageUtil;
        m_handler = handler;

        m_elements = new HashMap<String, CmsContainerElementData>();
        m_containerTypes = new HashSet<String>();
        m_containers = new HashMap<String, CmsContainerJso>();

        JsArray<CmsContainerJso> containers = CmsContainerJso.getContainers();
        for (int i = 0; i < containers.length(); i++) {
            CmsContainerJso container = containers.get(i);
            m_containerTypes.add(container.getType());
            m_containers.put(container.getName(), container);
        }
        m_targetContainers = m_containerpageUtil.consumeContainers(m_containers);

        Event.addNativePreviewHandler(new NativePreviewHandler() {

            public void onPreviewNativeEvent(NativePreviewEvent event) {

                previewNativeEvent(event);
            }
        });
    }

    /**
     * Returns if a sub-container is currently being edited.<p>
     * 
     * @return <code>true</code> if a sub-container is being edited
     */
    public boolean isSubcontainerEditing() {

        return m_editingSubcontainer != null;
    }

    /**
     * Method to leave the page without saving.<p>
     * 
     * @param targetUri the new URI to call
     */
    public void leaveUnsaved(String targetUri) {

        setPageChanged(false, true);
        Window.Location.assign(targetUri);
    }

    /**
     * Loads the favorite list and adds the elements to the favorite list widget of the tool-bar menu.<p>
     * 
     * @param callback the call-back to execute with the result data 
     */
    public void loadFavorites(final I_CmsSimpleCallback<List<CmsContainerElementData>> callback) {

        CmsRpcAction<List<CmsContainerElementData>> action = new CmsRpcAction<List<CmsContainerElementData>>() {

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
             */
            @Override
            public void execute() {

                getContainerpageService().getFavoriteList(getCurrentUri(), m_containerTypes, this);
            }

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
             */
            @Override
            protected void onResponse(List<CmsContainerElementData> result) {

                callback.execute(result);
            }
        };
        action.execute();
    }

    /**
     * Loads the recent list and adds the elements to the recent list widget of the tool-bar menu.<p>
     * 
     * @param callback the call-back to execute with the result data
     */
    public void loadRecent(final I_CmsSimpleCallback<List<CmsContainerElementData>> callback) {

        CmsRpcAction<List<CmsContainerElementData>> action = new CmsRpcAction<List<CmsContainerElementData>>() {

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
             */
            @Override
            public void execute() {

                getContainerpageService().getRecentList(getCurrentUri(), m_containerTypes, this);
            }

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
             */
            @Override
            protected void onResponse(List<CmsContainerElementData> result) {

                callback.execute(result);
            }
        };
        action.execute();
    }

    /**
     * Reloads the content for the given element and all related elements.<p>
     * 
     * Call this if the element content has changed.<p>
     * 
     * @param id the element id
     */
    public void reloadElement(String id) {

        Set<String> related = getRelatedElementIds(id);
        ReloadElementAction action = new ReloadElementAction(related);
        action.execute();
    }

    /**
     * Reloads a container page element with a new set of properties.<p>
     * 
     * @param elementWidget the widget of the container page element which should be reloaded
     * @param clientId the id of the container page element which should be reloaded
     * @param properties the new set of properties 
     */
    public void reloadElementWithProperties(
        final CmsDragContainerElement elementWidget,
        String clientId,
        Map<String, String> properties) {

        I_CmsSimpleCallback<CmsContainerElementData> callback = new I_CmsSimpleCallback<CmsContainerElementData>() {

            public void execute(CmsContainerElementData newElement) {

                try {
                    replaceDragElement(elementWidget, newElement);
                    setPageChanged(true, false);
                } catch (Exception e) {
                    //TODO: check if this can ever happen
                    CmsDebugLog.getInstance().printLine(e.getLocalizedMessage());
                }
            }

            public void onError(String message) {

                // will never be executed, do nothing 
            }
        };

        getElementWithProperties(clientId, properties, callback);
    }

    /**
     * Removes the given container element from its parent container.<p>
     * 
     * @param dragElement the element to remove
     */
    public void removeElement(CmsDragContainerElement dragElement) {

        dragElement.removeFromParent();
        setPageChanged();
    }

    /**
     * Replaces the given drag-element with the given container element.<p>
     * 
     * @param dragElement the drag-element to replace
     * @param elementData the new element data
     * 
     * @throws Exception if something goes wrong
     */
    public void replaceDragElement(CmsDragContainerElement dragElement, CmsContainerElementData elementData)
    throws Exception {

        I_CmsDragTargetContainer dragParent = dragElement.getDragParent();
        String containerType = dragParent.getContainerType();

        String elementContent = elementData.getContents().get(containerType);
        if ((elementContent != null) && (elementContent.trim().length() > 0)) {
            CmsDragContainerElement replacer = getContainerpageUtil().createElement(
                elementData,
                dragParent,
                containerType);
            if (dragElement.isNew()) {
                // if replacing element data has the same structure id, keep the 'new' state by setting the new type property
                // this should only be the case when editing properties of a new element that has not been created in the VFS yet
                String id = dragElement.getClientId();
                if (id.contains("#")) {
                    id = id.substring(0, id.indexOf("#"));
                }
                if (elementData.getClientId().startsWith(id)) {
                    replacer.setNewType(dragElement.getNewType());
                }
            }
            dragParent.insert(replacer, dragParent.getWidgetIndex(dragElement));
            dragElement.removeFromParent();
        }
    }

    /**
     * Resets the container-page.<p>
     */
    public void resetPage() {

        setPageChanged(false, true);
        Window.Location.reload();
    }

    /**
     * Method to save and leave the page.<p>
     * 
     * @param targetUri the new URI to call
     */
    public void saveAndLeave(final String targetUri) {

        if (hasPageChanged()) {
            CmsRpcAction<Void> action = new CmsRpcAction<Void>() {

                /**
                 * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
                 */
                @Override
                public void execute() {

                    getContainerpageService().saveContainerpage(getCurrentUri(), getPageContent(), this);
                }

                /**
                 * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
                 */
                @Override
                protected void onResponse(Void result) {

                    setPageChanged(false, true);
                    Window.Location.assign(targetUri);
                }
            };
            action.execute();
        }
    }

    /**
     * Saves the current state of the container-page.<p>
     */
    public void saveContainerpage() {

        if (hasPageChanged()) {
            CmsRpcAction<Void> action = new CmsRpcAction<Void>() {

                /**
                 * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
                 */
                @Override
                public void execute() {

                    getContainerpageService().saveContainerpage(getCurrentUri(), getPageContent(), this);
                }

                /**
                 * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
                 */
                @Override
                protected void onResponse(Void result) {

                    //TODO: add notification
                    setPageChanged(false, false);
                    Window.Location.reload();
                }
            };
            action.execute();
        }
    }

    /**
     * Saves the favorite list.<p>
     * 
     * @param clientIds the client id's of the list's elements
     */
    public void saveFavoriteList(final List<String> clientIds) {

        CmsRpcAction<Void> action = new CmsRpcAction<Void>() {

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
             */
            @Override
            public void execute() {

                getContainerpageService().saveFavoriteList(clientIds, this);
            }

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
             */
            @Override
            protected void onResponse(Void result) {

                //TODO: add notification
            }
        };
        action.execute();
    }

    /**
     * Saves the sub-container.<p>
     */
    public void saveSubcontainer() {

        // TODO: implement
    }

    /**
     * Sets the page changed flag to <code>true</code>.<p>
     */
    public void setPageChanged() {

        if (!hasPageChanged()) {
            setPageChanged(true, false);
        }
    }

    /**
     * Writes the tool-bar visibility into the session cache.<p>
     * 
     * @param visible <code>true</code> if the tool-bar is visible
     */
    public void setToolbarVisible(final boolean visible) {

        CmsRpcAction<Void> action = new CmsRpcAction<Void>() {

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
             */
            @Override
            public void execute() {

                getContainerpageService().setToolbarVisible(visible, this);
            }

            /**
             * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
             */
            @Override
            protected void onResponse(Void result) {

                //nothing to do
            }
        };
        action.execute();
    }

    /**
     * Tells the controller that sub-container editing has started.<p>
     * 
     * @param subContainer the sub-container
     */
    public void startEditingSubcontainer(CmsDragSubcontainer subContainer) {

        m_editingSubcontainer = subContainer;
    }

    /**
     * Tells the controller that sub-container editing has stopped.<p>
     */
    public void stopEditingSubcontainer() {

        m_editingSubcontainer = null;
    }

    /** 
     * Adds the given element data to the element cache.<p>
     * 
     * @param elements the element data
     */
    protected void addElements(Map<String, CmsContainerElementData> elements) {

        m_elements.putAll(elements);
    }

    /**
     * Returns the container-page RPC service.<p>
     * 
     * @return the container-page service
     */
    protected I_CmsContainerpageServiceAsync getContainerpageService() {

        if (m_containerpageService == null) {
            m_containerpageService = GWT.create(I_CmsContainerpageService.class);
        }
        return m_containerpageService;
    }

    /**
     * Returns the current containers and their elements.<p>
     * 
     * @return the list of containers
     */
    protected List<CmsContainer> getPageContent() {

        List<CmsContainer> containers = new ArrayList<CmsContainer>();
        Iterator<Entry<String, CmsDragTargetContainer>> it = m_targetContainers.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, CmsDragTargetContainer> entry = it.next();
            List<CmsContainerElement> elements = new ArrayList<CmsContainerElement>();
            Iterator<Widget> elIt = entry.getValue().iterator();
            while (elIt.hasNext()) {
                try {
                    CmsDragContainerElement elementWidget = (CmsDragContainerElement)elIt.next();
                    CmsContainerElement element = new CmsContainerElement();
                    element.setClientId(elementWidget.getClientId());
                    element.setNewType(elementWidget.getNewType());
                    element.setSitePath(elementWidget.getSitePath());
                    elements.add(element);
                } catch (ClassCastException e) {
                    // no proper container element, skip it (this should never happen!)
                    CmsDebugLog.getInstance().printLine("WARNING: there is an inappropriate element within a container");
                }
            }
            containers.add(new CmsContainer(entry.getKey(), m_containers.get(entry.getKey()).getType(), -1, elements));
        }
        return containers;
    }

    /**
     * Locks the container-page.<p>
     */
    protected void lockContainerpage() {

        if (CmsCoreProvider.get().lock(getCurrentUri())) {
            // nothing to do here
        } else {
            // TODO: add notification
        }
    }

    /**
    * Previews events. Shows the leaving page dialog, if the page has changed and an anchor has been clicked.<p>
    * 
    * @param event the native event
    */
    protected void previewNativeEvent(NativePreviewEvent event) {

        Event nativeEvent = Event.as(event.getNativeEvent());
        if (!hasPageChanged()) {
            return;
        }
        if ((nativeEvent.getTypeInt() == Event.ONCLICK)) {
            EventTarget target = nativeEvent.getEventTarget();
            if (!Element.is(target)) {
                return;
            }
            Element element = Element.as(target);
            element = CmsDomUtil.getAncestor(element, CmsDomUtil.Tag.a);
            if (element == null) {
                return;
            }
            AnchorElement anc = AnchorElement.as(element);
            final String uri = anc.getHref();
            if (CmsStringUtil.isEmptyOrWhitespaceOnly(uri)) {
                return;
            }
            nativeEvent.preventDefault();
            nativeEvent.stopPropagation();
            CmsLeavePageDialog dialog = new CmsLeavePageDialog(uri, this, null);
            dialog.center();
        }
        if ((event.getTypeInt() == Event.ONKEYDOWN) && (nativeEvent.getKeyCode() == 116)) {
            // user pressed F5
            nativeEvent.preventDefault();
            nativeEvent.stopPropagation();
            CmsLeavePageDialog dialog = new CmsLeavePageDialog(Window.Location.getHref(), this, null);
            dialog.center();
        }
    }

    /**
     * Sets the page changed flag and initializes the window closing handler if necessary.<p>
     * 
     * @param changed if <code>true</code> the page has changed
     * @param unlock if <code>true</code> the page will be unlocked for unchanged pages
     */
    protected void setPageChanged(boolean changed, boolean unlock) {

        if (changed) {
            m_pageChanged = changed;
            lockContainerpage();
            m_closingRegistration = Window.addWindowClosingHandler(new ClosingHandler() {

                /**
                 * @see com.google.gwt.user.client.Window.ClosingHandler#onWindowClosing(com.google.gwt.user.client.Window.ClosingEvent)
                 */
                public void onWindowClosing(ClosingEvent event) {

                    if (m_pageChanged) {
                        boolean savePage = Window.confirm("Do you want to save the page before leaving?");
                        if (savePage) {
                            syncSaveContainerpage();
                        } else {
                            unlockContainerpage();
                        }
                    }

                }
            });
            CmsDebugLog.getInstance().printLine("PAGE CHANGED");
            m_handler.enableSaveReset(true);

        } else {
            m_pageChanged = changed;
            m_handler.enableSaveReset(false);
            if (unlock) {
                unlockContainerpage();
            }
            if (m_closingRegistration != null) {
                m_closingRegistration.removeHandler();
                m_closingRegistration = null;
            }
        }
    }

    /**
     * Saves the container-page in a synchronized RPC call.<p>
     */
    protected void syncSaveContainerpage() {

        if (hasPageChanged()) {
            CmsRpcAction<Void> action = new CmsRpcAction<Void>() {

                /**
                 * @see org.opencms.gwt.client.rpc.CmsRpcAction#execute()
                 */
                @Override
                public void execute() {

                    getContainerpageService().syncSaveContainerpage(getCurrentUri(), getPageContent(), this);
                }

                /**
                 * @see org.opencms.gwt.client.rpc.CmsRpcAction#onResponse(java.lang.Object)
                 */
                @Override
                protected void onResponse(Void result) {

                    CmsDebugLog.getInstance().printLine("Page saved");
                    setPageChanged(false, false);
                }
            };
            action.execute();
        }
    }

    /**
     * Unlocks the container-page in a synchronized RPC call.<p>
     */
    protected void unlockContainerpage() {

        if (CmsCoreProvider.get().unlock(getCurrentUri())) {
            CmsDebugLog.getInstance().printLine("Page unlocked");
        } else {
            // ignore
        }
    }

    /**
     * Returns all element id's related to the given one.<p>
     * 
     * @param id the element id
     * @return the related id's
     */
    private Set<String> getRelatedElementIds(String id) {

        Set<String> result = new HashSet<String>();
        result.add(id);
        String serverId = id;
        if (id.contains("#")) {
            serverId = id.substring(0, id.indexOf("#"));
        }
        Iterator<String> it = m_elements.keySet().iterator();
        while (it.hasNext()) {
            String elId = it.next();
            if (elId.startsWith(serverId)) {
                result.add(elId);
            }
        }

        Iterator<CmsDragContainerElement> itEl = getAllDragElements().iterator();
        while (itEl.hasNext()) {
            CmsDragContainerElement element = itEl.next();
            if (element.getClientId().startsWith(serverId)) {
                result.add(element.getClientId());
            }
        }
        return result;
    }
}
