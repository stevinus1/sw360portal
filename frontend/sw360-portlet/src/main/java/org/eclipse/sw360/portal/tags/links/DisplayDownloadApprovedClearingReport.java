/*
 * Copyright Siemens AG, 2013-2017. Part of the SW360 Portal Project.
 *
 * SPDX-License-Identifier: EPL-1.0
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.sw360.portal.tags.links;

import org.apache.log4j.Logger;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.CheckStatus;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.tags.urlutils.UrlWriter;

import javax.servlet.jsp.JspException;

import static org.eclipse.sw360.portal.tags.TagUtils.escapeAttributeValue;

/**
 * Displays a download link for clearing reports. It enhances the tooltip with
 * further information.
 */
public class DisplayDownloadApprovedClearingReport extends DisplayDownloadAbstract {
    private static final Logger LOGGER = Logger.getLogger(DisplayDownloadApprovedClearingReport.class);

    private Attachment attachment;
    
    @Override
    protected String getAlternativeText() {
        return escapeAttributeValue(attachment.filename);
    }

    @Override
    protected String getTitleText() {
        String titleTemplateChecked = "Filename: %s&#010;Status: %s by %s on %s&#010;Comment: %s&#010;Created: %s on %s";

        String name = escapeAttributeValue(attachment.getFilename());
        String createdBy = escapeAttributeValue(attachment.getCreatedBy());
        String createdOn = escapeAttributeValue(attachment.getCreatedOn());
        String checkedBy = escapeAttributeValue(attachment.getCheckedBy());
        String checkedOn = escapeAttributeValue(attachment.getCheckedOn());
        String checkComment = escapeAttributeValue(attachment.getCheckedComment());

        return String.format(titleTemplateChecked, name, "APPROVED", checkedBy, checkedOn, checkComment, createdBy, createdOn);
    }

    @Override
    protected void configureUrlWriter(UrlWriter urlWriter) throws JspException {
        urlWriter.withParam(PortalConstants.ATTACHMENT_ID, attachment.attachmentContentId);
    }

    @Override
    public int doStartTag() throws JspException {
        if(attachment == null) {
            LOGGER.error("No attachment given!");
            return SKIP_BODY;
        }

        if (attachment.getAttachmentType() != AttachmentType.CLEARING_REPORT) {
            LOGGER.error("Invalid attachment type: " + attachment.getAttachmentType() + ". Expected CLEARING_REPORT("
                    + AttachmentType.CLEARING_REPORT.getValue() + ").");
            return SKIP_BODY;
        }

        if (attachment.getCheckStatus() != CheckStatus.ACCEPTED) {
            // show only approved reports
            return SKIP_BODY;
        }
    
        return super.doStartTag();
    }

    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
    }
}
