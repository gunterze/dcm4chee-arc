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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.dcm4che.data.Attributes;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
@NamedQuery(
    name="Code.findByCodeValueWithoutSchemeVersion",
    query="SELECT c FROM Code c " +
          "WHERE c.codeValue = ?1 " +
            "AND c.codingSchemeDesignator = ?2 " +
            "AND c.codingSchemeVersion IS NULL"),
@NamedQuery(
    name="Code.findByCodeValueWithSchemeVersion",
    query="SELECT c FROM Code c " +
          "WHERE c.codeValue = ?1 " +
            "AND c.codingSchemeDesignator = ?2 " +
            "AND c.codingSchemeVersion = ?3")
})
@Entity
@Table(name = "code")
public class Code extends org.dcm4che.data.Code {

    private static final long serialVersionUID = -130090842318534124L;

    public static final String FIND_BY_CODE_VALUE_WITHOUT_SCHEME_VERSION =
        "Code.findByCodeValueWithoutSchemeVersion";
    public static final String FIND_BY_CODE_VALUE_WITH_SCHEME_VERSION =
        "Code.findByCodeValueWithSchemeVersion";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    public Code() {}

    public Code(org.dcm4che.data.Code code) {
        super(code.getCodeValue(), code.getCodingSchemeDesignator(),
                code.getCodingSchemeVersion(), code.getCodeMeaning());
    }

    public Code(String codeValue, String codingSchemeDesignator,
            String codingSchemeVersion, String codeMeaning) {
        super(codeValue, codingSchemeDesignator, codingSchemeVersion, codeMeaning);
    }

    public Code(Attributes item) {
        super(item);
    }

    public long getPk() {
        return pk;
    }
}
