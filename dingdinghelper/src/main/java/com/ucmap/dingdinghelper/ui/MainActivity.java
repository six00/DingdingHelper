package com.ucmap.dingdinghelper.ui;

import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ucmap.dingdinghelper.ITimerListener;
import com.ucmap.dingdinghelper.ITimingAidlInterface;
import com.ucmap.dingdinghelper.R;
import com.ucmap.dingdinghelper.app.App;
import com.ucmap.dingdinghelper.common.MakeGroupRunnable;
import com.ucmap.dingdinghelper.common.OrderThread;
import com.ucmap.dingdinghelper.entity.AccountEntity;
import com.ucmap.dingdinghelper.pixelsdk.ActivityManager;
import com.ucmap.dingdinghelper.pixelsdk.PixelActivityUnion;
import com.ucmap.dingdinghelper.pixelsdk.PointActivity;
import com.ucmap.dingdinghelper.services.TimingService;
import com.ucmap.dingdinghelper.sphelper.SPUtils;
import com.ucmap.dingdinghelper.utils.Constants;
import com.ucmap.dingdinghelper.utils.DingHelperUtils;
import com.ucmap.dingdinghelper.utils.JsonUtils;
import com.ucmap.dingdinghelper.utils.ShellUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.ucmap.dingdinghelper.utils.Constants.AFTERNOON_CHECK_IN_TIME;
import static com.ucmap.dingdinghelper.utils.Constants.MORNING_CHECK_IN_TIME;
import static com.ucmap.dingdinghelper.utils.DateUtils.getHourAndMin;

