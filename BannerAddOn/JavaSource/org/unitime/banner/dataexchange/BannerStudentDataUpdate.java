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

package org.unitime.banner.dataexchange;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.dom4j.Element;
import org.unitime.banner.model.BannerSection;
import org.unitime.banner.model.BannerSession;
import org.unitime.banner.model.Queue;
import org.unitime.banner.model.QueueIn;
import org.unitime.banner.model.dao.QueueInDAO;
import org.unitime.banner.queueprocessor.exception.LoggableException;
import org.unitime.timetable.dataexchange.BaseImport;
import org.unitime.timetable.dataexchange.StudentEnrollmentImport.Pair;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.model.AcademicArea;
import org.unitime.timetable.model.AcademicAreaClassification;
import org.unitime.timetable.model.AcademicClassification;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseDemand;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.CourseRequest;
import org.unitime.timetable.model.CourseRequestOption;
import org.unitime.timetable.model.PosMajor;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.StudentClassEnrollment;
import org.unitime.timetable.model.StudentGroup;
import org.unitime.timetable.model.StudentSectioningQueue;

/**
 * @author says
 *
 */
public class BannerStudentDataUpdate extends BaseImport {

	private static String rootName = "studentUpdates";
	private static String studentElementName = "student";
	private static String crnElementName = "crn";
	private static String groupElementName = "studentGroup";
	
	private HashMap<Session,HashSet<Long>> studentIdsSucessfullyProcessed = new HashMap<Session, HashSet<Long>>();
	private HashSet<String> studentIdsNotProcessed = new HashSet<String>();
	private HashSet<String> studentIdsSucessfullyProcessedWithProblems = new HashSet<String>();
		
	private boolean trimLeadingZerosFromExternalId;
	private HashMap<String, ArrayList<Long>> bannerSessionIdMap = new HashMap<String, ArrayList<Long>>();

	/**
	 * 
	 */
	public BannerStudentDataUpdate() {
		super();
	}

	@Override
	public void loadXml(Element rootElement) throws Exception {
		Date start = new Date();
		Date elementStart;
		Date elementEnd;
		long maxElementTime = Long.MIN_VALUE;
		long minElementTime = Long.MAX_VALUE;
		long elementCount = 0;
		long timeDiff;
//		TreeMap<Long, Long> timeMap = new TreeMap<Long, Long>();
		trimLeadingZerosFromExternalId = ApplicationProperty.DataExchangeTrimLeadingZerosFromExternalIds.isTrue();

		if (rootElement.getName().equalsIgnoreCase(rootName)) {
			for (Iterator<?> eIt = rootElement.elementIterator(studentElementName); eIt.hasNext();) {
				elementStart = new Date();
				Element studentElement = (Element) eIt.next();
				processStudentElement(studentElement);
				elementEnd = new Date();
				timeDiff = elementEnd.getTime() - elementStart.getTime();
				if (timeDiff < minElementTime){
					minElementTime = timeDiff;
				}
				if (timeDiff > maxElementTime){
					maxElementTime = timeDiff;
				}
//				Long timeCount = timeMap.get(new Long(timeDiff));
//				if (timeCount == null){
//					timeCount = new Long(0);
//				}
//				timeMap.put(new Long(timeDiff), new Long(timeCount.longValue() + 1));
				elementCount++;
			}
			if (elementCount == 0){
				info("There were no student data update records to process.");				
			} else {
				beginTransaction();
				int studentCount = 0;
				for(Session session : studentIdsSucessfullyProcessed.keySet()){
					HashSet<Long> updatedStudents = studentIdsSucessfullyProcessed.get(session);
			        if (!updatedStudents.isEmpty()){
		 	 	        StudentSectioningQueue.studentChanged(getHibSession(), null, session.getUniqueId(), updatedStudents);
		 	 	        studentCount += updatedStudents.size();
			        }
				}
				commitTransaction();
				Date end = new Date();
			
			
				info(Integer.toString(studentCount) + " student records updated in " + (end.getTime() - start.getTime())+ " milliseconds.");
				info(Integer.toString(studentIdsNotProcessed.size()) + " student records failed to update.");
				info(Integer.toString(studentIdsSucessfullyProcessedWithProblems.size()) + " student records were updated, but had problems.");
				info("Minimum milliseconds required to process a record = " + minElementTime);
				info("Maximum milliseconds required to process a record = " + maxElementTime);
				info("Average milliseconds required to process a record = " + ((end.getTime() - start.getTime())/elementCount));
	//			info("The distribution of time to process each record is as follows:");
	//			for(Long elapsedTime : timeMap.keySet()){
	//				info("	" + elapsedTime.toString() + ":  " + timeMap.get(elapsedTime).toString());
	//			}
				if (!studentIdsNotProcessed.isEmpty()){
					error("The following student ids were not successfully processed:  ");
					for(String studentId : studentIdsNotProcessed){
						error("    " + studentId);
					}
				}
				if (!studentIdsSucessfullyProcessedWithProblems.isEmpty()){
					error("The following student ids were successfully processed, but may have had problems finding all classes the student was enrolled in:  ");
					for(String studentId : studentIdsSucessfullyProcessedWithProblems){
						error("    " + studentId);
					}
				}
			}
		}
	}


