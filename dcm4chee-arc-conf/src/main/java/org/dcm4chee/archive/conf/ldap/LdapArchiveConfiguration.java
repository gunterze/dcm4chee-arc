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

package org.dcm4chee.archive.conf.ldap;

import static org.dcm4che.conf.ldap.LdapDicomConfiguration.booleanValue;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.hasObjectClass;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.intValue;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.safeClose;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.storeDiff;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.storeNotDef;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.storeNotEmpty;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.storeNotNull;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.stringArray;
import static org.dcm4che.conf.ldap.LdapDicomConfiguration.stringValue;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchResult;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.ldap.LdapDicomConfiguration;
import org.dcm4che.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che.conf.ldap.hl7.LdapHL7ConfigurationExtension;
import org.dcm4che.data.Code;
import org.dcm4che.data.ValueSelector;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.hl7.HL7Application;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.conf.StoreDuplicate;
import org.dcm4chee.archive.conf.StoreDuplicate.Condition;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class LdapArchiveConfiguration extends LdapDicomConfigurationExtension
    implements LdapHL7ConfigurationExtension {

    @Override
    protected void storeTo(Device device, Attributes attrs) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;
        
        attrs.get("objectclass").add("dcmArchiveDevice");
        storeNotNull(attrs, "dcmIncorrectWorklistEntrySelectedCode",
                arcDev.getIncorrectWorklistEntrySelectedCode());
        storeNotNull(attrs, "dcmRejectedForQualityReasonsCode",
                arcDev.getRejectedForQualityReasonsCode());
        storeNotNull(attrs, "dcmRejectedForPatientSafetyReasonsCode",
                arcDev.getRejectedForPatientSafetyReasonsCode());
        storeNotNull(attrs, "dcmIncorrectModalityWorklistEntryCode",
                arcDev.getIncorrectModalityWorklistEntryCode());
        storeNotNull(attrs, "dcmDataRetentionPeriodExpiredCode",
                arcDev.getDataRetentionPeriodExpiredCode());
        storeNotNull(attrs, "dcmFuzzyAlgorithmClass",
                arcDev.getFuzzyAlgorithmClass());
        storeNotDef(attrs, "dcmConfigurationStaleTimeout",
                arcDev.getConfigurationStaleTimeout(), 0);
    }

    @Override
    protected void storeChilds(String deviceDN, Device device) throws NamingException {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        for (Entity entity : Entity.values())
            config.createSubcontext(
                    LdapDicomConfiguration.dnOf("dcmEntity", entity.toString(), deviceDN),
                    storeTo(arcDev.getAttributeFilter(entity), entity, new BasicAttributes(true)));
    }

    @Override
    protected void storeChilds(String aeDN, ApplicationEntity ae) throws NamingException {
        ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        if (arcAE == null)
            return;

        config.store(arcAE.getAttributeCoercions(), aeDN);
        for (StoreDuplicate sd : arcAE.getStoreDuplicates())
            config.createSubcontext(dnOf(sd, aeDN),
                    storeTo(sd, new BasicAttributes(true)));
    }

    private String dnOf(StoreDuplicate sd, String aeDN) {
        StringBuilder sb = new StringBuilder();
        sb.append("dcmStoreDuplicateCondition=").append(sd.getCondition());
        sb.append(',').append(aeDN);
        return sb.toString();
    }

    private Attributes storeTo(StoreDuplicate sd, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStoreDuplicate");
        storeNotNull(attrs, "dcmStoreDuplicateCondition", sd.getCondition());
        storeNotNull(attrs, "dcmStoreDuplicateAction", sd.getAction());
        return attrs;
    }

    private static Attributes storeTo(AttributeFilter filter, Entity entity, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmAttributeFilter");
        attrs.put("dcmEntity", entity.name());
        attrs.put(tagsAttr("dcmTag", filter.getSelection()));
        storeNotNull(attrs, "dcmCustomAttribute1", filter.getCustomAttribute1());
        storeNotNull(attrs, "dcmCustomAttribute2", filter.getCustomAttribute2());
        storeNotNull(attrs, "dcmCustomAttribute3", filter.getCustomAttribute3());
        return attrs;
    }

    private static Attribute tagsAttr(String attrID, int[] tags) {
        Attribute attr = new BasicAttribute(attrID);
        for (int tag : tags)
            attr.add(TagUtils.toHexString(tag));
        return attr;
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Attributes attrs) {
        ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        if (arcAE == null)
            return;

        attrs.get("objectclass").add("dcmArchiveNetworkAE");
        storeNotNull(attrs, "dcmFileSystemGroupID", arcAE.getFileSystemGroupID());
        storeNotNull(attrs, "dcmInitFileSystemURI", arcAE.getInitFileSystemURI());
        storeNotNull(attrs, "dcmSpoolFilePathFormat", arcAE.getSpoolFilePathFormat());
        storeNotNull(attrs, "dcmStorageFilePathFormat", arcAE.getStorageFilePathFormat());
        storeNotNull(attrs, "dcmDigestAlgorithm", arcAE.getDigestAlgorithm());
        storeNotNull(attrs, "dcmExternalRetrieveAET", arcAE.getExternalRetrieveAET());
        storeNotEmpty(attrs, "dcmRetrieveAET", arcAE.getRetrieveAETs());
        storeNotDef(attrs, "dcmMatchUnknown", arcAE.isMatchUnknown(), false);
        storeNotDef(attrs, "dcmSendPendingCGet", arcAE.isSendPendingCGet(), false);
        storeNotDef(attrs, "dcmSendPendingCMoveInterval", arcAE.getSendPendingCMoveInterval(), 0);
        storeNotDef(attrs, "dcmSuppressWarningCoercionOfDataElements",
                arcAE.isSuppressWarningCoercionOfDataElements(), false);
        storeNotDef(attrs, "dcmStoreOriginalAttributes",
                arcAE.isStoreOriginalAttributes(), false);
        storeNotDef(attrs, "dcmPreserveSpoolFileOnFailure",
                arcAE.isPreserveSpoolFileOnFailure(), false);
        storeNotNull(attrs, "dcmModifyingSystem", arcAE.getModifyingSystem());
        storeNotDef(attrs, "dcmStgCmtDelay", arcAE.getStorageCommitmentDelay(), 0);
        storeNotDef(attrs, "dcmStgCmtMaxRetries", arcAE.getStorageCommitmentMaxRetries(), 0);
        storeNotDef(attrs, "dcmStgCmtRetryInterval", arcAE.getStorageCommitmentRetryInterval(),
                    ArchiveAEExtension.DEF_RETRY_INTERVAL);
        storeNotEmpty(attrs, "dcmFwdMppsDestination", arcAE.getForwardMPPSDestinations());
        storeNotDef(attrs, "dcmFwdMppsMaxRetries", arcAE.getForwardMPPSMaxRetries(), 0);
        storeNotDef(attrs, "dcmFwdMppsRetryInterval", arcAE.getForwardMPPSRetryInterval(),
                    ArchiveAEExtension.DEF_RETRY_INTERVAL);
        storeNotEmpty(attrs, "dcmIanDestination", arcAE.getIANDestinations());
        storeNotDef(attrs, "dcmIanMaxRetries", arcAE.getIANMaxRetries(), 0);
        storeNotDef(attrs, "dcmIanRetryInterval", arcAE.getIANRetryInterval(),
                    ArchiveAEExtension.DEF_RETRY_INTERVAL);
        storeNotDef(attrs, "dcmReturnOtherPatientIDs", arcAE.isReturnOtherPatientIDs(), false);
        storeNotDef(attrs, "dcmReturnOtherPatientNames", arcAE.isReturnOtherPatientNames(), false);
        storeNotDef(attrs, "dcmShowRejectedInstances", arcAE.isShowRejectedInstances(), false);
        storeNotNull(attrs, "hl7PIXConsumerApplication", arcAE.getLocalPIXConsumerApplication());
        storeNotNull(attrs, "hl7PIXManagerApplication", arcAE.getRemotePIXManagerApplication());
    }

    @Override
    public void storeTo(HL7Application hl7App, String deviceDN, Attributes attrs) {
        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (arcHL7App == null)
            return;

        attrs.get("objectclass").add("dcmArchiveHL7Application");
        storeNotEmpty(attrs, "labeledURI", arcHL7App.getTemplatesURIs());
    }
 
    @Override
    protected void loadFrom(Device device, Attributes attrs)
            throws NamingException, CertificateException {
        if (!hasObjectClass(attrs, "dcmArchiveDevice"))
            return;

        ArchiveDeviceExtension arcdev = new ArchiveDeviceExtension();
        device.addDeviceExtension(arcdev);
        arcdev.setIncorrectWorklistEntrySelectedCode(new Code(
                stringValue(attrs.get("dcmIncorrectWorklistEntrySelectedCode"), null)));
        arcdev.setRejectedForQualityReasonsCode(new Code(
                stringValue(attrs.get("dcmRejectedForQualityReasonsCode"), null)));
        arcdev.setRejectedForPatientSafetyReasonsCode(new Code(
                stringValue(attrs.get("dcmRejectedForPatientSafetyReasonsCode"), null)));
        arcdev.setIncorrectModalityWorklistEntryCode(new Code(
                stringValue(attrs.get("dcmIncorrectModalityWorklistEntryCode"), null)));
        arcdev.setDataRetentionPeriodExpiredCode(new Code(
                stringValue(attrs.get("dcmDataRetentionPeriodExpiredCode"), null)));
        arcdev.setFuzzyAlgorithmClass(stringValue(attrs.get("dcmFuzzyAlgorithmClass"), null));
        arcdev.setConfigurationStaleTimeout(
                intValue(attrs.get("dcmConfigurationStaleTimeout"), 0));
    }

    @Override
    protected void loadChilds(Device device, String deviceDN)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension arcdev =
                device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcdev == null)
            return;

        loadAttributeFilters(arcdev, deviceDN);
    }

    private void loadAttributeFilters(ArchiveDeviceExtension device, String deviceDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = 
                config.search(deviceDN, "(objectclass=dcmAttributeFilter)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                AttributeFilter filter = new AttributeFilter(tags(attrs.get("dcmTag")));
                filter.setCustomAttribute1(valueSelector(attrs.get("dcmCustomAttribute1")));
                filter.setCustomAttribute2(valueSelector(attrs.get("dcmCustomAttribute2")));
                filter.setCustomAttribute3(valueSelector(attrs.get("dcmCustomAttribute3")));
                device.setAttributeFilter(
                        Entity.valueOf(stringValue(attrs.get("dcmEntity"), null)), filter);
            }
        } finally {
            safeClose(ne);
        }
    }

    private static ValueSelector valueSelector(Attribute attr) throws NamingException {
        return attr != null ? ValueSelector.valueOf((String) attr.get()) : null;
   }

    private  static AttributesFormat attributesFormat(Attribute attr) throws NamingException {
        return attr != null ? new AttributesFormat((String) attr.get()) : null;
    }

    protected static int[] tags(Attribute attr) throws NamingException {
        int[] is = new int[attr.size()];
        for (int i = 0; i < is.length; i++)
            is[i] = Integer.parseInt((String) attr.get(i), 16);

        return is;
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Attributes attrs) throws NamingException {
       if (!hasObjectClass(attrs, "dcmArchiveNetworkAE"))
           return;

       ArchiveAEExtension arcae = new ArchiveAEExtension();
       ae.addAEExtension(arcae);
       arcae.setFileSystemGroupID(stringValue(attrs.get("dcmFileSystemGroupID"), null));
       arcae.setInitFileSystemURI(stringValue(attrs.get("dcmInitFileSystemURI"), null));
       arcae.setSpoolFilePathFormat(attributesFormat(attrs.get("dcmSpoolFilePathFormat")));
       arcae.setStorageFilePathFormat(attributesFormat(attrs.get("dcmStorageFilePathFormat")));
       arcae.setDigestAlgorithm(stringValue(attrs.get("dcmDigestAlgorithm"), null));
       arcae.setExternalRetrieveAET(stringValue(attrs.get("dcmExternalRetrieveAET"), null));
       arcae.setRetrieveAETs(stringArray(attrs.get("dcmRetrieveAET")));
       arcae.setMatchUnknown(booleanValue(attrs.get("dcmMatchUnknown"), false));
       arcae.setSendPendingCGet(booleanValue(attrs.get("dcmSendPendingCGet"), false));
       arcae.setSendPendingCMoveInterval(intValue(attrs.get("dcmSendPendingCMoveInterval"), 0));
       arcae.setSuppressWarningCoercionOfDataElements(
               booleanValue(attrs.get("dcmSuppressWarningCoercionOfDataElements"), false));
       arcae.setStoreOriginalAttributes(
               booleanValue(attrs.get("dcmStoreOriginalAttributes"), false));
       arcae.setPreserveSpoolFileOnFailure(
               booleanValue(attrs.get("dcmPreserveSpoolFileOnFailure"), false));
       arcae.setModifyingSystem(stringValue(attrs.get("dcmModifyingSystem"), null));
       arcae.setStorageCommitmentDelay(intValue(attrs.get("dcmStgCmtDelay"), 0));
       arcae.setStorageCommitmentMaxRetries(intValue(attrs.get("dcmStgCmtMaxRetries"), 0));
       arcae.setStorageCommitmentRetryInterval(intValue(attrs.get("dcmStgCmtRetryInterval"),
               ArchiveAEExtension.DEF_RETRY_INTERVAL));
       arcae.setForwardMPPSDestinations(stringArray(attrs.get("dcmFwdMppsDestination")));
       arcae.setForwardMPPSMaxRetries(intValue(attrs.get("dcmFwdMppsMaxRetries"), 0));
       arcae.setForwardMPPSRetryInterval(intValue(attrs.get("dcmFwdMppsRetryInterval"),
               ArchiveAEExtension.DEF_RETRY_INTERVAL));
       arcae.setIANDestinations(stringArray(attrs.get("dcmIanDestination")));
       arcae.setIANMaxRetries(intValue(attrs.get("dcmIanMaxRetries"), 0));
       arcae.setIANRetryInterval(intValue(attrs.get("dcmIanRetryInterval"),
               ArchiveAEExtension.DEF_RETRY_INTERVAL));
       arcae.setReturnOtherPatientIDs(
               booleanValue(attrs.get("dcmReturnOtherPatientIDs"), false));
       arcae.setReturnOtherPatientNames(
               booleanValue(attrs.get("dcmReturnOtherPatientNames"), false));
       arcae.setShowRejectedInstances(
               booleanValue(attrs.get("dcmShowRejectedInstances"), false));
       arcae.setLocalPIXConsumerApplication(stringValue(attrs.get("hl7PIXConsumerApplication"), null));
       arcae.setRemotePIXManagerApplication(stringValue(attrs.get("hl7PIXManagerApplication"), null));
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, String aeDN) throws NamingException {
        ArchiveAEExtension arcae = ae.getAEExtension(ArchiveAEExtension.class);
        if (arcae == null)
            return;

        config.load(arcae.getAttributeCoercions(), aeDN);
        loadStoreDuplicates(arcae.getStoreDuplicates(), aeDN);
    }

    private void loadStoreDuplicates(List<StoreDuplicate> sds, String aeDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(aeDN, "(objectclass=dcmStoreDuplicate)");
        try {
            while (ne.hasMore())
                sds.add(storeDuplicate(ne.next().getAttributes()));
        } finally {
           safeClose(ne);
        }
    }

    private StoreDuplicate storeDuplicate(Attributes attrs) throws NamingException {
        return new StoreDuplicate(
                StoreDuplicate.Condition.valueOf(
                        stringValue(attrs.get("dcmStoreDuplicateCondition"), null)),
                StoreDuplicate.Action.valueOf(
                        stringValue(attrs.get("dcmStoreDuplicateAction"), null)));
    }

    @Override
    public void loadFrom(HL7Application hl7App, Attributes attrs)
            throws NamingException {
        if (!hasObjectClass(attrs, "dcmArchiveHL7Application"))
            return;

       ArchiveHL7ApplicationExtension arcHL7App = new ArchiveHL7ApplicationExtension();
       hl7App.addHL7ApplicationExtension(arcHL7App);
       arcHL7App.setTemplatesURIs(stringArray(attrs.get("labeledURI")));
    }

    @Override
    protected void storeDiffs(Device a, Device b, List<ModificationItem> mods) {
        ArchiveDeviceExtension aa = a.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = b.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        storeDiff(mods, "dcmIncorrectWorklistEntrySelectedCode",
                aa.getIncorrectWorklistEntrySelectedCode(),
                bb.getIncorrectWorklistEntrySelectedCode());
        storeDiff(mods, "dcmRejectedForQualityReasonsCode",
                aa.getRejectedForQualityReasonsCode(),
                bb.getRejectedForQualityReasonsCode());
        storeDiff(mods, "dcmRejectedForPatientSafetyReasonsCode",
                aa.getRejectedForPatientSafetyReasonsCode(),
                bb.getRejectedForPatientSafetyReasonsCode());
        storeDiff(mods, "dcmIncorrectModalityWorklistEntryCode",
                aa.getIncorrectModalityWorklistEntryCode(),
                bb.getIncorrectModalityWorklistEntryCode());
        storeDiff(mods, "dcmDataRetentionPeriodExpiredCode",
                aa.getDataRetentionPeriodExpiredCode(),
                bb.getDataRetentionPeriodExpiredCode());
        storeDiff(mods, "dcmFuzzyAlgorithmClass",
                aa.getFuzzyAlgorithmClass(),
                bb.getFuzzyAlgorithmClass());
        storeDiff(mods, "dcmConfigurationStaleTimeout",
                aa.getConfigurationStaleTimeout(),
                bb.getConfigurationStaleTimeout(),
                0);
    }

    @Override
    protected void storeDiffs(ApplicationEntity a, ApplicationEntity b,
            List<ModificationItem> mods) {
        ArchiveAEExtension aa = a.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = b.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;
        
        storeDiff(mods, "dcmFileSystemGroupID",
                aa.getFileSystemGroupID(),
                bb.getFileSystemGroupID());
        storeDiff(mods, "dcmInitFileSystemURI",
                aa.getInitFileSystemURI(),
                bb.getInitFileSystemURI());
        storeDiff(mods, "dcmSpoolFilePathFormat",
                aa.getSpoolFilePathFormat(),
                bb.getSpoolFilePathFormat());
        storeDiff(mods, "dcmStorageFilePathFormat",
                aa.getStorageFilePathFormat(),
                bb.getStorageFilePathFormat());
        storeDiff(mods, "dcmDigestAlgorithm",
                aa.getDigestAlgorithm(),
                bb.getDigestAlgorithm());
        storeDiff(mods, "dcmExternalRetrieveAET",
                aa.getExternalRetrieveAET(),
                bb.getExternalRetrieveAET());
        storeDiff(mods, "dcmRetrieveAET",
                aa.getRetrieveAETs(),
                bb.getRetrieveAETs());
        storeDiff(mods, "dcmMatchUnknown",
                aa.isMatchUnknown(),
                bb.isMatchUnknown(),
                false);
        storeDiff(mods, "dcmSendPendingCGet",
                aa.isSendPendingCGet(),
                bb.isSendPendingCGet(),
                false);
        storeDiff(mods, "dcmSendPendingCMoveInterval",
                aa.getSendPendingCMoveInterval(),
                bb.getSendPendingCMoveInterval(),
                0);
        storeDiff(mods, "dcmSuppressWarningCoercionOfDataElements",
                aa.isSuppressWarningCoercionOfDataElements(),
                bb.isSuppressWarningCoercionOfDataElements(),
                false);
        storeDiff(mods, "dcmStoreOriginalAttributes",
                aa.isStoreOriginalAttributes(),
                bb.isStoreOriginalAttributes(),
                false);
        storeDiff(mods, "dcmPreserveSpoolFileOnFailure",
                aa.isPreserveSpoolFileOnFailure(),
                bb.isPreserveSpoolFileOnFailure(),
                false);
        storeDiff(mods, "dcmModifyingSystem",
                aa.getModifyingSystem(),
                bb.getModifyingSystem());
        storeDiff(mods, "dcmStgCmtDelay",
                aa.getStorageCommitmentDelay(),
                bb.getStorageCommitmentDelay(),
                0);
        storeDiff(mods, "dcmStgCmtMaxRetries",
                aa.getStorageCommitmentMaxRetries(),
                bb.getStorageCommitmentMaxRetries(),
                0);
        storeDiff(mods, "dcmStgCmtRetryInterval",
                aa.getStorageCommitmentRetryInterval(),
                bb.getStorageCommitmentRetryInterval(),
                ArchiveAEExtension.DEF_RETRY_INTERVAL);
        storeDiff(mods, "dcmFwdMppsDestination",
                aa.getForwardMPPSDestinations(),
                bb.getForwardMPPSDestinations());
        storeDiff(mods, "dcmFwdMppsMaxRetries",
                aa.getForwardMPPSMaxRetries(),
                bb.getForwardMPPSMaxRetries(),
                0);
        storeDiff(mods, "dcmFwdMppsRetryInterval",
                aa.getForwardMPPSRetryInterval(),
                bb.getForwardMPPSRetryInterval(),
                ArchiveAEExtension.DEF_RETRY_INTERVAL);
        storeDiff(mods, "dcmIanDestination",
                aa.getIANDestinations(),
                bb.getIANDestinations());
        storeDiff(mods, "dcmIanMaxRetries",
                aa.getIANMaxRetries(),
                bb.getIANMaxRetries(),
                0);
        storeDiff(mods, "dcmIanRetryInterval",
                aa.getIANRetryInterval(),
                bb.getIANRetryInterval(),
                ArchiveAEExtension.DEF_RETRY_INTERVAL);
        storeDiff(mods, "dcmReturnOtherPatientIDs",
                aa.isReturnOtherPatientIDs(),
                bb.isReturnOtherPatientIDs(),
                false);
        storeDiff(mods, "dcmReturnOtherPatientNames",
                aa.isReturnOtherPatientNames(),
                bb.isReturnOtherPatientNames(),
                false);
        storeDiff(mods, "dcmShowRejectedInstances",
                aa.isShowRejectedInstances(),
                bb.isShowRejectedInstances(),
                false);
        storeDiff(mods, "hl7PIXConsumerApplication",
                aa.getLocalPIXConsumerApplication(),
                bb.getLocalPIXConsumerApplication());
        storeDiff(mods, "hl7PIXManagerApplication",
                aa.getRemotePIXManagerApplication(),
                bb.getRemotePIXManagerApplication());
    }

    @Override
    public void storeDiffs(HL7Application a, HL7Application b,
            List<ModificationItem> mods) {
        ArchiveHL7ApplicationExtension aa =
                a.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        ArchiveHL7ApplicationExtension bb =
                b.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (aa == null || bb == null)
            return;

        storeDiff(mods, "labeledURI",
                aa.getTemplatesURIs(),
                bb.getTemplatesURIs());
    }

    @Override
    protected void mergeChilds(Device prev, Device device, String deviceDN)
            throws NamingException {
        ArchiveDeviceExtension aa =
                prev.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = 
                device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        for (Entity entity : Entity.values())
            config.modifyAttributes(
                    LdapDicomConfiguration.dnOf("dcmEntity", entity.toString(), deviceDN),
                    storeDiffs(aa.getAttributeFilter(entity), bb.getAttributeFilter(entity),
                            new ArrayList<ModificationItem>()));
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, String aeDN)
            throws NamingException {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;

        config.merge(aa.getAttributeCoercions(), bb.getAttributeCoercions(), aeDN);
        mergeStoreDuplicates(aa.getStoreDuplicates(), bb.getStoreDuplicates(), aeDN);
    }

    private void mergeStoreDuplicates(List<StoreDuplicate> prevs, List<StoreDuplicate> acs, String parentDN)
            throws NamingException {
        for (StoreDuplicate prev : prevs)
            if (findByCondition(acs, prev.getCondition()) == null)
                config.destroySubcontext(dnOf(prev, parentDN));
        for (StoreDuplicate rn : acs) {
            String dn = dnOf(rn, parentDN);
            StoreDuplicate prev = findByCondition(prevs, rn.getCondition());
            if (prev == null)
                config.createSubcontext(dn, storeTo(rn, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prev, rn, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(StoreDuplicate prev,
            StoreDuplicate sd, ArrayList<ModificationItem> mods) {
        storeDiff(mods, "dcmRejectionAction",
                prev.getAction(),
                sd.getAction());
        return mods;
    }

    private StoreDuplicate findByCondition(List<StoreDuplicate> sds, Condition condition) {
       for (StoreDuplicate other : sds)
           if (other.getCondition() == condition)
               return other;
       return null;
    }

    private List<ModificationItem> storeDiffs(AttributeFilter prev,
            AttributeFilter filter, List<ModificationItem> mods) {
        storeDiffTags(mods, "dcmTag", 
                prev.getSelection(),
                filter.getSelection());
        storeDiff(mods, "dcmCustomAttribute1",
                prev.getCustomAttribute1(),
                filter.getCustomAttribute1());
        storeDiff(mods, "dcmCustomAttribute2",
                prev.getCustomAttribute2(),
                filter.getCustomAttribute2());
        storeDiff(mods, "dcmCustomAttribute3",
                prev.getCustomAttribute3(),
                filter.getCustomAttribute3());
        return mods;
    }

    private void storeDiffTags(List<ModificationItem> mods, String attrId,
            int[] prevs, int[] vals) {
        if (!Arrays.equals(prevs, vals))
            mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                    tagsAttr(attrId, vals)));
    }

}
