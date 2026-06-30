package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class YeGramCornerActivity extends UniversalFragment {

    private static final int BUTTON_CHANNEL = 1;
    private static final int BUTTON_SUPPORT = 2;
    private static final int BUTTON_CHECK_UPDATES = 3;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YeGramCornerTitle);
    }

    @Override
    public View createView(Context context) {
        super.createView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        if (parentLayout != null && parentLayout.isRightLayout()) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_close);
        }
        return fragmentView;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(null));
        items.add(SettingsActivity.SettingCell.Factory.ofPadded(BUTTON_CHANNEL, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.settings_channel, getString(R.string.YeGramChannelTitle), getString(R.string.YeGramChannelSubtitle), dp(8)));
        items.add(SettingsActivity.SettingCell.Factory.of(BUTTON_SUPPORT, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, R.drawable.settings_gift, getString(R.string.YeGramSupportTitle), getString(R.string.YeGramSupportSubtitle)));
        items.add(SettingsActivity.SettingCell.Factory.ofPadded(BUTTON_CHECK_UPDATES, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.settings_devices, getString(R.string.YeGramCheckUpdatesTitle), getString(R.string.YeGramCheckUpdatesSubtitle), dp(8)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case BUTTON_CHANNEL:
                Browser.openUrl(getParentActivity(), "https://t.me/yeGramOfficial");
                break;
            case BUTTON_SUPPORT:
                Browser.openUrl(getParentActivity(), "https://pay.cloudtips.ru/p/0c7fb861");
                break;
            case BUTTON_CHECK_UPDATES:
                YeGramUpdater.checkForUpdates(getParentActivity(), getResourceProvider());
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
