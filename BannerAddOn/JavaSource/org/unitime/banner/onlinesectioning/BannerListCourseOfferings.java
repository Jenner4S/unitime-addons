/*
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
*/
package org.unitime.banner.onlinesectioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.unitime.timetable.gwt.server.DayCode;
import org.unitime.timetable.gwt.shared.ClassAssignmentInterface;
import org.unitime.timetable.gwt.shared.ClassAssignmentInterface.CourseAssignment;
import org.unitime.timetable.onlinesectioning.OnlineSectioningHelper;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer;
import org.unitime.timetable.onlinesectioning.basic.ListCourseOfferings;
import org.unitime.timetable.onlinesectioning.model.XCourse;
import org.unitime.timetable.onlinesectioning.model.XCourseId;
import org.unitime.timetable.onlinesectioning.model.XInstructor;
import org.unitime.timetable.onlinesectioning.model.XOffering;
import org.unitime.timetable.onlinesectioning.model.XRoom;
import org.unitime.timetable.onlinesectioning.model.XSection;
import org.unitime.timetable.onlinesectioning.model.XSubpart;

/**
 * @author Tomas Muller
 */
public class BannerListCourseOfferings extends ListCourseOfferings {
	private static final long serialVersionUID = 1L;

	@Override
	protected List<CourseAssignment> listCourses(OnlineSectioningServer server, OnlineSectioningHelper helper) {
		List<CourseAssignment> ret = new ArrayList<CourseAssignment>();
		
		Map<Long, CourseAssignment> courses = null;
		if (iQuery != null && iQuery.length() >= 3) {
			for (Object[] courseClassId: (List<Object[]>)helper.getHibSession().createQuery(
					"select distinct bc.courseOfferingId, bsc.classId " +
					"from BannerSection bs inner join bs.bannerConfig.bannerCourse bc inner join bs.bannerSectionToClasses bsc, CourseOffering co " +
					"where co.uniqueId = bc.courseOfferingId and co.subjectArea.session.uniqueId = :sessionId and co.subjectArea.department.allowStudentScheduling = true " +
					"and ((:q like lower(co.subjectArea.subjectAreaAbbreviation || ' ' || co.courseNbr || ' %') and " +
					"(lower(co.subjectArea.subjectAreaAbbreviation || ' ' || co.courseNbr || ' ' || bs.crn || '-' || bs.sectionIndex) like :q || '%' or " +
					"lower(co.subjectArea.subjectAreaAbbreviation || ' ' || co.courseNbr || ' ' || bs.sectionIndex) like :q || '%')) " +
					"or bs.crn || '-' || bs.sectionIndex like :q || '%') " +
					"order by co.subjectArea.subjectAreaAbbreviation, co.courseNbr, bs.crn"
					).setString("q", iQuery).setLong("sessionId", server.getAcademicSession().getUniqueId()).setCacheable(true).list()) {
				Long courseId = (Long)courseClassId[0];
				Long sectionId = (Long)courseClassId[1];
				XCourse course = server.getCourse(courseId);
				if (course != null && (iMatcher == null || iMatcher.match(course))) {
					XOffering offering = server.getOffering(course.getOfferingId());
					XSection section = (offering == null ? null : offering.getSection(sectionId));
					if (section != null) {
						XSection parent = (section.getParentId() == null ? null : offering.getSection(section.getParentId()));
						if (parent != null && parent.getName(courseId).equals(section.getName(courseId)))
							continue;
						if (courses == null) courses = new HashMap<Long, CourseAssignment>();
						CourseAssignment ca = courses.get(courseId);
						if (ca == null) {
							ca = convert(course, server);
							courses.put(courseId, ca);
							ret.add(ca);
						}
						ClassAssignmentInterface.ClassAssignment a = ca.addClassAssignment();
						a.setClassId(section.getSectionId());
						XSubpart subpart = offering.getSubpart(section.getSubpartId());
						a.setSubpart(subpart.getName());
						a.setSection(section.getName(courseId));
						a.setClassNumber(section.getName(-1l));
						a.setCancelled(section.isCancelled());
						a.addNote(course.getNote());
						a.addNote(section.getNote());
						a.setCredit(subpart.getCredit(courseId));
						if (section.getTime() != null) {
							for (DayCode d: DayCode.toDayCodes(section.getTime().getDays()))
								a.addDay(d.getIndex());
							a.setStart(section.getTime().getSlot());
							a.setLength(section.getTime().getLength());
							a.setBreakTime(section.getTime().getBreakTime());
							a.setDatePattern(section.getTime().getDatePatternName());
						}
						if (section.getRooms() != null) {
							for (XRoom rm: section.getRooms()) {
								a.addRoom(rm.getName());
							}
						}
						for (XInstructor instructor: section.getInstructors()) {
							a.addInstructor(instructor.getName());
							a.addInstructoEmail(instructor.getEmail() == null ? "" : instructor.getEmail());
						}
						if (section.getParentId() != null)
							a.setParentSection(offering.getSection(section.getParentId()).getName(courseId));
						a.setSubpartId(subpart.getSubpartId());
						if (a.getParentSection() == null)
							a.setParentSection(course.getConsentLabel());
					}
				}
				if (iLimit != null && iLimit > 0 && ret.size() == iLimit) break;
			}
		}
		
		for (XCourseId id: server.findCourses(iQuery, iLimit, iMatcher)) {
			if (courses != null && courses.containsKey(id.getCourseId())) continue;
			XCourse course = server.getCourse(id.getCourseId());
			if (course != null)
				ret.add(convert(course, server));
			if (iLimit != null && iLimit > 0 && ret.size() == iLimit) break;
		}
		
		return ret;
	}
}
