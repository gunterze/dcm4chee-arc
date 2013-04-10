/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive.util.query;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;

import org.dcm4che.audit.AuditMessage;
import org.dcm4che.audit.AuditMessages;
import org.dcm4che.audit.ParticipantObjectDetail;
import org.dcm4che.audit.AuditMessages.EventActionCode;
import org.dcm4che.audit.AuditMessages.EventID;
import org.dcm4che.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che.audit.AuditMessages.ParticipantObjectIDTypeCode;
import org.dcm4che.audit.AuditMessages.ParticipantObjectTypeCode;
import org.dcm4che.audit.AuditMessages.ParticipantObjectTypeCodeRole;
import org.dcm4che.audit.AuditMessages.RoleIDCode;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.net.Association;
import org.dcm4che.net.audit.AuditLogger;
import org.dcm4che.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class AuditQuery {

    protected static final Logger LOG = LoggerFactory.getLogger(AuditQuery.class);

    public static void log(AuditLogger logger, Association as, Attributes rq, Attributes keys) {
        Calendar timeStamp = logger.timeStamp();
        AuditMessage msg = createAuditMessage(logger, timeStamp, as, rq, keys);
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("Send DICOM Query Audit Log message: {}", AuditMessages.toXML(msg));
            logger.write(timeStamp, msg);
        } catch (Exception e) {
            LOG.error("Failed to write audit log message: {}", e.getMessage());
            LOG.debug(e.getMessage(), e);
        }
    }

    private static AuditMessage createAuditMessage(AuditLogger logger, Calendar timeStamp, Association as,
            Attributes rq, Attributes keys) {
        AuditMessage msg = new AuditMessage();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DicomOutputStream dout = null;
        try {
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                    EventID.Query, 
                    EventActionCode.Execute, 
                    timeStamp, 
                    EventOutcomeIndicator.Success, 
                    null));
            msg.getActiveParticipant().add(logger.createActiveParticipant(false, RoleIDCode.Destination));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    as.getRemoteAET(), 
                    AuditMessages.alternativeUserIDForAETitle(as.getRemoteAET()), 
                    null, 
                    true, 
                    as.getSocket().getInetAddress().getCanonicalHostName(),
                    AuditMessages.NetworkAccessPointTypeCode.MachineName, 
                    null));
            ParticipantObjectDetail pod = new ParticipantObjectDetail();
            pod.setType("TransferSyntax");
            pod.setValue(UID.ExplicitVRLittleEndian.getBytes());
            try {
                dout = new DicomOutputStream(bout, UID.ExplicitVRLittleEndian);
                dout.writeDataset(null, keys);
            } catch (IOException ignore) {}
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    rq.getString(Tag.AffectedSOPClassUID), 
                    ParticipantObjectIDTypeCode.SOPClassUID, 
                    null, 
                    bout.toByteArray(), 
                    ParticipantObjectTypeCode.SystemObject, 
                    ParticipantObjectTypeCodeRole.Report, 
                    null, 
                    null, 
                    null, 
                    pod));
            msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        } finally {
            SafeClose.close(bout);
            SafeClose.close(dout);
        }
        return msg;
    }
}