	private void processStudentElement(Element studentElement) {
		String externalId = null;
		HashMap<Session, HashSet<Long>> studentUpdates = null;
		try {
			externalId = getRequiredStringAttribute(studentElement, "externalId", studentElementName);
            while (trimLeadingZerosFromExternalId && externalId.startsWith("0")) externalId = externalId.substring(1);	
		} catch (Exception e) {
			error(e.getMessage());
			return;
		}
		if (externalId.trim().length() == 0){
			error("For element '" + studentElementName + "' an 'externalId' is required");
		}

		String bannerSession = null;
		try {
			bannerSession = getRequiredStringAttribute(studentElement, "session", studentElementName);
		} catch (Exception e) {
			error(e.getMessage());
			studentIdsNotProcessed.add(externalId);
			return;
		}
		beginTransaction();
		HashSet<Student> studentRecords = new HashSet<Student>();
		for(Long sessionId : getSessionIdsForBannerSession(bannerSession)){
			Student s = Student.findByExternalIdBringBackEnrollments(getHibSession(), sessionId, externalId);
			if (s != null){
				studentRecords.add(s);
			}
		}
		String firstName = null;
		String lastName = null;
		String academicArea = null;
		String classification = null;
		String major = null;
		try {
			firstName = getRequiredStringAttribute(studentElement, "firstName", studentElementName);
			lastName = getRequiredStringAttribute(studentElement, "lastName", studentElementName);
			academicArea = getRequiredStringAttribute(studentElement, "academicArea", studentElementName);
			classification = getRequiredStringAttribute(studentElement, "classification", studentElementName);
			major = getRequiredStringAttribute(studentElement, "major", studentElementName);			
		} catch (Exception e) {
			error(e.getMessage());
			studentIdsNotProcessed.add(externalId);
			rollbackTransaction();
			return;
		}
		String email = getOptionalStringAttribute(studentElement, "email");
		String middleName = getOptionalStringAttribute(studentElement, "middleName");
		
		HashMap<Session, HashMap<CourseOffering, Vector<Class_>>> classEnrollments = null;
		try {
			classEnrollments = processCrnElements(studentElement, bannerSession, externalId);			
		} catch (Exception e) {
			studentIdsNotProcessed.add(externalId);
			error(e.getMessage());
			rollbackTransaction();
			return;
		}
		HashMap<Session, HashSet<StudentGroup>> groups = null;
		try {
			groups = processGroupElements(studentElement, bannerSession, externalId);			
		} catch (Exception e) {
			studentIdsNotProcessed.add(externalId);
			error(e.getMessage());
			rollbackTransaction();
			return;
		}
		try {
			studentUpdates = updateStudent(studentRecords, classEnrollments, externalId, firstName, middleName, lastName, academicArea, classification, major, email, groups);			
		} catch (Exception e) {
			studentIdsNotProcessed.add(externalId);
			error(e.getMessage());
			error(e.toString());
			rollbackTransaction();
			return;
		}
		commitTransaction();
		mergeSessionRecordLists(studentIdsSucessfullyProcessed, studentUpdates);
	}

