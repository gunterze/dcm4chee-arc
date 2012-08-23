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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.archive.pix;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.hl7.HL7Message;
import org.dcm4che.hl7.HL7Segment;
import org.dcm4che.hl7.MLLPConnection;
import org.dcm4che.net.CompatibleConnection;
import org.dcm4che.net.Connection;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.hl7.HL7Application;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.conf.ArchiveDevice;
import org.dcm4chee.archive.query.util.IDWithIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class PIXConsumer {

    private static Logger LOG = LoggerFactory.getLogger(PIXConsumer.class);
    
    private final HL7ApplicationCache hl7AppCache;

    public PIXConsumer(HL7ApplicationCache hl7AppCache) {
        this.hl7AppCache = hl7AppCache;
    }

    public IDWithIssuer[] pixQuery(ArchiveApplicationEntity ae, IDWithIssuer pid) {
        if (pid == null)
            return IDWithIssuer.EMPTY;

        String pixConsumer = ae.getLocalPIXConsumerApplication();
        String pixManager = ae.getRemotePIXManagerApplication();
        if (pixConsumer == null || pixManager == null
                || containsWildcard(pid.id) || pid.issuer == null)
            return new IDWithIssuer[] { pid };
        
        ArrayList<IDWithIssuer> pids = new ArrayList<IDWithIssuer>();
        pids.add(pid);
        try {
            ArchiveDevice dev = ae.getArchiveDevice();
            HL7Application pixConsumerApp = dev.getHL7Application(pixConsumer);
            if (pixConsumerApp == null)
                throw new ConfigurationException(
                        "Unknown HL7 Application: " + pixConsumer);
            HL7Application pixManagerApp = 
                    hl7AppCache.findHL7Application(pixManager);
            HL7Message qbp = HL7Message.makePixQuery(pid.toString());
            HL7Segment msh = qbp.get(0);
            msh.setSendingApplicationWithFacility(pixConsumer);
            msh.setReceivingApplicationWithFacility(pixManagerApp.getApplicationName());
            msh.setField(17, pixConsumerApp.getHL7DefaultCharacterSet());
            HL7Message rsp = pixQuery(pixConsumerApp, pixManagerApp, qbp);
            HL7Segment pidSeg = rsp.getSegment("PID");
            if (pidSeg != null) {
                String[] pidCXs = HL7Segment.split(pidSeg.getField(3, ""),
                        pidSeg.getRepetitionSeparator());
                for (String pidCX : pidCXs)
                    pids.add(new IDWithIssuer(pidCX));
            }
        } catch (Exception e) {
            LOG.info("PIX Query failed: ", e);
        }
        return pids.toArray(new IDWithIssuer[pids.size()]);
    }

    private boolean containsWildcard(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    private HL7Message pixQuery(HL7Application pixConsumerApp,
            HL7Application pixManagerApp, HL7Message qbp)
            throws IncompatibleConnectionException, IOException, GeneralSecurityException {
        CompatibleConnection cc = pixConsumerApp.findCompatibelConnection(pixManagerApp);
        Connection conn = cc.getLocalConnection();
        MLLPConnection mllpConn = pixConsumerApp.connect(conn, cc.getRemoteConnection());
        try {
            String charset = pixConsumerApp.getHL7DefaultCharacterSet();
            mllpConn.writeMessage(qbp.getBytes(charset));
            HL7Message rsp = HL7Message.parse(mllpConn.readMessage(), charset);
            return rsp;
        } finally {
            conn.close(mllpConn.getSocket());
        }
    }
}
