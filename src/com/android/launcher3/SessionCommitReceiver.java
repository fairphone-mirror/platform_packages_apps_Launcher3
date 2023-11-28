/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.os.SystemProperties;
import android.provider.Settings;
import android.database.ContentObserver;
import androidx.annotation.WorkerThread;

import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.util.Executors;

/**
 * BroadcastReceiver to handle session commit intent.
 */
public class SessionCommitReceiver extends BroadcastReceiver {

    private static final String LOG = "SessionCommitReceiver";

    // Preference key for automatically adding icon to homescreen.
    public static final String ADD_ICON_PREFERENCE_KEY = "pref_add_icon_to_home";
    private static final String OPERATOR_APP_LIST_KEY = "def_operator_applist";
    //Operator AppList Configuration {PackageName,Screen,X,Y}
    //Naming convention operatorAppPackageList + mcc + mcn
    private static final String[][] operatorAppPackageList20404 = new String[][]{{"com.vodafone.android","0","1","4"},{"com.google.android.apps.googleassistant","0","3","4"}};
    private static final String[][] operatorAppPackageList26202  = new String[][]{{"com.appseleration.android.selfcare","0","1","4"},{"com.vodafone.android.app.rbt","0","2","4"},{"au.com.vodafone.dreamlabapp","0","3","4"},{"com.google.android.apps.googleassistant","0","3","4"}};
    private static final String[][] operatorAppPackageList23415 = new String[][]{{"com.myvodafoneapp","0","1","4"},{"au.com.vodafone.dreamlabapp","0","2","4"},{"com.google.android.apps.googleassistant","0","3","4"}};
    //Google Folder AppList {PackageName,Container,ScreenId,Rank}
    private static final String[][] googleAppPackageList = new String[][]{{"com.google.android.apps.subscriptions.red","6","0","9"}
    ,{"com.google.android.apps.walletnfcrel","6","0","10"},{"com.google.android.apps.chromecast.app","6","0","11"}};

    @Override
    public void onReceive(Context context, Intent intent) {
        Executors.MODEL_EXECUTOR.execute(() -> processIntent(context, intent));
    }

    @WorkerThread
    private static void processIntent(Context context, Intent intent) {
        if (!isEnabled(context)) {
            // User has decided to not add icons on homescreen.
            return;
        }

        SessionInfo info = intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
        UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
        if (!PackageInstaller.ACTION_SESSION_COMMITTED.equals(intent.getAction())
                || info == null || user == null) {
            // Invalid intent.
            return;
        }

        InstallSessionHelper packageInstallerCompat = InstallSessionHelper.INSTANCE.get(context);

        boolean isOperatorApp = false;
        boolean isGoogleApp = false;
        String operatorAppList = Settings.Secure.getString(context.getContentResolver(), OPERATOR_APP_LIST_KEY);
        String[] operatorAppArray = null;
        if (operatorAppList != null) {
            operatorAppArray = operatorAppList.split(",");
        }
        String mccmnc = SystemProperties.get("persist.radio.sim.mcc.mnc");
        //mcc:204 mcn:04
        if("20404".equals(mccmnc)){
            for (int i = 0; i < operatorAppPackageList20404.length; i++) {
                if(info.getAppPackageName().equals(operatorAppPackageList20404[i][0])) {
                    isOperatorApp = true;
                    if (operatorAppArray != null) {
                        for(int j =0;j < operatorAppArray.length; j++) {
                            if (info.getAppPackageName().equals(operatorAppArray[j])) {
                                isOperatorApp = false;
                            }
                        }
                    }
                }
            }
        }

        //mcc:206 mcn:02
        if("26202".equals(mccmnc)){
            for (int i = 0; i < operatorAppPackageList26202.length; i++) {
                if(info.getAppPackageName().equals(operatorAppPackageList26202[i][0])) {
                    isOperatorApp = true;
                    if (operatorAppArray != null) {
                        for(int j =0;j < operatorAppArray.length; j++) {
                            if (info.getAppPackageName().equals(operatorAppArray[j])) {
                                isOperatorApp = false;
                            }
                        }
                    }

                }
            }
        }

        //mcc:234 mcn:15
        if("23415".equals(mccmnc)){
            for (int i = 0; i < operatorAppPackageList23415.length; i++) {
                if(info.getAppPackageName().equals(operatorAppPackageList23415[i][0])) {
                    isOperatorApp = true;
                    if (operatorAppArray != null) {
                        for(int j =0;j < operatorAppArray.length; j++) {
                            if (info.getAppPackageName().equals(operatorAppArray[j])) {
                                isOperatorApp = false;
                            }
                        }
                    }
                }
            }
        }

        //check google apps
        if ("20404".equals(mccmnc) || "26202".equals(mccmnc) || "23415".equals(mccmnc)) {
            for (int i = 0; i < googleAppPackageList.length; i++) {
                if(info.getAppPackageName().equals(googleAppPackageList[i][0])) {
                    isGoogleApp = true;
                    if (operatorAppArray != null) {
                        for(int j =0;j < operatorAppArray.length; j++) {
                            if (info.getAppPackageName().equals(operatorAppArray[j])) {
                                isGoogleApp = false;
                            }
                        }
                    }
                }
            }
        }

        FileLog.d(LOG,"isOperatorApp:"+isOperatorApp+" isGoogleApp:"+isGoogleApp);

        if ((isOperatorApp || isGoogleApp) && context != null) {
            ItemInstallQueue.INSTANCE.get(context).queueItem(info.getAppPackageName(), user);
        } else {
            if (TextUtils.isEmpty(info.getAppPackageName())
                    || info.getInstallReason() != PackageManager.INSTALL_REASON_USER
                    || packageInstallerCompat.promiseIconAddedForId(info.getSessionId())) {
                packageInstallerCompat.removePromiseIconId(info.getSessionId());
                return;
            }

        FileLog.d(LOG,
                "Adding package name to install queue. Package name: " + info.getAppPackageName()
                        + ", has app icon: " + (info.getAppIcon() != null)
                        + ", has app label: " + !TextUtils.isEmpty(info.getAppLabel()));

        ItemInstallQueue.INSTANCE.get(context)
                .queueItem(info.getAppPackageName(), user);
        }
    }

    public static boolean isEnabled(Context context) {
        return LauncherPrefs.getPrefs(context).getBoolean(ADD_ICON_PREFERENCE_KEY, true);
    }
}