	private void addSessionRecordToList(HashMap<Session, HashSet<Long>> processedList, Session session, Long uniqueId){
		HashSet<Long> ids = processedList.get(session);
		if (ids == null){
			ids = new HashSet<Long>();
			processedList.put(session, ids);
		}
		ids.add(uniqueId);
	}
	private void mergeSessionRecordLists(HashMap<Session, HashSet<Long>> globalList, HashMap<Session, HashSet<Long>> studentList){
		for(Session session : studentList.keySet()){
			HashSet<Long> ids = studentList.get(session);
			HashSet<Long> globalIds = globalList.get(session);
			if (globalIds == null){
				globalIds = new HashSet<Long>();
				globalList.put(session, globalIds);
			}
			globalIds.addAll(ids);
		}
	}
	
	private boolean updateStudentGroups(Student student, HashMap<Session, HashSet<StudentGroup>> groups){
		boolean changed = false;
    	HashSet<StudentGroup> addedGroups = groups.get(student.getSession());
    	if (addedGroups == null || addedGroups.isEmpty()){
    		if (student.getGroups() != null && !student.getGroups().isEmpty()){
    			HashSet<StudentGroup> removeGroups = new HashSet<StudentGroup>();
    			removeGroups.addAll(student.getGroups());
    			for(StudentGroup sg : removeGroups){
    				if (sg.getExternalUniqueId() != null){
	    				sg.getStudents().remove(student);
	    				student.getGroups().remove(sg);
	    				changed = true;
    				}
    			}
    		}
    	} else {
        	HashSet<StudentGroup> removedGroups = new HashSet<StudentGroup>();
        	removedGroups.addAll(student.getGroups());
    		removedGroups.removeAll(addedGroups);
    		addedGroups.removeAll(student.getGroups());
    		if (!removedGroups.isEmpty()){
    			for (StudentGroup sg : removedGroups){
    				if (sg.getExternalUniqueId() != null){
	    				sg.getStudents().remove(student);
	    				student.getGroups().remove(sg);
	    				changed = true;
    				}
    			}
    		
    		}
    		if (!addedGroups.isEmpty()){
    			for (StudentGroup sg: addedGroups){
    				sg.addTostudents(student);
    				student.addTogroups(sg);
    			}
    			changed = true;
    		}
    	}
    	return(changed);
	}
	
