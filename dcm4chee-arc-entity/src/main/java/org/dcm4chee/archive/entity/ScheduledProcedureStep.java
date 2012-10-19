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
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.PersonName;
import org.dcm4che.data.Tag;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4che.util.DateUtils;
import org.dcm4chee.archive.conf.AttributeFilter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
    @NamedQuery(
        name="ScheduledProcedureStep.findBySPSIDWithoutIssuer",
        query="SELECT sps FROM ScheduledProcedureStep sps " +
              "WHERE sps.scheduledProcedureStepID = ?1 " +
                "AND sps.requestedProcedure.requestedProcedureID = ?2 " +
                "AND sps.requestedProcedure.serviceRequest.accessionNumber = ?3 " +
                "AND sps.requestedProcedure.serviceRequest.issuerOfAccessionNumber IS NULL"),
    @NamedQuery(
        name="ScheduledProcedureStep.findBySPSIDWithIssuer",
        query="SELECT sps FROM ScheduledProcedureStep sps " +
              "WHERE sps.scheduledProcedureStepID = ?1 " +
                "AND sps.requestedProcedure.requestedProcedureID = ?2 " +
                "AND sps.requestedProcedure.serviceRequest.accessionNumber = ?3 " +
                "AND sps.requestedProcedure.serviceRequest.issuerOfAccessionNumber = ?4")
})
@Entity
@Table(name = "sps")
public class ScheduledProcedureStep implements Serializable {

    private static final long serialVersionUID = 7056153659801553552L;

    public static final String FIND_BY_SPS_ID_WITHOUT_ISSUER =
        "ScheduledProcedureStep.findBySPSIDWithoutIssuer";

    public static final String FIND_BY_SPS_ID_WITH_ISSUER =
        "ScheduledProcedureStep.findBySPSIDWithIssuer";

    public static final String SCHEDULED = "SCHEDULED";
    public static final String ARRIVED = "ARRIVED";
    public static final String READY = "READY";
    public static final String STARTED = "STARTED";
    public static final String DEPARTED = "DEPARTED";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "sps_id")
    private String scheduledProcedureStepID;

    @Column(name = "modality", nullable = false)
    private String modality;

    @Basic(optional = false)
    @Column(name = "sps_start_date")
    private String scheduledStartDate;

    @Basic(optional = false)
    @Column(name = "sps_start_time")
    private String scheduledStartTime;

    @Basic(optional = false)
    @Column(name = "perf_phys_name")
    private String scheduledPerformingPhysicianName;
    
    @Basic(optional = false)
    @Column(name = "perf_phys_i_name")
    private String scheduledPerformingPhysicianIdeographicName;

    @Basic(optional = false)
    @Column(name = "perf_phys_p_name")
    private String scheduledPerformingPhysicianPhoneticName;

    @Basic(optional = false)
    @Column(name = "perf_phys_fn_sx")
    private String scheduledPerformingPhysicianFamilyNameSoundex;
    
    @Basic(optional = false)
    @Column(name = "perf_phys_gn_sx")
    private String scheduledPerformingPhysicianGivenNameSoundex;

    @Basic(optional = false)
    @Column(name = "sps_status")
    private String status;

    @Basic(optional = false)
    @Column(name = "sps_attrs")
    private byte[] encodedAttributes;

    @Transient
    private Attributes cachedAttributes;

    @ManyToOne
    @JoinColumn(name = "req_proc_fk")
    private RequestedProcedure requestedProcedure;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sps_fk")
    private Collection<ScheduledStationAETitle> scheduledStationAETs;

    @ManyToMany(mappedBy="scheduledProcedureSteps")
    private Collection<Series> series;

    @Override
    public String toString() {
        return "SPS[pk=" + pk
                + ", id=" + scheduledProcedureStepID
                + ", startDate=" + scheduledStartDate
                + ", startTime=" + scheduledStartTime
                + ", modality=" + modality
                + ", status=" + status
                + "]";
    }

    public long getPk() {
        return pk;
    }

    public String getScheduledProcedureStepID() {
        return scheduledProcedureStepID;
    }

    public String getModality() {
        return modality;
    }

    public String getScheduledStartDate() {
        return scheduledStartDate;
    }

    public String getScheduledStartTime() {
        return scheduledStartTime;
    }

    public String getScheduledPerformingPhysicianName() {
        return scheduledPerformingPhysicianName;
    }

    public String getScheduledPerformingPhysicianIdeographicName() {
        return scheduledPerformingPhysicianIdeographicName;
    }

    public String getScheduledPerformingPhysicianPhoneticName() {
        return scheduledPerformingPhysicianPhoneticName;
    }

    public String getScheduledPerformingPhysicianFamilyNameSoundex() {
        return scheduledPerformingPhysicianFamilyNameSoundex;
    }

    public String getScheduledPerformingPhysicianGivenNameSoundex() {
        return scheduledPerformingPhysicianGivenNameSoundex;
    }

    public String getStatus() {
        return status;
    }

    public RequestedProcedure getRequestedProcedure() {
        return requestedProcedure;
    }

    public void setRequestedProcedure(RequestedProcedure requestedProcedure) {
        this.requestedProcedure = requestedProcedure;
    }

    public Collection<ScheduledStationAETitle> getScheduledStationAETs() {
        return scheduledStationAETs;
    }

    public void setScheduledStationAETs(
            Collection<ScheduledStationAETitle> scheduledStationAETs) {
        this.scheduledStationAETs = scheduledStationAETs;
    }

    public Collection<Series> getSeries() {
        return series;
    }

    public byte[] getEncodedAttributes() {
        return encodedAttributes;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        if (cachedAttributes == null)
            cachedAttributes = Utils.decodeAttributes(encodedAttributes);
        return cachedAttributes;
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr) {
        scheduledProcedureStepID = attrs.getString(Tag.ScheduledProcedureStepID);
        modality = attrs.getString(Tag.Modality, "*").toUpperCase();
        Date dt = attrs.getDate(Tag.ScheduledProcedureStepStartDateAndTime);
        if (dt != null) {
            scheduledStartDate = DateUtils.formatDA(null, dt);
            scheduledStartTime = attrs.containsValue(Tag.ScheduledProcedureStepStartTime)
                    ? DateUtils.formatTM(null, dt)
                    : "*";
        } else {
            scheduledStartDate = "*";
            scheduledStartTime = "*";
        }
        PersonName pn = new PersonName(attrs.getString(Tag.ScheduledPerformingPhysicianName), true);
        scheduledPerformingPhysicianName = pn.contains(PersonName.Group.Alphabetic) 
                ? pn.toString(PersonName.Group.Alphabetic, false) : "*";
        scheduledPerformingPhysicianIdeographicName = pn.contains(PersonName.Group.Ideographic)
                ? pn.toString(PersonName.Group.Ideographic, false) : "*";
        scheduledPerformingPhysicianPhoneticName = pn.contains(PersonName.Group.Phonetic)
                ? pn.toString(PersonName.Group.Phonetic, false) : "*";
        scheduledPerformingPhysicianFamilyNameSoundex = Utils.toFuzzy(fuzzyStr,
                pn.get(PersonName.Component.FamilyName));
        scheduledPerformingPhysicianGivenNameSoundex = Utils.toFuzzy(fuzzyStr,
                pn.get(PersonName.Component.GivenName));
        status = attrs.getString(Tag.ScheduledProcedureStepStatus, "*");

        encodedAttributes = Utils.encodeAttributes(
                cachedAttributes = new Attributes(attrs, filter.getSelection()));
    }

}
