/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package info.zamojski.soft.towercollector.broadcast;

import org.greenrobot.eventbus.EventBus;

import info.zamojski.soft.towercollector.CollectorService;
import info.zamojski.soft.towercollector.MyApplication;
import info.zamojski.soft.towercollector.R;
import info.zamojski.soft.towercollector.UploaderService;
import info.zamojski.soft.towercollector.analytics.IntentSource;
import info.zamojski.soft.towercollector.events.CollectorStartedEvent;
import info.zamojski.soft.towercollector.utils.BackgroundTaskHelper;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import info.zamojski.soft.towercollector.utils.PermissionUtils;
import timber.log.Timber;

public class ExternalBroadcastReceiver extends BroadcastReceiver {

    private static final String quickBootPowerOnAction = "android.intent.action.QUICKBOOT_POWERON";

    private static final String collectorStartAction = "info.zamojski.soft.towercollector.COLLECTOR_START";
    private static final String collectorStopAction = "info.zamojski.soft.towercollector.COLLECTOR_STOP";

    private static final String uploaderStartAction = "info.zamojski.soft.towercollector.UPLOADER_START";
    private static final String uploaderStopAction = "info.zamojski.soft.towercollector.UPLOADER_STOP";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (collectorStartAction.equals(action)) {
            startCollectorService(context, IntentSource.Application);
        } else if (collectorStopAction.equals(action)) {
            stopCollectorService(context);
        } else if (uploaderStartAction.equals(action)) {
            startUploaderService(context);
        } else if (uploaderStopAction.equals(action)) {
            stopUploaderService(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || quickBootPowerOnAction.equals(action)) {
            boolean startAtBootEnabled = MyApplication.getPreferencesProvider().getStartCollectorAtBoot();
            if (startAtBootEnabled) {
                startCollectorService(context, IntentSource.System);
            }
        }
    }

    public void startCollectorService(Context context, IntentSource source) {
        if (!canStartBackgroundService(context)) {
            return;
        }
        if (!hasAllCollectorRequiredPermissions(context)) {
            showCollectorPermissionsDenied(context);
            return;
        }
        Timber.d("startCollectorService(): Starting service from broadcast");
        Intent intent = getCollectorIntent(context);

        ContextCompat.startForegroundService(context, intent);
        EventBus.getDefault().post(new CollectorStartedEvent(intent));
        MyApplication.getAnalytics().sendCollectorStarted(source);
    }

    public void stopCollectorService(Context context) {
        Timber.d("stopCollectorService(): Stopping service from broadcast");
        context.stopService(getCollectorIntent(context));
    }

    private Intent getCollectorIntent(Context context) {
        return new Intent(context, CollectorService.class);
    }

    public void startUploaderService(Context context) {
        if (!canStartBackgroundService(context))
            return;
        Timber.d("startCollectorService(): Starting service from broadcast");
        ContextCompat.startForegroundService(context, getUploaderIntent(context));
        boolean isOcidUploadEnabled = MyApplication.getPreferencesProvider().isOpenCellIdUploadEnabled();
        boolean isMlsUploadEnabled = MyApplication.getPreferencesProvider().isMlsUploadEnabled();
        if (isOcidUploadEnabled)
            MyApplication.getAnalytics().sendUploadStarted(IntentSource.Application, true);
        if (isMlsUploadEnabled)
            MyApplication.getAnalytics().sendUploadStarted(IntentSource.Application, false);
    }

    public void stopUploaderService(Context context) {
        Timber.d("stopUploaderService(): Stopping service from broadcast");
        // don't use stopService because the worker needs to be stopped first
        Intent stopIntent = new Intent(UploaderService.BROADCAST_INTENT_STOP_SERVICE);
        context.sendBroadcast(stopIntent);
    }

    private Intent getUploaderIntent(Context context) {
        return new Intent(context, UploaderService.class);
    }

    private boolean canStartBackgroundService(Context context) {
        String runningTaskClassName = MyApplication.getBackgroundTaskName();
        if (runningTaskClassName != null) {
            Timber.d("canStartBackgroundService(): Another task is running in background: %s", runningTaskClassName);
            BackgroundTaskHelper backgroundTaskHelper = new BackgroundTaskHelper(context);
            backgroundTaskHelper.showTaskRunningMessage(runningTaskClassName);
            return false;
        }
        return true;
    }

    private boolean hasAllCollectorRequiredPermissions(Context context) {
        return PermissionUtils.hasPermissions(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE);
    }

    private void showCollectorPermissionsDenied(Context context) {
        Timber.d("showCollectorPermissionsDenied(): Cannot start collector due to denied permissions");
        Toast.makeText(context, R.string.permission_collector_denied_intent_message, Toast.LENGTH_LONG).show();
    }
}
