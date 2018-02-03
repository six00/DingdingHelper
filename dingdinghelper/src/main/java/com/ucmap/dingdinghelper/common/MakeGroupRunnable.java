package com.ucmap.dingdinghelper.common;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.ucmap.dingdinghelper.utils.ShellUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>@项目名：</b> dingdinghelper<br>
 * <b>@包名：</b>com.ucmap.dingdinghelper<br>
 * <b>@创建者：</b> cxz --  just<br>
 * <b>@创建时间：</b> &{DATE}<br>
 * <b>@公司：</b> 宝诺科技<br>
 * <b>@邮箱：</b> cenxiaozhong.qqcom@qq.com<br>
 * <b>@描述</b><br>
 */

public class MakeGroupRunnable implements Runnable {
    private Context mContext;

    private Callback c;

    public MakeGroupRunnable(Context context, Callback c) {
        this.mContext = context.getApplicationContext();
        this.c = c;
    }

    @Override
    public void run() {
        makeSystemApp();
    }

    private void makeSystemApp() {

        List<ApplicationInfo> mInfos = mContext.getPackageManager().getInstalledApplications(0);
        String appPath = findAppPathByApplicationInfo(mContext.getPackageName(), mInfos);
        if (TextUtils.isEmpty(appPath)) {
            if (c != null)
                c.call(false);
            return;
        }
        boolean tag = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tag = doSystemAppBeyongLollopop(appPath);
        } else {
            tag = doSystemAppCommon(appPath);
        }

        if (c != null)
            c.call(tag);
    }

    private boolean doSystemAppCommon(String appPath) {
        Log.i("Infoss", "appPath:" + appPath);
        List<String> mList = Collections.emptyList();
        //
        mList.add("busybox mount -o remount,rw /system");
        mList.add("busybox  cp -r " + appPath + " /system/app/" + (mContext.getPackageName() + ".apk"));
        mList.add("busybox chmod 777 -v /system/app/" + (mContext.getPackageName() + ".apk"));
        mList.add("busybox mount -o remount,ro /system");//只读
//        mList.add("busybox rm -f " + appPath);
        ShellUtils.CommandResult t = ShellUtils.execCmd(mList, true);
        Log.i("Infoss", "do result:" + t.toString());
        if (t.result == -1) {
            return false;
        }
        return true;
    }

    private boolean doSystemAppBeyongLollopop(String path) {
        File mFile = new File(path);
        String parent = mFile.getParent();
        String parentName = mFile.getParentFile().getName();
        Log.i("Infoss", "parent:" + parent + "   name:" + mFile.getParentFile().getName());

        List<String> mList = new ArrayList<>();
        //
        mList.add("busybox mount -o remount,rw /system");
        mList.add("busybox  cp -r " + parent + " /system/app/" + mContext.getPackageName());
        mList.add("busybox chmod 777 -v /system/app/" + (mContext.getPackageName()));
        mList.add("busybox chmod 777  /system/app/" + (mContext.getPackageName()) + "/base.apk");
        mList.add("busybox mount -o remount,ro /system");//只读
//        mList.add("busybox rm -rf /data/app/" + parentName);
        ShellUtils.CommandResult t = ShellUtils.execCmd(mList, true);
        Log.i("Infoss", "do result:" + t.toString());
        if (t.result == -1) {
            return false;
        }
        return true;
    }

    private String findAppPathByApplicationInfo(String packageName, List<ApplicationInfo> infos) {
        for (ApplicationInfo mInfo : infos) {
            if (mInfo.sourceDir.contains(packageName))
                return mInfo.sourceDir;
        }
        return null;
    }

    public interface Callback {
        void call(boolean isSuccess);
    }
}