	private boolean updateStudentDemographics(Student student, String firstName,
			String middleName, String lastName, String academicArea,
			String classification, String major, String email){
		boolean changed = false;
		Session session = student.getSession();
		if (student.getFirstName() == null || !student.getFirstName().equals(firstName)){
			student.setFirstName(firstName);
			changed = true;
		}
		if (student.getMiddleName() == null || !student.getMiddleName().equals(middleName)){
			student.setMiddleName(middleName);
			changed = true;
		}
		if (student.getLastName() == null || !student.getLastName().equals(lastName)){
			student.setLastName(lastName);
			changed = true;
		}
		if (student.getEmail() == null || !student.getEmail().equals(email)){
			student.setEmail(email);
			changed = true;
		}

		// This makes the assumption that when working with Banner students have only one AcademicAreaClassification
		AcademicAreaClassification aac = null;
		for(Iterator<?> it = student.getAcademicAreaClassifications().iterator(); it.hasNext();){
			aac = (AcademicAreaClassification) it.next();
			break;
		}
		if (aac == null || 
			!((aac.getAcademicArea().getExternalUniqueId().equalsIgnoreCase(academicArea) 
						|| aac.getAcademicArea().getAcademicAreaAbbreviation().equalsIgnoreCase(academicArea)) 
			  && (aac.getAcademicClassification().getExternalUniqueId().equalsIgnoreCase(classification) 
					  	|| aac.getAcademicClassification().getCode().equalsIgnoreCase(classification)))) {
			AcademicArea aa = AcademicArea.findByExternalId(getHibSession(), session.getUniqueId(), academicArea);						
			if (aa == null){
				aa = AcademicArea.findByAbbv(getHibSession(), session.getUniqueId(), academicArea);
			}
			if (aa == null){
				aa = new AcademicArea();
				aa.setAcademicAreaAbbreviation(academicArea);
				aa.setSession(session);
				aa.setExternalUniqueId(academicArea);
				aa.setTitle(academicArea);
				aa.setUniqueId((Long)getHibSession().save(aa));
				info("Added Academic Area:  " + academicArea);
			}
			if (aac == null) { 
				aac = new AcademicAreaClassification();
			}
			aac.setAcademicArea(aa);
			
			AcademicClassification ac = AcademicClassification.findByExternalId(getHibSession(), session.getUniqueId(), classification);
			if (ac == null){
				ac = AcademicClassification.findByCode(getHibSession(), session.getUniqueId(), classification);
			}
			if (ac == null){
				ac = new AcademicClassification();
				ac.setCode(classification);
				ac.setExternalUniqueId(classification);
				ac.setName(classification);
				ac.setSession(session);
				ac.setUniqueId((Long) getHibSession().save(ac));
				info("Added Academic Classification:  " + classification);
			}
			aac.setAcademicClassification(ac);
			if (aac.getStudent() == null){
				aac.setStudent(student);
				student.addToacademicAreaClassifications(aac);				
			}
			changed = true;
		}
		
		//This makes the assumption that when working with Banner students have only one Major
		PosMajor m = null;
		for(Iterator<?> it = student.getPosMajors().iterator(); it.hasNext();){
			m = (PosMajor) it.next();
		}
		if (m == null || !(major.equalsIgnoreCase(m.getExternalUniqueId()) || major.equalsIgnoreCase(m.getCode()) 
				|| (m.getAcademicAreas() != null && !m.getAcademicAreas().isEmpty() && m.getAcademicAreas().contains(aac.getAcademicArea())))){
			student.getPosMajors().clear();
			
			PosMajor posMajor = PosMajor.findByExternalIdAcadAreaExternalId(getHibSession(), session.getUniqueId(), major, academicArea);
			if (posMajor == null){
				posMajor = PosMajor.findByCodeAcadAreaAbbv(getHibSession(), session.getUniqueId(), major, academicArea);
			}
			if (posMajor == null){
				posMajor = new PosMajor();
				posMajor.setCode(major);
				posMajor.setExternalUniqueId(major);
				posMajor.setName(major);
				posMajor.setSession(session);
				posMajor.setUniqueId((Long)getHibSession().save(posMajor));
				posMajor.addToacademicAreas(aac.getAcademicArea());
				info("Added Major:  " + major + " to Academic Area:  " + academicArea);
			}
			student.addToposMajors(posMajor);
			changed = true;
		} else if (m.getAcademicAreas() == null || m.getAcademicAreas().isEmpty()) {
			m.addToacademicAreas(aac.getAcademicArea());
			info("Added Academic Area: " + academicArea + " to existing Major:  " + major);
			changed = true;
		}
	
		return (changed);
		
	}
	
