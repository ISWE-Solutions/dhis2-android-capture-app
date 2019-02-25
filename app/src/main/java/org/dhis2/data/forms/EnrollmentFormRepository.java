package org.dhis2.data.forms;

import android.content.ContentValues;
import android.database.Cursor;

import com.google.android.gms.maps.model.LatLng;
import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.data.forms.dataentry.fields.FieldViewModel;
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactoryImpl;
import org.dhis2.data.tuples.Pair;
import org.dhis2.data.tuples.Trio;
import org.dhis2.utils.CodeGenerator;
import org.dhis2.utils.Constants;
import org.dhis2.utils.DateUtils;
import org.hisp.dhis.android.core.category.CategoryComboModel;
import org.hisp.dhis.android.core.category.CategoryOptionComboModel;
import org.hisp.dhis.android.core.common.ObjectStyleModel;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.common.ValueType;
import org.hisp.dhis.android.core.common.ValueTypeDeviceRenderingModel;
import org.hisp.dhis.android.core.enrollment.EnrollmentModel;
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus;
import org.hisp.dhis.android.core.event.EventModel;
import org.hisp.dhis.android.core.event.EventStatus;
import org.hisp.dhis.android.core.period.PeriodType;
import org.hisp.dhis.android.core.program.ProgramModel;
import org.hisp.dhis.android.core.program.ProgramStageModel;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeValueModel;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceModel;
import org.hisp.dhis.rules.RuleEngine;
import org.hisp.dhis.rules.RuleEngineContext;
import org.hisp.dhis.rules.RuleExpressionEvaluator;
import org.hisp.dhis.rules.models.TriggerEnvironment;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.exceptions.OnErrorNotImplementedException;
import io.reactivex.functions.Consumer;
import timber.log.Timber;

import static android.text.TextUtils.isEmpty;

@SuppressWarnings({
        "PMD.AvoidDuplicateLiterals"
})
class EnrollmentFormRepository implements FormRepository {
    private static final List<String> TITLE_TABLES = Arrays.asList(
            EnrollmentModel.TABLE, ProgramModel.TABLE);

    private static final String SELECT_TITLE = "SELECT Program.displayName\n" +
            "FROM Enrollment\n" +
            "  JOIN Program ON Enrollment.program = Program.uid\n" +
            "WHERE Enrollment.uid = ? " +
            "LIMIT 1";

    private static final String SELECT_ENROLLMENT_UID = "SELECT Enrollment.uid\n" +
            "FROM Enrollment\n" +
            "WHERE Enrollment.uid = ?";

    private static final String SELECT_ENROLLMENT_STATUS = "SELECT Enrollment.status\n" +
            "FROM Enrollment\n" +
            "WHERE Enrollment.uid = ? " +
            "LIMIT 1";

    private static final String SELECT_ENROLLMENT_DATE = "SELECT Enrollment.*\n" +
            "FROM Enrollment\n" +
            "WHERE Enrollment.uid = ? " +
            "LIMIT 1";

    private static final String SELECT_ENROLLMENT_PROGRAM = "SELECT Program.*\n" +
            "FROM Program JOIN Enrollment ON Enrollment.program = Program.uid\n" +
            "WHERE Enrollment.uid = ? " +
            "LIMIT 1";

    private static final String SELECT_INCIDENT_DATE = "SELECT Enrollment.* FROM Enrollment WHERE Enrollment.uid = ? LIMIT 1";

    private static final String SELECT_AUTO_GENERATE_PROGRAM_STAGE = "SELECT " +
            "ProgramStage.uid, " +
            "Program.uid, " +
            "Enrollment.organisationUnit, " +
            "ProgramStage.minDaysFromStart, " +
            "ProgramStage.reportDateToUse, " +
            "Enrollment.incidentDate, " +
            "Enrollment.enrollmentDate, " +
            "ProgramStage.periodType \n" +
            "FROM Enrollment\n" +
            "  JOIN Program ON Enrollment.program = Program.uid\n" +
            "  JOIN ProgramStage ON Program.uid = ProgramStage.program \n" +
            "WHERE Enrollment.uid = ? AND ProgramStage.autoGenerateEvent = 1";

