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

package org.dcm4chee.archive.util.query;

import org.dcm4che.data.PersonName;
import org.dcm4che.data.PersonName.Group;
import org.dcm4che.soundex.FuzzyStr;
import org.dcm4chee.archive.common.QueryParam;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.types.ExpressionUtils;
import com.mysema.query.types.Predicate;
import com.mysema.query.types.expr.BooleanExpression;
import com.mysema.query.types.path.StringPath;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
class MatchPersonName {

    static Predicate match(StringPath alphabethicName,
            StringPath ideographicName, 
            StringPath phoneticName,
            StringPath familyNameSoundex,
            StringPath givenNameSoundex, 
            String value,
            QueryParam queryParam) {
        if (value.equals("*"))
            return null;

        PersonName pn = new PersonName(value);
        
        return  queryParam.isFuzzySemanticMatching()
            ? fuzzyMatch(familyNameSoundex, givenNameSoundex, pn, queryParam)
            : literalMatch(alphabethicName, ideographicName, phoneticName, pn, queryParam);
    }

    private static Predicate literalMatch(StringPath alphabethicName,
            StringPath ideographicName, StringPath phoneticName,
            PersonName pn, QueryParam param) {
        BooleanBuilder builder = new BooleanBuilder();
        boolean matchUnknown = param.isMatchUnknown();
        if (!pn.contains(PersonName.Group.Ideographic)
                && !pn.contains(PersonName.Group.Phonetic)) {
            String queryString = toQueryString(pn, PersonName.Group.Alphabetic);
            builder.or(Builder.wildCard(alphabethicName, queryString, false, true));
            builder.or(Builder.wildCard(ideographicName, queryString, false, false));
            builder.or(Builder.wildCard(phoneticName, queryString, false, false));
            if (matchUnknown) {
                Predicate emptyName = ExpressionUtils.and(alphabethicName.eq("*"),
                                      ExpressionUtils.and(ideographicName.eq("*"),
                                                          phoneticName.eq("*")));
                builder.or(emptyName);
            }
        } else {
            builder.and(wildCard(alphabethicName, pn, PersonName.Group.Alphabetic, matchUnknown, true));
            builder.and(wildCard(ideographicName, pn, PersonName.Group.Ideographic, matchUnknown, false));
            builder.and(wildCard(phoneticName, pn, PersonName.Group.Phonetic, matchUnknown, false));
        }
        return builder;
    }

    private static Predicate wildCard(StringPath path,
            PersonName pn, Group group, boolean matchUnknown, boolean ignoreCase) {
        return pn.contains(group)
            ? Builder.wildCard(path, toQueryString(pn, group), matchUnknown, ignoreCase)
            : null;
    }

    private static String toQueryString(PersonName pn, PersonName.Group g) {
        String s = pn.toString(g, true);
        return (s.endsWith("*") || pn.contains(g, PersonName.Component.NameSuffix)) ? s : s + "^*";
    }

    private static Predicate fuzzyMatch(StringPath familyNameSoundex, StringPath givenNameSoundex,
            PersonName pn, QueryParam param) {
        FuzzyStr fuzzyStr = param.getFuzzyStr();
        String familyName = pn.get(PersonName.Component.FamilyName);
        String fuzzyFamilyName = fuzzyStr.toFuzzy(familyName);
        String givenName = pn.get(PersonName.Component.GivenName);
        String fuzzyGivenName = fuzzyStr.toFuzzy(givenName);
        return fuzzyFamilyName.length() > 0
            ? fuzzyGivenName.length() > 0
                    ? fuzzyMatch(familyNameSoundex, givenNameSoundex, familyName, fuzzyFamilyName, 
                            givenName, fuzzyGivenName, param)
                    : fuzzyMatch(familyNameSoundex, givenNameSoundex, familyName, fuzzyFamilyName, param)
            : fuzzyGivenName.length() > 0
                    ? fuzzyMatch(familyNameSoundex, givenNameSoundex, givenName, fuzzyGivenName, param)
                    : null;
    }

    private static Predicate fuzzyMatch(StringPath familyNameSoundex, StringPath givenNameSoundex,
            String name, String fuzzyName, QueryParam param) {
        BooleanBuilder builder = new BooleanBuilder()
            .or(fuzzyWildCard(familyNameSoundex, name, fuzzyName))
            .or(fuzzyWildCard(givenNameSoundex, name, fuzzyName));
        if (param.isMatchUnknown())
            builder.or(ExpressionUtils.and(
                    givenNameSoundex.eq("*"),
                    familyNameSoundex.eq("*")));
        return builder;
    }

    private static Predicate fuzzyMatch(StringPath familyNameSoundex, StringPath givenNameSoundex,
            String familyName, String fuzzyFamilyName, String givenName, String fuzzyGivenName, 
            QueryParam param) {
        Predicate names = ExpressionUtils.and(
                fuzzyWildCard(givenNameSoundex, givenName, fuzzyGivenName),
                fuzzyWildCard(familyNameSoundex, familyName, fuzzyFamilyName));
        Predicate namesSwap = ExpressionUtils.and(
                fuzzyWildCard(givenNameSoundex, familyName, fuzzyFamilyName),
                fuzzyWildCard(familyNameSoundex, givenName, fuzzyGivenName));
        BooleanBuilder builder = new BooleanBuilder().or(names).or(namesSwap);
        if (param.isMatchUnknown()) {
            BooleanExpression noFamilyNameSoundex = familyNameSoundex.eq("*");
            BooleanExpression noGivenNameSoundex = givenNameSoundex.eq("*");
            builder
                .or(ExpressionUtils.and(
                        fuzzyWildCard(givenNameSoundex, givenName, fuzzyGivenName),
                        noFamilyNameSoundex))
                .or(ExpressionUtils.and(
                        fuzzyWildCard(familyNameSoundex, familyName, fuzzyFamilyName),
                        noGivenNameSoundex))
                .or(ExpressionUtils.and(
                        fuzzyWildCard(givenNameSoundex, familyName, fuzzyFamilyName),
                        noFamilyNameSoundex))
                .or(ExpressionUtils.and(
                        fuzzyWildCard(familyNameSoundex, givenName, fuzzyGivenName),
                        noGivenNameSoundex))
                .or(ExpressionUtils.and(noFamilyNameSoundex, noGivenNameSoundex));
        }
        return builder;
    }

    private static Predicate fuzzyWildCard(StringPath field, String name, String fuzzy) {
        return name.endsWith("*")
            ? field.like(fuzzy.concat("%"))
            : field.eq(fuzzy);
    }
}
