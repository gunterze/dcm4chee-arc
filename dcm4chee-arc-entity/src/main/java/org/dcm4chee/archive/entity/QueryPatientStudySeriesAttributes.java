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
public class QueryPatientStudySeriesAttributes extends PatientStudySeriesAttributes {
    
    private final Long studyPk;
    private int numberOfStudyRelatedSeries;
    private int numberOfStudyRelatedSeriesA;
    private int numberOfStudyRelatedInstances;
    private int numberOfStudyRelatedInstancesA;
    private int numberOfSeriesRelatedInstances;
    private int numberOfSeriesRelatedInstancesA;
    private String modalitiesInStudy;
    private String sopClassesInStudy;

    public QueryPatientStudySeriesAttributes(Long studyPk,
            int numberOfStudyRelatedSeries,
            int numberOfStudyRelatedSeriesA,
            int numberOfStudyRelatedInstances,
            int numberOfStudyRelatedInstancesA,
            int numberOfSeriesRelatedInstances,
            int numberOfSeriesRelatedInstancesA,
            String modalitiesInStudy,
            String sopClassesInStudy,
            byte[] seriesAttributes,
            byte[] studyAttributes,
            byte[] patientAttributes) {
        super(seriesAttributes, studyAttributes, patientAttributes);
        this.studyPk = studyPk;
        this.numberOfStudyRelatedSeries = numberOfStudyRelatedSeries;
        this.numberOfStudyRelatedSeriesA = numberOfStudyRelatedSeriesA;
        this.numberOfStudyRelatedInstances = numberOfStudyRelatedInstances;
        this.numberOfStudyRelatedInstancesA = numberOfStudyRelatedInstancesA;
        this.numberOfSeriesRelatedInstances = numberOfSeriesRelatedInstances;
        this.numberOfSeriesRelatedInstancesA = numberOfSeriesRelatedInstancesA;
        this.modalitiesInStudy = modalitiesInStudy;
        this.sopClassesInStudy = sopClassesInStudy;
    }

    public final Long getStudyPk() {
        return studyPk;
    }

    public boolean isNumberOfStudyRelatedInstancesInitialized() {
        return (numberOfStudyRelatedSeries
                | numberOfStudyRelatedSeriesA
                | numberOfStudyRelatedInstances
                | numberOfStudyRelatedInstancesA) >= 0;
        
    }

    public void initNumberOfStudyRelatedInstances(int[] a) {
        this.numberOfStudyRelatedSeries = a[0];
        this.numberOfStudyRelatedSeriesA = a[1];
        this.numberOfStudyRelatedInstances = a[2];
        this.numberOfStudyRelatedInstancesA = a[3];
    }

    public boolean isNumberOfSeriesRelatedInstancesInitialized() {
        return (numberOfSeriesRelatedInstances
                | numberOfSeriesRelatedInstancesA) >= 0;
    }

    public void initNumberOfSeriesRelatedInstances(int[] a) {
        this.numberOfSeriesRelatedInstances = a[0];
        this.numberOfSeriesRelatedInstancesA = a[1];
    }

    public void setQueryAttributes(Attributes attrs, boolean showRejectedInstances) {
        Utils.setStudyQueryAttributes(attrs,
                showRejectedInstances ? numberOfStudyRelatedSeriesA
                                      : numberOfStudyRelatedSeries,
                showRejectedInstances ? numberOfStudyRelatedInstancesA
                                      : numberOfStudyRelatedInstances,
                modalitiesInStudy,
                sopClassesInStudy);
        Utils.setSeriesQueryAttributes(attrs,
                showRejectedInstances ? numberOfSeriesRelatedInstancesA
                                      : numberOfSeriesRelatedInstances);
    }
}
