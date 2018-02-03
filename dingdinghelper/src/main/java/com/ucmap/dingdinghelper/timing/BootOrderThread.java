package com.ucmap.dingdinghelper.timing;

import android.util.Log;

import com.ucmap.dingdinghelper.entity.MessageEvent;
import com.ucmap.dingdinghelper.utils.ShellUtils;

import org.greenrobot.eventbus.EventBus;

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

public class BootOrderThread extends Thread {

    private List<String> o;
    private int flag;
    public BootOrderThread(List<String> o,int flag) {
        this.o = o;
        this.flag=flag;
    }

    @Override
    public void run() {

        ShellUtils.CommandResult mCommandResult = ShellUtils.execCmd(o, true);
        Log.i("Infoss", "result:" + mCommandResult.toString());

        EventBus.getDefault().post(new MessageEvent(flag));
    }
}
