/**
 *  Copyright Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.search.parser;

import net.sf.ehcache.Element;
import net.sf.ehcache.search.attribute.AttributeExtractor;
import net.sf.ehcache.search.attribute.AttributeExtractorException;


/**
 * @author Alex Snaps
 */
public class Indexer implements AttributeExtractor {

    /**
     *
     */
    private static final long serialVersionUID = 8759323054127235182L;

    public final Object attributeFor(final Element element, final String attributeName) throws AttributeExtractorException {
        final Object objectValue = element.getObjectValue();
        if (objectValue instanceof CacheValue) {
            return ((CacheValue)objectValue).getValue(attributeName);
        }
        return retrieveFromUnknownType(element, attributeName);
    }

    protected Object retrieveFromUnknownType(final Element element, final String attributeName) {
        return null;
    }
}