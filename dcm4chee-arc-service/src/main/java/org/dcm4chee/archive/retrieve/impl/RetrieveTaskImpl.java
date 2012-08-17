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
 * Portions created by the Initial Developer are Copyright (C) 2011
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

package org.dcm4chee.archive.retrieve.impl;

import java.io.IOException;
import java.util.List;

import javax.xml.transform.Templates;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.net.Association;
import org.dcm4che.net.DataWriter;
import org.dcm4che.net.DataWriterAdapter;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicRetrieveTask;
import org.dcm4che.net.service.InstanceLocator;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.pix.PIXConsumer;
import org.dcm4chee.archive.query.IDWithIssuer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class RetrieveTaskImpl extends BasicRetrieveTask {

    private final PIXConsumer pixConsumer;
    private final RetrieveService retrieveService;
    private final boolean withoutBulkData;
    private IDWithIssuer[] pids;
    private IDWithIssuer pidWithMatchingIssuer;
    private String[] patientNames;
    private boolean returnOtherPatientIDs;
    private boolean returnOtherPatientNames;
    private Issuer issuerOfPatientID;
    private Issuer issuerOfAccessionNumber;

    public RetrieveTaskImpl(BasicRetrieveTask.Service service, Association as,
            PresentationContext pc, Attributes rq, List<InstanceLocator> matches,
            PIXConsumer pixConsumer, RetrieveService retrieveService,
            boolean withoutBulkData) {
        super(service, as, pc, rq, matches);
        this.pixConsumer = pixConsumer;
        this.retrieveService = retrieveService;
        this.withoutBulkData = withoutBulkData;
    }

    public void setDestinationDevice(Device destDevice) {
        this.issuerOfPatientID = destDevice.getIssuerOfPatientID();
        this.issuerOfAccessionNumber = destDevice.getIssuerOfAccessionNumber();
    }

    public void setReturnOtherPatientIDs(boolean returnOtherPatientIDs) {
        this.returnOtherPatientIDs = returnOtherPatientIDs;
    }

    public void setReturnOtherPatientNames(boolean returnOtherPatientNames) {
        this.returnOtherPatientNames = returnOtherPatientNames;
    }

    @Override
    protected DataWriter createDataWriter(InstanceLocator inst, String tsuid)
            throws IOException {
        Attributes attrs;
        DicomInputStream in = new DicomInputStream(inst.getFile());
        try {
            if (withoutBulkData) {
                in.setIncludeBulkData(false);
                attrs = in.readDataset(-1, Tag.PixelData);
            } else {
                in.setIncludeBulkDataLocator(true);
                attrs = in.readDataset(-1, -1);
            }
        } finally {
            SafeClose.close(in);
        }
        attrs.addAll((Attributes) inst.getObject());
        adjustPatientID(attrs);
        adjustAccessionNumber(attrs);
        ArchiveApplicationEntity ae = (ArchiveApplicationEntity) as.getApplicationEntity();
        try {
            Templates tpl = ae.getAttributeCoercionTemplates(
                    inst.cuid, Dimse.C_STORE_RQ, Role.SCU, as.getRemoteAET());
            if (tpl != null)
                attrs.update(SAXTransformer.transform(attrs, tpl, false, false), null);
        } catch (Exception e) {
            throw new IOException(e);
        }
        return new DataWriterAdapter(attrs);
    }

    private void adjustPatientID(Attributes attrs) {
        IDWithIssuer pid0 = IDWithIssuer.pidWithIssuer(attrs, null);
        if (pid0 == null)
            return;

        if (pids == null) {
            pids = pixConsumer == null 
                    ? new IDWithIssuer[] { pid0 }
                    : pixConsumer.pixQuery(
                            (ArchiveApplicationEntity) as.getApplicationEntity(),
                            pid0);
            pidWithMatchingIssuer = pidWithMatchingIssuer(pids, issuerOfPatientID);
            if (returnOtherPatientNames) {
                if (pids.length > 0)
                    patientNames = retrieveService.patientNamesOf(pids);
                else {
                    String patientName = attrs.getString(Tag.PatientName);
                    if (patientName != null)
                        patientNames = new String[] { patientName };
                }
            }
        }

        if (pidWithMatchingIssuer != null) {
            pidWithMatchingIssuer.toPIDWithIssuer(attrs);
        } else {
            attrs.setNull(Tag.PatientID, VR.LO);
            issuerOfPatientID.toIssuerOfPatientID(attrs);
        }
        if (returnOtherPatientIDs)
            IDWithIssuer.addOtherPatientIDs(attrs, pids);
        if (patientNames != null)
            attrs.setString(Tag.OtherPatientNames, VR.PN, patientNames);
    }

    private IDWithIssuer pidWithMatchingIssuer(IDWithIssuer[] pids, Issuer issuer) {
        if (issuer == null)
            return pids[0];

        for (IDWithIssuer pid : pids)
            if (issuer.matches(pid.issuer))
                return pid;

        return null;
    }

    private void adjustAccessionNumber(Attributes attrs) {
        if (issuerOfAccessionNumber == null)
            return;

        adjustAccessionNumber(attrs, issuerOfAccessionNumber);
        Sequence rqAttrsSeq = attrs.getSequence(Tag.RequestAttributesSequence);
        if (rqAttrsSeq != null)
            for (Attributes rqAttrs : rqAttrsSeq)
                adjustAccessionNumber(rqAttrs, issuerOfAccessionNumber);
    }

    private void adjustAccessionNumber(Attributes attrs, Issuer destIssuer) {
        if (!attrs.containsValue(Tag.AccessionNumber))
            return;

        Attributes item = attrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence);
        if (item == null)
            return;

        Issuer issuer = new Issuer(item);
        if (!issuer.matches(destIssuer)) {
            attrs.setNull(Tag.AccessionNumber, VR.SH);
            attrs.remove(Tag.IssuerOfAccessionNumberSequence);
        }
    }

 }
