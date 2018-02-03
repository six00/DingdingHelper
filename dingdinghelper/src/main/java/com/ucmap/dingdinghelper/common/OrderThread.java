package com.ucmap.dingdinghelper.common;

import android.util.Log;

import com.ucmap.dingdinghelper.utils.ShellUtils;

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

public class OrderThread extends Thread {


    private List<String> mList = null;

    public OrderThread(List<String> o) {
        this.mList = o;
    }

    @Override
    public void run() {
        if (mList != null && !mList.isEmpty()) {
            ShellUtils.CommandResult mCommandResult = ShellUtils.execCmd(mList, true);
            Log.i("Infoss", "cmd:" + mCommandResult.toString());
        }
    }
}
