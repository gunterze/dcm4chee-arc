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

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public enum Availability {
    ONLINE,
    NEARLINE,
    OFFLINE,
    REJECTED_FOR_QUALITY_REASONS_REJECTION_NOTE,
    REJECTED_FOR_QUALITY_REASONS,
    REJECTED_FOR_PATIENT_SAFETY_REASONS_REJECTION_NOTE,
    REJECTED_FOR_PATIENT_SAFETY_REASONS,
    INCORRECT_MODALITY_WORKLIST_ENTRY_REJECTION_NOTE,
    INCORRECT_MODALITY_WORKLIST_ENTRY,
    DATA_RETENTION_PERIOD_EXPIRED_REJECTION_NOTE,
    DATA_RETENTION_PERIOD_EXPIRED;

    public String toCodeString() {
        return available() ? name() : "UNAVAILABLE";
    }

    public boolean available() {
        return compareTo(OFFLINE) <= 0;
    }

    public static Availability availabilityOfRejectedObject(Availability avail) {
        switch (avail) {
        case REJECTED_FOR_QUALITY_REASONS_REJECTION_NOTE:
            return REJECTED_FOR_QUALITY_REASONS;
        case REJECTED_FOR_PATIENT_SAFETY_REASONS_REJECTION_NOTE:
            return REJECTED_FOR_PATIENT_SAFETY_REASONS_REJECTION_NOTE;
        case INCORRECT_MODALITY_WORKLIST_ENTRY_REJECTION_NOTE:
            return INCORRECT_MODALITY_WORKLIST_ENTRY;
        case DATA_RETENTION_PERIOD_EXPIRED_REJECTION_NOTE:
            return DATA_RETENTION_PERIOD_EXPIRED;
       default:
            throw new IllegalArgumentException("availability: " + avail);
        }
    }

}
