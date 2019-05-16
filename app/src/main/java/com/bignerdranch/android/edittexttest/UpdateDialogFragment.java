package com.bignerdranch.android.edittexttest;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;

public class UpdateDialogFragment extends DialogFragment {

    private UpdateApk mUpdateApk;

    protected interface UpdateApk{
        void updateApkListener();
    }

    public void updateApkIn(UpdateApk updateApk){
        mUpdateApk = updateApk;
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle("发现新版本")
                .setMessage("更新了那些内容：...")
                .setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mUpdateApk.updateApkListener();
                    }
                })
                .setNegativeButton("暂不更新", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .create();
        return dialog;
    }
}
