<%--
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
--%>
<%@ page import="java.text.DecimalFormat"%>
<%@ page import="java.text.DateFormat"%>
<%@ page import="org.unitime.commons.web.*"%>
<%@ taglib uri="http://struts.apache.org/tags-bean"	prefix="bean"%>
<%@ taglib uri="http://struts.apache.org/tags-html"	prefix="html"%>
<%@ taglib uri="http://struts.apache.org/tags-logic"	prefix="logic"%>
<%@ taglib uri="http://www.unitime.org/tags-custom" prefix="tt" %>

<html:form action="bannerSessionEdit">

	<table width="98%" border="0" cellspacing="0" cellpadding="3">
		<tr>
			<td>
				<tt:section-header>
					<tt:section-title>
						
					</tt:section-title>
					<sec:authorize access="hasPermission(null, null, 'AcademicSessionAdd')">
					<html:submit property="doit" styleClass="btn" accesskey="A" titleKey="title.addSession">
						<bean:message key="button.addSession" />
					</html:submit>
					</sec:authorize>
				</tt:section-header>
			</td>
		</tr>
	</table>

	<table width="90%" border="0" cellspacing="0" cellpadding="3">
		<%
			WebTable webTable = new WebTable(
					7, "", "bannerSessionList.do?order=%%",					
					new String[] {
						"Academic<br>Session", "Academic<br>Initiative", "Banner<br>Term&nbsp;Code",
						"Banner<br>Campus", "Store&nbsp;Data<br>For&nbsp;Banner", "Send&nbsp;Data<br>To&nbsp;Banner", "Loading<br>Offerings" },
					new String[] { "left", "left", "left", "left",
						"center", "center", "center" }, 
					new boolean[] { true, true, false, false, false, true });
					
			webTable.enableHR("#EFEFEF");
	        webTable.setRowStyle("white-space: nowrap");
					
		%>

		<logic:iterate name="bannerSessionListForm" property="sessions" id="sessn">
			<%
					org.unitime.banner.model.BannerSession s = (org.unitime.banner.model.BannerSession) sessn;
					webTable
					.addLine(
							"onClick=\"document.location='bannerSessionEdit.do?doit=editSession&sessionId=" + s.getUniqueId() + "';\"",
							new String[] {
								s.getSession().getLabel() + "&nbsp;",
								s.getSession().academicInitiativeDisplayString() + "&nbsp;",
								s.getBannerTermCode() + "&nbsp;",
								s.getBannerCampus() + "&nbsp;",
								s.isStoreDataForBanner().booleanValue() ? "<img src='images/accept.png'> " : "&nbsp; ", 
								s.isSendDataToBanner().booleanValue() ? "<img src='images/accept.png'> " : "&nbsp; ", 
								s.isLoadingOfferingsFile().booleanValue() ? "<img src='images/accept.png'> " : "&nbsp; "},
							new Comparable[] {
								s.getSession().getLabel(),
								s.getSession().academicInitiativeDisplayString(),
								s.getBannerTermCode(),
								s.getBannerCampus(),
								s.isStoreDataForBanner().booleanValue() ? "<img src='images/accept.png'>" : "",
								s.isSendDataToBanner().booleanValue() ? "<img src='images/accept.png'>" : "",
								s.isLoadingOfferingsFile().booleanValue() ? "<img src='images/accept.png'>" : "" } );
			%>

		</logic:iterate>
		<%-- end interate --%>
		<%
		int orderCol = 3;
		if (request.getParameter("order")!=null) {
			try {
				orderCol = Integer.parseInt(request.getParameter("order"));
			}
			catch (Exception e){
				orderCol = 3;
			}
		}
		out.println(webTable.printTable(orderCol));
		%>

		<%-- print out the add link --%>

	</table>
	
	<table width="98%" border="0" cellspacing="0" cellpadding="3">
		<tr>
			<td align="center" class="WelcomeRowHead">
			&nbsp;
			</td>
		</tr>
		<tr>
			<td align="right">
				<sec:authorize access="hasPermission(null, null, 'AcademicSessionAdd')">
					<html:submit property="doit" styleClass="btn" accesskey="A" titleKey="title.addSession">
						<bean:message key="button.addSession" />
					</html:submit>
				</sec:authorize>
			</td>
		</tr>
	</table>

</html:form>
	
