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

package org.dcm4chee.archive.store;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Code;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.net.Device;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
class Supplements {

    public static void supplementComposite(Attributes ds, Device device) {
        supplementValue(ds, Tag.Manufacturer, VR.LO, device.getManufacturer());
        supplementValue(ds, Tag.ManufacturerModelName, VR.LO, device.getManufacturerModelName());
        supplementValue(ds, Tag.StationName, VR.SH, device.getStationName());
        supplementValue(ds, Tag.DeviceSerialNumber, VR.LO, device.getDeviceSerialNumber());
        supplementValues(ds, Tag.SoftwareVersions, VR.LO, device.getSoftwareVersions());
        supplementValue(ds, Tag.InstitutionName, VR.LO, device.getInstitutionNames());
        supplementCode(ds, Tag.InstitutionCodeSequence, device.getInstitutionCodes());
        supplementValue(ds, Tag.InstitutionalDepartmentName, VR.LO,
        device.getInstitutionalDepartmentNames());
        supplementIssuers(ds, device);
        supplementRequestIssuers(ds, device);
        supplementRequestIssuers(ds.getSequence(Tag.RequestAttributesSequence),
                device);
    }

    private static void supplementIssuers(Attributes ds, Device device) {
        if (ds.containsValue(Tag.PatientID))
            supplementIssuerOfPatientID(ds, device.getIssuerOfPatientID());
        if (ds.containsValue(Tag.AdmissionID))
            supplementIssuer(ds, Tag.IssuerOfAdmissionIDSequence,
                    device.getIssuerOfAdmissionID());
        if (ds.containsValue(Tag.ServiceEpisodeID))
            supplementIssuer(ds, Tag.IssuerOfServiceEpisodeID,
                    device.getIssuerOfServiceEpisodeID());
        if (ds.containsValue(Tag.ContainerIdentifier))
            supplementIssuer(ds, Tag.IssuerOfTheContainerIdentifierSequence,
                    device.getIssuerOfContainerIdentifier());
        if (ds.containsValue(Tag.SpecimenIdentifier))
            supplementIssuer(ds, Tag.IssuerOfTheSpecimenIdentifierSequence,
                    device.getIssuerOfSpecimenIdentifier());
    }

    private static void supplementRequestIssuers(Sequence rqSeq, Device device) {
        if (rqSeq != null)
            for (Attributes rq : rqSeq)
                supplementRequestIssuers(rq, device);
    }

    private static void supplementRequestIssuers(Attributes rq, Device device) {
        if (rq.containsValue(Tag.AccessionNumber))
            supplementIssuer(rq, Tag.IssuerOfAccessionNumberSequence,
                    device.getIssuerOfAccessionNumber());
        if (rq.containsValue(Tag.PlacerOrderNumberImagingServiceRequest))
            supplementIssuer(rq, Tag.OrderPlacerIdentifierSequence,
                    device.getOrderPlacerIdentifier());
        if (rq.containsValue(Tag.FillerOrderNumberImagingServiceRequest))
            supplementIssuer(rq, Tag.OrderFillerIdentifierSequence,
                    device.getOrderFillerIdentifier());
    }

    public static void supplementMPPS(Attributes mpps, Device device) {
        supplementIssuers(mpps, device);
        supplementRequestIssuers(
                mpps.getSequence(Tag.ScheduledStepAttributesSequence),
                device);
    }

    public static boolean supplementIssuerOfPatientID(Attributes ds, Issuer issuer) {
        if (issuer == null
                || ds.containsValue(Tag.IssuerOfPatientID) 
                || ds.containsValue(Tag.IssuerOfPatientIDQualifiersSequence))
            return false;
        
        String localNamespaceEntityID = issuer.getLocalNamespaceEntityID();
        if (localNamespaceEntityID != null)
            ds.setString(Tag.IssuerOfPatientID, VR.LO, localNamespaceEntityID);
        String universalEntityID = issuer.getUniversalEntityID();
        if (universalEntityID != null) {
            Attributes item = new Attributes(ds.bigEndian(), 2);
            item.setString(Tag.UniversalEntityID, VR.UT, universalEntityID);
            item.setString(Tag.UniversalEntityIDType, VR.CS,
                    issuer.getUniversalEntityIDType());
            ds.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1).add(item);
        }
        return true;
    }

    private static boolean supplementValue(Attributes ds, int tag, VR vr,
            String... values) {
        if (values.length == 0 || values[0] == null
                || ds.containsValue(tag))
            return false;

        ds.setString(tag, vr, values[0]);
        return true;
    }

    private static boolean supplementValues(Attributes ds, int tag, VR vr,
            String... values) {
        if (values.length == 0
                || ds.containsValue(tag))
            return false;

        ds.setString(tag, vr, values);
        return true;
    }

    public static boolean supplementIssuer(Attributes ds, int seqTag, Issuer issuer) {
        if (issuer == null || ds.containsValue(seqTag))
            return false;

        Attributes item = new Attributes(ds.bigEndian(), 3);
        String localNamespaceEntityID = issuer.getLocalNamespaceEntityID();
        if (localNamespaceEntityID != null)
            item.setString(Tag.LocalNamespaceEntityID, VR.LO, localNamespaceEntityID);
        String universalEntityID = issuer.getUniversalEntityID();
        if (universalEntityID != null) {
            item.setString(Tag.UniversalEntityID, VR.UT, universalEntityID);
            item.setString(Tag.UniversalEntityIDType, VR.CS,
                    issuer.getUniversalEntityIDType());
        }
        ds.newSequence(seqTag, 1).add(item);
        return true;
    }

    public static boolean supplementCode(Attributes ds, int seqTag, Code... codes) {
        if (codes.length == 0 || codes[0] == null || ds.containsValue(seqTag))
            return false;

        Attributes item = new Attributes(ds.bigEndian(), 4);
        item.setString(Tag.CodeValue, VR.SH, codes[0].getCodeValue());
        item.setString(Tag.CodingSchemeDesignator, VR.SH,
                codes[0].getCodingSchemeDesignator());
        String version = codes[0].getCodingSchemeVersion();
        if (version != null)
            item.setString(Tag.CodingSchemeVersion, VR.SH, version);
        item.setString(Tag.CodeMeaning, VR.LO, codes[0].getCodeMeaning());
        ds.newSequence(seqTag, 1).add(item);
        return true;
    }

}
