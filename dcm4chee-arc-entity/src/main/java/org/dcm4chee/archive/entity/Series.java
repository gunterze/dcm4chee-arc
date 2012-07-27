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

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.PersonName;
import org.dcm4che.data.Tag;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4che.util.DateUtils;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.common.AttributeFilter;
import org.hibernate.annotations.Index;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
@NamedQuery(
    name="Series.findBySeriesInstanceUID",
    query="SELECT s FROM Series s WHERE s.seriesInstanceUID = ?1"),
@NamedQuery(
    name="Series.encodedAttributes",
    query="SELECT s.encodedAttributes, " +
                 "s.study.encodedAttributes, " +
                 "s.study.patient.encodedAttributes " +
          "FROM Series s WHERE s.pk = ?1"),
@NamedQuery(
    name="Series.encodedAttributes2",
    query="SELECT s.study.numberOfStudyRelatedSeries, " +
                 "s.study.numberOfStudyRelatedInstances, " +
                 "s.numberOfSeriesRelatedInstances, " +
                 "s.study.modalitiesInStudy, " +
                 "s.study.sopClassesInStudy, " +
                 "s.encodedAttributes, " +
                 "s.study.encodedAttributes, " +
                 "s.study.patient.encodedAttributes " +
          "FROM Series s WHERE s.pk = ?1"),
@NamedQuery(
    name="Series.retrieveAETs",
    query="SELECT DISTINCT(i.retrieveAETs) FROM Instance i WHERE i.series = ?1 AND i.replaced = false"),
@NamedQuery(
    name="Series.externalRetrieveAET",
    query="SELECT DISTINCT(i.externalRetrieveAET) FROM Instance i WHERE i.series = ?1 AND i.replaced = false"),
@NamedQuery(
    name="Series.availability",
    query="SELECT MAX(i.availability) FROM Instance i WHERE i.series = ?1 AND i.replaced = false")
})
@Entity
@Table(name = "series")
public class Series implements Serializable {

    private static final long serialVersionUID = -8317105475421750944L;

    public static final String FIND_BY_SERIES_INSTANCE_UID = "Series.findBySeriesInstanceUID";
    public static final String ENCODED_ATTRIBUTES = "Series.encodedAttributes";
    public static final String ENCODED_ATTRIBUTES2 = "Series.encodedAttributes2";
    public static final String RETRIEVE_AETS = "Series.retrieveAETs";
    public static final String EXTERNAL_RETRIEVE_AET = "Series.externalRetrieveAET";
    public static final String AVAILABILITY = "Series.availability";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Column(name = "updated_time")
    private Date updatedTime;

    @Basic(optional = false)
    @Column(name = "series_iuid", updatable = false)
    @Index(name="series_iuid_idx")
    private String seriesInstanceUID;

    @Basic(optional = false)
    @Column(name = "series_no")
    @Index(name="series_no_idx")
    private String seriesNumber;

    @Basic(optional = false)
    @Column(name = "series_desc")
    @Index(name="series_desc_idx")
    private String seriesDescription;

    @Basic(optional = false)
    @Column(name = "modality")
    @Index(name="modality_idx")
    private String modality;

    @Basic(optional = false)
    @Column(name = "department")
    @Index(name="department_idx")
    private String institutionalDepartmentName;

    @Basic(optional = false)
    @Column(name = "institution")
    @Index(name="institution_idx")
    private String institutionName;

    @Basic(optional = false)
    @Column(name = "station_name")
    @Index(name="station_name_idx")
    private String stationName;

    @Basic(optional = false)
    @Column(name = "body_part")
    @Index(name="body_part_idx")
    private String bodyPartExamined;

    @Basic(optional = false)
    @Column(name = "laterality")
    @Index(name="laterality_idx")
    private String laterality;

    @Basic(optional = false)
    @Column(name = "perf_phys_name")
    @Index(name="perf_phys_name_idx")
    private String performingPhysicianName;
    
    @Basic(optional = false)
    @Column(name = "perf_phys_fn_sx")
    @Index(name="perf_phys_fn_sx_idx")
    private String performingPhysicianFamilyNameSoundex;
    
    @Basic(optional = false)
    @Column(name = "perf_phys_gn_sx")
    @Index(name="perf_phys_gn_sx_idx")
    private String performingPhysicianGivenNameSoundex;

    @Basic(optional = false)
    @Column(name = "perf_phys_i_name")
    @Index(name="perf_phys_i_name_idx")
    private String performingPhysicianIdeographicName;

    @Basic(optional = false)
    @Column(name = "perf_phys_p_name")
    @Index(name="perf_phys_p_name_idx")
    private String performingPhysicianPhoneticName;

