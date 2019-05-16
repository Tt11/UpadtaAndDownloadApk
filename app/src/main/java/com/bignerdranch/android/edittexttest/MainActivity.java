package com.bignerdranch.android.edittexttest;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.mbms.DownloadProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.widget.Toast.LENGTH_SHORT;
import static java.security.AccessController.getContext;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private Button mButton;
    private Handler mHandler;
    private String apkUrl;
    private Activity activity = MainActivity.this;
    private File file;
    private int versionCode;//服务器中的版本号
    private ProgressDialog progressDialog;

    private static final int GET_INTENET_VERSION = 0;
    private static final int UPDATE_YES = 1;
    private static final int SHOW_VERSION_IS_NEW = 2;
    private static final int SHOW_UPDATE_DIALOG = 4;
    private static final int UPDATE_PROGRESS = 6;
    private static final int INSTALL_APK = 7;
    private static final int NEWEST_VERSION = 9;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case GET_INTENET_VERSION:
                        getAppTomcatVersion();//从网上得到apk的版本号
                        break;
                    case SHOW_UPDATE_DIALOG:
//                      //弹出更新对话框提示
                        showUpdateDialog();
                        break;
                    case UPDATE_YES:
                        //创建进度对话框
                        createProgressDialog();
                        downLoadApk();//下载apk
//                        initNotification();
                        break;
                    case SHOW_VERSION_IS_NEW:
                        Toast.makeText(MainActivity.this, "当前已是最新版本", LENGTH_SHORT).show();
                        break;
                    case UPDATE_PROGRESS:
                        int progress = msg.arg1;
                        progressDialog.setProgress(progress);
//                        downloadProgress(progress);
                        break;
                    case INSTALL_APK:
                        installApk(activity, file);
//                        clickInstallApk();
                        break;
                }


            }
        };

        mButton = findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //发送一网络请求，得到服务器中的版本号，然后与正在使用的app的版本好相比较
                mHandler.sendEmptyMessage(GET_INTENET_VERSION);

            }
        });
        //用来控制软键盘的问题
        AndroidBug5497Workaround.assistActivity(this);

    }

    //弹出有新版本的更新对话框
    private void showUpdateDialog() {
        FragmentManager manager = getSupportFragmentManager();
        UpdateDialogFragment dialogFragment = new UpdateDialogFragment();
        dialogFragment.updateApkIn(new UpdateDialogFragment.UpdateApk() {
            @Override
            public void updateApkListener() {
                dialogFragment.dismiss();
                //向handler发送消息，告诉handler用户点击了立刻更新按钮
                mHandler.sendEmptyMessage(UPDATE_YES);
//                downLoadApk();
                //创建进度对话框
//                createProgressDialog();
            }
        });
        dialogFragment.show(manager, "asdc");
    }

    //下载apk
    private void downLoadApk() {
        //请求服务端的apk
        if (apkUrl != null) {
            Request request = new Request.Builder()
                    .url(apkUrl)
                    .build();
            OkHttpClient client = new OkHttpClient();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {

                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    requestPermission(response);
                }
            });
        }

    }

    //请求权限
    private void requestPermission(@NonNull Response response) {
        //android6.0系统后增加运行时权限，需要动态添加内存卡读取权限
        if (Build.VERSION.SDK_INT >= 23) {
            int permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {//如果没有权限
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "没有权限", LENGTH_SHORT).show();
                    }
                });
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                return;

            } else {
                downLoadFile(response);//下载文件
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                mHandler.sendEmptyMessage(INSTALL_APK);
            }
        } else {
            downLoadFile(response);//下载文件
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            mHandler.sendEmptyMessage(INSTALL_APK);
        }
    }

    //下载文件
    private void downLoadFile(Response response) {
        InputStream is = null;
        FileOutputStream fos = null;
        byte[] buf = new byte[1024];//每次读取1K的数据

        int len = 0;
        long sum = 0;
        int progress = 0;

        long downloadApkLength = response.body().contentLength();//要下载的文件的长度
        Log.i(TAG, "downApkFlie: downloadApkLength---" + downloadApkLength);

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            file = new File(Environment.getExternalStorageDirectory(), "test.apk");
            try {
                //如果文件已经存在，得到已经下载的Apk的版本信息，和服务器中的版本信息相比较
                //相等的话，直接调用已经下载的进行安装，否则重新从网上下载安装
                if (file.exists()) {
                    //如果文件存在，判断已下载文件的长度
                    if(file.length() == downloadApkLength){
                        //得到已经下载的文件的版本信息
                        int localVersionCode = getLocalApkInfo(file.getAbsolutePath(), activity);
                        if (localVersionCode != versionCode){
                            file.delete();
//                        file.createNewFile();
                        }else{
                            Log.d(TAG, "downLoadFile: " + "执行了吗？？？？？");
                            installApk(activity, file);
                            //更新进度
                            Message msg = mHandler.obtainMessage();
                            msg.what = UPDATE_PROGRESS;
                            msg.arg1 = 100;
                            mHandler.sendMessage(msg);
                            return;
                        }
                    }
//                    file.delete();
                } else {
                    file.createNewFile();

                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            is = response.body().byteStream();

            //捕捉是否动态分配读写内存权限异常
            try {
                fos = new FileOutputStream(file);
                //捕捉输入流读取异常
                try {
                    /**
                     * read(),从输入流中读取数据的下一个字节，返回0~255范围内的字节值，如果已经到达
                     * 流末尾而没有可用的字节，则返回-1
                     */
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);//write(byte[]b, off, int len), 将指定的byte数组中从偏移量off开始的len个字节写入此输出流
                        Log.d(TAG, "downLoadFile: len = " + len);
                        sum += len;
                        progress = (int) (sum * 1.0f / downloadApkLength * 100);
                        Log.d("h_bl", "progress=" + progress);
                        //更新进度
                        Message msg = mHandler.obtainMessage();
                        msg.what = UPDATE_PROGRESS;
                        msg.arg1 = progress;
                        mHandler.sendMessage(msg);
                    }
                    fos.flush();//彻底完成输出并清空缓存区
                    Log.i(TAG, "downApkFlie: 下载完毕");
                } catch (IOException e) {
//                    handler.sendEmptyMessage(IO_ERROR);
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "downApkFlie: 下载失败");
            } finally {
                //清空file输入输出流
                try {
                    if (is != null) {
                        is.close();//关闭输入流
                    }
                    if (fos != null) {
                        fos.close();//关闭输出流
                    }
                } catch (IOException e) {
//                    handler.sendEmptyMessage(IO_ERROR);
                }
            }
        }

    }

