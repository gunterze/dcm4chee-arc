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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.archive.conf;

import java.io.Serializable;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class StoreDuplicate implements Serializable {

    private static final long serialVersionUID = 3499899064498849214L;

    public enum Action {
        IGNORE,
        STORE,
        REPLACE,
    }

    public enum Condition {
        NO_FILE {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum, boolean eqFsGroup) {
                return noFiles;
            }
        },
        EQ_CHECKSUM {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum, boolean eqFsGroup) {
                return !noFiles && eqChecksum;
            }
        },
        NE_CHECKSUM {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum, boolean eqFsGroup) {
                return !noFiles && !eqChecksum;
            }
        },
        EQ_FSGROUP {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum,  boolean eqFsGroup) {
                return !noFiles && eqFsGroup;
            }
        },
        NE_FSGROUP {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum,  boolean eqFsGroup) {
                return !noFiles && !eqFsGroup;
            }
        },
        EQ_CHECKSUM_AND_EQ_FSGROUP {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum, boolean eqFsGroup) {
                return !noFiles && eqChecksum && eqFsGroup;
            }
        },
        EQ_CHECKSUM_AND_NE_FSGROUP {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum, boolean eqFsGroup) {
                return !noFiles && eqChecksum && !eqFsGroup;
            }
        },
        NE_CHECKSUM_AND_EQ_FSGROUP {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum, boolean eqFsGroup) {
                return !noFiles && !eqChecksum && eqFsGroup;
            }
        },
        NE_CHECKSUM_AND_NE_FSGROUP {
            @Override
            public boolean matches(boolean noFiles, boolean eqChecksum, boolean eqFsGroup) {
                return !noFiles && !eqChecksum && eqFsGroup;
            }
        };

        public abstract boolean matches(boolean noFiles, boolean eqChecksum,  boolean eqFsGroup);
    }
    
    private final Condition condition;
    private final Action action;

    public StoreDuplicate(Condition condition, Action action) {
        this.condition = condition;
        this.action = action;
    }

    public final Condition getCondition() {
        return condition;
    }

    public final Action getAction() {
        return action;
    }

}