	private HashMap<Session, HashSet<Long>> updateStudent(HashSet<Student> studentRecords,
			HashMap<Session, HashMap<CourseOffering,Vector<Class_>>> classEnrollments, 
			String externalId, String firstName,
			String middleName, String lastName, String academicArea,
			String classification, String major, String email, HashMap<Session, HashSet<StudentGroup>> groups) {

		HashMap<Session, HashSet<Long>> recordsProcessed = new HashMap<Session, HashSet<Long>>();
		
		HashSet<Student> records = new HashSet<Student>();
		records.addAll(studentRecords);
		for(Session session : classEnrollments.keySet()){
			boolean changed = false;
			Student record = null;
			for(Student student : records){
				if (student.getSession().getUniqueId().equals(session.getUniqueId())){
					record = student;
					break;
				}
			}
			if (record == null){
				record = new Student();
				record.setExternalUniqueId(externalId);
				record.setSession(session);
				record.setFreeTimeCategory(0);
	            record.setSchedulePreference(0);
	            record.setClassEnrollments(new HashSet<StudentClassEnrollment>());
	            record.setAcademicAreaClassifications(new HashSet<AcademicAreaClassification>());
	            record.setPosMajors(new HashSet<PosMajor>());
	            record.setCourseDemands(new HashSet<CourseDemand>());
	            changed = true;
			} else {
				records.remove(record);
			}
			
			if (updateStudentDemographics(record, firstName, middleName, lastName, academicArea, classification, major, email)){
				changed = true;
			}

			Hashtable<Pair, StudentClassEnrollment> enrollments = new Hashtable<Pair, StudentClassEnrollment>();
        	if (record.getClassEnrollments() != null){
            	for (StudentClassEnrollment enrollment: record.getClassEnrollments()) {
            		enrollments.put(new Pair(enrollment.getCourseOffering().getUniqueId(), enrollment.getClazz().getUniqueId()), enrollment);
            	}
        	}
        	int nextPriority = 0;
        	for (CourseDemand cd: record.getCourseDemands())
        		if (!cd.isAlternative() && cd.getPriority() >= nextPriority)
        			nextPriority = cd.getPriority() + 1;
        	Set<CourseDemand> remaining = new HashSet<CourseDemand>(record.getCourseDemands());
        	boolean fixCourseDemands = false;

        	HashMap<CourseOffering, Vector<Class_>> courseToClassEnrollments = classEnrollments.get(session);
        	for (CourseOffering co : courseToClassEnrollments.keySet()){
            	for (Class_ clazz: courseToClassEnrollments.get(co)) {
            		StudentClassEnrollment enrollment = enrollments.remove(new Pair(co.getUniqueId(), clazz.getUniqueId()));
            		if (enrollment == null) {
                		enrollment = new StudentClassEnrollment();
                		enrollment.setStudent(record);
                		enrollment.setClazz(clazz);
                		enrollment.setCourseOffering(co);
                		enrollment.setTimestamp(new java.util.Date());
                		record.getClassEnrollments().add(enrollment);    
                		changed = true;
            		}
            		
            		if (enrollment.getCourseRequest() == null) {
                		demands: for (CourseDemand d: record.getCourseDemands()) {
                			for (CourseRequest r: d.getCourseRequests()) {
                				if (r.getCourseOffering().equals(co)) {
                					enrollment.setCourseRequest(r);
                					break demands;
                				}
                			}
                		}
                		changed = true;
            		}
            		
            		if (enrollment.getCourseRequest() != null) {
            			remaining.remove(enrollment.getCourseRequest().getCourseDemand());
            		} else {
            			CourseDemand cd = new CourseDemand();
            			cd.setTimestamp(enrollment.getTimestamp());
            			cd.setCourseRequests(new HashSet<CourseRequest>());
            			cd.setStudent(record);
            			record.getCourseDemands().add(cd);
            			cd.setAlternative(false);
            			cd.setPriority(nextPriority++);
            			cd.setWaitlist(false);
            			CourseRequest cr = new CourseRequest();
            			cd.getCourseRequests().add(cr);
            			cr.setCourseDemand(cd);
            			cr.setCourseRequestOptions(new HashSet<CourseRequestOption>());
            			cr.setAllowOverlap(false);
            			cr.setCredit(0);
            			cr.setOrder(0);
            			cr.setCourseOffering(enrollment.getCourseOffering());
            			enrollment.setCourseRequest(cr);
            			fixCourseDemands = true;
                		
                		changed = true;
            		}            		
        		}
        	}         	
        	if (!enrollments.isEmpty()) {
        		for (StudentClassEnrollment enrollment: enrollments.values()) {
        			record.getClassEnrollments().remove(enrollment);
        			getHibSession().delete(enrollment);
        		}
        		changed = true;
        	}
        	if (updateStudentGroups(record, groups)){
        		changed = true;
        	}
        	Long uid = record.getUniqueId();
        	if (uid == null){
        		uid = (Long) getHibSession().save(record);
        	} else if (changed) {
        		getHibSession().update(record);	
        	}
        	
        	if (fixCourseDemands) {
        		// removed unused course demands
        		for (CourseDemand cd: remaining) {
        			if (cd.getFreeTime() != null)
        				getHibSession().delete(cd.getFreeTime());
        			for (CourseRequest cr: cd.getCourseRequests())
        				getHibSession().delete(cr);
        			record.getCourseDemands().remove(cd);
        			getHibSession().delete(cd);
        		}
        		int priority = 0;
        		for (CourseDemand cd: new TreeSet<CourseDemand>(record.getCourseDemands())) {
        			cd.setPriority(priority++);
        			getHibSession().saveOrUpdate(cd);
        		}
        	}
        	
        	addSessionRecordToList(recordsProcessed, session, uid);
        	
		}
		if (!records.isEmpty()){
			for(Student student : records){
				addSessionRecordToList(recordsProcessed, student.getSession(), student.getUniqueId());
				if (updateStudentDemographics(student, firstName, middleName, lastName, academicArea, classification, major, email)){
					getHibSession().update(student);
				}
				student.removeAllEnrollments(getHibSession());
				if (updateStudentGroups(student, groups)){
					getHibSession().update(student);
				}
			}
			
		}
		
		return(recordsProcessed);
		
	}

