//by sprd
package com.sprd.messaging.simmessage.ui;

import android.graphics.drawable.ColorDrawable;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.android.messaging.R;

public abstract class BaseActionBarActivity extends AppCompatActivity {

    protected CustomActionMode mActionMode;
    private Menu mActionBarMenu;

    protected void updateActionBar(ActionBar actionBar) {
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_HOME_AS_UP);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && mActionMode != null) {
            dismissActionMode();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        mActionBarMenu = menu;
        if (mActionMode != null && mActionMode.getCallback().onCreateActionMode(mActionMode, menu)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        mActionBarMenu = menu;
        if (mActionMode != null && mActionMode.getCallback().onPrepareActionMode(mActionMode, menu)) {
            return true;
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (mActionMode != null
                && mActionMode.getCallback().onActionItemClicked(mActionMode, menuItem)) {
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback) {
        mActionMode = new CustomActionMode(callback);
        supportInvalidateOptionsMenu();
        invalidateActionBar();
        return mActionMode;
    }

    void dismissActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
            invalidateActionBar();
        }
    }

    public ActionMode getActionMode() {
        return mActionMode;
    }

    protected ActionMode.Callback getActionModeCallback() {
        if (mActionMode == null) {
            return null;
        }
        return mActionMode.getCallback();
    }

    /**
     * Receives and handles action bar invalidation request from sub-components
     * of this activity.
     * <p>
     * Normally actions have sole control over the action bar, but in order to
     * support seamless transitions for components such as the full screen media
     * picker, we have to let it take over the action bar and then restore its
     * state afterwards
     * </p>
     * <p>
     * If a fragment does anything that may change the action bar, it should
     * call this method and then it is this method's responsibility to figure
     * out which component "controls" the action bar and delegate the updating
     * of the action bar to that component
     * </p>
     */
    public final void invalidateActionBar() {
        if (mActionMode != null) {
            mActionMode.updateActionBar(getSupportActionBar());
        } else {
            updateActionBar(getSupportActionBar());
        }
    }

    public void UpdateSelectMessageCount(int cnt) {
        if (mActionMode != null) {
            String string = getString(R.string.have_select_message);
            String haveSelected = String.format(string, cnt);
            getSupportActionBar().setTitle(haveSelected);
        }
    }

    /**
     * Custom ActionMode implementation which allows us to just replace the
     * contents of the main action bar rather than overlay over it
     */
    private class CustomActionMode extends ActionMode {
        private CharSequence mTitle;
        private CharSequence mSubtitle;
        private View mCustomView;
        private final Callback mCallback;

        public CustomActionMode(final Callback callback) {
            mCallback = callback;
        }

        @Override
        public void setTitle(final CharSequence title) {
            mTitle = title;
        }

        @Override
        public void setTitle(final int resId) {
            mTitle = getResources().getString(resId);
        }

        @Override
        public void setSubtitle(final CharSequence subtitle) {
            mSubtitle = subtitle;
        }

        @Override
        public void setSubtitle(final int resId) {
            mSubtitle = getResources().getString(resId);
        }

        @Override
        public void setCustomView(final View view) {
            mCustomView = view;
        }

        @Override
        public void invalidate() {
            invalidateActionBar();
        }

        @Override
        public void finish() {
            mActionMode = null;
            mCallback.onDestroyActionMode(this);
            supportInvalidateOptionsMenu();
            invalidateActionBar();
        }

        @Override
        public Menu getMenu() {
            return mActionBarMenu;
        }

        @Override
        public CharSequence getTitle() {
            return mTitle;
        }

        @Override
        public CharSequence getSubtitle() {
            return mSubtitle;
        }

        @Override
        public View getCustomView() {
            return mCustomView;
        }

        @Override
        public MenuInflater getMenuInflater() {
            return BaseActionBarActivity.this.getMenuInflater();
        }

        public Callback getCallback() {
            return mCallback;
        }

        public void updateActionBar(final ActionBar actionBar) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayShowCustomEnabled(true);
            mActionMode.getCallback().onPrepareActionMode(mActionMode, mActionBarMenu);
            actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(
                    R.color.delete_action_bar_background_color)));
            actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_light);//Modify by SPRD for bug:572739
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            actionBar.show();
        }
    }
}