    private static final String GET_FIRST_STAGE = "SELECT " +
            "ProgramStage.* FROM ProgramStage " +
            "WHERE ProgramStage.program = ? " +
            "AND ProgramStage.sortOrder != 0 " +
            "ORDER BY ProgramStage.sortOrder ASC LIMIT 1";
    private static final String CHECK_IF_FIRST_STAGE_EVENT_EXIST_IN_ENROLLMENT = "SELECT " +
            "Event.uid, " +
            "Program.trackedEntityType " +
            "FROM Event " +
            "JOIN Enrollment ON Enrollment.uid = Event.enrollment " +
            "JOIN Program ON Program.uid = Enrollment.program " +
            "WHERE Event.programStage = ? AND Enrollment.uid = ?";

    private static final String SELECT_USE_FIRST_STAGE =
            "SELECT ProgramStage.uid, " +
                    "ProgramStage.program, " +
                    "Enrollment.organisationUnit, " +
                    "Program.trackedEntityType, " +
                    "Event.uid\n" +
                    "FROM Enrollment\n" +
                    "  JOIN Program ON Enrollment.program = Program.uid\n" +
                    "  JOIN ProgramStage ON Program.uid = ProgramStage.program\n" +
                    "  JOIN Event ON event.enrollment = Enrollment.uid\n" +
                    "WHERE Enrollment.uid = ? AND (Program.useFirstStageDuringRegistration  = 1 OR ProgramStage.openAfterEnrollment = 1) " +
                    "ORDER BY ProgramStage.sortOrder ASC LIMIT 1";

    private static final String SELECT_USE_FIRST_STAGE_WITHOUT_AUTOGENERATE_EVENT =
            "SELECT ProgramStage.uid, " +
                    "ProgramStage.program, " +
                    "Enrollment.organisationUnit, " +
                    "Program.trackedEntityType\n" +
                    "FROM Enrollment\n" +
                    "  JOIN Program ON Enrollment.program = Program.uid\n" +
                    "  JOIN ProgramStage ON Program.uid = ProgramStage.program\n" +
                    "WHERE Enrollment.uid = ? AND (Program.useFirstStageDuringRegistration  = 1 OR ProgramStage.openAfterEnrollment = 1) " +
                    "ORDER BY ProgramStage.sortOrder ASC";

    private static final String SELECT_PROGRAM = "SELECT \n" +
            "  program\n" +
            "FROM Enrollment\n" +
            "WHERE uid = ?\n" +
            "LIMIT 1;";

    private static final String SELECT_TE_TYPE = "SELECT " +
            "Program.uid," +
            "Enrollment.trackedEntityInstance\n" +
            "FROM Program\n" +
            "JOIN Enrollment ON Enrollment.program = Program.uid\n" +
            "WHERE Enrollment.uid = ? LIMIT 1";

    private static final String SELECT_VALUES = "SELECT TrackedEntityAttributeValue.value FROM TrackedEntityAttributeValue " +
            "JOIN TrackedEntityInstance ON TrackedEntityInstance.uid = TrackedEntityAttributeValue.trackedEntityInstance " +
            "JOIN Enrollment ON Enrollment.trackedEntityInstance = TrackedEntityInstance.uid WHERE Enrollment.uid = ?";