	private HashMap<Session, HashMap<CourseOffering, Vector<Class_>>> processCrnElements(
			Element studentElement, String bannerSession, String externalId) throws Exception {
		HashMap<Session, HashMap<CourseOffering, Vector<Class_>>> enrollments = new HashMap<Session, HashMap<CourseOffering,Vector<Class_>>>();
		for (Iterator<?> eIt = studentElement.elementIterator(crnElementName); eIt.hasNext();) {
			Element enrollmentElement = (Element) eIt.next();
			String crnString = null;
			Integer crn = null;
			try {
				crnString = (String) enrollmentElement.getData();
				crn = new Integer(crnString);
			} catch (Exception e) {
				throw new Exception("For element '" + crnElementName + "' an integer value is required");
			}
			CourseOffering co = BannerSection.findCourseOfferingForCrnAndTermCode(getHibSession(), crn, bannerSession);
			if (co == null){
				studentIdsSucessfullyProcessedWithProblems.add(externalId);
				error("Course Offering not found for CRN = " + crn.toString() + ", banner session = " + bannerSession);
				continue;
			}
			boolean foundClasses = false;
			for(Iterator<?> it = BannerSection.findAllClassesForCrnAndTermCode(getHibSession(), crn, bannerSession).iterator(); it.hasNext();){
				Class_ c = (Class_) it.next();
				foundClasses = true;
				HashMap<CourseOffering,Vector<Class_>> sessionCourseToClasses = enrollments.get(c.getSession());
				if (sessionCourseToClasses == null){
					sessionCourseToClasses = new HashMap<CourseOffering, Vector<Class_>>();
					enrollments.put(c.getSession(), sessionCourseToClasses);
				}
				Vector<Class_> classes = sessionCourseToClasses.get(co);
				if (classes == null){
					classes = new Vector<Class_>();
					sessionCourseToClasses.put(co, classes);
				}
				classes.add(c);
			}
			if (!foundClasses){
				studentIdsSucessfullyProcessedWithProblems.add(externalId);
				error("Classes not found for CRN = " + crn.toString() + ", banner session = " + bannerSession);
			}
		}			
		return(enrollments);
	}

