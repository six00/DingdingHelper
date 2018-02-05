package com.ucmap.dingdinghelper.services;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.ucmap.dingdinghelper.R;
import com.ucmap.dingdinghelper.app.App;
import com.ucmap.dingdinghelper.common.OrderThread;
import com.ucmap.dingdinghelper.entity.AccountEntity;
import com.ucmap.dingdinghelper.entity.MessageEvent;
import com.ucmap.dingdinghelper.sphelper.SPUtils;
import com.ucmap.dingdinghelper.ui.MainActivity;
import com.ucmap.dingdinghelper.utils.Constants;
import com.ucmap.dingdinghelper.utils.DateUtils;
import com.ucmap.dingdinghelper.utils.DingHelperUtils;
import com.ucmap.dingdinghelper.utils.JsonUtils;
import com.ucmap.dingdinghelper.utils.ShellUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.ucmap.dingdinghelper.utils.Constants.AFTERNOON_CHECK_IN_TIME;
import static com.ucmap.dingdinghelper.utils.Constants.MORNING_CHECK_IN_TIME;
import static com.ucmap.dingdinghelper.utils.DateUtils.getHourAndMin;

/**
 * author Just  岑晓中
 * <p>
 * 1.AccessibilityService 的oom_adj 在红米手机1s上为1  ， 可见进程的adj值为0， 基本情况下
 * 是不会杀死该进程， 在红米手机上启动通知， 并没有提升service的优先级。
 * <p>
 * 2.手机需要root，并且该应用需要获得root 权限。
 * <p>
 * 3.可用于保存多个钉钉账户， 设定时间， 程序10s就监测一次
 * 达到条件会执行自动打卡程序， 打卡成功后退出切换账号，直到打卡完成所有账号(钉钉对ID设备记录，所以这条多账号打卡暂时没实现)。
 * 4.因为国内很多手机都对AlarmManager 进行了限制， 像红米1s ， 大约6分钟轮训回调一次，导致AlarmManager 回调时间不准时、在比如华为note8直接不回调
 * 导致打卡失败， 为了解决该问题，会对比较旧的系统使用AlarmManager回调， 对Android5.0或者以上系统使用TimgService 计时唤醒， 所以TimingService服务
 * 不能被杀死.
 */
public class DingDingHelperAccessibilityService extends AccessibilityService {

    /*登录出现异常按钮*/
    private static final String SURE_EXCEPTION_ID = "android:id/button1";
    /*退出登录按钮的ID*/
    private static final String CONFIRM_QUIT_ID = "android:id/button1";
    /*电话号码输入框*/
    private static final String PHONE_ID = "com.alibaba.android.rimet:id/et_phone_input";
    /*密码输入框*/
    private static final String PASSWORD_ID = "com.alibaba.android.rimet:id/et_pwd_login";
    /*登陆点击按钮*/
    private static final String BUTTON_ID = "com.alibaba.android.rimet:id/btn_next";
    /*Bottom 栏*/
    private static final String HOME_BOTTOM_ID = "com.alibaba.android.rimet:id/bottom_tab";
    /*工作页面*/
    private static final String HOME_BOTTOM_WORK_ID = "com.alibaba.android.rimet:id/home_bottom_tab_button_work";
    /*当前公司的指标*/
    private static final String TAB_COMPANY_INDEX_ID = "com.alibaba.android.rimet:id/menu_current_company";
    /*工作页面的RecyclerView id*/
    private static final String RECYCLERVIEW_WORK_ID = "com.alibaba.android.rimet:id/oa_fragment_gridview";
    /*webView*/
    private static final String CHECK_IN_PAGER_TAGET = "com.alibaba.lightapp.runtime.activity.CommonWebViewActivity";
    /*设置页面*/
    private static final String SETTINGWINDOW = "com.alibaba.android.user.settings.activity.NewSettingActivity";
    /*用于监控线程生命周期控制*/
    private volatile boolean runing_monitor = true;
    /*登陆窗口*/
    public static final String LOGINWINDOW = "com.alibaba.android.user.login.SignUpWithPwdActivity";
    /*主页面*/
    private static final String HOMEWINDOW = "com.alibaba.android.rimet.biz.home.activity.HomeActivity";
    /*当前窗口*/
    private static String CURRENT_WINDOW = "";
    /*表示当前钉钉用户是否打卡*/
    private static boolean isCheckIn = false;
    /*监控线程*/
//    private MonitorThread mMonitorThread;
    /**/
    private static int STATE = 0;
    /*当前状态是已经打卡状态*/
    private static final int STATE_CHECKED_IN = 1;
    /*当前状态是未打卡状态*/
    private static final int STATE_UNCHECKED_IN = 0;
    /*当前状态进入休息状态， 无需进入 打卡*/
    private static final int STATE_RELEASE = -1;
    /**
     * 通知的ID
     */
    private static final int NOTIFICATION_ID = 0x000088;
    /*钉钉的包名*/
    private static final String DING_DING_PAGKET_NAME = "com.alibaba.android.rimet";

    private static final String WEBVIEW_PARENT = "com.alibaba.android.rimet:id/common_webview";

    private static final String AFTER_WORK = "下班打卡";
    private static final String GO_TO_WORK = "上班打卡";

