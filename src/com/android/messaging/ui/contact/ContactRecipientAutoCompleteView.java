/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.messaging.ui.contact;

import android.app.ActivityManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import com.android.messaging.R;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import com.android.ex.chips.RecipientEditTextView;
import com.android.ex.chips.RecipientEntry;
import com.android.ex.chips.recipientchip.DrawableRecipientChip;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.ui.conversation.ConversationActivity;
import com.android.messaging.util.ContactRecipientEntryUtils;
import com.android.messaging.util.ContactUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.ui.contact.NumberMatchUtils;
import com.sprd.messaging.util.Utils;
import com.android.plat.mms.plugin.Impl.NotifyStatus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
//add for bug 555230 begin
import com.android.messaging.util.UiUtils;
//add for bug 555230 end
import java.lang.Exception;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuItem;
import android.telephony.PhoneNumberUtils;   //UNISOC add for bug 1391885

/**
 * An extension for {@link RecipientEditTextView} which shows a list of Materialized contact chips.
 * It uses Bugle's ContactUtil to perform contact lookup, and is able to return the list of
 * recipients in the form of a ParticipantData list.
 */
public class ContactRecipientAutoCompleteView extends RecipientEditTextView implements RecipientEditTextView.RecipientChipsParsedListener {
    public interface ContactChipsChangeListener {
        void onContactChipsChanged(int oldCount, int newCount);

        void onInvalidContactChipsPruned(int prunedCount);

        void onContactChipsReplaced(); //Bug 916372

        void onEntryComplete();
    }

    private final int mTextHeight;
    private ContactChipsChangeListener mChipsChangeListener;

    private boolean mEditing;
    private String mLastText;

    /**
     * Watches changes in contact chips to determine possible state transitions.
     */
    private class ContactChipsWatcher implements TextWatcher {
        /**
         * Tracks the old chips count before text changes. Note that we currently don't compare
         * the entire chip sets but just the cheaper-to-do before and after counts, because
         * the chips view don't allow for replacing chips.
         */
        private int mLastChipsCount = 0;

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before,
                                  final int count) {
            String current = s.toString();
            /* SPRD: modify for Bug 521135 begin */
            String lastS = current.length() > 0 ? current.substring(current.length() - 1) : "";
            if (",".equals(lastS) || ";".equals(lastS)) {
                ConversationActivity.sContactConfirmAct = true;
            }
            /* SPRD: modify for Bug 521135 end */
            if (!current.replace(" ", "").equals(mLastText)) {
                mLastText = current.replace(" ", "");
                mEditing = true;
            }
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count,
                                      final int after) {
            // We don't take mLastChipsCount from here but from the last afterTextChanged() run.
            // The reason is because at this point, any chip spans to be removed is already removed
            // from s in the chips text view.
        }

