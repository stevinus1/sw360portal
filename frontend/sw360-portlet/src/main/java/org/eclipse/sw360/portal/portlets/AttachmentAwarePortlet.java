/*
 * Copyright Siemens AG, 2013-2015. Part of the SW360 Portal Project.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.sw360.portal.portlets;

import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.portal.common.AttachmentPortletUtils;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.common.UsedAsLiferayAction;
import org.eclipse.sw360.portal.users.UserCacheHolder;

import javax.portlet.*;
import java.io.IOException;
import java.util.*;

import static org.eclipse.sw360.datahandler.common.CommonUtils.nullToEmptySet;
import static org.eclipse.sw360.datahandler.common.CommonUtils.toSingletonSet;
import static org.eclipse.sw360.portal.common.PortalConstants.ATTACHMENTS;

/**
 * Attachment portlet implementation
 *
 * @author cedric.bodet@tngtech.com
 * @author Johannes.Najjar@tngtech.com
 * @author birgit.heydenreich@tngtech.com
 */
public abstract class AttachmentAwarePortlet extends Sw360Portlet {

    protected final AttachmentPortletUtils attachmentPortletUtils;
    protected Map< String, Map< String, Set<String>>> uploadHistoryPerUserEmailAndDocumentId;

    protected AttachmentAwarePortlet() {
        this(new ThriftClients());
    }

    public AttachmentAwarePortlet(ThriftClients thriftClients) {
        this(thriftClients, new AttachmentPortletUtils(thriftClients));
    }

    public AttachmentAwarePortlet(ThriftClients thriftClients, AttachmentPortletUtils attachmentPortletUtils) {
        super(thriftClients);
        this.attachmentPortletUtils = attachmentPortletUtils;
        uploadHistoryPerUserEmailAndDocumentId = new HashMap<>();
    }


    public static void setAttachmentsInRequest(PortletRequest request, Set<Attachment> attachments) {
        request.setAttribute(ATTACHMENTS, CommonUtils.nullToEmptySet(attachments));
    }

    private String getDocumentType(ResourceRequest request) {
        return request.getParameter(PortalConstants.DOCUMENT_TYPE);
    }

    protected abstract Set<Attachment> getAttachments(String documentId, String documentType, User user);