    private static final String ALERT_DIALOG_WINDOW = "android.app.AlertDialog";
////测试
    /**
     * 我的ID
     */
    private static final String HOME_MINE_ID = "com.alibaba.android.rimet:id/home_bottom_tab_button_mine";
    /*设置ID*/
    private static final String MINE_SETTING_ID = "com.alibaba.android.rimet:id/rl_setting";
    /*退出登录按钮ID*/
    private static final String SETTING_SIGN_OUT_ID = "com.alibaba.android.rimet:id/setting_sign_out";
    /*表现当前Fragment 二级页面*/
    private static String CURRENT_PAGER = "message";
    private static final String MESSAGE_PAGER = "message";
    private static final String DING_PAGER = "ding";
    private static final String TCN_PAGER = "tcn";
    private static final String CONTACT_PAGER = "contact";
    private static final String MINE_PAGER = "mine";

    private static final String HEADER_MINE_ID = "com.alibaba.android.rimet:id/header_mine";
    private static final String HEADER_CONTACT_ID = "com.alibaba.android.rimet:id/header_contact";
    private static final String HEADER_DING = "com.alibaba.android.rimet:id/header_ding";
    private static final String HEADER_MESSAGE = "com.alibaba.android.rimet:id/header_message";

    /*打卡页面的返回按钮*/
    private static final String CHECK_IN_PAGER_BACK = "com.alibaba.android.rimet:id/back_layout";
    /**
     * 中午十二点为上班或者下班打卡中间界限
     */
    private static final int TIME_LIMIT = 12;
    private List<AccountEntity> mAccountEntities;