    private static final String QUERY = "SELECT \n" +
            "  Field.id,\n" +
            "  Field.label,\n" +
            "  Field.type,\n" +
            "  Field.mandatory,\n" +
            "  Field.optionSet,\n" +
            "  Value.value,\n" +
            "  Option.displayName,\n" +
            "  Field.allowFutureDate,\n" +
            "  Field.generated,\n" +
            "  Enrollment.organisationUnit,\n" +
            "  Enrollment.status,\n" +
            "  Field.displayDescription\n" +
            "FROM (Enrollment INNER JOIN Program ON Program.uid = Enrollment.program)\n" +
            "  LEFT OUTER JOIN (\n" +
            "      SELECT\n" +
            "        TrackedEntityAttribute.uid AS id,\n" +
            "        TrackedEntityAttribute.displayName AS label,\n" +
            "        TrackedEntityAttribute.valueType AS type,\n" +
            "        TrackedEntityAttribute.optionSet AS optionSet,\n" +
            "        ProgramTrackedEntityAttribute.program AS program,\n" +
            "        ProgramTrackedEntityAttribute.mandatory AS mandatory,\n" +
            "        ProgramTrackedEntityAttribute.allowFutureDate AS allowFutureDate,\n" +
            "        TrackedEntityAttribute.generated AS generated,\n" +
            "        TrackedEntityAttribute.displayDescription AS displayDescription\n" +
            "      FROM ProgramTrackedEntityAttribute INNER JOIN TrackedEntityAttribute\n" +
            "          ON TrackedEntityAttribute.uid = ProgramTrackedEntityAttribute.trackedEntityAttribute\n" +
            "    ) AS Field ON Field.program = Program.uid\n" +
            "  LEFT OUTER JOIN TrackedEntityAttributeValue AS Value ON (\n" +
            "    Value.trackedEntityAttribute = Field.id\n" +
            "        AND Value.trackedEntityInstance = Enrollment.trackedEntityInstance)\n" +
            "  LEFT OUTER JOIN Option ON (\n" +
            "    Field.optionSet = Option.optionSet AND Value.value = Option.code\n" +
            "  )\n" +
            "WHERE Enrollment.uid = ?";
    private static final String CHECK_STAGE_IS_NOT_CREATED = "SELECT * FROM Event JOIN Enrollment ON Event.enrollment = Enrollment.uid WHERE Enrollment.uid = ? AND Event.programStage = ?";
    @NonNull
    private final BriteDatabase briteDatabase;

    @NonNull
    private final CodeGenerator codeGenerator;

    @NonNull
    private final Flowable<RuleEngine> cachedRuleEngineFlowable;

    @NonNull
    private final String enrollmentUid;

    private String programUid;

    EnrollmentFormRepository(@NonNull BriteDatabase briteDatabase,
                             @NonNull RuleExpressionEvaluator expressionEvaluator,
                             @NonNull RulesRepository rulesRepository,
                             @NonNull CodeGenerator codeGenerator,
                             @NonNull String enrollmentUid) {
        this.briteDatabase = briteDatabase;
        this.codeGenerator = codeGenerator;
        this.enrollmentUid = enrollmentUid;

        // We don't want to rebuild RuleEngine on each request, since metadata of
        // the event is not changing throughout lifecycle of FormComponent.
        this.cachedRuleEngineFlowable = enrollmentProgram()
                .switchMap(program -> Flowable.zip(
                        rulesRepository.rulesNew(program),
                        rulesRepository.ruleVariables(program),
                        rulesRepository.enrollmentEvents(enrollmentUid),
                        (rules, variables, events) -> {
                            RuleEngine.Builder builder = RuleEngineContext.builder(expressionEvaluator)
                                    .rules(rules)
                                    .ruleVariables(variables)
                                    .calculatedValueMap(new HashMap<>())
                                    .supplementaryData(new HashMap<>())
                                    .build().toEngineBuilder();
                            builder.triggerEnvironment(TriggerEnvironment.ANDROIDCLIENT);
                            builder.events(events);
                            return builder.build();
                        }))
                .cacheWithInitialCapacity(1);
    }

    @NonNull
    @Override
    public Flowable<RuleEngine> ruleEngine() {
        return cachedRuleEngineFlowable;
    }

