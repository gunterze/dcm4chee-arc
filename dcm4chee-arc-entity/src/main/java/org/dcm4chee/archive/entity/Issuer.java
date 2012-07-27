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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.hibernate.annotations.Index;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
@NamedQuery(
    name="Issuer.findByEntityID",
    query="SELECT i FROM Issuer i WHERE i.entityID = ?1"),
@NamedQuery(
    name="Issuer.findByEntityUID",
    query="SELECT i FROM Issuer i " +
          "WHERE i.entityUID = ?1 AND i.entityUIDType = ?2"),
@NamedQuery(
    name="Issuer.findByEntityIDorUID",
    query="SELECT i FROM Issuer i WHERE i.entityID = ?1 " +
          "OR (i.entityUID = ?2 AND i.entityUIDType = ?3)")
})
@Entity
@Table(name = "issuer")
@org.hibernate.annotations.Table(appliesTo = "issuer", 
indexes = { @Index(name = "issuer_uidt_idx", 
    columnNames = { "entity_uid", "entity_uid_type" } ) } )
public class Issuer implements Serializable {

    private static final long serialVersionUID = -5050458184841995777L;

    public static final String FIND_BY_ENTITY_ID = "Issuer.findByEntityID";

    public static final String FIND_BY_ENTITY_UID = "Issuer.findByEntityUID";

    public static final String FIND_BY_ENTITY_ID_OR_UID =
        "Issuer.findByEntityIDorUID";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Column(name = "entity_id")
    @Index(name="entity_id_idx")
    private String entityID;

    @Column(name = "entity_uid")
    private String entityUID;

    @Column(name = "entity_uid_type")
    private String entityUIDType;

    public Issuer() {}

    public Issuer(String entityID, String entityUID, String entityUIDType) {
        this.entityID = entityID;
        this.entityUID = entityUID;
        this.entityUIDType = entityUIDType;
    }

    public long getPk() {
        return pk;
    }

    public String getLocalNamespaceEntityID() {
        return entityID;
    }

    public void setLocalNamespaceEntityID(String entityId) {
        this.entityID = entityId;
    }

    public String getUniversalEntityID() {
        return entityUID;
    }

    public void setUniversalEntityID(String entityUid) {
        this.entityUID = entityUid;
    }

    public String getUniversalEntityIDType() {
        return entityUIDType;
    }

    public void setUniversalEntityIDType(String entityUidType) {
        this.entityUIDType = entityUidType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("Issuer[pk=").append(pk);
        sb.append(", id=");
        if (entityID != null)
            sb.append(entityID);
        sb.append(", uid=");
        if (entityUID != null)
            sb.append(entityUID);
        sb.append(", type=");
        if (entityUIDType != null)
            sb.append(entityUIDType);
        sb.append("]");
        return sb.toString();
    }

}
