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
package org.dcm4chee.archive.wado;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.dcm4che.data.Attributes;
import org.dcm4chee.archive.dao.SeriesService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public enum WadoAttributesCache {

    INSTANCE;

    private static final class CacheEntry {
        final Attributes value;
        final long fetchTime;
        CacheEntry(Attributes value, long fetchTime) {
            this.value = value;
            this.fetchTime = fetchTime;
        }
    }

    @SuppressWarnings("serial")
    private final Map<Long, CacheEntry> cache =
            new LinkedHashMap<Long, CacheEntry>(){

                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Long, CacheEntry> eldest) {
                    long now = System.currentTimeMillis();
                    if (now > eldest.getValue().fetchTime + staleTimeout) {
                        Iterator<CacheEntry> it = values().iterator();
                        while (it.hasNext() && now > it.next().fetchTime + staleTimeout)
                            it.remove();
                    }
                    return false;
                }
            };

    private long staleTimeout;

    public int getStaleTimeout() {
        return (int) (staleTimeout / 1000);
    }

    public void setStaleTimeout(int staleTimeout) {
        this.staleTimeout = staleTimeout * 1000L;
    }

    public void clear() {
        cache.clear();
    }

    public Attributes getAttributes(SeriesService service,
            Long seriesPk) {
        long staleTimeout = this.staleTimeout;
        if (staleTimeout <= 0)
            return service.getAttributes(seriesPk);
        synchronized (this) {
            CacheEntry entry = cache.get(seriesPk);
            long now = System.currentTimeMillis();
            if (entry != null) {
                if (now <= entry.fetchTime + staleTimeout)
                    return entry.value;
                cache.remove(seriesPk);
            }
            entry = new CacheEntry(service.getAttributes(seriesPk), now);
            cache.put(seriesPk, entry);
            return entry.value;
        }
    }
}
