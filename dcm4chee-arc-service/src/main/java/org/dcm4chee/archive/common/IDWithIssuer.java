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

package org.dcm4chee.archive.common;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class IDWithIssuer {

    public static final IDWithIssuer[] EMPTY = {};

    public final String id;
    public final Issuer issuer;

    public IDWithIssuer(String id, Issuer issuer) {
        this.id = id;
        this.issuer = issuer;
    }

    public IDWithIssuer(String id, String issuer) {
        this.id = id;
        this.issuer = issuer != null ? new Issuer(issuer, '&') : null;
    }

    public IDWithIssuer(String cx) {
        String[] ss = StringUtils.split(cx, '^');
        this.id = ss[0];
        this.issuer = ss.length > 3 ? new Issuer(ss[3], '&') : null;
    }

    @Override
    public String toString() {
        return issuer == null ? id : id + "^^^" + issuer.toString('&');
    }

    public Attributes toPIDWithIssuer(Attributes attrs) {
        if (attrs == null)
            attrs = new Attributes(3);

        attrs.setString(Tag.PatientID, VR.LO, id);
        if (issuer == null)
            return attrs;

        return issuer.toIssuerOfPatientID(attrs);
    }

    public static IDWithIssuer pidWithIssuer(Attributes keys,
            Issuer defaultIssuerWithPatientID) {
        String id = keys.getString(Tag.PatientID);
        if (id == null)
            return null;

        Issuer issuerOfPatientID = Issuer.issuerOfPatientID(keys);
        return new IDWithIssuer(id,
                issuerOfPatientID != null
                    ? issuerOfPatientID
                    : defaultIssuerWithPatientID);
    }

    public static void addOtherPatientIDs(Attributes attrs, IDWithIssuer... pids) {
        Sequence seq = attrs.newSequence(Tag.OtherPatientIDsSequence, pids.length);
        for (IDWithIssuer pid : pids)
            if (pid.issuer != null)
                seq.add(pid.toPIDWithIssuer(null));
        if (seq.isEmpty())
            attrs.remove(Tag.OtherPatientIDsSequence);
    }
}
