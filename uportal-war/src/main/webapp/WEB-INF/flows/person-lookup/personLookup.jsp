<%--

    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.

--%>

<%@ include file="/WEB-INF/jsp/include.jsp"%>

<portlet:actionURL var="selectPersonUrl" escapeXml="false">
    <portlet:param name="execution" value="${flowExecutionKey}" />
    <portlet:param name="_eventId" value="select"/>
    <portlet:param name="username" value="USERNAME"/>
</portlet:actionURL>

<!-- Portlet -->
<div class="fl-widget portlet prs-lkp view-lookup" role="section">

	<!-- Portlet Titlebar -->
    <div class="fl-widget-titlebar titlebar portlet-titlebar" role="sectionhead">
        <h2 class="title" role="heading"><spring:message code="search.for.users" /></h2>
    </div>
    
    <!-- Portlet Content -->
    <div class="fl-widget-content content portlet-content" role="main">

        <div class="portlet-content">
            <form id="${n}personQueryForm" action="${queryUrl}">
                <table class="portlet-table">
                    <thead>
                      <tr>
                          <th><spring:message code="attribute.name"/></th>
                          <th><spring:message code="attribute.value"/></th>
                      </tr>
                    </thead>
                    <tbody>
                        <c:forEach var="queryAttribute" items="${queryAttributes}">
                            <tr>
                                <td class="attribute-name">
                                    <label for="attributes['${queryAttribute}'].value">
                                        <strong><spring:message code="attribute.displayName.${queryAttribute}" text="${queryAttribute}"/></strong>
                                        ${ fn:escapeXml(queryAttribute) }
                                    </label>
                                </td>
                                <td class="attribute-value">
                                    <input type="text" id="attributes['${queryAttribute}'].value" name="${queryAttribute}"/>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>            
                </table>
				
                <!-- Buttons -->
                <div class="buttons">
                    <spring:message var="searchButtonText" code="search" />
                    <input class="button primary" type="submit" class="button" name="_eventId_search" value="${searchButtonText}" />

                    <portlet:renderURL var="cancelUrl">
                        <portlet:param name="execution" value="${flowExecutionKey}"/>
                        <portlet:param name="_eventId" value="cancel"/>
                    </portlet:renderURL>
                    <a class="button" class="button" href="${ cancelUrl }">
                        <spring:message code="cancel" />
                    </a>
                </div>
                
            </form>
        </div>
        <div id="${n}searchResults" class="portlet-section" style="display:none" role="region">
            <div class="titlebar">
                <h3 role="heading" class="title">Search Results</h3>
            </div>
            <ul class="person-search-results">
                <li class="person-search-result">
                    <a class="person-link" href="">Person name 1</a>
                    <table>
                        <tr class="person-attribute">
                            <td class="person-attribute-name">Name:</td> 
                            <td class="person-attribute-value">Value</td>
                        </tr>
                    </table>
                </li>
            </ul>
        </div>

    </div>
</div>

<script type="text/javascript">
    up.jQuery(function() {
        var $ = up.jQuery;
        var fluid = up.fluid;

        var templates;

        var displayAttributes = [<c:forEach items="${displayAttributes}" var="attribute" varStatus="status">{ key: '<spring:escapeBody javaScriptEscape="true">${ attribute }</spring:escapeBody>', displayName: '<spring:message javaScriptEscape="true" code="attribute.displayName.${attribute}"/>' }${ !status.last ? ',' : ''}</c:forEach>];
        
        var cutpoints = [
            { id: "personSearchResult:", selector: ".person-search-result" },
            { id: "personSearchLink", selector: ".person-link" },
            { id: "personAttribute:", selector: ".person-attribute" },
            { id: "personAttributeName", selector: ".person-attribute-name" },
            { id: "personAttributeValue", selector: ".person-attribute-value" },
        ];

        var getResultsTree = function (people) {
            var tree = { children: [] };

            $(people).each(function (idx, person) {
                var component = {
                    ID: "personSearchResult:",
                    children: [
                        {
                            ID: "personSearchLink", 
                            linktext: person.attributes.displayName || person.name, 
                            target: "${selectPersonUrl}".replace("USERNAME", person.name)
                        }
                    ]
                };
                $(displayAttributes).each(function (idx2, attribute) {
                    component.children.push({
                        ID: "personAttribute:",
                        children: [
                            { ID: "personAttributeName", value: attribute.displayName },
                            { ID: "personAttributeValue", value: person.attributes[attribute.key] ? person.attributes[attribute.key][0] : '' }
                        ]
                    });
                });
                tree.children.push(component);
            });
            return tree;
        };
        
        $(document).ready(function(){
            $("#${n}personQueryForm").submit(function() {
                var form = $(this).serializeArray();
                var data = { searchTerms: [] };
    
                $(form).each(function (idx, item) {
                    if (item.value !== '') {
                        data[item.name] = item.value;
                        data.searchTerms.push(item.name);
                    }
                });
    
                if (data.searchTerms.length > 0) {
                    $.get(
                        "<c:url value="/api/people.json"/>", 
                        data, 
                        function(data) {
                            var tree = getResultsTree(data.people), c;
                            if (!templates) {
                                templates = fluid.selfRender($("#${n}searchResults"), tree, { cutpoints: cutpoints });
                                $("#${n}searchResults").show();
                            } else {
                                fluid.reRender(templates, $("#${n}searchResults"), tree, { cutpoints: cutpoints });
                            }
                            c=tree.children.length;
                            $("#${n}searchResults .titlebar .title").html( c + (c==1?" user" : " users") + " found" );
                            $("#${n}searchResults .person-search-results")[ c == 0 ? "slideUp" : "slideDown" ]();
                        }
                    );
                } else if (templates) {
                    // A successful search was done, but now all search terms have been cleared
                    var tree = { children: [] };
                    fluid.reRender(templates, $("#${n}searchResults"), tree, { cutpoints: cutpoints });
                    $("#${n}searchResults .titlebar .title").html( "0 users found" );
                    $("#${n}searchResults .person-search-results").slideUp();
                }
                return false;
            }); 
        });
    });
</script>