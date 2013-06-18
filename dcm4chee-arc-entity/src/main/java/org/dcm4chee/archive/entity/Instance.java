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
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4che.util.DateUtils;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.conf.AttributeFilter;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
@NamedQuery(
    name="Instance.findBySOPInstanceUID",
    query="SELECT i FROM Instance i "
            + "WHERE i.sopInstanceUID = ?1 AND i.replaced = FALSE"),
@NamedQuery(
    name="Instance.findBySeriesInstanceUID",
    query="SELECT i FROM Instance i "
            + "WHERE i.series.seriesInstanceUID = ?1 AND i.replaced = FALSE"),
@NamedQuery(
    name="Instance.sopInstanceReferenceBySeriesInstanceUID",
    query="SELECT NEW org.dcm4chee.archive.entity.SOPInstanceReference("
            + "i.series.study.studyInstanceUID, "
            + "i.series.performedProcedureStepClassUID, "
            + "i.series.performedProcedureStepInstanceUID, "
            + "i.series.seriesInstanceUID, "
            + "i.sopClassUID, "
            + "i.sopInstanceUID, "
            + "i.availability,"
            + "i.retrieveAETs,"
            + "i.externalRetrieveAET) "
            + "FROM Instance i "
            + "WHERE i.series.seriesInstanceUID = ?1 AND i.replaced = FALSE"),
@NamedQuery(
    name="Instance.instanceFileRefBySOPInstanceUID",
    query="SELECT NEW org.dcm4chee.archive.entity.InstanceFileRef("
            + "i.series.pk, "
            + "i.sopClassUID, "
            + "i.sopInstanceUID, "
            + "i.availability, "
            + "i.retrieveAETs, "
            + "i.externalRetrieveAET, "
            + "f.fileSystem.uri, "
            + "f.filePath, "
            + "f.transferSyntaxUID, "
            + "f.fileSystem.availability, "
            + "i.encodedAttributes) "
            + "FROM Instance i "
            + "LEFT JOIN i.fileRefs f "
            + "WHERE i.sopInstanceUID = ?1 AND i.replaced = FALSE"),
@NamedQuery(
    name="Instance.instanceFileRefByStudyInstanceUID",
    query="SELECT NEW org.dcm4chee.archive.entity.InstanceFileRef("
            + "i.series.pk, "
            + "i.sopClassUID, "
            + "i.sopInstanceUID, "
            + "i.availability, "
            + "i.retrieveAETs, "
            + "i.externalRetrieveAET, "
            + "f.fileSystem.uri, "
            + "f.filePath, "
            + "f.transferSyntaxUID, "
            + "f.fileSystem.availability, "
            + "i.encodedAttributes) "
            + "FROM Instance i "
            + "LEFT JOIN i.fileRefs f "
            + "WHERE i.series.study.studyInstanceUID = ?1 AND i.replaced = FALSE"),
@NamedQuery(
    name="Instance.instanceFileRefBySeriesInstanceUID",
    query="SELECT NEW org.dcm4chee.archive.entity.InstanceFileRef("
            + "i.series.pk, "
            + "i.sopClassUID, "
            + "i.sopInstanceUID, "
            + "i.availability, "
            + "i.retrieveAETs, "
            + "i.externalRetrieveAET, "
            + "f.fileSystem.uri, "
            + "f.filePath, "
            + "f.transferSyntaxUID, "
            + "f.fileSystem.availability, "
            + "i.encodedAttributes) "
            + "FROM Instance i "
            + "LEFT JOIN i.fileRefs f "
            + "WHERE i.series.seriesInstanceUID = ?1 AND i.replaced = FALSE"),
@NamedQuery(
    name="Instance.numberOfStudyRelatedInstances",
    query="SELECT COUNT(i) FROM Instance i "
            + "WHERE i.series.study.pk = ?1 "
            + "AND i.replaced = FALSE AND i.availability <= ?2"),
@NamedQuery(
    name="Instance.numberOfSeriesRelatedInstances",
    query="SELECT COUNT(i) FROM Instance i "
            + "WHERE i.series.pk = ?1 "
            + "AND i.replaced = FALSE AND i.availability <= ?2")})
@Entity
@Table(name = "instance")
public class Instance implements Serializable {

    private static final long serialVersionUID = -6510894512195470408L;

    public static final String FIND_BY_SOP_INSTANCE_UID =
            "Instance.findBySOPInstanceUID";
    public static final String FIND_BY_SERIES_INSTANCE_UID =
            "Instance.findBySeriesInstanceUID";
    public static final String SOP_INSTANCE_REFERENCE_BY_SERIES_INSTANCE_UID =
            "Instance.sopInstanceReferenceBySeriesInstanceUID";
    public static final String SOP_INSTANCE_REFERENCE_BY_STUDY_INSTANCE_UID =
            "Instance.sopInstanceReferenceByStudyInstanceUID";
    public static final String INSTANCE_FILE_REF_BY_SOP_INSTANCE_UID =
            "Instance.instanceFileRefBySOPInstanceUID";
    public static final String INSTANCE_FILE_REF_BY_SERIES_INSTANCE_UID =
            "Instance.instanceFileRefBySeriesInstanceUID";
    public static final String INSTANCE_FILE_REF_BY_STUDY_INSTANCE_UID =
            "Instance.instanceFileRefByStudyInstanceUID";
    public static final String NUMBER_OF_STUDY_RELATED_INSTANCES =
            "Instance.numberOfStudyRelatedInstances";
    public static final String NUMBER_OF_SERIES_RELATED_INSTANCES =
            "Instance.numberOfSeriesRelatedInstances";

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
    @Column(name = "sop_iuid", updatable = false)
    private String sopInstanceUID;

