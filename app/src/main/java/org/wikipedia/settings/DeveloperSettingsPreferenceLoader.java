package org.wikipedia.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.TwoStatePreference;
import android.text.TextUtils;

import org.wikipedia.R;
import org.wikipedia.WikipediaApp;
import org.wikipedia.crash.RemoteLogException;
import org.wikipedia.dataclient.WikiSite;
import org.wikipedia.editactionfeed.provider.MissingDescriptionProvider;
import org.wikipedia.history.HistoryEntry;
import org.wikipedia.page.PageActivity;
import org.wikipedia.page.PageTitle;
import org.wikipedia.readinglist.database.ReadingList;
import org.wikipedia.readinglist.database.ReadingListDbHelper;
import org.wikipedia.readinglist.database.ReadingListPage;
import org.wikipedia.util.StringUtil;
import org.wikipedia.util.log.L;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

class DeveloperSettingsPreferenceLoader extends BasePreferenceLoader {
    private static final String TEXT_OF_TEST_READING_LIST = "Test reading list";
    private static final String TEXT_OF_READING_LIST = "Reading list";

    @NonNull private final Context context;

    @NonNull private final Preference.OnPreferenceChangeListener setRestBaseManuallyChangeListener
            = new Preference.OnPreferenceChangeListener() {
        /**
         * Called when the useRestBaseSetManually preference has been changed by the user. This is
         * called before the state of the Preference is about to be updated and
         * before the state is persisted.
         *
         * @param preference The changed Preference.
         * @param newValue   The new value of the Preference.
         * @return True to update the state of the Preference with the new value.
         */
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            setUseRestBasePreference((Boolean) newValue);
            return true;
        }
    };

    @NonNull private final Preference.OnPreferenceChangeListener setMediaWikiBaseUriChangeListener
            = new Preference.OnPreferenceChangeListener() {
        /**
         * Called when the mediaWikiBaseUri preference has been changed by the user. This is
         * called before the state of the Preference is about to be updated and
         * before the state is persisted.
         *
         * @param preference The changed Preference.
         * @param newValue   The new value of the Preference.
         * @return True to update the state of the Preference with the new value.
         */
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            resetMediaWikiSettings();
            return true;
        }
    };

    @NonNull private final Preference.OnPreferenceChangeListener setMediaWikiMultiLangSupportChangeListener
            = new Preference.OnPreferenceChangeListener() {
        /**
         * Called when the mediaWikiBaseUriSupportsLangCode preference has been changed by the user.
         * This is called before the state of the Preference is about to be updated and
         * before the state is persisted.
         *
         * @param preference The changed Preference.
         * @param newValue   The new value of the Preference.
         * @return True to update the state of the Preference with the new value.
         */
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            resetMediaWikiSettings();
            return true;
        }
    };

    DeveloperSettingsPreferenceLoader(@NonNull PreferenceFragmentCompat fragment) {
        super(fragment);
        this.context = fragment.requireActivity();
    }

    @Override
    public void loadPreferences() {
        loadPreferences(R.xml.developer_preferences);
        setUpRestBaseCheckboxes();
        setUpMediaWikiSettings();

        findPreference(context.getString(R.string.preferences_developer_crash_key))
                .setOnPreferenceClickListener(preference -> {
                    throw new TestException("User tested crash functionality.");
                });

        findPreference(R.string.preference_key_remote_log)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    L.logRemoteError(new RemoteLogException(newValue.toString()));
                    WikipediaApp.getInstance().checkCrashes(getActivity());
                    return true;
                });

        findPreference(R.string.preference_key_add_articles)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.toString().trim().equals("") || newValue.toString().trim().equals("0")) {
                        return true;
                    }

                    int numberOfArticles = Integer.valueOf(newValue.toString().trim());
                    createTestReadingList(TEXT_OF_TEST_READING_LIST, 1, numberOfArticles);

                    return true;
                });

        findPreference(R.string.preference_key_add_reading_lists)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.toString().trim().equals("") || newValue.toString().trim().equals("0")) {
                        return true;
                    }

                    int numOfLists = Integer.valueOf(newValue.toString().trim());
                    createTestReadingList(TEXT_OF_READING_LIST, numOfLists, 10);

                    return true;
                });

        findPreference(R.string.preference_key_delete_reading_lists)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.toString().trim().equals("") || newValue.toString().trim().equals("0")) {
                        return true;
                    }
                    int numOfLists = Integer.valueOf(newValue.toString().trim());
                    deleteTestReadingList(TEXT_OF_READING_LIST, numOfLists);
                    return true;
                });
        findPreference(R.string.preference_key_delete_test_reading_lists)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue.toString().trim().equals("") || newValue.toString().trim().equals("0")) {
                        return true;
                    }
                    int numOfLists = Integer.valueOf(newValue.toString().trim());
                    deleteTestReadingList(TEXT_OF_TEST_READING_LIST, numOfLists);
                    return true;
                });

        findPreference(R.string.preference_key_add_malformed_reading_list_page)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    int numberOfArticles = TextUtils.isEmpty(newValue.toString()) ? 1 :  Integer.valueOf(newValue.toString().trim());
                    List<ReadingListPage> pages = new ArrayList<>();
                    for (int i = 0; i < numberOfArticles; i++) {
                        PageTitle pageTitle = new PageTitle("Malformed page " + i, WikiSite.forLanguageCode("foo"));
                        pages.add(new ReadingListPage(pageTitle));
                    }
                    ReadingListDbHelper.instance().addPagesToList(ReadingListDbHelper.instance().getDefaultList(), pages, true);
                    return true;
                });

        findPreference(context.getString(R.string.preference_key_missing_description_test))
                .setOnPreferenceClickListener(preference -> {
                    MissingDescriptionProvider.INSTANCE.getNextArticleWithMissingDescription(WikipediaApp.getInstance().getWikiSite())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(summary -> new AlertDialog.Builder(getActivity())
                                            .setTitle(StringUtil.fromHtml(summary.getDisplayTitle()))
                                            .setMessage(StringUtil.fromHtml(summary.getExtract()))
                                            .setPositiveButton("Go", (dialog, which) -> {
                                                PageTitle title = new PageTitle(summary.getNormalizedTitle(), WikipediaApp.getInstance().getWikiSite());
                                                getActivity().startActivity(PageActivity.newIntentForNewTab(getActivity(), new HistoryEntry(title, HistoryEntry.SOURCE_INTERNAL_LINK), title));
                                            })
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show(),
                                    throwable -> new AlertDialog.Builder(getActivity())
                                            .setMessage(throwable.getMessage())
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show());
                    return true;
                });

        findPreference(context.getString(R.string.preference_key_missing_description_test2))
                .setOnPreferenceClickListener(preference -> {
                    MissingDescriptionProvider.INSTANCE.getNextArticleWithMissingDescription(WikipediaApp.getInstance().getWikiSite(),
                            WikipediaApp.getInstance().language().getAppLanguageCodes().get(1), true)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(summary -> new AlertDialog.Builder(getActivity())
                                            .setTitle(StringUtil.fromHtml(summary.getDisplayTitle()))
                                            .setMessage(StringUtil.fromHtml(summary.getDescription()))
                                            .setPositiveButton("Go", (dialog, which) -> {
                                                PageTitle title = new PageTitle(summary.getNormalizedTitle(), WikipediaApp.getInstance().getWikiSite());
                                                getActivity().startActivity(PageActivity.newIntentForNewTab(getActivity(), new HistoryEntry(title, HistoryEntry.SOURCE_INTERNAL_LINK), title));
                                            })
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show(),
                                    throwable -> new AlertDialog.Builder(getActivity())
                                            .setMessage(throwable.getMessage())
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show());
                    return true;
                });
    }

    private void setUpRestBaseCheckboxes() {
        TwoStatePreference manualPreference = (TwoStatePreference) findPreference(getManualKey());
        manualPreference.setOnPreferenceChangeListener(setRestBaseManuallyChangeListener);
        setUseRestBasePreference(manualPreference.isChecked());
    }

    private String getManualKey() {
        return context.getString(R.string.preference_key_use_restbase_manual);
    }

    private void setUseRestBasePreference(boolean manualMode) {
        RbSwitch.INSTANCE.update();
        TwoStatePreference useRestBasePref = getUseRestBasePreference();
        useRestBasePref.setEnabled(manualMode);
        useRestBasePref.setChecked(RbSwitch.INSTANCE.isRestBaseEnabled());
    }

    private TwoStatePreference getUseRestBasePreference() {
        return (TwoStatePreference) findPreference(getUseRestBaseKey());
    }

    private String getUseRestBaseKey() {
        return context.getString(R.string.preference_key_use_restbase);
    }

    private void setUpMediaWikiSettings() {
        Preference uriPreference = findPreference(context.getString(R.string.preference_key_mediawiki_base_uri));
        uriPreference.setOnPreferenceChangeListener(setMediaWikiBaseUriChangeListener);
        TwoStatePreference multiLangPreference
                = (TwoStatePreference) findPreference(context.getString(R.string.preference_key_mediawiki_base_uri_supports_lang_code));
        multiLangPreference.setOnPreferenceChangeListener(setMediaWikiMultiLangSupportChangeListener);
    }

    private void resetMediaWikiSettings() {
        WikipediaApp.getInstance().resetWikiSite();
    }

    private void createTestReadingList(String listName, int numOfLists, int numOfArticles) {
        int index = 0;

        List<ReadingList> lists = ReadingListDbHelper.instance().getAllListsWithoutContents();
        for (int i = lists.size() - 1; i >= 0; i--) {
            ReadingList lastReadingList = lists.get(i);
            if (lastReadingList.title().contains(listName)) {
                String trimmedListTitle = lastReadingList.title().substring(listName.length()).trim();
                index = (trimmedListTitle.isEmpty()) ? index : (Integer.valueOf(trimmedListTitle) > index ? Integer.valueOf(trimmedListTitle) : index);
                break;
            }
        }

        for (int i = 0; i < numOfLists; i++) {
            index += 1;
            ReadingList list = ReadingListDbHelper.instance().createList(listName + " " + index, "");
            List<ReadingListPage> pages = new ArrayList<>();
            for (int j = 0; j < numOfArticles; j++) {
                PageTitle pageTitle = new PageTitle("" + (j + 1), WikipediaApp.getInstance().getWikiSite());
                pages.add(new ReadingListPage(pageTitle));
            }
            ReadingListDbHelper.instance().addPagesToList(list, pages, true);
        }
    }

    private void deleteTestReadingList(String listName, int numOfLists) {
        List<ReadingList> lists = ReadingListDbHelper.instance().getAllLists();
        for (ReadingList list : lists) {
            if (list.title().contains(listName) && numOfLists > 0) {
                ReadingListDbHelper.instance().deleteList(list);
                numOfLists--;
            }
        }
    }

    private static class TestException extends RuntimeException {
        TestException(String message) {
            super(message);
        }
    }
}
