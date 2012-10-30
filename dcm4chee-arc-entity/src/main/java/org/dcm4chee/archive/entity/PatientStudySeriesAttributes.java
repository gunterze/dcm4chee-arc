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
package org.dcm4chee.archive.entity;

import org.dcm4che.data.Attributes;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class PatientStudySeriesAttributes {
    
    private final Long studyPk;
    private int numberOfStudyRelatedSeries;
    private int numberOfStudyRelatedInstances;
    private int numberOfStudyRelatedRejectedInstances;
    private int numberOfSeriesRelatedInstances;
    private int numberOfSeriesRelatedRejectedInstances;
    private String modalitiesInStudy;
    private String sopClassesInStudy;
    private final byte[] seriesAttrs;
    private final byte[] studyAttrs;
    private final byte[] patientAttrs;

    public PatientStudySeriesAttributes(Long studyPk,
            int numberOfStudyRelatedSeries,
            int numberOfStudyRelatedInstances,
            int numberOfStudyRelatedRejectedInstances,
            int numberOfSeriesRelatedInstances,
            int numberOfSeriesRelatedRejectedInstances,
            String modalitiesInStudy,
            String sopClassesInStudy,
            byte[] seriesAttributes,
            byte[] studyAttributes,
            byte[] patientAttributes) {
        this.studyPk = studyPk;
        this.numberOfStudyRelatedSeries = numberOfStudyRelatedSeries;
        this.numberOfStudyRelatedInstances = numberOfStudyRelatedInstances;
        this.numberOfStudyRelatedRejectedInstances = numberOfStudyRelatedRejectedInstances;
        this.numberOfSeriesRelatedInstances = numberOfSeriesRelatedInstances;
        this.numberOfSeriesRelatedRejectedInstances = numberOfSeriesRelatedRejectedInstances;
        this.modalitiesInStudy = modalitiesInStudy;
        this.sopClassesInStudy = sopClassesInStudy;
        this.seriesAttrs = seriesAttributes;
        this.studyAttrs = studyAttributes;
        this.patientAttrs = patientAttributes;
   }

    public final Long getStudyPk() {
        return studyPk;
    }

    public final int getNumberOfStudyRelatedSeries() {
        return numberOfStudyRelatedSeries;
    }

    public final void setNumberOfStudyRelatedSeries(int number) {
        this.numberOfStudyRelatedSeries = number;
    }

    public final int getNumberOfStudyRelatedInstances() {
        return numberOfStudyRelatedInstances;
    }

    public final void setNumberOfStudyRelatedInstances(int number) {
        this.numberOfStudyRelatedInstances = number;
    }

    public final int getNumberOfSeriesRelatedInstances() {
        return numberOfSeriesRelatedInstances;
    }

    public final void setNumberOfSeriesRelatedInstances(int number) {
        this.numberOfSeriesRelatedInstances = number;
    }

    public final String getModalitiesInStudy() {
        return modalitiesInStudy;
    }

    public final void setModalitiesInStudy(String modalitiesInStudy) {
        this.modalitiesInStudy = modalitiesInStudy;
    }

    public final String getSopClassesInStudy() {
        return sopClassesInStudy;
    }

    public final void setSopClassesInStudy(String sopClassesInStudy) {
        this.sopClassesInStudy = sopClassesInStudy;
    }

    public Attributes getAttributes(
            boolean includeQueryAttributes,
            boolean hideRejectedInstances) {
        Attributes attrs = new Attributes();
        Utils.decodeAttributes(attrs, patientAttrs);
        Utils.decodeAttributes(attrs, studyAttrs);
        Utils.decodeAttributes(attrs, seriesAttrs);
        if (includeQueryAttributes) {
            Utils.setStudyQueryAttributes(attrs,
                    numberOfStudyRelatedSeries,
                    hideRejectedInstances
                        ? numberOfStudyRelatedInstances
                        : numberOfStudyRelatedInstances
                            + numberOfStudyRelatedRejectedInstances,
                    modalitiesInStudy,
                    sopClassesInStudy);
            Utils.setSeriesQueryAttributes(attrs,
                    hideRejectedInstances
                        ? numberOfSeriesRelatedInstances
                        : numberOfSeriesRelatedInstances
                            + numberOfSeriesRelatedRejectedInstances);
        }
        return attrs;
    }
}