    @Basic(optional = false)
    @Column(name = "pps_start_date")
    @Index(name="pps_start_date_idx")
    private String performedProcedureStepStartDate;

    @Basic(optional = false)
    @Column(name = "pps_start_time")
    @Index(name="pps_start_time_idx")
    private String performedProcedureStepStartTime;

    @Basic(optional = false)
    @Column(name = "pps_iuid")
    private String performedProcedureStepInstanceUID;

    @Basic(optional = false)
    @Column(name = "pps_cuid")
    private String performedProcedureStepClassUID;

    @Basic(optional = false)
    @Column(name = "series_custom1")
    @Index(name="series_custom1_idx")
    private String seriesCustomAttribute1;

    @Basic(optional = false)
    @Column(name = "series_custom2")
    @Index(name="series_custom2_idx")
    private String seriesCustomAttribute2;

    @Basic(optional = false)
    @Column(name = "series_custom3")
    @Index(name="series_custom3_idx")
    private String seriesCustomAttribute3;

    @Basic(optional = false)
    @Column(name = "num_instances")
    @Index(name="series_num_instances_idx")
    private int numberOfSeriesRelatedInstances;

    @Column(name = "src_aet")
    private String sourceAET;

    @Column(name = "retrieve_aets")
    private String retrieveAETs;

    @Column(name = "ext_retr_aet")
    private String externalRetrieveAET;

    @Basic(optional = false)
    @Column(name = "availability")
    private Availability availability;

    @Basic(optional = false)
    @Column(name = "dirty")
    private boolean dirty;

    @Basic(optional = false)
    @Column(name = "series_attrs")
    private byte[] encodedAttributes;

    @Transient
    private Attributes cachedAttributes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inst_code_fk")
    private Code institutionCode;

    @ManyToMany
    @JoinTable(name = "rel_series_sps", 
        joinColumns = @JoinColumn(name = "series_fk", referencedColumnName = "pk"),
        inverseJoinColumns = @JoinColumn(name = "sps_fk", referencedColumnName = "pk"))
    private Collection<ScheduledProcedureStep> scheduledProcedureSteps;

    @ManyToOne
    @JoinColumn(name = "study_fk")
    private Study study;

    @OneToMany(mappedBy = "series", orphanRemoval = true)
    private Collection<Instance> instances;

    @Override
    public String toString() {
        return "Series[pk=" + pk
                + ", uid=" + seriesInstanceUID
                + ", no=" + seriesNumber
                + ", mod=" + modality
                + ", numI=" + numberOfSeriesRelatedInstances
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedTime = new Date();
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        if (cachedAttributes == null)
            cachedAttributes = Utils.decodeAttributes(encodedAttributes);
        return cachedAttributes;
    }

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public String getSeriesNumber() {
        return seriesNumber;
    }

    public String getSeriesDescription() {
        return seriesDescription;
    }

    public String getModality() {
        return modality;
    }

