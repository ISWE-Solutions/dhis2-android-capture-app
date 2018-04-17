package com.dhis2.data.forms;

import android.support.annotation.NonNull;

import com.dhis2.data.tuples.Trio;

import org.hisp.dhis.android.core.program.ProgramModel;
import org.hisp.dhis.rules.RuleEngine;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;

public interface FormRepository {

    @NonNull
    Flowable<String> title();

    @NonNull
    Flowable<String> reportDate();

    Flowable<ProgramModel> incidentDate();

    @NonNull
    Flowable<RuleEngine> ruleEngine();

    @NonNull
    Consumer<String> storeReportDate();

    Consumer<String> storeIncidentDate();

    @NonNull
    Flowable<ReportStatus> reportStatus();

    @NonNull
    Flowable<List<FormSectionViewModel>> sections();

    @NonNull
    Consumer<ReportStatus> storeReportStatus();

    @NonNull
    Consumer<String> autoGenerateEvent();

    @NonNull
    Observable<Trio<String, String, String>> useFirstStageDuringRegistration();

}