    @NonNull
    @Override
    public Flowable<String> title() {
        return briteDatabase
                .createQuery(TITLE_TABLES, SELECT_TITLE, enrollmentUid == null ? "" : enrollmentUid)
                .mapToOne(cursor -> cursor.getString(0)).toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<Pair<ProgramModel, String>> reportDate() {
        return briteDatabase.createQuery(ProgramModel.TABLE, SELECT_ENROLLMENT_PROGRAM, enrollmentUid == null ? "" : enrollmentUid)
                .mapToOne(ProgramModel::create)
                .flatMap(programModel -> briteDatabase.createQuery(EnrollmentModel.TABLE, SELECT_ENROLLMENT_DATE, enrollmentUid == null ? "" : enrollmentUid)
                        .mapToOne(EnrollmentModel::create)
                        .map(enrollmentModel -> Pair.create(programModel, enrollmentModel.enrollmentDate() != null ?
                                DateUtils.uiDateFormat().format(enrollmentModel.enrollmentDate()) : "")))
                .toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<Pair<ProgramModel, String>> incidentDate() {
        return briteDatabase.createQuery(ProgramModel.TABLE, SELECT_ENROLLMENT_PROGRAM, enrollmentUid == null ? "" : enrollmentUid)
                .mapToOne(ProgramModel::create)
                .flatMap(programModel -> briteDatabase.createQuery(EnrollmentModel.TABLE, SELECT_INCIDENT_DATE, enrollmentUid == null ? "" : enrollmentUid)
                        .mapToOne(EnrollmentModel::create)
                        .map(enrollmentModel -> Pair.create(programModel, enrollmentModel.incidentDate() != null ?
                                DateUtils.uiDateFormat().format(enrollmentModel.incidentDate()) : "")))
                .toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @Override
    public Flowable<ProgramModel> getAllowDatesInFuture() {
        return briteDatabase.createQuery(ProgramModel.TABLE, SELECT_ENROLLMENT_PROGRAM, enrollmentUid == null ? "" : enrollmentUid)
                .mapToOne(ProgramModel::create)
                .toFlowable(BackpressureStrategy.LATEST);
    }

    @NonNull
    @Override
    public Flowable<ReportStatus> reportStatus() {
        return briteDatabase
                .createQuery(EnrollmentModel.TABLE, SELECT_ENROLLMENT_STATUS, enrollmentUid == null ? "" : enrollmentUid)
                .mapToOne(cursor ->
                        ReportStatus.fromEnrollmentStatus(EnrollmentStatus.valueOf(cursor.getString(0)))).toFlowable(BackpressureStrategy.LATEST)
                .distinctUntilChanged();
    }

    @NonNull
    @Override
    public Flowable<List<FormSectionViewModel>> sections() {
        return briteDatabase
                .createQuery(EnrollmentModel.TABLE, SELECT_ENROLLMENT_UID, enrollmentUid == null ? "" : enrollmentUid)
                .mapToList(cursor -> FormSectionViewModel
                        .createForEnrollment(cursor.getString(0))).toFlowable(BackpressureStrategy.LATEST);
    }

    @NonNull
    @Override
    public Consumer<String> storeReportDate() {
        return reportDate -> {
            Calendar cal = Calendar.getInstance();
            Date date = DateUtils.databaseDateFormat().parse(reportDate);
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            ContentValues enrollment = new ContentValues();
            enrollment.put(EnrollmentModel.Columns.ENROLLMENT_DATE, DateUtils.databaseDateFormat().format(cal.getTime()));
            enrollment.put(EnrollmentModel.Columns.STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            briteDatabase.update(EnrollmentModel.TABLE, enrollment,
                    EnrollmentModel.Columns.UID + " = ?", enrollmentUid == null ? "" : enrollmentUid);
        };
    }

    @NonNull
    @Override
    public Consumer<LatLng> storeCoordinates() {
        return latLng -> {
            ContentValues enrollment = new ContentValues();
            enrollment.put(EnrollmentModel.Columns.LATITUDE, latLng.latitude);
            enrollment.put(EnrollmentModel.Columns.LONGITUDE, latLng.longitude); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            briteDatabase.update(EnrollmentModel.TABLE, enrollment,
                    EnrollmentModel.Columns.UID + " = ?", enrollmentUid == null ? "" : enrollmentUid);
        };
    }

    @NonNull
    @Override
    public Consumer<String> storeIncidentDate() {
        return incidentDate -> {
            Calendar cal = Calendar.getInstance();
            Date date = DateUtils.databaseDateFormat().parse(incidentDate);
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            ContentValues enrollment = new ContentValues();
            enrollment.put(EnrollmentModel.Columns.INCIDENT_DATE, DateUtils.databaseDateFormat().format(cal.getTime()));
            enrollment.put(EnrollmentModel.Columns.STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            briteDatabase.update(EnrollmentModel.TABLE, enrollment,
                    EnrollmentModel.Columns.UID + " = ?", enrollmentUid == null ? "" : enrollmentUid);
        };
    }

    @NonNull
    @Override
    public Consumer<ReportStatus> storeReportStatus() {
        return reportStatus -> {
            ContentValues enrollment = new ContentValues();
            enrollment.put(EnrollmentModel.Columns.ENROLLMENT_STATUS,
                    ReportStatus.toEnrollmentStatus(reportStatus).name());
            enrollment.put(EnrollmentModel.Columns.STATE, State.TO_UPDATE.name()); // TODO: Check if state is TO_POST
            // TODO: and if so, keep the TO_POST state

            briteDatabase.update(EnrollmentModel.TABLE, enrollment,
                    EnrollmentModel.Columns.UID + " = ?", enrollmentUid == null ? "" : enrollmentUid);
        };
    }

    @NonNull
    @Override
    public Observable<String> autoGenerateEvents(String enrollmentUid) {

        Calendar calNow = Calendar.getInstance();
        calNow.set(Calendar.HOUR_OF_DAY, 0);
        calNow.set(Calendar.MINUTE, 0);
        calNow.set(Calendar.SECOND, 0);
        calNow.set(Calendar.MILLISECOND, 0);
        Date now = calNow.getTime();


        Cursor cursor = briteDatabase.query(SELECT_AUTO_GENERATE_PROGRAM_STAGE, enrollmentUid == null ? "" : enrollmentUid);

        if (cursor != null) {
            cursor.moveToFirst();
            for (int i = 0; i < cursor.getCount(); i++) {


                String programStage = cursor.getString(0);
                String program = cursor.getString(1);
                String orgUnit = cursor.getString(2);
                int minDaysFromStart = cursor.getInt(3);
                String reportDateToUse = cursor.getString(4) != null ? cursor.getString(4) : "";
                String incidentDateString = cursor.getString(5);
                String reportDateString = cursor.getString(6);
                Date incidentDate = null;
                Date enrollmentDate = null;
                PeriodType periodType = cursor.getString(7) != null ? PeriodType.valueOf(cursor.getString(7)) : null;

                if (incidentDateString != null)
                    try {
                        incidentDate = DateUtils.databaseDateFormat().parse(incidentDateString);
                    } catch (Exception e) {
                        Timber.e(e);
                    }

                if (reportDateString != null)
                    try {
                        enrollmentDate = DateUtils.databaseDateFormat().parse(reportDateString);
                    } catch (Exception e) {
                        Timber.e(e);
                    }

                Date eventDate;
                Calendar cal = DateUtils.getInstance().getCalendar();
                switch (reportDateToUse) {
                    case Constants.ENROLLMENT_DATE:
                        cal.setTime(enrollmentDate != null ? enrollmentDate : Calendar.getInstance().getTime());
                        break;
                    case Constants.INCIDENT_DATE:
                        cal.setTime(incidentDate != null ? incidentDate : Calendar.getInstance().getTime());
                        break;
                    default:
                        cal.setTime(Calendar.getInstance().getTime());
                        break;
                }
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.add(Calendar.DATE, minDaysFromStart);
                eventDate = cal.getTime();

                if (periodType != null)
                    eventDate = DateUtils.getInstance().getNextPeriod(periodType, eventDate, 0); //Sets eventDate to current Period date

                Cursor eventCursor = briteDatabase.query(CHECK_STAGE_IS_NOT_CREATED, enrollmentUid, programStage);

                if (!eventCursor.moveToFirst()) {

                    EventModel.Builder eventBuilder = EventModel.builder()
                            .uid(codeGenerator.generate())
                            .created(Calendar.getInstance().getTime())
                            .lastUpdated(Calendar.getInstance().getTime())
//                            .eventDate(eventDate)
//                            .dueDate(eventDate)
                            .enrollment(enrollmentUid)
                            .program(program)
                            .programStage(programStage)
                            .organisationUnit(orgUnit)
                            .status(eventDate.after(now) ? EventStatus.SCHEDULE : EventStatus.ACTIVE)
                            .state(State.TO_POST);
                    if (eventDate.after(now)) //scheduling
                        eventBuilder.dueDate(eventDate);
                    else
                        eventBuilder.eventDate(eventDate);

                    EventModel event = eventBuilder.build();


                    if (briteDatabase.insert(EventModel.TABLE, event.toContentValues()) < 0) {
                        throw new OnErrorNotImplementedException(new Throwable("Unable to store event:" + event));
                    }

                } else
                    eventCursor.close();

                cursor.moveToNext();
            }
            cursor.close();

        }

        return Observable.just(enrollmentUid);
    }

    @NonNull
    @Override
    public Observable<List<FieldViewModel>> fieldValues() {
        return briteDatabase
                .createQuery(TrackedEntityAttributeValueModel.TABLE, QUERY, enrollmentUid == null ? "" : enrollmentUid)
                .mapToList(this::transform);
    }


    @Override
    public void deleteTrackedEntityAttributeValues(@NonNull String trackedEntityInstanceId) {
        String DELETE_WHERE_RELATIONSHIP = String.format(
                "%s.%s = ",
                TrackedEntityAttributeValueModel.TABLE, TrackedEntityAttributeValueModel.Columns.TRACKED_ENTITY_INSTANCE);
        briteDatabase.delete(TrackedEntityAttributeValueModel.TABLE, DELETE_WHERE_RELATIONSHIP + "'" + trackedEntityInstanceId + "'");
    }

    @Override
    public void deleteEnrollment(@NonNull String trackedEntityInstanceId) {
        String DELETE_WHERE_RELATIONSHIP = String.format(
                "%s.%s = ",
                EnrollmentModel.TABLE, EnrollmentModel.Columns.TRACKED_ENTITY_INSTANCE);
        briteDatabase.delete(EnrollmentModel.TABLE, DELETE_WHERE_RELATIONSHIP + "'" + trackedEntityInstanceId + "'");
    }

    @Override
    public void deleteEvent() {
        // not necessary
    }

    @Override
    public void deleteTrackedEntityInstance(@NonNull String trackedEntityInstanceId) {
        String DELETE_WHERE_RELATIONSHIP = String.format(
                "%s.%s = ",
                TrackedEntityInstanceModel.TABLE, TrackedEntityInstanceModel.Columns.UID);
        briteDatabase.delete(TrackedEntityInstanceModel.TABLE, DELETE_WHERE_RELATIONSHIP + "'" + trackedEntityInstanceId + "'");
    }

    @NonNull
    @Override
    public Observable<String> getTrackedEntityInstanceUid() {
        String SELECT_TE = "SELECT " + EnrollmentModel.TABLE + "." + EnrollmentModel.Columns.TRACKED_ENTITY_INSTANCE +
                " FROM " + EnrollmentModel.TABLE +
                " WHERE " + EnrollmentModel.Columns.UID + " = ?" +
                " LIMIT 1";

        return briteDatabase.createQuery(EnrollmentModel.TABLE, SELECT_TE, enrollmentUid == null ? "" : enrollmentUid).mapToOne(cursor -> cursor.getString(0));
    }

    @Override
    public Observable<Trio<Boolean, CategoryComboModel, List<CategoryOptionComboModel>>> getProgramCategoryCombo() {
        return null;
    }

    @Override
    public void saveCategoryOption(CategoryOptionComboModel selectedOption) {

    }

    @Override
    public Observable<Boolean> captureCoodinates() {
        return briteDatabase.createQuery("Program", "SELECT Program.captureCoordinates FROM Program " +
                "JOIN Enrollment ON Enrollment.program = Program.uid WHERE Enrollment.uid = ?", enrollmentUid)
                .mapToOne(cursor -> cursor.getInt(0) == 1);
    }

    @NonNull
    private FieldViewModel transform(@NonNull Cursor cursor) {
        String uid = cursor.getString(0);
        String label = cursor.getString(1);
        ValueType valueType = ValueType.valueOf(cursor.getString(2));
        boolean mandatory = cursor.getInt(3) == 1;
        String optionSetUid = cursor.getString(4);
        String dataValue = cursor.getString(5);
        String optionCodeName = cursor.getString(6);
        String section = cursor.getString(7);
        Boolean allowFutureDates = cursor.getInt(8) == 1;
        EnrollmentStatus status = EnrollmentStatus.valueOf(cursor.getString(10));
        String description = cursor.getString(11);
        if (!isEmpty(optionCodeName)) {
            dataValue = optionCodeName;
        }

        int optionCount = 0;
        try{
            Cursor countCursor = briteDatabase.query("SELECT COUNT (uid) FROM Option WHERE optionSet = ?", optionSetUid);
            if(countCursor!=null){
                if(countCursor.moveToFirst())
                    optionCount = countCursor.getInt(0);
                countCursor.close();
            }
        }catch (Exception e){
            Timber.e(e);
        }

        ValueTypeDeviceRenderingModel fieldRendering = null;
        Cursor rendering = briteDatabase.query("SELECT ValueTypeDeviceRendering.* FROM ValueTypeDeviceRendering " +
                "JOIN ProgramTrackedEntityAttribute ON ProgramTrackedEntityAttribute.uid = ValueTypeDeviceRendering.uid WHERE ProgramTrackedEntityAttribute.trackedEntityAttribute = ?", uid);
        if (rendering != null) {
            if (rendering.moveToFirst())
                fieldRendering = ValueTypeDeviceRenderingModel.create(rendering);
            rendering.close();
        }

        FieldViewModelFactoryImpl fieldFactory = new FieldViewModelFactoryImpl(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");

        ObjectStyleModel objectStyle = ObjectStyleModel.builder().build();
        Cursor objStyleCursor = briteDatabase.query("SELECT * FROM ObjectStyle WHERE uid = ?", uid);
        try {
            if (objStyleCursor.moveToFirst())
                objectStyle = ObjectStyleModel.create(objStyleCursor);
        } finally {
            if (objStyleCursor != null)
                objStyleCursor.close();
        }

        return fieldFactory.create(uid, label, valueType, mandatory, optionSetUid, dataValue, section,
                allowFutureDates, status == EnrollmentStatus.ACTIVE, null, description, fieldRendering,optionCount,objectStyle);
    }

    @NonNull
    @Override
    public Observable<Trio<String, String, String>> useFirstStageDuringRegistration() { //enrollment uid, trackedEntityType, event uid

        return briteDatabase.createQuery(ProgramModel.TABLE, "SELECT * FROM Program WHERE uid = ?", programUid)
                .mapToOne(ProgramModel::create)
                .flatMap(programModel ->
                        briteDatabase.createQuery(ProgramStageModel.TABLE, "SELECT * FROM ProgramStage WHERE program = ? ORDER BY ProgramStage.sortOrder", programModel.uid())
                                .mapToList(ProgramStageModel::create).map(programstages -> Trio.create(programModel.useFirstStageDuringRegistration(), programstages, programModel.trackedEntityType())))
                .map(data -> {
                    ProgramStageModel stageToOpen = null;
                    if (data.val0()) {
                        stageToOpen = data.val1().get(0);
                    } else {
                        for (ProgramStageModel programStage : data.val1()) {
                            if (programStage.openAfterEnrollment() && stageToOpen == null)
                                stageToOpen = programStage;
                        }
                    }

                    if (stageToOpen != null) { //we should check if event exist (if not create) and open
                        Cursor eventCursor = briteDatabase.query("SELECT Event.uid FROM Event WHERE Event.programStage = ? AND Event.enrollment = ?", stageToOpen.uid(), enrollmentUid);
                        if (eventCursor != null && eventCursor.moveToFirst()) {
                            String eventUid = eventCursor.getString(0);
                            eventCursor.close();
                            return Trio.create(getTeiUid(), programUid, eventUid);
                        } else {
                            Cursor enrollmentOrgUnitCursor = briteDatabase.query("SELECT Enrollment.organisationUnit FROM Enrollment WHERE Enrollment.uid = ?", enrollmentUid);
                            if (enrollmentOrgUnitCursor != null && enrollmentOrgUnitCursor.moveToFirst()) {
                                Date createdDate = DateUtils.getInstance().getCalendar().getTime();
                                EventModel eventToCreate = EventModel.builder()
                                        .uid(codeGenerator.generate())
                                        .created(createdDate)
                                        .lastUpdated(createdDate)
                                        .eventDate(createdDate)
                                        .enrollment(enrollmentUid)
                                        .program(stageToOpen.program())
                                        .programStage(stageToOpen.uid())
                                        .organisationUnit(enrollmentOrgUnitCursor.getString(0))
                                        .status(EventStatus.ACTIVE)
                                        .state(State.TO_POST)
                                        .build();

                                enrollmentOrgUnitCursor.close();
                                if (briteDatabase.insert(EventModel.TABLE, eventToCreate.toContentValues()) < 0) {
                                    throw new OnErrorNotImplementedException(new Throwable("Unable to store event:" + eventToCreate));
                                }

                                return Trio.create(getTeiUid(), programUid, eventToCreate.uid());//teiUid, programUio, eventUid
                            } else
                                throw new IllegalArgumentException("Can't create event in enrollment with null organisation unit");
                        }
                    } else { //open Dashboard
                        Cursor tetCursor = briteDatabase.query(SELECT_TE_TYPE, enrollmentUid == null ? "" : enrollmentUid);
                        String programUid = "";
                        String teiUid = "";
                        if (tetCursor != null && tetCursor.moveToFirst()) {
                            programUid = tetCursor.getString(0);
                            teiUid = tetCursor.getString(1);
                            tetCursor.close();
                        }
                        return Trio.create(teiUid, programUid, "");
                    }
                });
    }

    private String getTeiUid() {
        Cursor teiUidCursor = briteDatabase.query("SELECT DISTINCT TrackedEntityInstance.uid " +
                "FROM TrackedEntityInstance JOIN Enrollment ON Enrollment.trackedEntityInstance = TrackedEntityInstance.uid " +
                "WHERE Enrollment.uid = ? LIMIT 1", enrollmentUid);
        String teiUid = "";
        if (teiUidCursor != null && teiUidCursor.moveToFirst()) {
            teiUid = teiUidCursor.getString(0);
            teiUidCursor.close();
        }
        return teiUid;
    }

    @NonNull
    private Flowable<String> enrollmentProgram() {
        return briteDatabase
                .createQuery(EnrollmentModel.TABLE, SELECT_PROGRAM, enrollmentUid == null ? "" : enrollmentUid)
                .mapToOne(cursor -> {
                    programUid = cursor.getString(0);
                    return programUid;
                })
                .toFlowable(BackpressureStrategy.LATEST);
    }
}