//  修改周六代码
public class MainActivity extends AppCompatActivity {

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what) {
                case 0x001:
                    updateUI((String) msg.obj);
                    break;
            }
            return false;
        }
    });

    private TextView mPTimeTextView;

    private Runnable m = new Runnable() {
        @Override
        public void run() {
            if (mFrameLayout != null)
                mFrameLayout.setVisibility(View.GONE);
        }
    };
    private Button mCheckInCurrent;
    private Toolbar mToolbar;
    private AlertDialog mAlertDialog;
    private ProgressDialog mProgressDialog;

    private void updateUI(String time) {
        mHandler.removeCallbacks(m);
        if (mFrameLayout != null)
            mFrameLayout.setVisibility(View.VISIBLE);

        if (mPTimeTextView != null) {
            mPTimeTextView.setVisibility(View.VISIBLE);
            mPTimeTextView.setText(time);
        }
        mHandler.postDelayed(m, 30 * 1000);
    }

    private ITimingAidlInterface mITimingAidlInterface;
    private ITimerListener mITimerListener = new ITimerListener.Stub() {

        @Override
        public void toCallback(String time) throws RemoteException {
            mHandler.obtainMessage(0x001, time).sendToTarget();
        }
    };
    private boolean isBindServices = false;
    private ServiceConnection mServiceConnection = new ServiceConnection() {


        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                isBindServices = true;
                mITimingAidlInterface = ITimingAidlInterface.Stub.asInterface(service);
                mITimingAidlInterface.registerTimerListener(mITimerListener);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private FrameLayout mFrameLayout;

    private TimePickerDialog mTimePickerDialog;
    private TextView mTimeTextView;


    private TextView mNTimeTextView;

    private void showPickerMorning() {

        if (mTimePickerDialog == null) {
            mTimePickerDialog = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {

                    if (hourOfDay > 12) {
                        Toast.makeText(App.mContext, "早上打卡时间不能超过12点", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SPUtils.save(MORNING_CHECK_IN_TIME, hourOfDay + ":" + minute);
                    setAlarmList(getAccountLists());
                    if (mTimeTextView != null)
                        mTimeTextView.setText(hourOfDay + ":" + minute);
                    toModifyNotifyTime(hourOfDay + ":" + minute);
                }
            }, 8, 45, true);
        }
        mTimePickerDialog.show();
    }

    private Executor mComminicationPool = Executors.newCachedThreadPool();

    private void toModifyNotifyTime(final String time) {

        mComminicationPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mITimingAidlInterface != null)
                        mITimingAidlInterface.reInitCheckInTime(time);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private TimePickerDialog mNoonTimePickerDialog = null;

    private List<AccountEntity> getAccountLists() {
        return JsonUtils.fromJsonList((String) SPUtils.getString(Constants.ACCOUNT_LIST, "-1"), AccountEntity.class);
    }

    private void showPickerAfterNoon() {
        if (mNoonTimePickerDialog == null) {
            mNoonTimePickerDialog = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {

                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    if (hourOfDay < 12) {
                        Toast.makeText(App.mContext, "下班打卡需要12点以后", Toast.LENGTH_SHORT).show();
                        return;
                    }
//                    minute = 20+(int)(Math.random()*10); by six
//                    Toast.makeText(MainActivity.this, "change"+minute, Toast.LENGTH_SHORT).show();
                    SPUtils.save(AFTERNOON_CHECK_IN_TIME, hourOfDay + ":" + minute);
                    setAlarmList(getAccountLists());
                    if (mNTimeTextView != null)
                        mNTimeTextView.setText(hourOfDay + ":" + minute);
                    toModifyNotifyTime(hourOfDay + ":" + minute);
                }
            }, 20, 45, true);
        }
        mNoonTimePickerDialog.show();
    }

    private void initAccount() {

        String jsonAccountList = (String) SPUtils.getString(Constants.ACCOUNT_LIST, "-1");
        List<AccountEntity> mAccountEntities = JsonUtils.listJson(jsonAccountList, AccountEntity.class);

        Log.i("Infoss", "size:" + mAccountEntities.toString());
        if (mAccountEntities == null || mAccountEntities.isEmpty()) {
            this.findViewById(R.id.check_time_linearLayout).setVisibility(View.GONE);
            mClearButton.setVisibility(View.GONE);
        } else {
            mClearButton.setVisibility(View.VISIBLE);
            mCheckInCurrent.setVisibility(View.VISIBLE);

            if (!isRunning(TimingService.class.getName()))
                startService(new Intent(this, TimingService.class));
            this.bindService(new Intent(this, TimingService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
            this.findViewById(R.id.check_time_linearLayout).setVisibility(View.VISIBLE);
        }
        setAlarmList(mAccountEntities);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isSystemApp();
        String current = getHourAndMin(System.currentTimeMillis());
        int hour = Integer.parseInt(current.split(":")[0]);
        if(hour > 12){
            int minuteMoring = 20+(int)(Math.random()*10);
            SPUtils.save(MORNING_CHECK_IN_TIME, 8 + ":" + minuteMoring);
            setAlarmList(getAccountLists());
            toModifyNotifyTime(8+":"+minuteMoring);
        }else{
            Random r = new Random();
            int minuteNight = 30 + r.nextInt(30);
            SPUtils.save(AFTERNOON_CHECK_IN_TIME, 18 + ":" + minuteNight);//by six
            setAlarmList(getAccountLists());
            toModifyNotifyTime(18 + ":" + minuteNight);
        }
        Log.i("six","onResume");
    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {

            List<ApplicationInfo> mInfos = getPackageManager().getInstalledApplications(0);
            for (ApplicationInfo mInfo : mInfos) {
                if (mInfo.sourceDir.contains(getPackageName())) {
                    toShowBeSystemApp(mInfo);
                    break;
                }
            }
        }
    };

    private void toShowBeSystemApp(final ApplicationInfo applicationInfo) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toShowBeSystemApp(applicationInfo);
                }
            });
            return;
        }
        if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1)
            mDoSystemApp.setVisibility(View.GONE);
        else
            mDoSystemApp.setVisibility(View.VISIBLE);
    }

    private void isSystemApp() {
        mComminicationPool.execute(mRunnable);
    }

    private void clearAccount() {

        SPUtils.clear();
        List<String> mList = new ArrayList<>();
        mList.add(Constants.POINT_SERVICES_ORDER);
        mList.add(Constants.DISENABLE_SERVICE_PUT);
        ShellUtils.execCmd(mList, true);
        if (mServiceConnection != null && isBindServices) {
            this.unbindService(mServiceConnection);
            isBindServices = false;
        }
        if (isRunning(TimingService.class.getName()))
            stopService(new Intent(this, TimingService.class));

        if (mFrameLayout != null) {
            mFrameLayout.setVisibility(View.GONE);
        }
        initAccount();

    }

    private boolean isRunning(String className) {

        boolean tag = false;
        android.app.ActivityManager mActivityManager = (android.app.ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        List<android.app.ActivityManager.RunningServiceInfo> mRunningTaskInfos = mActivityManager.getRunningServices(150);
        Log.i("Infoss", "  size:" + mRunningTaskInfos.size());
        for (int i = 0; i < mRunningTaskInfos.size(); i++) {
            Log.i("Infoss", "RunningInfo:" + mRunningTaskInfos.get(i).service.getClassName());
            if (mRunningTaskInfos.get(i).service.getClassName().equals(className)) {
                return true;
            }
        }
        return tag;

    }

    private Button mClearButton;
    private Button mDoSystemApp;

    private boolean isRoot = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PixelActivityUnion
                .with(App.mContext)
                .targetActivityClazz(PointActivity.class)//
                .args(null)//
                .setActiviyManager(ActivityManager.getInstance())
                .start();
        mToolbar = (Toolbar) this.findViewById(R.id.toolbar);
        mToolbar.setTitleTextColor(Color.WHITE);
        this.setSupportActionBar(mToolbar);
        this.mFrameLayout = (FrameLayout) this.findViewById(R.id.parentGroup_frameLayout);
        mPTimeTextView = (TextView) this.findViewById(R.id.p_time_textView_);
        String id = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        mClearButton = (Button) this.findViewById(R.id.clear_account);
        mCheckInCurrent = (Button) this.findViewById(R.id.open_service);
        Log.i("Infoss", "  android _id:" + id);
        initAccount();
        mNTimeTextView = (TextView) this.findViewById(R.id.n_time_textView);
        this.findViewById(R.id.n_time_textView)
                .setOnClickListener(mMainListener);
        mTimeTextView = (TextView) this.findViewById(R.id.time_textView);
        this.findViewById(R.id.select_check_in_time)//
                .setOnClickListener(mMainListener);
        this.findViewById(R.id.clear_account)//
                .setOnClickListener(mMainListener);
        this.findViewById(R.id.save_password)//
                .setOnClickListener(mMainListener);
        this.findViewById(R.id.open_service)//
                .setOnClickListener(mMainListener);
        mDoSystemApp = (Button) findViewById(R.id.do_system_app);
        mDoSystemApp.setOnClickListener(mMainListener);
        new Thread(new Runnable() {
            @Override
            public void run() {
                //当前应用的代码执行目录
                if (ShellUtils.upgradeRootPermission(getPackageCodePath())) {
                    isRoot = true;
                } else {
                    if (mClearButton != null) {
                        mClearButton.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(App.mContext, "root权限失败.", Toast.LENGTH_SHORT).show();
                                isRoot = false;
                            }
                        });
                    }

                }
            }
        }).start();

    }

    private View.OnClickListener mMainListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            if (!isRoot) {
                Toast.makeText(App.mContext, "没有root无法进行下一步", Toast.LENGTH_SHORT).show();
                return;
            }
            switch (v.getId()) {

                case R.id.save_password:
                    Intent mIntent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivityForResult(mIntent, 0x88);
                    break;
                case R.id.open_service:
                    List<String> orders = new ArrayList<String>();
                    orders.add("am broadcast -a com.ucmap.dingdinghelper.clock");
                    new OrderThread(orders).start();
                    break;
                case R.id.clear_account:
                    mClearButton.setVisibility(View.GONE);
                    mCheckInCurrent.setVisibility(View.GONE);
                    clearAccount();
                    Toast.makeText(App.mContext, "已经清空账户", Toast.LENGTH_SHORT).show();
                    break;
                /*设置打卡时间*/
                case R.id.select_check_in_time:
                    showPickerMorning();
                    break;
                /*设置下班时间*/
                case R.id.n_time_textView:
                    showPickerAfterNoon();
                    break;
                case R.id.do_system_app:
                    doSystemAppDialog();
                    break;
            }
        }
    };

    private void doSystemAppDialog() {

        //
        if (mAlertDialog == null)
            mAlertDialog = new AlertDialog.Builder(this)//
                    .setMessage("确定要成为系统App吗？")//
                    .setPositiveButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mAlertDialog.dismiss();
                        }
                    }).setNegativeButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mAlertDialog.dismiss();
                            doSystemApp();
                        }
                    }).create();

        mAlertDialog.show();
    }

    private boolean isMaking = false;

    private void doSystemApp() {
        if (!isMaking) {
            isMaking = true;
            showProgressDialog();
            mComminicationPool.execute(new MakeGroupRunnable(this, mCallback));
        }
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = (ProgressDialog) new ProgressDialog(this);
            mProgressDialog.setMessage("成为系统App中...");
            mProgressDialog.setCancelable(false);
        }
        mProgressDialog.show();
    }

    private MakeGroupRunnable.Callback mCallback = new MakeGroupRunnable.Callback() {
        @Override
        public void call(final boolean isSuccess) {
            isMaking = false;
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mProgressDialog != null)
                        mProgressDialog.dismiss();

                    String msg = isSuccess == true ? "成功,请重启手机生效" : "失败,请检查是否root";
                    Log.i("Infoss", "isSuccess:" + isSuccess);
                    Toast.makeText(App.mContext, msg + "", Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i("Infoss", "回调  request code:" + requestCode + "   resultCode:" + resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0x88 && resultCode == RESULT_OK) {
            initAccount();
        }
    }

    private void setAlarmList(List<AccountEntity> mList) {
//        Log.i("six",mList);
        if (mList == null || mList.isEmpty()) {
            return;
        }
        /*如果系统api>19转化为通知形式也唤醒广播*/
        if (Constants.IS_NOTITY_TYPE_CHECK_IN_TAG) {
            Log.i("Infoss", "通知唤醒广播 打卡");
            return;
        } else {
            Log.i("Infoss", "Alarm Manager 唤醒");
        }
        for (AccountEntity mAccountEntity : mList) {
            DingHelperUtils.setAlarm(mAccountEntity, App.mContext);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConnection != null && isBindServices) {
            this.unbindService(mServiceConnection);
            isBindServices = false;
        }
        mHandler.removeCallbacksAndMessages(null);
        try {
            if (mITimingAidlInterface != null)
                mITimingAidlInterface.unRegisterTimerListener(mITimerListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
