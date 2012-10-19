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

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4chee.archive.conf.AttributeFilter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
    @NamedQuery(
        name="RequestedProcedure.findByRequestedProcedureIDWithoutIssuer",
        query="SELECT rp FROM RequestedProcedure rp " + 
              "WHERE rp.requestedProcedureID = ?1 " +
                "AND rp.serviceRequest.accessionNumber = ?2 " +
                "AND rp.serviceRequest.issuerOfAccessionNumber IS NULL"),
    @NamedQuery(
        name="RequestedProcedure.findByRequestedProcedureIDWithIssuer",
        query="SELECT rp FROM RequestedProcedure rp " + 
              "WHERE rp.requestedProcedureID = ?1 " +
                "AND rp.serviceRequest.accessionNumber = ?2 " +
                "AND rp.serviceRequest.issuerOfAccessionNumber = ?3")

})
@Entity
@Table(name = "req_proc")
public class RequestedProcedure implements Serializable {

    private static final long serialVersionUID = -573191967238630680L;

    public static final String FIND_BY_REQUESTED_PROCEDURE_ID_WITHOUT_ISSUER =
            "RequestedProcedure.findByRequestedProcedureIDWithoutIssuer";

    public static final String FIND_BY_REQUESTED_PROCEDURE_ID_WITH_ISSUER =
            "RequestedProcedure.findByRequestedProcedureIDWithIssuer";

   @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "study_iuid")
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "req_proc_id")
    private String requestedProcedureID;

    @Basic(optional = false)
    @Column(name = "req_proc_attrs")
    private byte[] encodedAttributes;

    @Transient
    private Attributes cachedAttributes;

    @ManyToOne
    @JoinColumn(name = "request_fk")
    private ServiceRequest serviceRequest;

    @OneToMany(mappedBy = "requestedProcedure", orphanRemoval = true)
    private Collection<ScheduledProcedureStep> scheduledProcedureSteps;

    @Override
    public String toString() {
        return "RequestedProcedure[pk=" + pk
                + ", id=" + requestedProcedureID
                + ", suid=" + studyInstanceUID
                + "]";
    }

    public long getPk() {
        return pk;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getRequestedProcedureID() {
        return requestedProcedureID;
    }

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public void setServiceRequest(ServiceRequest serviceRequest) {
        this.serviceRequest = serviceRequest;
    }

    public void setScheduledProcedureSteps(Collection<ScheduledProcedureStep> scheduledProcedureSteps) {
        this.scheduledProcedureSteps = scheduledProcedureSteps;
    }

    public Collection<ScheduledProcedureStep> getScheduledProcedureSteps() {
        return scheduledProcedureSteps;
    }

    public byte[] getEncodedAttributes() {
        return encodedAttributes;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        if (cachedAttributes == null)
            cachedAttributes = Utils.decodeAttributes(encodedAttributes);
        return cachedAttributes;
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter) {
        requestedProcedureID = attrs.getString(Tag.RequestedProcedureID);
        studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);

        encodedAttributes = Utils.encodeAttributes(
                cachedAttributes = new Attributes(attrs, filter.getSelection()));
    }

}
