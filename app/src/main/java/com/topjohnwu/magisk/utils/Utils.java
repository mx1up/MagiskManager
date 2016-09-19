package com.topjohnwu.magisk.utils;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.topjohnwu.magisk.ModulesFragment;
import com.topjohnwu.magisk.R;
import com.topjohnwu.magisk.ReposFragment;
import com.topjohnwu.magisk.module.Module;
import com.topjohnwu.magisk.module.Repo;
import com.topjohnwu.magisk.module.RepoHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

public class Utils {

    public static int magiskVersion, remoteMagiskVersion = -1, remoteAppVersion = -1;
    public static String magiskLink, magiskChangelog, appChangelog, appLink, phhLink, supersuLink;
    private static final String TAG = "Magisk";

    public static final String MAGISK_PATH = "/magisk";
    public static final String MAGISK_CACHE_PATH = "/cache/magisk";
    public static final String UPDATE_JSON = "https://raw.githubusercontent.com/topjohnwu/MagiskManager/updates/magisk_update.json";

    public static boolean fileExist(String path) {
        List<String> ret;
        String command = "if [ -f " + path + " ]; then echo true; else echo false; fi";
        if (Shell.rootAccess()) {
            ret = Shell.su(command);
        } else {
            ret = Shell.sh(command);
        }
        return Boolean.parseBoolean(ret.get(0));
    }

    public static boolean rootEnabled() {
        List<String> ret;
        String command = "if [ -z $(which su) ]; then echo false; else echo true; fi";
        ret = Shell.sh(command);
        return Boolean.parseBoolean(ret.get(0));
    }

    public static boolean createFile(String path) {
        String command = "touch " + path + " 2>/dev/null; if [ -f " + path + " ]; then echo true; else echo false; fi";
        if (!Shell.rootAccess()) {
            return false;
        } else {
            return Boolean.parseBoolean(Shell.su(command).get(0));
        }
    }

    public static boolean removeFile(String path) {
        String command = "rm -f " + path + " 2>/dev/null; if [ -f " + path + " ]; then echo false; else echo true; fi";
        if (!Shell.rootAccess()) {
            return false;
        } else {
            return Boolean.parseBoolean(Shell.su(command).get(0));
        }
    }

    public static void toggleRoot(Boolean b) {
        if (b) {
            Shell.su("ln -s $(getprop magisk.supath) /magisk/.core/bin", "setprop magisk.root 1");
        } else {
            Shell.su("rm -rf /magisk/.core/bin", "setprop magisk.root 0");
        }
    }

    public static List<String> getModList(String path) {
        List<String> ret;
        ret = Shell.sh("find " + path + " -type d -maxdepth 1 ! -name \"*.core\" ! -name \"*lost+found\" ! -name \"*magisk\"");
        if (ret.isEmpty() && Shell.rootAccess())
            ret = Shell.su("find " + path + " -type d -maxdepth 1 ! -name \"*.core\" ! -name \"*lost+found\" ! -name \"*magisk\"");
        return ret;
    }

    public static List<String> readFile(String path) {
        List<String> ret;
        ret = Shell.sh("cat " + path);
        if (ret.isEmpty() && Shell.rootAccess())
            ret = Shell.su("cat " + path);
        return ret;
    }