    /*标示service是否已经开启*/
    public static volatile boolean IS_ENABLE_DINGDINGHELPERACCESSIBILITYSERVICE = false;

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        IS_ENABLE_DINGDINGHELPERACCESSIBILITYSERVICE = true;
    }

    /**
     * TimingBroadcastReceiver 调过来， 说明时间到了 执行打卡
     *
     * @param messageEvent
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent messageEvent) {
        Log.i("Infoss", "收到信息的推送  messageEvent ...  ");
        if (messageEvent != null && messageEvent.getFlag_bit() == 1) {
            isChecking = false;
        }
        doCheckIn();
    }

    /**
     * 当启动服务的时候就会被调用
     */
    @Override
    protected void onServiceConnected() {
        Log.i("Infoss", "onServiceConnected  开启 ...");
        Toast.makeText(this.getApplicationContext(), "服务已经启动", Toast.LENGTH_SHORT).show();
        super.onServiceConnected();
        init();
        increasePriority();
    }

    /*提升service 的优先级*/
    private void increasePriority() {
        Log.i("Infoss", "increasePriority:" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Notification.Builder mBuilder = new Notification.Builder(this);
            mBuilder.setSmallIcon(R.mipmap.ic_launcher);
            startForeground(NOTIFICATION_ID, mBuilder.build());
            InnerService.startInnerService(this);
        } else {
            startForeground(NOTIFICATION_ID, new Notification());
        }
    }

    /**
     * 打卡时间
     */
    private int hour = 8;
    private int min = 30;

    /**
     * 当前需要签到的账户
     */
    private AccountEntity targetCheckInAcount = new AccountEntity();

    /**
     * 下标
     */
    private int index = 0;

    private void init() {

        String jsonAccountList = (String) SPUtils.getString(Constants.ACCOUNT_LIST, "-1");
        mAccountEntities = JsonUtils.listJson(jsonAccountList, AccountEntity.class);
        if (mAccountEntities == null || mAccountEntities.isEmpty()) {
            Toast.makeText(App.mContext, "至少需要保存一个钉钉账号", Toast.LENGTH_SHORT).show();
            return;
        }
        doScript();
    }

    private void doScript() {
        if (mAccountEntities == null)
            return;
        if (index >= mAccountEntities.size())
            return;
        targetCheckInAcount = mAccountEntities.get(index++);

        STATE = STATE_UNCHECKED_IN;
        Log.i("Infoss", "current account:" + mAccountEntities.toString());

        String current = getHourAndMin(System.currentTimeMillis());
        int hour = Integer.parseInt(current.split(":")[0]);
        String time = "";
        if (hour < TIME_LIMIT) {
            time = (String) SPUtils.getString(MORNING_CHECK_IN_TIME, "8:45");
        } else {
            time = (String) SPUtils.getString(AFTERNOON_CHECK_IN_TIME, "20:45");
        }

        Log.i("Infoss", "target Time:" + time);
        if (time.length() >= 4) {
            String[] hourAndMin = time.split(":");
            this.hour = Integer.parseInt(hourAndMin[0]);
            this.min = Integer.parseInt(hourAndMin[1]);
        }

    }

    private long tag_callback_time = 0;

    /**
     * 监听窗口变化的回调
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        tag_callback_time = System.currentTimeMillis();
        int eventType = event.getEventType();
        Log.i("Infoss", "onAccessibilityEvent   ..    ");

        Log.i("Infoss", " eventType: " + eventType + "   getEventTime:  " + event.getEventTime() + "     getAction：" +
                event.getAction() + "getContentChangeTypes:" + event.getContentChangeTypes()
                + " getText :" + event.getText().toString()
                + "getPackageName :" + event.getPackageName() + "getRecordCount : " + event.getRecordCount()
                + "  getClassName:" + event.getClassName() + "  :" + event.getClass() + "   getParcelableData:" + event.getParcelableData()
        );

        switch (eventType) {
            /*窗口变化*/
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                windowContentChanged();
                break;
            //当通知栏发生改变时
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                notificationChanged(event);
                break;
            //当Activity的状态发生改变时
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                windowChanged(event);
                break;
        }
    }

    private void windowChanged(AccessibilityEvent event) {
        String className = event.getClassName() + "";
                /*当前窗口赋值*/
        DingDingHelperAccessibilityService.CURRENT_WINDOW = className;
        if (findById(HOME_BOTTOM_WORK_ID) != null) {
            DingDingHelperAccessibilityService.CURRENT_WINDOW = HOMEWINDOW;
        }
        Log.i("Infoss", "current className:" + className + "   isTrue:" + (className.equals(LOGINWINDOW)));
                /*如果当前的窗口登陆窗口*/
        if (className.equals(LOGINWINDOW) && STATE == STATE_UNCHECKED_IN) {
            toSignIn();
        } else if (HOMEWINDOW.equals(className)) {
            Log.i("Infoss", "c state:" + STATE);
            if (STATE == STATE_UNCHECKED_IN)
                performWaitCheckIn();
            else if (STATE == STATE_UNCHECKED_IN)
                switchMine();

        } else if (CHECK_IN_PAGER_TAGET.equals(className)) {
            Log.i("Infoss", "openCheckInDialog");
            if (STATE != STATE_CHECKED_IN) {
                openCheckInDialog();
            } else {
                backHomePager();
            }
        } else if (SETTINGWINDOW.equals(className) && STATE != STATE_UNCHECKED_IN) {
            inputClick(SETTING_SIGN_OUT_ID);

            List<AccessibilityNodeInfo> mNodeQuit = new ArrayList<>();
            recurseFindByTextToList("您确定要退出登录吗", this.getRootInActiveWindow(), mNodeQuit);
//            if (mNodeQuit != null && !mNodeQuit.isEmpty())
            doQuit();

        } else if (ALERT_DIALOG_WINDOW.equals(className)) {
            Toast.makeText(this, "alert", Toast.LENGTH_SHORT).show();
            List<AccessibilityNodeInfo> mAccessibilityNodeInfos = new ArrayList<>();
            /*模糊匹配,因为文字会改变*/
            recurseFindByTextToList("请检查网络", this.getRootInActiveWindow(), mAccessibilityNodeInfos);
            if (mAccessibilityNodeInfos != null && !mAccessibilityNodeInfos.isEmpty())
                inputClick(SURE_EXCEPTION_ID);

            List<AccessibilityNodeInfo> mNodeUpdate = new ArrayList<>();
            recurseFindByTextToList("版本过旧",this.getRootInActiveWindow(),mNodeUpdate);
            if ((mNodeUpdate !=null && !mNodeUpdate.isEmpty()))
//                mNodeUpdate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                inputClick(SURE_EXCEPTION_ID);


        /*    List<AccessibilityNodeInfo> mNodeQuit = new ArrayList<>();
            recurseFindByTextToList("您确定要退出登录吗", this.getRootInActiveWindow(), mNodeQuit);
            Toast.makeText(this, "quit confirm", Toast.LENGTH_SHORT).show();
//            if (mNodeQuit != null && !mNodeQuit.isEmpty())
                doQuit();*/
        }
    }

    private static final String DD_PROGRESS_DIALOG = "com.alibaba.android.dingtalkbase.widgets.dialog.DDProgressDialog";
    private Handler mHandler = new Handler(Looper.getMainLooper());

    /*处理首页不灵敏回调,默认超过两秒钟，认为是不灵敏回调*/
    private void handleHomeInsensitiveCallback(final long time) {

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                Log.i("Infoss", "---------------->>  不灵敏触发回调:" + (DingDingHelperAccessibilityService.CURRENT_WINDOW));
                if (DingDingHelperAccessibilityService.this == null)
                    return;
                // DingDingHelperAccessibilityService.CURRENT_WINDOW.equals(DingDingHelperAccessibilityService.DD_PROGRESS_DIALOG)
                Log.i("Infoss", "---------------->>  time:" + DingDingHelperAccessibilityService.this.tag_callback_time + "   time:" + time);
                if (DingDingHelperAccessibilityService.this.tag_callback_time == time || DingDingHelperAccessibilityService.CURRENT_WINDOW.equals(DingDingHelperAccessibilityService.CHECK_IN_PAGER_TAGET))
                    ShellUtils.execCmd("input swipe 300 800 300 500", true);
                Log.i("Infoss", "---------------->>  switchMine");
            }
        }, 2000);

    }

    private void notificationChanged(AccessibilityEvent event) {
        try {

            List<CharSequence> mCharSequences = event.getText();
            if (mCharSequences == null || mCharSequences.isEmpty())
                return;
            StringBuffer sb = new StringBuffer();
            for (CharSequence c : mCharSequences) {
                sb.append(c.toString());
            }
            if (!sb.toString().contains("考勤打卡"))
                return;
            Parcelable mParcelable = event.getParcelableData();
            if (mParcelable != null && mParcelable instanceof Notification) {
                Notification mNotification = (Notification) mParcelable;
                PendingIntent mPendingIntent = mNotification.contentIntent;
                if (mPendingIntent == null)
                    return;
                /*打开该应用*/
                mPendingIntent.send();
            }
        } catch (Exception e) {

        }
    }

    private void windowContentChanged() {
        Log.i("Infoss", "current window:" + CURRENT_WINDOW + "   is:" + isCheckInPager());
        switch (CURRENT_WINDOW) {

            case HOMEWINDOW:
                setCurrentPagerId();


            /*如果当前状态是没有打卡状态*/
                if (STATE == STATE_UNCHECKED_IN) {
                /*检查下是否在考勤打卡页面*/
                    boolean isCheckInPager = isCheckInPager();
                    if (!isCheckInPager) {
                        Log.i("Infoss", "切换页面");
                        performWaitCheckIn();
                    } else {
                /*如果当前页面是公司页面*/
                        Log.i("Infoss", "当前页面是公司页面");
                        openCheckInPager();
                    }
                } else if (STATE == STATE_CHECKED_IN) {//如果当前状态为已经打卡了，那么执行退出逻辑

                    Log.i("Infoss", "current:" + CURRENT_PAGER);
                    if (MINE_PAGER.equals(CURRENT_PAGER)) {//如果当前页面已经是切换到我的页面 ， 执行设置点击
                        inputClick(MINE_SETTING_ID);
                    } else {
                    /*切换到我的页面*/
                        switchMine();
                    }

                }
                break;
            case CHECK_IN_PAGER_TAGET:
                try {
                    Log.i("Infoss", "openCheckInDialog:::" + STATE);
                    if (STATE != STATE_CHECKED_IN) {
                        openCheckInDialog();
                    } else {
                        finishSignIn();
                    }
                } catch (Exception e) {
                    Log.i("Infoss", "异常:" + e.getMessage());
                    e.printStackTrace();
                }


                break;
            case SETTINGWINDOW:
//                doQuit();
                break;
            case DingDingHelperAccessibilityService.LOGINWINDOW:
                if (STATE == STATE_UNCHECKED_IN) {
                    toSignIn();
                }
                break;

        }
    }


    private void finishSignIn() {
        Log.i("Infoss", "finishSign");
        AccessibilityNodeInfo mAccessibilityNodeInfo = this.getRootInActiveWindow();
        if (mAccessibilityNodeInfo == null) {
            return;
        }
        AccessibilityNodeInfo mNodeInfos = recurseFindByText("我知道了", mAccessibilityNodeInfo);//找到我知道了，完成打卡， 退出
        if (mNodeInfos == null) {
            backHomePager();
            handleHomeInsensitiveCallback(tag_callback_time);
            return;
        }
        Log.i("Infoss", "mNodeInfos");
        AccessibilityNodeInfo mInfo = mNodeInfos;
        Rect mRect = new Rect();
        mInfo.getBoundsInScreen(mRect);
        Log.i("Infoss", "x:" + mRect.centerX() + "   y:" + mRect.centerY());

        doShellCmdInputTap(mRect.centerX(), mRect.centerY());//利用adb命令进行模拟物理点击， 注意需要root

        backHomePager();
    }

    /*关闭DingDingHelperAccessibilityService*/
    private void closeHelperService() {

        List<String> mList = new ArrayList<>();
        mList.add(Constants.POINT_SERVICES_ORDER);
        mList.add(Constants.DISENABLE_SERVICE_PUT);
        //杀死钉钉进程
        mList.add("am force-stop " + DING_DING_PAGKET_NAME);

        /*如果没有屏锁则进行锁屏*/
        if (DingHelperUtils.isScreenLight(App.mContext))
            mList.add("input keyevent 26");
//        ShellUtils.execCmd(mList, true);
        new OrderThread(mList).start();

    }

    /*执行退出登录*/
    private void doQuit() {
        AccessibilityNodeInfo mAccessibilityNodeInfo = findById(CONFIRM_QUIT_ID);
//        if (mAccessibilityNodeInfo != null && mAccessibilityNodeInfo.isClickable()) {
            Toast.makeText(this, "quit confirm", Toast.LENGTH_SHORT).show();
            STATE = STATE_RELEASE;
//            mAccessibilityNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//            doShellCmdInputTap(900, 1065);
            inputClick("android:id/button1");
            isChecking = false;
            if (mAccountEntities == null)
                return;
            Log.i("Infoss", "data");
            if (index >= mAccountEntities.size()) {
                Log.i("Infoss", "已经没有账户了:  " + mAccountEntities.toString() + "  current:" + targetCheckInAcount.toString());
                String[] hm = DateUtils.getHourAndMin(System.currentTimeMillis()).split(":");
                if (Integer.parseInt(hm[0]) > TIME_LIMIT) {
                    index = 0;
                    targetCheckInAcount = mAccountEntities.get(index++);
                    Log.i("Infoss", "回到第一个账户:" + targetCheckInAcount.toString() + "    index:" + (index - 1));
                }
                closeHelperService();
                return;
            }
            Log.i("Infoss", "doScript");

            doScript();

            if (STATE == STATE_UNCHECKED_IN) {
                toSignIn();
            }
            int minute = 20+(int)(Math.random()*10);
            SPUtils.save(MORNING_CHECK_IN_TIME, 8 + ":" + minute);
//            SPUtils.save(AFTERNOON_CHECK_IN_TIME, hourOfDay + ":" + minute);

            List<String> mList = new ArrayList<>();
            mList.add("input keyevent 3");
            mList.add("input keyevent 26");
            new OrderThread(mList).start();
//        }
    }

    /*设置当前Activity在哪个Fragment*/
    private void setCurrentPagerId() {

        List<AccessibilityNodeInfo> listMine = null;
        AccessibilityNodeInfo mAccessibilityNodeInfo = getRootInActiveWindow();
        if (mAccessibilityNodeInfo == null)
            return;
        listMine = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(HEADER_MINE_ID);
        if (listMine != null && !listMine.isEmpty()) {
            CURRENT_PAGER = MINE_PAGER;
            return;
        }

        List<AccessibilityNodeInfo> listConstact = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(HEADER_CONTACT_ID);
        if (listConstact != null && !listConstact.isEmpty()) {
            CURRENT_PAGER = CONTACT_PAGER;
            return;
        }

        List<AccessibilityNodeInfo> listCompany = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(TAB_COMPANY_INDEX_ID);
        if (listCompany != null && !listCompany.isEmpty()) {
            CURRENT_PAGER = TCN_PAGER;
            return;
        }
        List<AccessibilityNodeInfo> listDing = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(HEADER_DING);
        if (listDing != null && !listDing.isEmpty()) {
            CURRENT_PAGER = DING_PAGER;
            return;
        }
        List<AccessibilityNodeInfo> listMessage = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(HEADER_MESSAGE);
        if (listMessage != null && !listMessage.isEmpty()) {
            CURRENT_PAGER = MESSAGE_PAGER;
            return;
        }

    }


    /*切换到我的，执行退出 操作*/
    private void switchMine() {
        Log.i("Infoss", "state:" + STATE);
        if (STATE != STATE_CHECKED_IN)
            return;
        AccessibilityNodeInfo mAccessibilityNodeInfo = findById(HOME_MINE_ID);
        Log.i("Infoss", "mAccessibilityNodeInfo:" + mAccessibilityNodeInfo);
        if (mAccessibilityNodeInfo == null)
            return;
        Log.i("Infoss", "switchMineClick");
        mAccessibilityNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        CURRENT_PAGER = MINE_PAGER;
    }


    /**
     * 点击打卡
     * 这里进行的简单逻辑，
     * 获取当前时间， 对当前时间进行判断， 以中午12点为中间界限
     * 如果当前时间小于12点， 去寻找上班打卡，如果大于12点， 去寻找
     * 下班打卡。
     */
    private void openCheckInDialog() {
/*        try {
            Thread.currentThread().sleep(3000);//阻断2秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        AccessibilityNodeInfo mAccessibilityNodeInfo = findById(WEBVIEW_PARENT);
        if (mAccessibilityNodeInfo == null)
            return;


        String date = getHourAndMin(System.currentTimeMillis());

        String[] arrayDate = date.split(":");
        int hour = Integer.parseInt(arrayDate[0]);

        if (hour > TIME_LIMIT) {//下班时间  by six
            Log.i("Infoss", "执行下班打卡  openCheckInDialog");

            List<AccessibilityNodeInfo> mNodeInfos = new ArrayList<>();
            recurseFindByTextToList("打卡时间", this.getRootInActiveWindow(), mNodeInfos);

            Log.i("Infoss", "recurseFindByTextToList:" + mNodeInfos.size());
            if (mNodeInfos.size() >= 2) {
                STATE = STATE_CHECKED_IN;
                mNodeInfos.clear();
                mNodeInfos = null;
                Toast.makeText(App.mContext, "您已经打卡了", Toast.LENGTH_SHORT).show();
                backHomePager();
                return;
            }

            AccessibilityNodeInfo mInfoAfter = recurseFindByText(AFTER_WORK, this.getRootInActiveWindow());//递归找到下班打卡的节点
            if (mInfoAfter == null) {
                Log.i("Infoss", "mInfoAfter  null");
                return;
            }

            recycle(mAccessibilityNodeInfo, AFTER_WORK, true);
//            doShellCmdInputTap(540, 1200);
        } else {

            AccessibilityNodeInfo mInfo = recurseFindByText(AFTER_WORK, this.getRootInActiveWindow());
            Log.i("Infoss", "上班打卡为空   openCheckInDialog");
            if (mInfo != null) {
                Log.i("Infoss", "mInfo  null");
//                Toast.makeText(App.mContext, "已经打卡", Toast.LENGTH_SHORT).show();
                STATE = STATE_CHECKED_IN;
                backHomePager();
                return;
            }
            AccessibilityNodeInfo mInfoGo = recurseFindByText(GO_TO_WORK, this.getRootInActiveWindow());
            if (mInfoGo == null) {
                Log.i("Infoss", "mInfo  上班打卡为空   null");
                return;
            }
            Log.i("Infoss", "shangban打卡  openCheckInDialog  iscut:" + isGo);
            recycle(mAccessibilityNodeInfo, GO_TO_WORK, true);
        }

    }

    private void backHomePager() {
        inputClick(CHECK_IN_PAGER_BACK);
        handleHomeInsensitiveCallback(tag_callback_time);
    }

    /**
     * 通过文字找到AccessibilityNodeInfo,文字一定要唯一，
     * 如果不唯一， 找到的是第一个出现text的AccessibilityNodeInfo
     *
     * @param text
     * @return
     */
    private AccessibilityNodeInfo findByText(String text) {
        AccessibilityNodeInfo mAccessibilityNodeInfo = this.getRootInActiveWindow();
        if (mAccessibilityNodeInfo == null)
            return null;
        List<AccessibilityNodeInfo> list = mAccessibilityNodeInfo.findAccessibilityNodeInfosByText(text);
        if (list == null || list.isEmpty())
            return null;
        return list.get(0);
    }

    /*打开签到页面---考勤*/
    private void openCheckInPager() {
/*        AccessibilityNodeInfo mAccessibilityNodeInfo = findById(RECYCLERVIEW_WORK_ID);
        if (mAccessibilityNodeInfo == null)
            return;
        recycle(mAccessibilityNodeInfo, "考勤打卡", false);
        by six
        */
        new Thread(){
            public void run(){
                try {
                    sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        doShellCmdInputTap(135, 1633);//利用adb命令进行模拟物理点击打开签到页面， 注意需要root
    }

    /*判断一下是不是签到考勤页面*/
    private boolean isCheckInPager() {

        return findById(TAB_COMPANY_INDEX_ID) != null;
    }

    private AccessibilityNodeInfo findById(String id) {
        AccessibilityNodeInfo mAccessibilityNodeInfo = this.getRootInActiveWindow();
        if (mAccessibilityNodeInfo == null)
            return null;
        List<AccessibilityNodeInfo> mNodeInfos = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(id);
        if (mNodeInfos == null || mNodeInfos.isEmpty())
            return null;
        return mNodeInfos.get(0);
    }


    /*切换到准备打卡窗口*/
    private void performWaitCheckIn() {

        Log.i("Infoss", "performWaitCheckIn  .. ");
        AccessibilityNodeInfo mAccessibilityNodeInfo = this.getRootInActiveWindow();
        if (mAccessibilityNodeInfo == null)
            return;
        List<AccessibilityNodeInfo> mNodeInfos = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(HOME_BOTTOM_WORK_ID);
        if (mNodeInfos == null || mNodeInfos.isEmpty())
            return;
        /*获取第一个Bottom*/
        AccessibilityNodeInfo mNodeInfoBottomWork = mNodeInfos.get(0);
        if (mNodeInfoBottomWork.isClickable()) {
            mNodeInfoBottomWork.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            CURRENT_PAGER = TCN_PAGER;
        }

    }

    private KeyguardManager.KeyguardLock kl;


    private void wakeAndUnlock() {
        //获取电源管理器对象
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        //获取PowerManager.WakeLock对象，后面的参数|表示同时传入两个值，最后的是调试用的Tag
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");

        //点亮屏幕
        wl.acquire(1000);

        //得到键盘锁管理器对象
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        kl = km.newKeyguardLock("unLock");

        //解锁
        kl.disableKeyguard();

    }


    /*执行登陆*/
    private void toSignIn() {
        AccessibilityNodeInfo mAccessibilityNodeInfo = this.getRootInActiveWindow();
        if (mAccessibilityNodeInfo == null)
            return;
        List<AccessibilityNodeInfo> mEdPhoneNodes = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(PHONE_ID);
        if (mEdPhoneNodes == null || mEdPhoneNodes.isEmpty())
            return;
        isChecking = true;
        AccessibilityNodeInfo mEdPhoneNode = mEdPhoneNodes.get(0);
        CharSequence mCharSequence = mEdPhoneNode.getText();
        Log.i("Infoss", "mCharSequence:" + mCharSequence);
        Log.i("Infoss", "account:" + targetCheckInAcount.getAccount());

        if (mCharSequence != null && !mCharSequence.toString().equals(targetCheckInAcount.getAccount())) {
            mEdPhoneNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);//先聚焦
            Rect mRect = new Rect();
            mEdPhoneNode.getBoundsInScreen(mRect);
            doShellCmdInputTap(mRect.right - 10, mRect.centerY());
            try {
                Thread.currentThread().sleep(1000);//阻断2秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            setTextToView(mEdPhoneNode, "");
            try {
                Thread.currentThread().sleep(1000);//阻断2秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            setTextToView(mEdPhoneNode, targetCheckInAcount.getAccount());
            Log.i("targetCheckInAcount",targetCheckInAcount.getAccount()+"11");
//            Toast.makeText(this, targetCheckInAcount.getAccount(), Toast.LENGTH_SHORT).show();
//            setTextToView(mEdPhoneNode,"15669933530");//手动输入手机号
//            try {
//                Thread.currentThread().sleep(1000);//阻断2秒
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }

        List<AccessibilityNodeInfo> mEdPasswordNodes = mAccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(PASSWORD_ID);
        if (mEdPasswordNodes == null)
            return;
        AccessibilityNodeInfo mEdPasswordNode = mEdPasswordNodes.get(0);
        CharSequence mCharSequencePassword = mEdPasswordNode.getText();
        Log.i("Infoss", "password:" + mCharSequencePassword);
//        setTextToView(mEdPasswordNode, "");
        setTextToView(mEdPasswordNode, targetCheckInAcount.getPassword());
        try {
            Thread.currentThread().sleep(2000);//阻断2秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        /*执行登陆操作*/
        inputClick(BUTTON_ID);
    }

    /**
     * 设置文本
     */
    private void setTextToView(AccessibilityNodeInfo node, String text) {
        Bundle arguments = new Bundle();
        arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD);
        arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                true);
        node.performAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                arguments);
        /*判断下当前版本*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } else {
            ClipData data = ClipData.newPlainText("reply", text);
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClip(data);
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS); // 获取焦点
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE); // 执行粘贴
        }

    }

    /**
     * 通过ID获取控件，并进行模拟点击
     *
     * @param clickId
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void inputClick(String clickId) {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(clickId);
            for (AccessibilityNodeInfo item : list) {
                item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
    }

    /*表示是否已经中断*/
    private volatile boolean isCut = false;

    private ShellUtils.CommandResult doShellCmdInputTap(int x, int y) {
        List<String> mCmds = new ArrayList<>();
        mCmds.add("input tap " + x + " " + y);
        ShellUtils.CommandResult mCommandResult = ShellUtils.execCmd(mCmds, true);

        Log.i("Infoss", "comm:" + mCommandResult.toString());
        return mCommandResult;
    }

    private void handleIt(AccessibilityNodeInfo info) {

        if (!isCut) {
            isCut = true;
//            Toast.makeText(App.mContext, "你已经打卡", Toast.LENGTH_SHORT).show();
        }
        Log.i("Infoss", "执行完成 adb");
        Rect mRect = new Rect();
        info.getBoundsInScreen(mRect);
        Log.i("Infoss", "rect:" + mRect + "   " + mRect.centerX() + "   " + mRect.centerY());
        ShellUtils.CommandResult mCommandResult = doShellCmdInputTap(mRect.centerX(), mRect.centerY());
        try {
            Thread.currentThread().sleep(8000);//阻断2秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (mCommandResult.result == 0) {
            STATE = STATE_CHECKED_IN;
        }

    }

    private volatile boolean isGo = true;

    private void handleGoToWork(AccessibilityNodeInfo info) {
        if (isGo) {
            isGo = false;
//            Toast.makeText(App.mContext, "打卡失败，被拦截，进入高级权限打卡 ... ", Toast.LENGTH_SHORT).show();
            Rect mRect = new Rect();
            info.getBoundsInScreen(mRect);
            Log.i("Infoss", "rect:" + mRect + "   " + mRect.centerX() + "   " + mRect.centerY());
            ShellUtils.CommandResult mCommandResult = doShellCmdInputTap(mRect.centerX(), mRect.centerY());
            try {
                Thread.currentThread().sleep(8000);//阻断2秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mCommandResult.result == 0) {
                STATE = STATE_CHECKED_IN;
            }
        }
    }


    /**
     * 递归寻找AccessibilityNodeInfo  这里主要用于寻找webView 内部的文本
     * 找到控件添加进List
     * 这里属于模糊匹配， 只要包含target即可
     *
     * @param target
     * @param accessibilityNodeInfo
     * @return
     */
    private void recurseFindByTextToList(String target, AccessibilityNodeInfo accessibilityNodeInfo, @NonNull List<AccessibilityNodeInfo> mInfos) {

        if (accessibilityNodeInfo != null && accessibilityNodeInfo.getChildCount() == 0) {

            if (((accessibilityNodeInfo.getText() != null && accessibilityNodeInfo.getText().toString().contains(target)) || (accessibilityNodeInfo.getContentDescription() != null && accessibilityNodeInfo.getContentDescription().toString().contains(target)))) {
                mInfos.add(accessibilityNodeInfo);
                return;
            } else
                return;
        } else {
            if (accessibilityNodeInfo == null)
                return;

            for (int i = 0; i < accessibilityNodeInfo.getChildCount(); i++) {
                AccessibilityNodeInfo child = accessibilityNodeInfo.getChild(i);
                recurseFindByTextToList(target, child, mInfos);
            }
            return;
        }
    }

    /**
     * 递归寻找AccessibilityNodeInfo  这里主要用于寻找webView 内部的文本
     * 适用于 文本唯一的控件
     *
     * @param target
     * @param accessibilityNodeInfo
     * @return
     */
    private AccessibilityNodeInfo recurseFindByText(String target, AccessibilityNodeInfo accessibilityNodeInfo) {

        if (accessibilityNodeInfo != null && accessibilityNodeInfo.getChildCount() == 0) {
            if ((target.equals(accessibilityNodeInfo.getText()) || target.equals(accessibilityNodeInfo.getContentDescription())))
                return accessibilityNodeInfo;
            else
                return null;
        } else {
            if (accessibilityNodeInfo == null)
                return null;

            for (int i = 0; i < accessibilityNodeInfo.getChildCount(); i++) {
                AccessibilityNodeInfo child = accessibilityNodeInfo.getChild(i);
                AccessibilityNodeInfo isTaget = recurseFindByText(target, child);
                if (isTaget == null)
                    continue;
                else
                    return isTaget;
            }
            return null;
        }
    }

    /**
     *用于点击打卡
     */
    public void recycle(AccessibilityNodeInfo info, String target, boolean isClickSelft) {
        int temp = 0;
        if (info != null && info.getChildCount() == 0) {
            if (info.getContentDescription() != null && info.getContentDescription().toString().equals(target) && isClickSelft) {

                String content = info.getContentDescription().toString();
                info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (content.equals(AFTER_WORK))
                    handleIt(info);//下班
                else if (content.equals(GO_TO_WORK))
                    handleGoToWork(info);//上班
                return;
            }
            if (info.getText() != null) {
                if (target.equals(info.getText().toString())) {
                    if (isClickSelft) {
                        info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return;
                    }
                    AccessibilityNodeInfo parent = info.getParent();
                    while (parent != null) {
                        if (parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            break;
                        }
                        parent = parent.getParent();
                    }
                }


            }
        } else {

            for (int i = 0; info != null && i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    temp = i;
                    recycle(info.getChild(i), target, isClickSelft);
                }
            }
        }
    }

    /**
     * 中断服务的回调
     */
    @Override
    public void onInterrupt() {
        Log.i("Infoss", "服务已经中断 ...");
        runing_monitor = false;
        isChecking = false;
    }


    /**
     * 打开App
     *
     * @param context     上下文
     * @param packageName 包名
     */
    public static void launchApp(Context context, String packageName) {
        if ((packageName == null || packageName.trim().length() == 0)) return;
        context.startActivity(getLaunchAppIntent(context, packageName));
    }

    /**
     * 获取打开App的意图
     *
     * @param context     上下文
     * @param packageName 包名
     * @return intent
     */
    public static Intent getLaunchAppIntent(Context context, String packageName) {
        return context.getPackageManager().getLaunchIntentForPackage(packageName);
    }


    private boolean isChecking = false;

    private void doCheckIn() {

        Log.i("Infoss", "doCheckIn:" + DingHelperUtils.isScreenLocked(App.mContext));

        if (isChecking) {
            return;
        }
        if (mAccountEntities == null || mAccountEntities.isEmpty() || targetCheckInAcount == null) {
            Toast.makeText(App.mContext, "账户为空", Toast.LENGTH_SHORT).show();
            return;
        }
        /*标示上已经正在打卡*/
        isChecking = true;
        STATE = STATE_UNCHECKED_IN;
//        runing_monitor = false;
//        launchApp(this, DING_DING_PAGKET_NAME);

        List<String> mStringList = new ArrayList<>();
        /*唤醒屏幕*/
        if (!DingHelperUtils.isScreenLight(App.mContext)) {
            mStringList.add("input keyevent 26");
//            wakeAndUnlock();
        }
        if (DingHelperUtils.isScreenLocked(App.mContext)) {
                 /*从下往上滑动解锁*/
            mStringList.add("input swipe 200 800 200 100");
        }
        /*启动钉钉*/
        mStringList.add("am start -n " + DING_DING_PAGKET_NAME + "/" + LOGINWINDOW);
        new OrderThread(mStringList).start();
    }

    @Override
    public void onDestroy() {
        Log.i("Infoss", "onDestroy");
        IS_ENABLE_DINGDINGHELPERACCESSIBILITYSERVICE = false;
        stopForeground(true);
        EventBus.getDefault().unregister(this);
        isChecking = false;
        if (mHandler != null)
            mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static final int NOTIFICATION_ID_PROGRESS_ID = 0x120;

    private void sendNotification(int hour, int min) {

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);

        int lHour = 0;

        if (hour <= this.hour) {
            lHour = this.hour - hour;
        } else {
            lHour = 24 - hour + this.hour;
        }

        int lMin = 0;
        if (min <= this.min) {
            lMin = this.min - min;
        } else {
            if (this.hour != hour)
                lHour--;
            if (hour == this.hour) {
                lMin = this.min - min;
            } else
                lMin = 60 - min + this.min;
        }
        Log.i("Infoss", "sendNotification 打卡时间是:" + this.hour + ":" + this.min + " 当前时间为: " + hour + ":" + min + "   " + "距离打卡时间还有: " + lHour + " 个小时" + lMin + " 分");
        mBuilder.setContentText("距离打卡时间还有: " + lHour + " 个小时" + lMin + " 分").setContentTitle("钉钉自动打卡");
        PendingIntent mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(mPendingIntent);
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID_PROGRESS_ID, mBuilder.build());
    }


    /*提升优先级,然而对AccessibilityService 并没有什么卵用*/
    public static class InnerService extends Service {
        private static final void startInnerService(Context context) {
            context.startService(new Intent(context, InnerService.class));
        }

        @Override
        public void onCreate() {
            super.onCreate();
            Log.i("Infoss", "a service oncreate");
            Notification.Builder mBuilder = new Notification.Builder(this);
            mBuilder.setSmallIcon(R.mipmap.ic_launcher);
            startForeground(NOTIFICATION_ID, mBuilder.build());


            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopForeground(true);
                    NotificationManager mNotificationManager = (NotificationManager) InnerService.this.getSystemService(NOTIFICATION_SERVICE);
                    mNotificationManager.cancel(NOTIFICATION_ID);
                    stopSelf();
                }
            }, 50);
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            stopForeground(true);
        }
    }
}
