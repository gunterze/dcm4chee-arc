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

package org.dcm4chee.archive.conf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;

import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.AttributeCoercions;
import org.dcm4che.imageio.codec.CompressionRule;
import org.dcm4che.imageio.codec.CompressionRules;
import org.dcm4che.io.TemplatesCache;
import org.dcm4che.net.AEExtension;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.QueryOption;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ArchiveAEExtension extends AEExtension {

    private static final long serialVersionUID = -2390448404282661045L;

    public static final int DEF_RETRY_INTERVAL = 60;

    private String modifyingSystem;
    private String[] retrieveAETs;
    private String externalRetrieveAET;
    private String fileSystemGroupID;
    private String initFileSystemURI;
    private String digestAlgorithm;
    private String spoolDirectoryPath;
    private AttributesFormat storageFilePathFormat;
    private boolean storeOriginalAttributes;
    private boolean suppressWarningCoercionOfDataElements;
    private boolean preserveSpoolFileOnFailure;
    private boolean matchUnknown;
    private boolean sendPendingCGet;
    private int sendPendingCMoveInterval;
    private int storageCommitmentDelay;
    private int storageCommitmentMaxRetries;
    private int storageCommitmentRetryInterval = DEF_RETRY_INTERVAL;
    private String[] forwardMPPSDestinations = {};
    private int forwardMPPSMaxRetries;
    private int forwardMPPSRetryInterval = DEF_RETRY_INTERVAL;
    private String[] ianDestinations = {};
    private int ianMaxRetries;
    private int ianRetryInterval = DEF_RETRY_INTERVAL;
    private final List<StoreDuplicate> storeDuplicates  = new ArrayList<StoreDuplicate>();
    private final AttributeCoercions attributeCoercions = new AttributeCoercions();
    private final CompressionRules compressionRules = new CompressionRules();
    private boolean returnOtherPatientIDs;
    private boolean returnOtherPatientNames;
    private boolean showRejectedInstances;
    private String pixManagerApplication;
    private String pixConsumerApplication;
    private int qidoMaxNumberOfResults;

    public AttributeCoercion getAttributeCoercion(String sopClass,
            Dimse dimse, Role role, String aeTitle) {
        return attributeCoercions.findAttributeCoercion(sopClass, dimse, role, aeTitle);
    }

    public AttributeCoercions getAttributeCoercions() {
        return attributeCoercions;
    }

    public void addAttributeCoercion(AttributeCoercion ac) {
        attributeCoercions.add(ac);
    }

    public void setAttributeCoercions(AttributeCoercions acs) {
        attributeCoercions.clear();
        attributeCoercions.add(acs);
    }

    public boolean removeAttributeCoercion(AttributeCoercion ac) {
        return attributeCoercions.remove(ac);
    }

    public CompressionRules getCompressionRules() {
        return compressionRules;
    }

    public void addCompressionRule(CompressionRule rule) {
        compressionRules.add(rule);
    }

    public void setCompressionRules(CompressionRules rules) {
        compressionRules.clear();
        compressionRules.add(rules);
    }

    public boolean removeCompressionRule(CompressionRule ac) {
        return compressionRules.remove(ac);
    }

    public List<StoreDuplicate> getStoreDuplicates() {
        return storeDuplicates;
    }

    public void addStoreDuplicate(StoreDuplicate storeDuplicate) {
        storeDuplicates.add(storeDuplicate);
    }

    public void setStoreDuplicates(Collection<StoreDuplicate> storeDuplicates) {
        storeDuplicates.clear();
        storeDuplicates.addAll(storeDuplicates);
    }

    public boolean removeStoreDuplicate(StoreDuplicate storeDuplicate) {
        return storeDuplicates.remove(storeDuplicate);
    }

    public String getModifyingSystem() {
        return modifyingSystem;
    }

    public String getEffectiveModifyingSystem() {
        return modifyingSystem != null 
                ? modifyingSystem
                : getApplicationEntity().getDevice().getDeviceName();
    }

    public void setModifyingSystem(String modifyingSystem) {
        this.modifyingSystem = modifyingSystem;
    }

    public String[] getRetrieveAETs() {
        return retrieveAETs;
    }

    public void setRetrieveAETs(String... retrieveAETs) {
        this.retrieveAETs = retrieveAETs;
    }

    public String getExternalRetrieveAET() {
        return externalRetrieveAET;
    }

    public void setExternalRetrieveAET(String externalRetrieveAET) {
        this.externalRetrieveAET = externalRetrieveAET;
    }

    public String getFileSystemGroupID() {
        return fileSystemGroupID;
    }

    public void setFileSystemGroupID(String fileSystemGroupID) {
        this.fileSystemGroupID = fileSystemGroupID;
    }

    public String getInitFileSystemURI() {
        return initFileSystemURI;
    }

    public void setInitFileSystemURI(String initFileSystemURI) {
        this.initFileSystemURI = initFileSystemURI;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public String getSpoolDirectoryPath() {
        return spoolDirectoryPath;
    }

    public void setSpoolDirectoryPath(String spoolDirectoryPath) {
        this.spoolDirectoryPath = spoolDirectoryPath;
    }

    public AttributesFormat getStorageFilePathFormat() {
        return storageFilePathFormat;
    }

    public void setStorageFilePathFormat(AttributesFormat storageFilePathFormat) {
        this.storageFilePathFormat = storageFilePathFormat;
    }

    public Templates getAttributeCoercionTemplates(String cuid, Dimse dimse,
            TransferCapability.Role role, String aet) throws TransformerConfigurationException {
        AttributeCoercion ac = getAttributeCoercion(cuid, dimse, role, aet);
        return ac != null 
                ? TemplatesCache.getDefault().get(
                        StringUtils.replaceSystemProperties(ac.getURI()))
                : null;
    }

    public boolean isStoreOriginalAttributes() {
        return storeOriginalAttributes;
    }

    public void setStoreOriginalAttributes(boolean storeOriginalAttributes) {
        this.storeOriginalAttributes = storeOriginalAttributes;
    }

    public boolean isSuppressWarningCoercionOfDataElements() {
        return suppressWarningCoercionOfDataElements;
    }

    public void setSuppressWarningCoercionOfDataElements(
            boolean suppressWarningCoercionOfDataElements) {
        this.suppressWarningCoercionOfDataElements = suppressWarningCoercionOfDataElements;
    }

    public boolean isPreserveSpoolFileOnFailure() {
        return preserveSpoolFileOnFailure;
    }

    public void setPreserveSpoolFileOnFailure(boolean preserveSpoolFileOnFailure) {
        this.preserveSpoolFileOnFailure = preserveSpoolFileOnFailure;
    }

    public boolean isMatchUnknown() {
        return matchUnknown;
    }

    public void setMatchUnknown(boolean matchUnknown) {
        this.matchUnknown = matchUnknown;
    }

    public boolean isSendPendingCGet() {
        return sendPendingCGet;
    }

    public void setSendPendingCGet(boolean sendPendingCGet) {
        this.sendPendingCGet = sendPendingCGet;
    }

    public int getSendPendingCMoveInterval() {
        return sendPendingCMoveInterval;
    }

    public void setSendPendingCMoveInterval(int sendPendingCMoveInterval) {
        this.sendPendingCMoveInterval = sendPendingCMoveInterval;
    }

    public final int getStorageCommitmentDelay() {
        return storageCommitmentDelay;
    }

    public final void setStorageCommitmentDelay(int storageCommitmentDelay) {
        this.storageCommitmentDelay = storageCommitmentDelay;
    }

    public final int getStorageCommitmentMaxRetries() {
        return storageCommitmentMaxRetries;
    }

    public final void setStorageCommitmentMaxRetries(int storageCommitmentMaxRetries) {
        this.storageCommitmentMaxRetries = storageCommitmentMaxRetries;
    }

    public final int getStorageCommitmentRetryInterval() {
        return storageCommitmentRetryInterval;
    }

    public final void setStorageCommitmentRetryInterval(
            int storageCommitmentRetryInterval) {
        this.storageCommitmentRetryInterval = storageCommitmentRetryInterval;
    }

    public final String[] getForwardMPPSDestinations() {
        return forwardMPPSDestinations;
    }

    public final void setForwardMPPSDestinations(String[] forwardMPPSDestinations) {
        this.forwardMPPSDestinations = forwardMPPSDestinations;
    }

    public final int getForwardMPPSMaxRetries() {
        return forwardMPPSMaxRetries;
    }

    public final void setForwardMPPSMaxRetries(int forwardMPPSMaxRetries) {
        this.forwardMPPSMaxRetries = forwardMPPSMaxRetries;
    }

    public final int getForwardMPPSRetryInterval() {
        return forwardMPPSRetryInterval;
    }

    public final void setForwardMPPSRetryInterval(int forwardMPPSRetryInterval) {
        this.forwardMPPSRetryInterval = forwardMPPSRetryInterval;
    }

    public String[] getIANDestinations() {
        return ianDestinations;
    }

    public void setIANDestinations(String[] ianDestinations) {
        this.ianDestinations = ianDestinations;
    }

    public boolean hasIANDestinations() {
        return ianDestinations.length > 0;
    }

    public int getIANMaxRetries() {
        return ianMaxRetries;
    }

    public void setIANMaxRetries(int ianMaxRetries) {
        this.ianMaxRetries = ianMaxRetries;
    }

    public int getIANRetryInterval() {
        return ianRetryInterval;
    }

    public void setIANRetryInterval(int ianRetryInterval) {
        this.ianRetryInterval = ianRetryInterval;
    }

    public boolean isReturnOtherPatientIDs() {
        return returnOtherPatientIDs;
    }

    public void setReturnOtherPatientIDs(boolean returnOtherPatientIDs) {
        this.returnOtherPatientIDs = returnOtherPatientIDs;
    }

    public boolean isReturnOtherPatientNames() {
        return returnOtherPatientNames;
    }

    public void setReturnOtherPatientNames(boolean returnOtherPatientNames) {
        this.returnOtherPatientNames = returnOtherPatientNames;
    }

    public boolean isShowRejectedInstances() {
        return showRejectedInstances;
    }

    public void setShowRejectedInstances(boolean showRejectedInstances) {
        this.showRejectedInstances = showRejectedInstances;
    }

    public String getRemotePIXManagerApplication() {
        return pixManagerApplication;
    }

    public void setRemotePIXManagerApplication(String appName) {
        this.pixManagerApplication = appName;
    }

    public String getLocalPIXConsumerApplication() {
        return pixConsumerApplication;
    }

    public void setLocalPIXConsumerApplication(String appName) {
        this.pixConsumerApplication = appName;
    }

    public int getQIDOMaxNumberOfResults() {
        return qidoMaxNumberOfResults;
    }

    public void setQIDOMaxNumberOfResults(int qidoMaxNumberOfResults) {
        this.qidoMaxNumberOfResults = qidoMaxNumberOfResults;
    }

    @Override
    public void reconfigure(AEExtension from) {
        ArchiveAEExtension arcae = (ArchiveAEExtension) from;
        setModifyingSystem(arcae.modifyingSystem);
        setRetrieveAETs(arcae.retrieveAETs);
        setExternalRetrieveAET(arcae.externalRetrieveAET);
        setFileSystemGroupID(arcae.fileSystemGroupID);
        setInitFileSystemURI(arcae.initFileSystemURI);
        setDigestAlgorithm(arcae.digestAlgorithm);
        setSpoolDirectoryPath(arcae.spoolDirectoryPath);
        setStorageFilePathFormat(arcae.storageFilePathFormat);
        setStoreOriginalAttributes(arcae.storeOriginalAttributes);
        setPreserveSpoolFileOnFailure(arcae.preserveSpoolFileOnFailure);
        setSuppressWarningCoercionOfDataElements(arcae.suppressWarningCoercionOfDataElements);
        setMatchUnknown(arcae.matchUnknown);
        setSendPendingCGet(arcae.sendPendingCGet);
        setSendPendingCMoveInterval(arcae.sendPendingCMoveInterval);
        setStorageCommitmentDelay(arcae.storageCommitmentDelay);
        setStorageCommitmentMaxRetries(arcae.storageCommitmentMaxRetries);
        setStorageCommitmentRetryInterval(arcae.storageCommitmentRetryInterval);
        setForwardMPPSDestinations(arcae.forwardMPPSDestinations);
        setForwardMPPSMaxRetries(arcae.forwardMPPSMaxRetries);
        setForwardMPPSRetryInterval(arcae.forwardMPPSRetryInterval);
        setIANDestinations(arcae.ianDestinations);
        setIANMaxRetries(arcae.ianMaxRetries);
        setIANRetryInterval(arcae.ianRetryInterval);
        setReturnOtherPatientIDs(arcae.returnOtherPatientIDs);
        setReturnOtherPatientNames(arcae.returnOtherPatientNames);
        setShowRejectedInstances(arcae.showRejectedInstances);
        setRemotePIXManagerApplication(arcae.pixManagerApplication);
        setLocalPIXConsumerApplication(arcae.pixConsumerApplication);
        setQIDOMaxNumberOfResults(arcae.qidoMaxNumberOfResults);
        setStoreDuplicates(arcae.getStoreDuplicates());
        setAttributeCoercions(arcae.getAttributeCoercions());
        setCompressionRules(arcae.getCompressionRules());
    }

    public StoreParam getStoreParam() {
        StoreParam storeParam = ae.getDevice()
                .getDeviceExtension(ArchiveDeviceExtension.class)
                .getStoreParam();
        storeParam.setStoreOriginalAttributes(storeOriginalAttributes);
        storeParam.setModifyingSystem(getEffectiveModifyingSystem());
        storeParam.setRetrieveAETs(retrieveAETs);
        storeParam.setExternalRetrieveAET(externalRetrieveAET);
        storeParam.setStoreDuplicates(storeDuplicates);
        return storeParam;
    }

    public QueryParam getQueryParam(EnumSet<QueryOption> queryOpts,
            String[] accessControlIDs) {
        ArchiveDeviceExtension devExt = ae.getDevice()
                .getDeviceExtension(ArchiveDeviceExtension.class);
        QueryParam queryParam = new QueryParam();
        queryParam.setFuzzyStr(devExt.getFuzzyStr());
        queryParam.setAttributeFilters(devExt.getAttributeFilters());
        queryParam.setCombinedDatetimeMatching(queryOpts
                .contains(QueryOption.DATETIME));
        queryParam.setFuzzySemanticMatching(queryOpts
                .contains(QueryOption.FUZZY));
        queryParam.setMatchUnknown(matchUnknown);
        queryParam.setAccessControlIDs(accessControlIDs);
        queryParam.setShowRejectedInstances(showRejectedInstances);
        queryParam.setReturnOtherPatientIDs(returnOtherPatientIDs);
        queryParam.setReturnOtherPatientNames(returnOtherPatientNames);

        return queryParam;
    }

}