	private HashMap<Session, HashSet<StudentGroup>> processGroupElements(
			Element studentElement, String bannerSession, String externalId) throws Exception {
		HashMap<Session, HashSet<StudentGroup>> groups = new HashMap<Session, HashSet<StudentGroup>>();
		for (Iterator<?> eIt = studentElement.elementIterator(groupElementName); eIt.hasNext();) {
			Element groupElement = (Element) eIt.next();
			String groupExternalId = getRequiredStringAttribute(groupElement, "externalId", groupElementName);
			String campus = getRequiredStringAttribute(groupElement, "campus", groupElementName);
			Session acadSession = getSessionFor(bannerSession, campus);
			StudentGroup sg = StudentGroup.findByExternalId(getHibSession(), groupExternalId, acadSession.getUniqueId());
			if (sg == null){
				sg = new StudentGroup();
				sg.setExternalUniqueId(groupExternalId);
				sg.setSession(acadSession);
				String abbreviation = getOptionalStringAttribute(groupElement, "abbreviation");
				if (abbreviation != null){
					sg.setGroupAbbreviation(abbreviation);
				} else {
					sg.setGroupAbbreviation(groupExternalId);
				}
				String name = getOptionalStringAttribute(groupElement, "name");
				if (name != null){
					sg.setGroupName(name);
				} else {
					sg.setGroupName(groupExternalId);
				}
				sg.setUniqueId((Long)getHibSession().save(sg));
				info("Added Student Group:  " + sg.getExternalUniqueId() + " -  " + sg.getGroupAbbreviation() + " - " + sg.getGroupName() + " to session " + sg.getSession().academicInitiativeDisplayString());
			} else {
				boolean changed = false;
				String abbreviation = getOptionalStringAttribute(groupElement, "abbreviation");
				if (abbreviation != null &&  !abbreviation.equals(sg.getGroupAbbreviation())){
					info("Changed Student Group:  " + sg.getExternalUniqueId() + " - old abbreviation:  " + sg.getGroupAbbreviation() + ", new abbreviation:  " + abbreviation + " in session " + sg.getSession().academicInitiativeDisplayString());
					sg.setGroupAbbreviation(abbreviation);
					changed = true;
				} 
				String name = getOptionalStringAttribute(groupElement, "name");
				if (name != null && !name.equals(sg.getGroupName())){
					info("Changed Student Group:  " + sg.getExternalUniqueId() + " - old name:  " + sg.getGroupName() + ", new name:  " + name + " in session " + sg.getSession().academicInitiativeDisplayString());
					sg.setGroupName(name);
					changed = true;
				}
				if (changed){
					getHibSession().update(sg);
				}
			}
			HashSet<StudentGroup> stuGrps = groups.get(sg.getSession());
			if (stuGrps == null){
				stuGrps = new HashSet<StudentGroup>();
				groups.put(sg.getSession(), stuGrps);
			}
			stuGrps.add(sg);
		}			
		return(groups);
	}
	
 
    
    private Session getSessionFor(String bannerSessionId, String campus){
    	return (Session)getHibSession().
        createQuery(
                "select bs.session from BannerSession bs where "+
                "bs.bannerTermCode = :termCode and " +
                "bs.session.academicInitiative = :campus").
         setString("termCode", bannerSessionId).
         setString("campus", campus).
         setCacheable(true).
         uniqueResult(); 
    }

	
	private ArrayList<Long> getSessionIdsForBannerSession(String bannerSessionId) {
		if (bannerSessionIdMap.get(bannerSessionId) == null){
			ArrayList<Long> sessionIds = new ArrayList<Long>();
			bannerSessionIdMap.put(bannerSessionId, sessionIds);
			for (Iterator<?> it = getHibSession().createQuery("from BannerSession bs where bs.bannerTermCode = :termCode)").setString("termCode", bannerSessionId).list().iterator(); it.hasNext();){
				BannerSession bs = (BannerSession) it.next();
				sessionIds.add(bs.getSession().getUniqueId());
			}
		}
		return(bannerSessionIdMap.get(bannerSessionId));
	}

	public static void receiveResponseDocument(QueueIn queueIn) throws LoggableException {
		Element rootElement = queueIn.getXml().getRootElement();
		if (rootElement.getName().equalsIgnoreCase(rootName)){
			try {
				BannerStudentDataUpdate rbrm = new BannerStudentDataUpdate();
				rbrm.loadXml(rootElement);
				queueIn.setProcessDate(new Date());
				queueIn.setStatus(Queue.STATUS_PROCESSED);
				QueueInDAO.getInstance().getSession().update(queueIn);
			} catch (Exception e) {
				LoggableException le = new LoggableException(e, queueIn);
				le.logError();
				throw le;
			}
		} 	
	}

}