        @Override
        public void afterTextChanged(final Editable s) {
            final int currentChipsCount = s.getSpans(0, s.length(),
                    DrawableRecipientChip.class).length;
            if (currentChipsCount != mLastChipsCount) {
                // When a sanitizing task is running, we don't want to notify any chips count
                // change, but we do want to track the last chip count.
                if (mChipsChangeListener != null && mCurrentSanitizeTask == null) {
                    mChipsChangeListener.onContactChipsChanged(mLastChipsCount, currentChipsCount);
                }
                mLastChipsCount = currentChipsCount;
                mEditing = false;
            }
        }
    }

    private static final String TEXT_HEIGHT_SAMPLE = "a";

    public ContactRecipientAutoCompleteView(final Context context, final AttributeSet attrs) {
        super(new ContextThemeWrapper(context, R.style.ColorAccentGrayOverrideStyle), attrs);

        // Get the height of the text, given the currently set font face and size.
        final Rect textBounds = new Rect(0, 0, 0, 0);
        final TextPaint paint = getPaint();
        paint.getTextBounds(TEXT_HEIGHT_SAMPLE, 0, TEXT_HEIGHT_SAMPLE.length(), textBounds);
        mTextHeight = textBounds.height();

        setTokenizer(new Rfc822Tokenizer());
        addTextChangedListener(new ContactChipsWatcher());
        setOnFocusListShrinkRecipients(false);
        setRecipientChipsParsedListener(this);

        setBackground(context.getResources().getDrawable(
                R.drawable.abc_textfield_search_default_mtrl_alpha));
        setCustomInsertionActionModeCallback(new RecipientEditTextViewInsertionActionModeCallback());  //Bug 1368228
    }

    public void setContactChipsListener(final ContactChipsChangeListener listener) {
        mChipsChangeListener = listener;
    }

    /**
     * A tuple of chips which AsyncContactChipSanitizeTask reports as progress to have the
     * chip actually replaced/removed on the UI thread.
     */
    private class ChipReplacementTuple {
        public final DrawableRecipientChip removedChip;
        public final RecipientEntry replacedChipEntry;

        public ChipReplacementTuple(final DrawableRecipientChip removedChip,
                                    final RecipientEntry replacedChipEntry) {
            this.removedChip = removedChip;
            this.replacedChipEntry = replacedChipEntry;
        }
    }

    //Bug 916372 begin
    private class AsyncResult {
        public int invalidChipsRemoved;
        public int chipsReplacedCount;

        public AsyncResult(final int invalidChipsRemoved, final int chipsReplacedCount) {
            this.invalidChipsRemoved = invalidChipsRemoved;
            this.chipsReplacedCount = chipsReplacedCount;
        }
    }
    //Bug 916372 end

    /*add for bug 787311 start*/
    private boolean IsDuplicateEntry(DrawableRecipientChip[] allentrys, RecipientEntry curentry, final int pos) {
        if (allentrys == null || curentry == null || pos <= 0) {
            return false;
        }
        if (pos >= allentrys.length) {
            return false;
        }
        for (int i = 0; i < pos; i++) {
            if (allentrys[i].getEntry() != null) {
                if (allentrys[i].getEntry().getDestination() != null) {
                    //Bug 916372, 1391885 begin
                    if (PhoneNumberUtils.compareStrictly(allentrys[i].getEntry().getDestination()
                            .replace(" ", ""),curentry.getDestination().replace(" ", ""))) {
                        //Bug 916372, 1391885 end
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public HashSet<String> getRecipNumSet() {
        final DrawableRecipientChip[] recips = getText()
                .getSpans(0, getText().length(), DrawableRecipientChip.class);
        HashSet<String> recipNumSet = new HashSet<String>();
        for (final DrawableRecipientChip recipient : recips) {
            recipNumSet.add(recipient.getEntry().getDestination());
        }
        return recipNumSet;
    }
    /*add for bug 787311 end*/

    /**
     * An AsyncTask that cleans up contact chips on every chips commit (i.e. get or create a new
     * conversation with the given chips).
     */
    /**
     * execute params type convert Void to Boolean, if contacts' db change, chips also update for Bug 916372
     * execute return type convert int to class AsyncResult
     */

    private class AsyncContactChipSanitizeTask extends
            AsyncTask<Boolean, ChipReplacementTuple, AsyncResult> {

        @Override
        protected AsyncResult doInBackground(final Boolean... params) {
            try {/*modified for Bug 818961 start*/
                final boolean allContactsCursorUpdated = params[0];
                final Editable s = getText();
                final DrawableRecipientChip[] recips = s.getSpans(0, s.length(), DrawableRecipientChip.class);
                AsyncResult asyncResult = new AsyncResult(0, 0);
                int pos = 0;/*add for bug 787311*/
                for (final DrawableRecipientChip recipient : recips) {
                    final RecipientEntry entry = recipient.getEntry();
                    if (entry != null) {
                        if (entry.isValid() && IsDuplicateEntry(recips, entry, pos)) { /*add for bug 787311 start*/
                            publishProgress(new ChipReplacementTuple(recipient, null)); /*add for bug 787311 end*/
                        } else if (entry.isValid() &&
                                (NumberMatchUtils.getNumberMatchUtils().getNumberMatchNotify().OnNotify(0, 0, 0, entry.getDestination(), null) == NumberMatchUtils.getNumberMatchUtils().SUCC)) {
                            if (RecipientEntry.isCreatedRecipient(entry.getContactId()) ||
                                    ContactRecipientEntryUtils.isSendToDestinationContact(entry) ||
                                    allContactsCursorUpdated) {

                                // This is a generated/send-to contact chip, try to look it up and
                                // display a chip for the corresponding local contact.
                                final Cursor lookupResult = ContactUtil.lookupDestination(getContext(),
                                        entry.getDestination()).performSynchronousQuery();
                                if (lookupResult != null && lookupResult.moveToNext()) {
                                    // Found a match, remove the generated entry and replace with
                                    // a better local entry.
                                    publishProgress(new ChipReplacementTuple(recipient,
                                            ContactUtil.createRecipientEntryForPhoneQuery(
                                                    lookupResult, true)));
                                    asyncResult.chipsReplacedCount++;
                                } else if (NumberMatchUtils.getNumberMatchUtils().getNumberMatchNotify().OnNotify(0, 0, 0, entry.getDestination(), null) == NumberMatchUtils.getNumberMatchUtils().SUCC/*PhoneUtils.isValidSmsMmsDestination(
                                        entry.getDestination())*/) {
                                    // No match was found, but we have a valid destination so let's at
                                    // least create an entry that shows an avatar.
                                    publishProgress(new ChipReplacementTuple(recipient,
                                            ContactRecipientEntryUtils.constructNumberWithAvatarEntry(
                                                    entry.getDestination())));
                                    asyncResult.chipsReplacedCount++;
                                } else {
                                    // Not a valid contact. Remove and show an error.
                                    publishProgress(new ChipReplacementTuple(recipient, null));
                                    asyncResult.invalidChipsRemoved++;
                                }
                            }
                        } else {
                            publishProgress(new ChipReplacementTuple(recipient, null));
                            asyncResult.invalidChipsRemoved++;
                        }
                    }
                    pos++;/*add for bug 787311*/
                }
                return asyncResult;
            } catch (Exception e) {
                e.printStackTrace();
                return new AsyncResult(0, 0);
            }/*modified for Bug 818961 end*/
        }

        @Override
        protected void onProgressUpdate(final ChipReplacementTuple... values) {
            for (final ChipReplacementTuple tuple : values) {
                if (tuple.removedChip != null) {
                    final Editable text = getText();
                    final int text_len = text.length(); //Bug 923756
                    final int chipStart = text.getSpanStart(tuple.removedChip);
                    int chipEnd = text.getSpanEnd(tuple.removedChip);
                    try {
                        //Bug 923756 begin
                        //Bug 951588 begin
                        if (!ActivityManager.isUserAMonkey() && chipEnd < 1500) {
                            chipEnd += 1;
                        }
                        //Bug 951588 end
                        if (chipStart >= 0 && chipEnd >= 0) {
                            text.delete(chipStart, chipEnd);
                        }
                        //Bug 923756 end
                        if (tuple.replacedChipEntry != null) {
                            appendRecipientEntry(tuple.replacedChipEntry);
                        }
                    } catch (IndexOutOfBoundsException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(final AsyncResult asyncResult) {
            mCurrentSanitizeTask = null;
            if (asyncResult.invalidChipsRemoved > 0) {
                mChipsChangeListener.onInvalidContactChipsPruned(asyncResult.invalidChipsRemoved);
            }
            //Bug 916372 begin
            if (asyncResult.chipsReplacedCount > 0) {
                mChipsChangeListener.onContactChipsReplaced();
            }
            //Bug 916372 end
        }
    }

    /**
     * We don't use SafeAsyncTask but instead use a single threaded executor to ensure that
     * all sanitization tasks are serially executed so as not to interfere with each other.
     */
    private static final Executor SANITIZE_EXECUTOR = Executors.newSingleThreadExecutor();

    private AsyncContactChipSanitizeTask mCurrentSanitizeTask;

    /**
     * Whenever the caller wants to start a new conversation with the list of chips we have,
     * make sure we asynchronously:
     * 1. Remove invalid chips.
     * 2. Attempt to resolve unknown contacts to known local contacts.
     * 3. Convert still unknown chips to chips with generated avatar.
     * <p>
     * Note that we don't need to perform this synchronously since we can
     * resolve any unknown contacts to local contacts when needed.
     */
    private void sanitizeContactChips(final boolean allContactsCursorUpdated) {
        if (mCurrentSanitizeTask != null && !mCurrentSanitizeTask.isCancelled()) {
            mCurrentSanitizeTask.cancel(true);/*modifed for Bug 806870*/
            mCurrentSanitizeTask = null;
        }
        mCurrentSanitizeTask = new AsyncContactChipSanitizeTask();
        mCurrentSanitizeTask.executeOnExecutor(SANITIZE_EXECUTOR, allContactsCursorUpdated);
    }

    /**
     * Returns a list of ParticipantData from the entered chips in order to create
     * new conversation.
     */
    public ArrayList<ParticipantData> getRecipientParticipantDataForConversationCreation() {

        final DrawableRecipientChip[] recips = getText()
                .getSpans(0, getText().length(), DrawableRecipientChip.class);
        final ArrayList<ParticipantData> contacts =
                new ArrayList<ParticipantData>(recips.length);
        for (final DrawableRecipientChip recipient : recips) {
            final RecipientEntry entry = recipient.getEntry();
            if (entry != null && entry.isValid() && entry.getDestination() != null &&
                    NumberMatchUtils.getNumberMatchUtils().getNumberMatchNotify().OnNotify(0, 0, 0, entry.getDestination(), null) == NumberMatchUtils.getNumberMatchUtils().SUCC/*PhoneUtils.isValidSmsMmsDestination(entry.getDestination())*/) {
                contacts.add(ParticipantData.getFromRecipientEntry(recipient.getEntry()));
            }
        }
        sanitizeContactChips(false);
        return contacts;
    }

    /**
     * c
     * Gets a set of currently selected chips' emails/phone numbers. This will facilitate the
     * consumer with determining quickly whether a contact is currently selected.
     */
    public Set<String> getSelectedDestinations() {
        Set<String> set = new HashSet<String>();
        final DrawableRecipientChip[] recips = getText()
                .getSpans(0, getText().length(), DrawableRecipientChip.class);

        for (final DrawableRecipientChip recipient : recips) {
            final RecipientEntry entry = recipient.getEntry();
            if (entry != null && entry.isValid() && entry.getDestination() != null) {
                /*Modify By SPRD for bug:583380 {@*/
                // bug 865871 begin return only origin number
                //String dest = PhoneUtils.getDefault().getCanonicalBySystemLocale(
                //        entry.getDestination()).replace(" ","");
                String dest = entry.getDestination();
                // bug 865871 end
                /*@}*/
                set.add(dest);
            }
        }
        return set;
    }

    @Override
    public boolean onEditorAction(final TextView view, final int actionId, final KeyEvent event) {
        /* SPRD: modify for Bug 521135 begin */
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            ConversationActivity.sContactConfirmAct = true;
            /* Add by SPRD for bug 571073 Start */
            if (getText().toString().replace(" ", "").length() == 0) {
                return true;
            }
            /* Add by SPRD for bug 571073 End */
            //for bug775676 begin
            //if (!mEditing) {
            boolean ret = super.onEditorAction(view, actionId, event);
            mChipsChangeListener.onEntryComplete();
            return ret;
            //}//for bug775676 end
        }
        /* SPRD: modify for Bug 521135 end */
        return super.onEditorAction(view, actionId, event);
    }

    /* Add by SPRD for Bug:504724 2015.12.14 Start */
    // FIXME: fixed bug in APP, better may is in FW.
    @Override
    public void removeRecipientEntry(@NonNull final RecipientEntry entry) {
        if (entry == null) {
            return;
        }
        final DrawableRecipientChip[] recips = getText()
                .getSpans(0, getText().length(), DrawableRecipientChip.class);
        for (final DrawableRecipientChip recipient : recips) {
            final RecipientEntry existingEntry = recipient.getEntry();
            // FIXME: Handle email address if need.
            boolean needRemove = existingEntry != null && existingEntry.isValid()
                    && existingEntry.isSamePerson(entry) // same contact ID
                    && existingEntry.getDestination().equals(entry.getDestination()); // same phone number
            if (needRemove) {
                removeChip(recipient);
            }
        }

    }

    /**
     * FIXME: a more effective resolution is to set accessiblity of com.android.ex.chips.RecipientEditTextView.removeChip to "protected".
     */
    private void removeChip(DrawableRecipientChip entry) {
        Method m = Utils.getSuperMethod("removeChip", 1,
                getClass().getSuperclass(), DrawableRecipientChip.class);
        if (m != null) {
            Utils.invoke(this, m, new Object[]{entry});
        }
    }
    /* Add by SPRD for Bug:504724 2015.12.18 End */

    //Bug 916372 begin
    public void updateContactChips() {
        sanitizeContactChips(true);
    }
    //Bug 916372 end

    public int getRecipientCount() {
        Editable s = getText();
        return s.getSpans(0, s.length(),
                DrawableRecipientChip.class).length;
    }

    //Bug #967003 begin
    @Override
    public void onFocusChanged(boolean hasFocus, int direction, Rect previous) {
        String s = getText().toString();
        if (s != null &&  !"".equals(s) && s.replace(" ", "").length() == 0) {
            setText("");
        }
        super.onFocusChanged(hasFocus, direction, previous);
    }

    //Bug #967003 end
    //Bug #895259 begin
    public ArrayList<ParticipantData> getParticipantDataAfterConversationCreated() {
        final DrawableRecipientChip[] recips = getText()
                .getSpans(0, getText().length(), DrawableRecipientChip.class);
        final ArrayList<ParticipantData> contacts =
                new ArrayList<ParticipantData>(recips.length);
        for (final DrawableRecipientChip recipient : recips) {
            contacts.add(ParticipantData.getFromRecipientEntry(recipient.getEntry()));
        }

        return contacts;
    }
    //Bug #895259 end

    //Bug 1007238 begin
    @Override
    public void onLmitExceededWarning(final int flag) {
        int msgId = 0;
        switch (flag) {
            case UPPER_TO_MAX_TEXT_LENGTH:
                msgId = R.string.upper_to_max_text_length;
                break;
            case UPPER_TO_MAX_CHIPS_PARSED:
                msgId = R.string.upper_to_max_chips_parsed;
                break;
            default:
                break;
        }
        UiUtils.showToast(msgId);
    }
    //Bug 1007238 end

  //Bug 1368228 begin
    private class RecipientEditTextViewInsertionActionModeCallback implements Callback {
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        /**
         * No chips are selectable.
         */
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (null != menu) {
                if (null != menu.findItem(android.R.id.selectAll)) {
                    menu.findItem(android.R.id.selectAll).setVisible(false);
                }
                return true;
            } else {
                return false;
            }
        }
    }
    //Bug 1368228 end
}