    public String getInstitutionalDepartmentName() {
        return institutionalDepartmentName;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getStationName() {
        return stationName;
    }

    public String getBodyPartExamined() {
        return bodyPartExamined;
    }

    public String getLaterality() {
        return laterality;
    }

    public String getPerformingPhysicianName() {
        return performingPhysicianName;
    }

    public String getPerformingPhysicianFamilyNameSoundex() {
        return performingPhysicianFamilyNameSoundex;
    }

    public String getPerformingPhysicianGivenNameSoundex() {
        return performingPhysicianGivenNameSoundex;
    }

    public String getPerformingPhysicianIdeographicName() {
        return performingPhysicianIdeographicName;
    }

    public String getPerformingPhysicianPhoneticName() {
        return performingPhysicianPhoneticName;
    }

    public String getPerformedProcedureStepStartDate() {
        return performedProcedureStepStartDate;
    }

    public String getPerformedProcedureStepStartTime() {
        return performedProcedureStepStartTime;
    }

    public String getPerformedProcedureStepInstanceUID() {
        return performedProcedureStepInstanceUID;
    }

    public String getPerformedProcedureStepClassUID() {
        return performedProcedureStepClassUID;
    }

   public String getSeriesCustomAttribute1() {
        return seriesCustomAttribute1;
    }

    public String getSeriesCustomAttribute2() {
        return seriesCustomAttribute2;
    }

    public String getSeriesCustomAttribute3() {
        return seriesCustomAttribute3;
    }

    public int getNumberOfSeriesRelatedInstances() {
        return numberOfSeriesRelatedInstances;
    }

    public void setNumberOfSeriesRelatedInstances(int numberOfSeriesRelatedInstances) {
        this.numberOfSeriesRelatedInstances = numberOfSeriesRelatedInstances;
    }

    public void incNumberOfSeriesRelatedInstances() {
        numberOfSeriesRelatedInstances++;
    }

    public String getSourceAET() {
        return sourceAET;
    }

    public void setSourceAET(String sourceAET) {
        this.sourceAET = sourceAET;
    }

    public String[] getRetrieveAETs() {
        return StringUtils.split(retrieveAETs, '\\');
    }

    public void setRetrieveAETs(String... retrieveAETs) {
        this.retrieveAETs = StringUtils.concat(retrieveAETs, '\\');
    }

    public String getExternalRetrieveAET() {
        return externalRetrieveAET;
    }

    public void setExternalRetrieveAET(String externalRetrieveAET) {
        this.externalRetrieveAET = externalRetrieveAET;
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public byte[] getEncodedAttributes() {
        return encodedAttributes;
    }

    public Code getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(Code institutionCode) {
        this.institutionCode = institutionCode;
    }

    public Collection<ScheduledProcedureStep> getScheduledProcedureSteps() {
        return scheduledProcedureSteps;
    }

    public void setScheduledProcedureSteps(
            Collection<ScheduledProcedureStep> scheduledProcedureSteps) {
        this.scheduledProcedureSteps = scheduledProcedureSteps;
    }

    public Study getStudy() {
        return study;
    }

    public void setStudy(Study study) {
        this.study = study;
    }

    public Collection<Instance> getInstances() {
        return instances;
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr) {
        seriesInstanceUID = attrs.getString(Tag.SeriesInstanceUID);
        seriesNumber = attrs.getString(Tag.SeriesNumber, "*");
        seriesDescription = attrs.getString(Tag.SeriesDescription, "*");
        institutionName = attrs.getString(Tag.InstitutionName, "*");
        institutionalDepartmentName = attrs.getString(Tag.InstitutionalDepartmentName, "*");
        modality = attrs.getString(Tag.Modality, "*").toUpperCase();
        stationName = attrs.getString(Tag.StationName, "*");
        bodyPartExamined = attrs.getString(Tag.BodyPartExamined, "*").toUpperCase();
        laterality = attrs.getString(Tag.Laterality, "*").toUpperCase();
        Attributes refPPS = attrs.getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence);
        if (refPPS != null) {
            performedProcedureStepInstanceUID = refPPS.getString(Tag.ReferencedSOPInstanceUID, "*");
            performedProcedureStepClassUID = refPPS.getString(Tag.ReferencedSOPClassUID, "*");
        } else {
            performedProcedureStepInstanceUID = "*";
            performedProcedureStepClassUID = "*";
        }
        Date dt = attrs.getDate(Tag.PerformedProcedureStepStartDateAndTime, null);
        if (dt != null) {
            performedProcedureStepStartDate = DateUtils.formatDA(null, dt);
            performedProcedureStepStartTime = 
                attrs.containsValue(Tag.PerformedProcedureStepStartDate)
                    ? DateUtils.formatTM(null, dt)
                    : "*";
        } else {
            performedProcedureStepStartDate = "*";
            performedProcedureStepStartTime = "*";
        }
        PersonName pn = new PersonName(attrs.getString(Tag.PerformingPhysicianName), true);
        if (pn.isEmpty()) {
            performingPhysicianName = "*";
            performingPhysicianIdeographicName = "*";
            performingPhysicianPhoneticName = "*";
            performingPhysicianFamilyNameSoundex = "*";
            performingPhysicianGivenNameSoundex = "*";
        } else {
            performingPhysicianName = pn.contains(PersonName.Group.Alphabetic) 
                    ? pn.toString(PersonName.Group.Alphabetic, false) : "*";
            performingPhysicianIdeographicName = pn.contains(PersonName.Group.Ideographic)
                    ? pn.toString(PersonName.Group.Ideographic, false) : "*";
            performingPhysicianPhoneticName = pn.contains(PersonName.Group.Phonetic)
                    ? pn.toString(PersonName.Group.Phonetic, false) : "*";
            performingPhysicianFamilyNameSoundex = Utils.toFuzzy(fuzzyStr,
                    pn.get(PersonName.Component.FamilyName));
            performingPhysicianGivenNameSoundex = Utils.toFuzzy(fuzzyStr,
                    pn.get(PersonName.Component.GivenName));
        }
        seriesCustomAttribute1 = 
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), "*");
        seriesCustomAttribute2 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), "*");
        seriesCustomAttribute3 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), "*");

        encodedAttributes = Utils.encodeAttributes(
                cachedAttributes = new Attributes(attrs, filter.getSelection()));
        
    }
}
