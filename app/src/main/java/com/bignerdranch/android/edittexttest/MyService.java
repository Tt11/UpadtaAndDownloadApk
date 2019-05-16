package com.bignerdranch.android.edittexttest;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MyService extends Service {

    private NotificationCompat.Builder builder;
    private DownloadBinder mBinder;

    class DownloadBinder extends Binder {
        public void startDownload(String url){

        }

        public void pauseDownload(){

        }

        public void cancelDownload(){

        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    //回去notificationManager管理器
    public NotificationManager getNotificationManager(){
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    //初始化消息通知
    public Notification getNotification(String title, int progress) {
        builder = new NotificationCompat.Builder(this, "default")
                .setContentTitle(title)
//                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentText("下载进度：" + "0%")
                .setProgress(100, progress, false)
                .setAutoCancel(true);//设置通知被点击一次是否自动取消
        return builder.build();//构建通知对象
    }

    //通知栏中的下载进度
//    public void downloadProgress(int progress) {
//        builder.setProgress(100, progress, false);
//        builder.setContentText("下载进度:" + progress + "%");
//        notification = builder.build();
//        notificationManager.notify(1, notification);
//    }

    //通知栏中下载完成后，自动安装
//    private void autoInstallApk() {
        //自动安装
//        installApk(activity, file);
//        notificationManager.cancel(1);//取消通知
//    }

    //通知栏中下载完成后，点击安装
    private void clickInstallApk() {
        builder.setContentTitle("下载完成")
                .setContentText("点击安装")
                .setAutoCancel(true);//设置通知被点击一次是否自动取消
        //点击安装
//        Intent intent = installIntentAPK(activity, file);
//        PendingIntent pi = PendingIntent.getActivity(activity, 0, intent, 0);
//        notification = builder.setContentIntent(pi).build();
//        notificationManager.notify(1, notification);
    }

  

}
