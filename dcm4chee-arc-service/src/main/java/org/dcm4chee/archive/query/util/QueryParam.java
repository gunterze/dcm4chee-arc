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

package org.dcm4chee.archive.query.util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.dcm4che.data.Issuer;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.QueryOption;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4chee.archive.conf.ArchiveApplicationEntity;
import org.dcm4chee.archive.conf.ArchiveDevice;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.RejectionNote;
import org.dcm4chee.archive.entity.Code;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class QueryParam {

    private FuzzyStr fuzzyStr;
    private AttributeFilter[] attributeFilters;
    private boolean combinedDatetimeMatching;
    private boolean fuzzySemanticMatching;
    private boolean matchUnknown;
    private List<Code> hideConceptNameCodes;
    private List<Code> hideRejectionCodes;
    private String[] roles;
    private boolean returnOtherPatientIDs;
    private Issuer defaultIssuerOfPatientID;
    private Issuer defaultIssuerOfAccessionNumber;

    public final boolean isCombinedDatetimeMatching() {
        return combinedDatetimeMatching;
    }

    public final void setCombinedDatetimeMatching(boolean combinedDatetimeMatching) {
        this.combinedDatetimeMatching = combinedDatetimeMatching;
    }

    public final boolean isFuzzySemanticMatching() {
        return fuzzySemanticMatching;
    }

    public final void setFuzzySemanticMatching(boolean fuzzySemanticMatching) {
        this.fuzzySemanticMatching = fuzzySemanticMatching;
    }

    public final boolean isMatchUnknown() {
        return matchUnknown;
    }

    public final void setMatchUnknown(boolean matchUnknown) {
        this.matchUnknown = matchUnknown;
    }

    public final String[] getRoles() {
        return roles != null ? roles.clone() : null;
    }

    public final void setRoles(String... roles) {
        this.roles = roles != null ? roles.clone() : null;
    }

    public final void setFuzzyStr(FuzzyStr fuzzyStr) {
        this.fuzzyStr = fuzzyStr;
    }

    public final FuzzyStr getFuzzyStr() {
        return fuzzyStr;
    }

    public final void setAttributeFilters(AttributeFilter[] attributeFilters) {
        this.attributeFilters = attributeFilters;
    }

    public final AttributeFilter[] getAttributeFilters() {
        return attributeFilters;
    }

    public List<Code> getHideConceptNameCodes() {
        return hideConceptNameCodes;
    }

    public List<Code> getHideRejectionCodes() {
        return hideRejectionCodes;
    }

    public void setRejectionNotes(List<RejectionNote> rejectionNotes) {
        this.hideConceptNameCodes = codesForAction(
                rejectionNotes, RejectionNote.Action.HIDE_REJECTION_NOTE);
        this.hideRejectionCodes = codesForAction(
                rejectionNotes, RejectionNote.Action.HIDE_REJECTED_INSTANCES);
    }

    private List<Code> codesForAction(List<RejectionNote> rns,
            RejectionNote.Action action) {
        List<Code> codes = new ArrayList<Code>(rns.size());
        for (RejectionNote rn : rns) {
            if (rn.getActions().contains(action))
                codes.add((Code) rn.getCode());
        }
        return codes ;
    }

    public boolean isReturnOtherPatientIDs() {
        return returnOtherPatientIDs;
    }

    public void setReturnOtherPatientIDs(boolean returnOtherPatientIDs) {
        this.returnOtherPatientIDs = returnOtherPatientIDs;
    }

    public Issuer getDefaultIssuerOfPatientID() {
        return defaultIssuerOfPatientID;
    }

    public void setDefaultIssuerOfPatientID(Issuer issuer) {
        this.defaultIssuerOfPatientID = issuer;
    }

    public Issuer getDefaultIssuerOfAccessionNumber() {
        return defaultIssuerOfAccessionNumber;
    }

    public void setDefaultIssuerOfAccessionNumber(Issuer issuer) {
        this.defaultIssuerOfAccessionNumber = issuer;
    }

    public static QueryParam valueOf(ArchiveApplicationEntity ae,
            EnumSet<QueryOption> queryOpts, ApplicationEntity sourceAE,
            String[] roles) throws Exception {
        ArchiveDevice dev = ae.getArchiveDevice();
        QueryParam queryParam = new QueryParam();
        queryParam.setFuzzyStr(dev.getFuzzyStr());
        queryParam.setAttributeFilters(dev.getAttributeFilters());
        queryParam.setCombinedDatetimeMatching(queryOpts
                .contains(QueryOption.DATETIME));
        queryParam.setFuzzySemanticMatching(queryOpts
                .contains(QueryOption.FUZZY));
        queryParam.setMatchUnknown(ae.isMatchUnknown());
        queryParam.setRoles(roles);
        queryParam.setRejectionNotes(ae.getRejectionNotes());
        queryParam.setReturnOtherPatientIDs(ae.isReturnOtherPatientIDs());

        if (sourceAE != null) {
            Device sourceDevice = sourceAE.getDevice();
            queryParam.setDefaultIssuerOfPatientID(sourceDevice
                    .getIssuerOfPatientID());
            queryParam.setDefaultIssuerOfAccessionNumber(sourceDevice
                    .getIssuerOfAccessionNumber());
        }
        return queryParam;
    }

}