    protected void dealWithAttachments(ResourceRequest request, ResourceResponse response, String action) throws IOException, PortletException {
        if (PortalConstants.ATTACHMENT_DOWNLOAD.equals(action)) {
            attachmentPortletUtils.serveFile(request, response);
        } else if (PortalConstants.ATTACHMENT_LIST.equals(action)) {
            serveAttachmentSet(request, response);
        } else if (PortalConstants.ATTACHMENT_LINK_TO.equals(action)) {
            doGetAttachmentForDisplay(request, response);
        } else if (PortalConstants.ATTACHMENT_RESERVE_ID.equals(action)) {
            serveNewAttachmentId(request, response);
        } else if (PortalConstants.ATTACHMENT_UPLOAD.equals(action)) {
            if ("POST".equals(request.getMethod())) {
                // POST is for actual upload
                storeUploadedAttachmentIdInHistory(request);
                if (!attachmentPortletUtils.uploadAttachmentPart(request, "file")) {
                    response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "404");
                }
            } else {
                // GET is to check if it already exists: 200 or 204 return code
                if (!attachmentPortletUtils.checkAttachmentExistsFromRequest(request)) {
                    response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "204");
                }
            }
        } else if (PortalConstants.ATTACHMENT_CANCEL.equals(action)) {
            RequestStatus status = attachmentPortletUtils.cancelUpload(request);
            renderRequestStatus(request, response, status);
        }
    }

    private void doGetAttachmentForDisplay(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        String attachmentId = request.getParameter(PortalConstants.ATTACHMENT_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);

        Attachment attachment = attachmentPortletUtils.getAttachmentForDisplay(user, attachmentId);

        if(attachment==null) {
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "500");
        } else {
            request.setAttribute(PortalConstants.DOCUMENT_TYPE, getDocumentType(request));
            request.setAttribute(PortalConstants.ATTACHMENTS, toSingletonSet(attachment));
            include("/html/utils/ajax/attachmentsAjax.jsp", request, response, PortletRequest.RESOURCE_PHASE);
        }
    }

    private void serveAttachmentSet(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        String documentId = request.getParameter(PortalConstants.DOCUMENT_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);

        request.setAttribute(PortalConstants.DOCUMENT_TYPE, getDocumentType(request));
        request.setAttribute(PortalConstants.ATTACHMENTS, getAttachments(documentId, getDocumentType(request), user));
        include("/html/utils/ajax/attachmentsAjax.jsp", request, response, PortletRequest.RESOURCE_PHASE);
    }

    private void serveNewAttachmentId(ResourceRequest request, ResourceResponse response) throws IOException {
        final AttachmentContent attachment = attachmentPortletUtils.createAttachmentContent(request);
        if (attachment == null) {
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "404");
        } else {
            final String attachmentId = attachment.getId();
            response.getWriter().write(attachmentId);
        }
    }

    private void storeUploadedAttachmentIdInHistory(ResourceRequest request){
        String documentId = request.getParameter(PortalConstants.DOCUMENT_ID);
        String userEmail = UserCacheHolder.getUserFromRequest(request).getEmail();
        String attachmentId = request.getParameter("resumableIdentifier");

        if(!uploadHistoryPerUserEmailAndDocumentId.containsKey(userEmail)){
            Map<String,Set<String>> documentIdsToAttachmentIds = new HashMap<>();
            documentIdsToAttachmentIds.put(documentId,new HashSet<>());
            uploadHistoryPerUserEmailAndDocumentId.put(userEmail, documentIdsToAttachmentIds);
        } else if (!uploadHistoryPerUserEmailAndDocumentId.get(userEmail).containsKey(documentId)){
            uploadHistoryPerUserEmailAndDocumentId.get(userEmail).put(documentId,new HashSet<>());
        }

        Set<String> attachmentIdsForDocument = uploadHistoryPerUserEmailAndDocumentId.get(userEmail).get(documentId);
        attachmentIdsForDocument.add(attachmentId);
    }

    public void cleanUploadHistory(String userEmail, String documentId){
        if(uploadHistoryPerUserEmailAndDocumentId.containsKey(userEmail)) {
            if (uploadHistoryPerUserEmailAndDocumentId.get(userEmail).containsKey(documentId)) {
                uploadHistoryPerUserEmailAndDocumentId.get(userEmail).remove(documentId);
            }
        }
    }

    public void deleteUnneededAttachments(String userEmail, String documentId){
        if(uploadHistoryPerUserEmailAndDocumentId.containsKey(userEmail)) {
            Set<String> uploadedAttachmentIds = nullToEmptySet(uploadHistoryPerUserEmailAndDocumentId.get(userEmail).get(documentId));
            attachmentPortletUtils.deleteAttachments(uploadedAttachmentIds);
            cleanUploadHistory(userEmail,documentId);
        }
    }

    protected boolean isAttachmentAwareAction(String action) {
        return action.startsWith(PortalConstants.ATTACHMENT_PREFIX);
    }

    @Override
    protected boolean isGenericAction(String action) {
        return super.isGenericAction(action) || isAttachmentAwareAction(action);
    }

    @Override
    protected void dealWithGenericAction(ResourceRequest request, ResourceResponse response, String action) throws IOException, PortletException {
        if (super.isGenericAction(action)) {
            super.dealWithGenericAction(request, response, action);
        } else {
            dealWithAttachments(request, response, action);
        }
    }

    @UsedAsLiferayAction
    public void attachmentDeleteOnCancel(ActionRequest request, ActionResponse response){
        String userEmail = UserCacheHolder.getUserFromRequest(request).getEmail();
        if(uploadHistoryPerUserEmailAndDocumentId.containsKey(userEmail)) {
            String documentId = request.getParameter(PortalConstants.DOCUMENT_ID);
            Set<String> uploadedAttachmentIds = nullToEmptySet(uploadHistoryPerUserEmailAndDocumentId.get(userEmail).get(documentId));
            attachmentPortletUtils.deleteAttachments(uploadedAttachmentIds);
            cleanUploadHistory(userEmail, documentId);
        }
    }
}
