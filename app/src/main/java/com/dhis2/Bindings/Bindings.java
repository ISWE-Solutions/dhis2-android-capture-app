package com.dhis2.Bindings;

import android.annotation.SuppressLint;
import android.content.res.TypedArray;
import android.databinding.BindingAdapter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.dhis2.R;
import com.dhis2.data.forms.dataentry.OptionAdapter;
import com.dhis2.data.metadata.MetadataRepository;
import com.dhis2.utils.CatComboAdapter;
import com.dhis2.utils.DateUtils;

import org.hisp.dhis.android.core.category.CategoryOptionComboModel;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus;
import org.hisp.dhis.android.core.event.EventModel;
import org.hisp.dhis.android.core.event.EventStatus;
import org.hisp.dhis.android.core.program.ProgramType;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by ppajuelo on 28/09/2017.
 */

public class Bindings {

    private static MetadataRepository metadataRepository;

    @BindingAdapter("date")
    public static void setDate(TextView textView, String date) {
        SimpleDateFormat formatIn = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        SimpleDateFormat formatOut = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        try {
            Date dateIn = formatIn.parse(date);
            String dateOut = formatOut.format(dateIn);
            textView.setText(dateOut);
        } catch (ParseException e) {
            Timber.e(e);
        }

    }

    @BindingAdapter("currentFragment")
    public static void setCurrentFragment(TextView textView, int currentFragmentId) {
        TypedValue typedValue = new TypedValue();
        TypedArray a = textView.getContext().obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorPrimary});
        int colorPrimary = a.getColor(0, 0);
        a.recycle();
        int colorAccent = ContextCompat.getColor(textView.getContext(), R.color.colorAccent);
        if (currentFragmentId == textView.getId()) {
            textView.setTextColor(colorPrimary);
            textView.setBackgroundColor(colorAccent);
        } else {
            textView.setTextColor(colorAccent);
            textView.setBackgroundColor(colorPrimary);
        }
    }

    @BindingAdapter("date")
    public static void parseDate(TextView textView, Date date) {
        if (date != null) {
            SimpleDateFormat formatOut = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String dateOut = formatOut.format(date);
            textView.setText(dateOut);
        }

    }

    @BindingAdapter("drawableEnd")
    public static void setDrawableEnd(TextView textView, Drawable drawable) {
        textView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (drawable instanceof AnimatedVectorDrawable)
                ((AnimatedVectorDrawable) drawable).start();
        }
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("lastEventDate")
    public static void setLastEventDate(TextView textView, Observable<List<EventModel>> listObservable) {
        listObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        data -> textView.setText(DateUtils.getInstance().formatDate(data.get(0).lastUpdated())),
                        Timber::d);
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("numberOfEvents")
    public static void setNumberOfEvents(TextView textView, Observable<List<EventModel>> listObservable) {
        listObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        data -> {
                            String text = data.size() + " " + textView.getContext().getString(R.string.events);
                            textView.setText(text);
                        },
                        Timber::d);
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("enrollmentLastEventDate")
    public static void setEnrollmentLastEventDate(TextView textView, String enrollmentUid) {
        metadataRepository.getEnrollmentLastEvent(enrollmentUid)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        data -> textView.setText(DateUtils.getInstance().formatDate(data.eventDate())),
                        Timber::d);
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("eventLabel")
    public static void setEventLabel(TextView textView, String programUid) {
        metadataRepository.getProgramWithId(programUid)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        data -> textView.setText(data.displayIncidentDate() ? data.incidentDateLabel() : data.enrollmentDateLabel()),
                        Timber::d);
    }


    @BindingAdapter("initGrid")
    public static void setLayoutManager(RecyclerView recyclerView, boolean horizontal) {
        RecyclerView.LayoutManager recyclerLayout;
        if (!horizontal)
            recyclerLayout = new GridLayoutManager(recyclerView.getContext(), 2, LinearLayoutManager.VERTICAL, false);
        else
            recyclerLayout = new GridLayoutManager(recyclerView.getContext(), 4, LinearLayoutManager.VERTICAL, false);

        recyclerView.setLayoutManager(recyclerLayout);

    }

    @BindingAdapter("spanSize")
    public static void setSpanSize(RecyclerView recyclerView, boolean setSpanSize) {
        if (recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    int itemViewType = recyclerView.getAdapter().getItemViewType(position);
                    return (itemViewType == 4 || itemViewType == 8) ? 2 : 1;
                }
            });
        }
    }

    @BindingAdapter("randomColor")
    public static void setRandomColor(ImageView imageView, String textToColor) {
        String color;
        if (textToColor != null)
            color = String.format("#%X", String.valueOf(textToColor).hashCode());
        else
            color = "#FFFFFF";

        imageView.setBackgroundColor(Color.parseColor(color));
    }


    @BindingAdapter("tintRandomColor")
    public static void setTintRandomColor(ImageView imageView, String textToColor) {
        String color;
        if (textToColor != null)
            color = String.format("#%X", textToColor.hashCode());
        else
            color = "#FFFFFF";

        Drawable drawable = ContextCompat.getDrawable(imageView.getContext(), R.drawable.ic_program).mutate();
        drawable.setColorFilter(Color.parseColor(color), PorterDuff.Mode.SRC_IN);
        imageView.setImageDrawable(drawable);
    }

    @BindingAdapter("programTypeIcon")
    public static void setProgramIcon(ImageView view, ProgramType programType) {
        if (programType.equals(ProgramType.WITH_REGISTRATION))
            view.setImageDrawable(ContextCompat.getDrawable(view.getContext(), R.drawable.ic_with_registration));
        else
            view.setImageDrawable(ContextCompat.getDrawable(view.getContext(), R.drawable.ic_without_reg));
    }

    @BindingAdapter("progressColor")
    public static void setProgressColor(ProgressBar progressBar, int color) {
        TypedValue typedValue = new TypedValue();
        TypedArray a = progressBar.getContext().obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorPrimary});
        color = a.getColor(0, 0);
        a.recycle();
        progressBar.getIndeterminateDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("programStage")
    public static void getStageName(TextView textView, String stageId) {
        metadataRepository.programStage(stageId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        programStageModel -> textView.setText(programStageModel.displayName()),
                        Timber::d
                );
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("programStageDescription")
    public static void getStageDescription(TextView textView, String stageId) {
        metadataRepository.programStage(stageId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        // TODO CRIS: ADD DESCRIPTION IN NEXT SDK RELEASES
                        programStageModel -> textView.setText(textView.getContext().getString(R.string.lorem)),
                        Timber::d
                );
    }

    @BindingAdapter("srcBackGround")
    public static void setBackGroundCompat(View view, int drawableId) {
        view.setBackground(ContextCompat.getDrawable(view.getContext(), drawableId));
    }

    @BindingAdapter("enrolmentIcon")
    public static void setEnrolmentIcon(ImageView view, EnrollmentStatus status) {
        Drawable lock;
        if (status == null)
            status = EnrollmentStatus.ACTIVE;
        switch (status) {
            case ACTIVE:
                lock = ContextCompat.getDrawable(view.getContext(), R.drawable.ic_lock_open_green);
                break;
            case COMPLETED:
                lock = ContextCompat.getDrawable(view.getContext(), R.drawable.ic_lock_completed);
                break;
            case CANCELLED:
                lock = ContextCompat.getDrawable(view.getContext(), R.drawable.ic_lock_inactive);
                break;
            default:
                lock = ContextCompat.getDrawable(view.getContext(), R.drawable.ic_lock_read_only);
                break;
        }

        view.setImageDrawable(lock);

    }

    @BindingAdapter("enrolmentAction")
    public static void setEnrolmentAction(TextView textView, EnrollmentStatus status) {
        String action;
        if (status == null)
            status = EnrollmentStatus.ACTIVE;
        switch (status) {
            case ACTIVE:
                action = textView.getContext().getString(R.string.complete);
                break;
            case COMPLETED:
                action = textView.getContext().getString(R.string.re_open);
                break;
            case CANCELLED:
                action = textView.getContext().getString(R.string.activate);
                break;
            default:
                action = "";
                break;
        }
        textView.setText(action);
    }

    @BindingAdapter("enrolmentText")
    public static void setEnrolmentText(TextView view, EnrollmentStatus status) {
        String text;
        if (status == null)
            status = EnrollmentStatus.ACTIVE;
        switch (status) {
            case ACTIVE:
                text = view.getContext().getString(R.string.event_open);
                break;
            case COMPLETED:
                text = view.getContext().getString(R.string.completed);
                break;
            case CANCELLED:
                text = view.getContext().getString(R.string.cancelled);
                break;
            default:
                text = view.getContext().getString(R.string.read_only);
                break;
        }

        view.setText(text);
    }

    @BindingAdapter("eventIcon")
    public static void setEventIcon(ImageView view, EventStatus status) {
        Drawable lock;
        if (status == null)
            status = EventStatus.ACTIVE;
        switch (status) {
            default:
                lock = ContextCompat.getDrawable(view.getContext(), R.drawable.ic_edit);
                break;
            case COMPLETED:
                lock = ContextCompat.getDrawable(view.getContext(), R.drawable.ic_visibility);
                break;
        }

        view.setImageDrawable(lock);

    }

    @BindingAdapter("eventText")//TODO: IT NEEDS ENROLLMENTSTATUS
    public static void setEventText(TextView view, EventStatus status) {
        String text;
        if (status == null)
            status = EventStatus.ACTIVE;
        switch (status) {
            case ACTIVE:
                text = view.getContext().getString(R.string.event_open);
                break;
            case COMPLETED:
                text = view.getContext().getString(R.string.program_completed);
                break;
            case SCHEDULE:
                text = view.getContext().getString(R.string.program_inactive);
                break;
            default:
                text = view.getContext().getString(R.string.read_only);
                break;
        }

        view.setText(text);
    }

    @BindingAdapter("eventColor")
    public static void setEventText(CardView view, EventStatus status) {
        int eventColor;
        if (status == null)
            status = EventStatus.ACTIVE;
        switch (status) {
            case ACTIVE:
                eventColor = R.color.event_yellow;
                break;
            case COMPLETED:
                eventColor = R.color.event_gray;
                break;
            case SCHEDULE:
                eventColor = R.color.event_green;
                break;
            default:
                eventColor = R.color.event_red;
                break;
        }

        view.setCardBackgroundColor(ContextCompat.getColor(view.getContext(), eventColor));
    }

    @BindingAdapter("scheduleColor")
    public static void setScheduleColor(ImageView view, EventStatus status) {
        int drawable;
        if (status == null)
            status = EventStatus.ACTIVE;
        switch (status) {
            case SCHEDULE:
                drawable = R.drawable.schedule_circle_green;
                break;
            default:
                drawable = R.drawable.schedule_circle_red;
                break;
        }

        view.setImageDrawable(ContextCompat.getDrawable(view.getContext(), drawable));
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("programName")
    public static void setProgramName(TextView textView, String programUid) {
        metadataRepository.getProgramWithId(programUid)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        programModel -> textView.setText(programModel.displayShortName()),
                        Timber::d
                );
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("organisationUnitName")
    public static void setOrganisationUnitName(TextView textView, String organisationUnitUid) {
        metadataRepository.getOrganisationUnit(organisationUnitUid)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        organisationUnitModel -> textView.setText(organisationUnitModel.displayShortName()),
                        Timber::d
                );
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("categoryOptionName")
    public static void setCategoryOptionName(TextView textView, String categoryOptionId) {
        metadataRepository.getCategoryOptionWithId(categoryOptionId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        categoryOptionModel -> textView.setText(categoryOptionModel.displayName()),
                        Timber::d
                );
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("categoryOptionComboName")
    public static void setCategoryOptionComboName(TextView textView, String categoryOptionComboId) {
        metadataRepository.getCategoryOptionComboWithId(categoryOptionComboId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        categoryOptionComboModel -> textView.setText(categoryOptionComboModel.displayName()),
                        Timber::d
                );
    }

    @BindingAdapter("eventWithoutRegistrationStatusText")
    public static void setEventWithoutRegistrationStatusText(TextView textView, EventModel eventModel) {
        if (eventModel.dueDate() != null && !DateUtils.getInstance().hasExpired(eventModel.dueDate())) {
            textView.setText(textView.getContext().getString(R.string.event_editing_expired));
            textView.setTextColor(ContextCompat.getColor(textView.getContext(), R.color.red_060));
        } else {
            switch (eventModel.status()) {
                case ACTIVE:
                    textView.setText(textView.getContext().getString(R.string.event_open));
                    textView.setTextColor(ContextCompat.getColor(textView.getContext(), R.color.yellow_fdd));
                    break;
                case COMPLETED:
                    textView.setText(textView.getContext().getString(R.string.event_completed));
                    textView.setTextColor(ContextCompat.getColor(textView.getContext(), R.color.gray_b2b));
                    break;
                default:
                    // TODO CRIS: HERE CHECK THE EVENT APPROVAL
                    textView.setText(textView.getContext().getString(R.string.read_only));
                    textView.setTextColor(ContextCompat.getColor(textView.getContext(), R.color.green_7ed));
                    break;
            }
        }
    }

    @BindingAdapter("eventWithoutRegistrationStatusIcon")
    public static void setEventWithoutRegistrationStatusIcon(ImageView imageView, EventModel eventModel) {
        if (eventModel.dueDate() != null && !DateUtils.getInstance().hasExpired(eventModel.dueDate())) {
            imageView.setImageResource(R.drawable.ic_eye_red);
        } else {
            switch (eventModel.status()) {
                case ACTIVE:
                    imageView.setImageResource(R.drawable.ic_edit_yellow);
                    break;
                case COMPLETED:
                    imageView.setImageResource(R.drawable.ic_eye_grey);
                    break;
                default:
                    // TODO CRIS: HERE CHECK THE EVENT APPROVAL
                    imageView.setImageResource(R.drawable.ic_eye_green);
                    break;
            }
        }
    }

    @BindingAdapter("stateText")
    public static void setStateText(TextView textView, State state) {
        switch (state) {
            case TO_POST:
                textView.setText(textView.getContext().getString(R.string.state_to_post));
                break;
            case TO_UPDATE:
                textView.setText(textView.getContext().getString(R.string.state_to_update));
                break;
            case TO_DELETE:
                textView.setText(textView.getContext().getString(R.string.state_to_delete));
                break;
            case ERROR:
                textView.setText(textView.getContext().getString(R.string.state_error));
                break;
            case SYNCED:
                textView.setText(textView.getContext().getString(R.string.state_synced));
                break;
            default:
                break;
        }
    }

    @BindingAdapter("stateIcon")
    public static void setStateIcon(ImageView imageView, State state) {
        switch (state) {
            case TO_POST:
                imageView.setImageResource(R.drawable.ic_sync_problem_grey);
                break;
            case TO_UPDATE:
                imageView.setImageResource(R.drawable.ic_sync_problem_grey);
                break;
            case TO_DELETE:
                imageView.setImageResource(R.drawable.ic_sync_problem_grey);
                break;
            case ERROR:
                imageView.setImageResource(R.drawable.ic_sync_problem_red);
                break;
            case SYNCED:
                imageView.setImageResource(R.drawable.ic_sync);
                break;
            default:
                break;
        }
    }

    public static void setMetadataRepository(MetadataRepository metadata) {
        metadataRepository = metadata;
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("dataElementHint")
    public static void setDataElementName(TextInputLayout view, String dataElementUid) {
        metadataRepository.getDataElement(dataElementUid)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        dataModel -> view.setHint(dataModel.displayShortName()),
                        Timber::d
                );
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("attrHint")
    public static void setAttrName(TextInputLayout view, String teAttribute) {
        metadataRepository.getTrackedEntityAttribute(teAttribute)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        dataModel -> view.setHint(dataModel.displayShortName()),
                        Timber::d
                );
    }

    @BindingAdapter("spinnerOptions")
    public static void setSpinnerOptions(Spinner spinner, List<CategoryOptionComboModel> options) {
        CatComboAdapter adapter = new CatComboAdapter(spinner.getContext(),
                R.layout.spinner_layout,
                R.id.spinner_text,
                options,
                "",
                R.color.white_faf);
        spinner.setAdapter(adapter);
    }

    @SuppressLint("CheckResult")
    @BindingAdapter("eventCompletion")
    public static void setEventCompletion(TextView textView, EventModel eventModel) {
        metadataRepository.getProgramStageDataElementCount(eventModel.programStage())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        programStageCount -> metadataRepository.getTrackEntityDataValueCount(eventModel.uid()).subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        trackEntityCount -> {
                                            float perone = (float) trackEntityCount / (float) programStageCount;
                                            int percent = (int) (perone * 100);
                                            String completionText = textView.getContext().getString(R.string.completion) + " " + percent + "%";
                                            textView.setText(completionText);
                                        },
                                        Timber::d
                                ),
                        Timber::d
                );
    }

    @BindingAdapter({"optionSet", "label"})
    public static void setOptionSet(Spinner spinner, String optionSet, String label) {
        if (metadataRepository != null && optionSet != null)
            metadataRepository.optionSet(optionSet)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            optionModels -> {
                                OptionAdapter adapter = new OptionAdapter(spinner.getContext(),
                                        R.layout.spinner_layout,
                                        R.id.spinner_text,
                                        optionModels,
                                        label);
                                spinner.setAdapter(adapter);
                                spinner.setPrompt(label);
                            },
                            Timber::d);
    }
}