    @Basic(optional = false)
    @Column(name = "sop_cuid", updatable = false)
    private String sopClassUID;

    @Basic(optional = false)
    @Column(name = "inst_no")
    private String instanceNumber;

    @Basic(optional = false)
    @Column(name = "content_date")
    private String contentDate;

    @Basic(optional = false)
    @Column(name = "content_time")
    private String contentTime;

    @Basic(optional = false)
    @Column(name = "sr_complete")
    private String completionFlag;

    @Basic(optional = false)
    @Column(name = "sr_verified")
    private String verificationFlag;

    @Basic(optional = false)
    @Column(name = "inst_custom1")
    private String instanceCustomAttribute1;

    @Basic(optional = false)
    @Column(name = "inst_custom2")
    private String instanceCustomAttribute2;

    @Basic(optional = false)
    @Column(name = "inst_custom3")
    private String instanceCustomAttribute3;

    @Column(name = "retrieve_aets")
    private String retrieveAETs;

    @Column(name = "ext_retr_aet")
    private String externalRetrieveAET;

    @Basic(optional = false)
    @Column(name = "availability")
    private Availability availability;

    @Basic(optional = false)
    @Column(name = "replaced")
    private boolean replaced;

    @Basic(optional = false)
    @Column(name = "archived")
    private boolean archived;

    @Basic(optional = false)
    @Column(name = "inst_attrs")
    private byte[] encodedAttributes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "srcode_fk")
    private Code conceptNameCode;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "instance_fk")
    private Collection<VerifyingObserver> verifyingObservers;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "instance_fk")
    private Collection<ContentItem> contentItems;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "instance_fk")
    private Collection<FileRef> fileRefs;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "series_fk")
    private Series series;

    @Transient
    private Attributes cachedAttributes;

    @Override
    public String toString() {
        return "Instance[pk=" + pk
                + ", uid=" + sopInstanceUID
                + ", class=" + sopClassUID
                + ", no=" + instanceNumber
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

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public String getInstanceNumber() {
        return instanceNumber;
    }

    public String getContentDate() {
        return contentDate;
    }

    public String getContentTime() {
        return contentTime;
    }

    public String getCompletionFlag() {
        return completionFlag;
    }

    public String getVerificationFlag() {
        return verificationFlag;
    }

    public String getInstanceCustomAttribute1() {
        return instanceCustomAttribute1;
    }

    public String getInstanceCustomAttribute2() {
        return instanceCustomAttribute2;
    }

    public String getInstanceCustomAttribute3() {
        return instanceCustomAttribute3;
    }

    public String[] getRetrieveAETs() {
        return StringUtils.split(retrieveAETs, '\\');
    }

    public void setRetrieveAETs(String... retrieveAETs) {
        this.retrieveAETs = StringUtils.concat(retrieveAETs, '\\');
    }

    public String getEncodedRetrieveAETs() {
        return retrieveAETs;
    }

    public void setEncodedRetrieveAETs(String retrieveAETs) {
        this.retrieveAETs = retrieveAETs;
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

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public boolean isReplaced() {
        return replaced;
    }

    public void setReplaced(boolean replaced) {
        this.replaced = replaced;
    }

    public byte[] getEncodedAttributes() {
        return encodedAttributes;
    }

    public Code getConceptNameCode() {
        return conceptNameCode;
    }

    public void setConceptNameCode(Code conceptNameCode) {
        this.conceptNameCode = conceptNameCode;
    }

    public Collection<VerifyingObserver> getVerifyingObservers() {
        return verifyingObservers;
    }

    public void setVerifyingObservers(
            Collection<VerifyingObserver> verifyingObservers) {
        this.verifyingObservers = verifyingObservers;
    }

    public Collection<ContentItem> getContentItems() {
        return contentItems;
    }

    public void setContentItems(Collection<ContentItem> contentItems) {
        this.contentItems = contentItems;
    }

    public Collection<FileRef> getFileRefs() {
        return fileRefs;
    }

    public Series getSeries() {
        return series;
    }

    public void setSeries(Series series) {
        this.series = series;
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr) {
        sopInstanceUID = attrs.getString(Tag.SOPInstanceUID);
        sopClassUID = attrs.getString(Tag.SOPClassUID);
        instanceNumber = attrs.getString(Tag.InstanceNumber, "*");
        Date dt = attrs.getDate(Tag.ContentDateAndTime);
        if (dt != null) {
            contentDate = DateUtils.formatDA(null, dt);
            contentTime = 
                attrs.containsValue(Tag.ContentTime)
                    ? DateUtils.formatTM(null, dt)
                    : "*";
        } else {
            contentDate = "*";
            contentTime = "*";
        }
        completionFlag = attrs.getString(Tag.CompletionFlag, "*").toUpperCase();
        verificationFlag = attrs.getString(Tag.VerificationFlag, "*").toUpperCase();

        instanceCustomAttribute1 = 
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), "*");
        instanceCustomAttribute2 =
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), "*");
        instanceCustomAttribute3 =
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), "*");

        encodedAttributes = Utils.encodeAttributes(
                cachedAttributes = new Attributes(attrs, filter.getSelection()));
    }
}