//    创建进度对话框
    private void createProgressDialog() {
        progressDialog = new ProgressDialog(activity);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.setMessage("正在加载...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.show();
    }

    //安装新版本APK
    protected void installApk(Activity activity, File file) {
        Intent intent = installIntentAPK(activity, file);
        if (intent == null) return;
        startActivity(intent);
    }

    //安装新版本的apk第一步
    @Nullable
    private Intent installIntentAPK(Activity activity, File file) {
        if (activity == null || !file.exists()) {
            return null;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        // 由于没有在Activity环境下启动Activity,设置下面的标签
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setDataAndType(FileProvider.getUriForFile(activity, "com.wjx.fileprovider", file),
                    "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        }
        return intent;
    }

    //获取versionName
    private int getAppLocalVersion() throws PackageManager.NameNotFoundException {
        PackageManager packageManager = getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
        return packageInfo.versionCode;
    }

    //获取服务器中的apk的版本号
    private void getAppTomcatVersion() {
        String url = "http://192.168.0.137:8080/NewApk/output.json";//输入请求的地址
        OkHttpClient client = new OkHttpClient();
        final Request request = new Request.Builder()
                .url(url)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d(TAG, "onResponse: " + responseBody);
                try {
                    JSONObject jsonObject = new JSONObject(responseBody);
                    JSONObject object = jsonObject.getJSONObject("apkInfo");
                    versionCode = object.getInt("versionCode");
//                    apkUrl = object.getString("apkUrl");
                    apkUrl = "https://raw.githubusercontent.com/guolindev/eclipse/master/eclipse-inst-win64.exe";

                    if (versionCode > getAppLocalVersion()) {
                        //服务器中的apk版本大于本地的apk版本，弹出对话框，需要更新
                        Log.d(TAG, "onResponse: " + "需要更新");

                        mHandler.sendEmptyMessage(SHOW_UPDATE_DIALOG);

                    } else {
                        mHandler.sendEmptyMessage(NEWEST_VERSION);
                    }

                } catch (JSONException | PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }

            }
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "已经申请到权限", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "未申请到权限", Toast.LENGTH_SHORT).show();
        }
    }

    //    获取下载到本地的apk包的信息：版本号，名称，图标等
    public int getLocalApkInfo(String localApkPath, Context context) {
        int versionCode = 0;
        PackageManager manager = context.getPackageManager();
        PackageInfo packageInfo = manager.getPackageArchiveInfo(localApkPath, PackageManager.GET_ACTIVITIES);
        if (packageInfo != null) {
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            //必须加这两句，不然下面icon获取是default icon而不是应用包的icon
            applicationInfo.sourceDir = localApkPath;
            applicationInfo.publicSourceDir = localApkPath;

            String appName = manager.getApplicationLabel(applicationInfo).toString();//得到应用名
            String packageName = applicationInfo.packageName;//得到包名
            String versionName = packageInfo.versionName;//得到版本信息
            versionCode = packageInfo.versionCode;//得到版本号

            Drawable icon1 = manager.getApplicationIcon(applicationInfo);//得到图标信息

            String pkgInfoStr = String.format("PackageName:%s, Vesion: %s, AppName: %s", packageName, versionName, appName);
            Log.d(TAG, "getLocalApkInfo: " + String.format("PKgInfo: %s", pkgInfoStr));

        }
        return versionCode;
    }

}