    public static void downloadAndReceive(Context context, DownloadReceiver receiver, String link, String file) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, R.string.permissionNotGranted, Toast.LENGTH_LONG).show();
            return;
        }
        File downloadFile, dir = new File(Environment.getExternalStorageDirectory() + "/MagiskManager");
        downloadFile = new File(dir + "/" + file);
        if (!dir.exists()) dir.mkdir();
        if (downloadFile.exists()) downloadFile.delete();

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
        request.setDestinationUri(Uri.fromFile(downloadFile));

        receiver.setDownloadID(downloadManager.enqueue(request));
        context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public static String procFile(String value, Context context) {

        String cryptoPass = context.getResources().getString(R.string.pass);
        try {
            DESKeySpec keySpec = new DESKeySpec(cryptoPass.getBytes("UTF8"));
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey key = keyFactory.generateSecret(keySpec);

            byte[] encrypedPwdBytes = Base64.decode(value, Base64.DEFAULT);
            // cipher is not thread safe
            Cipher cipher = Cipher.getInstance("DES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypedValueBytes = (cipher.doFinal(encrypedPwdBytes));

            return new String(decrypedValueBytes);

        } catch (InvalidKeyException | UnsupportedEncodingException | NoSuchAlgorithmException
                | BadPaddingException | NoSuchPaddingException | IllegalBlockSizeException
                | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return value;
    }

        public static boolean hasStatsPermission(Context context) {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;

        }


    public abstract static class DownloadReceiver extends BroadcastReceiver {
        public Context mContext;
        long downloadID;
        public String mName;

        public DownloadReceiver() {
        }

        public DownloadReceiver(String name) {
            mName = name;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mContext = context;
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            String action = intent.getAction();
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadID);
                Cursor c = downloadManager.query(query);
                if (c.moveToFirst()) {
                    int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = c.getInt(columnIndex);
                    switch (status) {
                        case DownloadManager.STATUS_SUCCESSFUL:
                            File file = new File(Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))).getPath());
                            task(file);
                            break;
                        default:
                            Toast.makeText(context, R.string.download_file_error, Toast.LENGTH_LONG).show();
                            break;
                    }
                    context.unregisterReceiver(this);
                }
            }
        }

        public void setDownloadID(long id) {
            downloadID = id;
        }

        public abstract void task(File file);
    }

    public static class Initialize extends AsyncTask<Void, Void, Void> {

        private Context mContext;

        public Initialize(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            List<String> ret = Shell.sh("getprop magisk.version");
            if (ret.get(0).replaceAll("\\s", "").isEmpty()) {
                magiskVersion = -1;
            } else {
                magiskVersion = Integer.parseInt(ret.get(0));
            }

            // Install Busybox and set as top priority
//            if (Shell.rootAccess()) {
//                String busybox = mContext.getApplicationInfo().nativeLibraryDir + "/libbusybox.so";
//                Shell.su(
//                        "rm -rf /data/busybox",
//                        "mkdir -p /data/busybox",
//                        "cp -af " + busybox + " /data/busybox/busybox",
//                        "chmod 755 /data/busybox /data/busybox/busybox",
//                        "chcon u:object_r:system_file:s0 /data/busybox /data/busybox/busybox",
//                        "/data/busybox/busybox --install -s /data/busybox",
//                        "rm -f /data/busybox/su",
//                        "export PATH=/data/busybox:$PATH"
//                );
//            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (!Shell.rootAccess()) {
                Snackbar.make(((Activity) mContext).findViewById(android.R.id.content), R.string.no_root_access, Snackbar.LENGTH_LONG).show();
            }

        }
    }

    public static class CheckUpdates extends AsyncTask<Void, Void, Void> {

        private Context mContext;

        public CheckUpdates(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(UPDATE_JSON).openConnection();
                c.setRequestMethod("GET");
                c.setInstanceFollowRedirects(false);
                c.setDoOutput(false);
                c.connect();

                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                JSONObject json = new JSONObject(sb.toString());
                JSONObject magisk = json.getJSONObject("magisk");
                JSONObject app = json.getJSONObject("app");
                JSONObject root = json.getJSONObject("root");

                remoteMagiskVersion = magisk.getInt("versionCode");
                magiskLink = magisk.getString("link");
                magiskChangelog = magisk.getString("changelog");

                remoteAppVersion = app.getInt("versionCode");
                appLink = app.getString("link");
                appChangelog = app.getString("changelog");

                phhLink = root.getString("phh");
                supersuLink = root.getString("supersu");

            } catch (IOException | JSONException ignored) {
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (Shell.rootAccess() && magiskVersion == -1) {
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.no_magisk_title)
                        .setMessage(R.string.no_magisk_msg)
                        .setCancelable(true)
                        .setPositiveButton(R.string.download_install, (dialogInterface, i) -> {
                            new AlertDialog.Builder(mContext)
                                    .setTitle(R.string.root_method_title)
                                    .setItems(new String[]{mContext.getString(R.string.phh), mContext.getString(R.string.supersu)}, (dialogInterface1, root) -> {
                                        DownloadReceiver rootReceiver;
                                        String link, filename;
                                        switch (root) {
                                            case 0:
                                                link = phhLink;
                                                filename = "phhsu.zip";
                                                rootReceiver = new DownloadReceiver(mContext.getString(R.string.phh)) {
                                                    @Override
                                                    public void task(File file) {
                                                        new RemoveSystemSU().execute();
                                                        new FlashZIP(mContext, mName, file.getPath()).execute();
                                                    }
                                                };
                                                break;
                                            case 1:
                                                link = supersuLink;
                                                filename = "supersu.zip";
                                                rootReceiver = new DownloadReceiver(mContext.getString(R.string.supersu)) {
                                                    @Override
                                                    public void task(File file) {
                                                        new RemoveSystemSU().execute();
                                                        new FlashZIP(mContext, mName, file.getPath()).execute();
                                                    }
                                                };
                                                break;
                                            default:
                                                rootReceiver = null;
                                                link = filename = null;
                                        }
                                        DownloadReceiver magiskReceiver = new DownloadReceiver(mContext.getString(R.string.magisk)) {
                                            @Override
                                            public void task(File file) {
                                                Context temp = mContext;
                                                new FlashZIP(mContext, mName, file.getPath()) {
                                                    @Override
                                                    protected void done() {
                                                        downloadAndReceive(temp, rootReceiver, link, filename);
                                                    }
                                                }.execute();
                                            }
                                        };
                                        downloadAndReceive(mContext, magiskReceiver, magiskLink, "latest_magisk.zip");
                                    })
                                    .show();
                        })
                        .setNegativeButton(R.string.no_thanks, null)
                        .show();
            } else if (Shell.rootStatus == 2) {
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.root_system)
                        .setMessage(R.string.root_system_msg)
                        .setCancelable(true)
                        .setPositiveButton(R.string.download_install, (dialogInterface, i) -> {
                            new AlertDialog.Builder(mContext)
                                    .setTitle(R.string.root_method_title)
                                    .setItems(new String[]{mContext.getString(R.string.phh), mContext.getString(R.string.supersu)}, (dialogInterface1, root) -> {
                                        switch (root) {
                                            case 0:
                                                downloadAndReceive(
                                                        mContext,
                                                        new DownloadReceiver(mContext.getString(R.string.phh)) {
                                                            @Override
                                                            public void task(File file) {
                                                                new FlashZIP(mContext, mName, file.getPath()).execute();
                                                            }
                                                        },
                                                        phhLink, "phhsu.zip");
                                                break;
                                            case 1:
                                                downloadAndReceive(
                                                        mContext,
                                                        new DownloadReceiver(mContext.getString(R.string.supersu)) {
                                                            @Override
                                                            public void task(File file) {
                                                                new FlashZIP(mContext, mName, file.getPath()).execute();
                                                            }
                                                        },
                                                        supersuLink, "supersu.zip");
                                                break;
                                        }
                                    })
                                    .show();
                        })
                        .setNegativeButton(R.string.no_thanks, null)
                        .show();
            }
        }
    }

    public static class RemoveSystemSU extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            Shell.su(
                    "umount /system/xbin",
                    "umount -l /system/xbin",
                    "if [ ! -z $(which su | grep system) ]; then",
                    "mount -o rw,remount /system",
                    "rm -rf /system/.pin /system/app/SuperSU /system/bin/.ext /system/etc/.installed_su_daemon " +
                            "/system/etc/install-recovery.sh /system/etc/init.d/99SuperSUDaemon /system/xbin/daemonsu " +
                            "/system/xbin/su /system/xbin/sugote /system/xbin/sugote-mksh /system/xbin/supolicy " +
                            "/data/app/eu.chainfire.supersu-*",
                    "mv -f /system/bin/app_process32_original /system/bin/app_process32",
                    "mv -f /system/bin/app_process64_original /system/bin/app_process64",
                    "mv -f /system/bin/install-recovery_original.sh /system/bin/install-recovery.sh",
                    "if [ -e /system/bin/app_process64 ]; then",
                    "ln -sf /system/bin/app_process64 /system/bin/app_process",
                    "else",
                    "ln -sf /system/bin/app_process32 /system/bin/app_process",
                    "fi",
                    "umount /system",
                    "fi",
                    "setprop magisk.root 1"
            );
            return null;
        }
    }

    public static boolean rootStatus() {
        try {
            String rootStatus = Shell.su("getprop magisk.root").toString();
            String fuckyeah = Shell.sh("which su").toString();
            Log.d("Magisk", "Utils: Rootstatus Checked, " + rootStatus + " and " + fuckyeah);
            if (rootStatus.contains("0") && !fuckyeah.contains("su")) {
                return false;
            } else {
                return true;
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
       }
        return false;
    }

    public static class LoadModules extends AsyncTask<Void, Void, Void> {

        private Context mContext;

        public LoadModules(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            ModulesFragment.listModules.clear();
            List<String> magisk = getModList(MAGISK_PATH);
            Log.d("Magisk", "Utils: Reload called, loading modules");
            List<String> magiskCache = getModList(MAGISK_CACHE_PATH);

            for (String mod : magisk) {
                Log.d("Magisk", "Utils: Adding module from string " + mod);
                ModulesFragment.listModules.add(new Module(mod, mContext));
            }

            for (String mod : magiskCache) {
                Log.d("Magisk", "Utils: Adding cache module from string " + mod);
                Module cacheMod = new Module(mod, mContext);
                // Prevent people forgot to change module.prop
                cacheMod.setCache();
                ModulesFragment.listModules.add(cacheMod);
            }

            return null;
        }

    }

    public static class LoadRepos extends AsyncTask<Void, Void, Void> {

        private Context mContext;
        private boolean doReload;
        private RepoHelper.TaskDelegate mTaskDelegate;

        public LoadRepos(Context context, boolean reload, RepoHelper.TaskDelegate delegate) {
            mContext = context;
            doReload = reload;
            mTaskDelegate = delegate;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            ReposFragment.mListRepos.clear();
            List<Repo> magiskRepos = RepoHelper.listRepos(mContext, doReload, mTaskDelegate);

            for (Repo repo : magiskRepos) {
                Log.d("Magisk", "Utils: Adding repo from string " + repo.getId());
                ReposFragment.mListRepos.add(repo);
            }

            return null;
        }

    }

    public static class FlashZIP extends AsyncTask<Void, Void, Boolean> {

        private String mPath, mName;
        private Uri mUri;
        private ProgressDialog progress;
        private File mFile;
        private Context mContext;
        private List<String> ret;
        private boolean deleteFileAfter;

        public FlashZIP(Context context, String name, String path) {
            mContext = context;
            mName = name;
            mPath = path;
            deleteFileAfter = false;
        }

        public FlashZIP(Context context, Uri uRi) {
            mContext = context;
            mUri = uRi;
            deleteFileAfter = true;
            String file = "";
            final String docId = DocumentsContract.getDocumentId(mUri);

            Log.d("Magisk", "Utils: FlashZip Running, " + docId + " and " + mUri.toString());
            if (docId.contains(":"))
                mName = docId.split(":")[1];
            else mName = docId;
            if (mName.contains("/"))
                mName = mName.substring(mName.lastIndexOf('/') + 1);
            if (mName.contains(".zip")) {
                file = mContext.getFilesDir() + "/" + mName;
                Log.d("Magisk", "Utils: FlashZip running for uRI " + mUri.toString());
            } else {
                Log.e("Magisk", "Utils: error parsing Zipfile " + mUri.getPath());
                this.cancel(true);
            }
            ContentResolver contentResolver = mContext.getContentResolver();
            //contentResolver.takePersistableUriPermission(mUri, flags);
            try {
                InputStream in = contentResolver.openInputStream(mUri);
                Log.d("Magisk", "Firing inputStream");
                mFile = createFileFromInputStream(in, file, mContext);
                if (mFile != null) {
                    mPath = mFile.getPath();
                    Log.d("Magisk", "Utils: Mpath is " + mPath);
                } else {
                    Log.e("Magisk", "Utils: error creating file " + mUri.getPath());
                    this.cancel(true);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // TODO handle non-primary volumes

        }

        private static File createFileFromInputStream(InputStream inputStream, String fileName, Context context) {

            try {
                File f = new File(fileName);
                f.setWritable(true, false);
                OutputStream outputStream = new FileOutputStream(f);
                byte buffer[] = new byte[1024];
                int length;

                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }

                outputStream.close();
                inputStream.close();
                Log.d("Magisk", "Holy balls, I think it worked.  File is " + f.getPath());
                return f;

            } catch (IOException e) {
                System.out.println("error in creating a file");
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            progress = ProgressDialog.show(mContext, mContext.getString(R.string.zip_install_progress_title), mContext.getString(R.string.zip_install_progress_msg, mName));
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            if (mPath != null) {
                Log.e("Magisk", "Utils: Error, flashZIP called without a valid zip file to flash.");
                progress.dismiss();
                this.cancel(true);
                return false;
            }
            if (!Shell.rootAccess()) {
                return false;
            } else {
                ret = Shell.su(
                        "rm -rf /dev/tmp",
                        "mkdir -p /dev/tmp",
                        "cp -af " + mPath + " /dev/tmp/install.zip",
                        "unzip -o /dev/tmp/install.zip META-INF/com/google/android/* -d /dev/tmp",
                        "BOOTMODE=true sh /dev/tmp/META-INF/com/google/android/update-binary dummy 1 /dev/tmp/install.zip",
                        "if [ $? -eq 0 ]; then echo true; else echo false; fi"
                );
                return ret != null && Boolean.parseBoolean(ret.get(ret.size() - 1));
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (deleteFileAfter) {
                Shell.su("rm -rf " + mPath);
                Log.d("Magisk", "Utils: Deleting file " + mPath);
            }
            progress.dismiss();
            if (!result) {
                Toast.makeText(mContext, mContext.getString(R.string.manual_install, mPath), Toast.LENGTH_LONG).show();
                return;
            }
            done();
        }

        protected void done() {
            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.reboot_title)
                    .setMessage(R.string.reboot_msg)
                    .setPositiveButton(R.string.reboot, (dialogInterface1, i) -> Shell.su("reboot"))
                    .setNegativeButton(R.string.no_thanks, null)
                    .show();
        }
    }

    public interface ItemClickListener {

        void onItemClick(View view, int position);

    }

}