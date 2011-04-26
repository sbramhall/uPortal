/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.io.xml;


/**
 * Describes a type of portal data that can be imported or exported via the {@link IDataImportExportService}
 * 
 * @author Eric Dalquist
 * @version $Revision$
 */
public interface IPortalDataType {
    /**
     * @return The unique name of this portal data type
     */
    public String getTypeId();
    /**
     * @return The user readable title of the type
     */
    public String getTitle();
    /**
     * @return The user readable description of the type
     */
    public String getDescription();
}