package com.sprd.messaging.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.IWindowManager;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.DisplayInfo;
import android.view.DisplayCutout;

import com.android.ims.ImsConfig;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.ImsManagerEx;
import com.android.messaging.util.LogUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by lxg on 15-12-18.
 */
public class Utils {
    /* Add by SPRD for Bug:504724 2015.12.18 Start */
    /**
     *  Get supper method via reflection, mainly for private method.
     * @param methodName The method name who will be invoked.
     * @param depth Search depth.
     * @param supperClass Supper class.
     * @param argsType The type of args in the method.
     * @return The method if find in super class, null otherwise.
     */
    public static Method getSuperMethod(String methodName, int depth, Class<?> supperClass, Class<?>... argsType) {
        if (methodName == null || methodName.length() == 0
                || supperClass == null || depth <= 0) {
            Log.d(LogUtil.BUGLE_TAG, "Not find out mehtod '" + methodName + "' in supper class");
            return null;
        }
        Method m;
        try {
            m = supperClass.getDeclaredMethod(methodName, argsType);
        } catch (NoSuchMethodException e) {
            try {
                m = supperClass.getMethod(methodName, argsType);
            } catch (NoSuchMethodException e1) {
                return getSuperMethod(methodName, --depth, supperClass.getSuperclass(), argsType);
            }
        }
        return m;
    }

    /**
     *  Invoke method vid reflection, same as caller.m(args1, args2...)
     * @param caller The method caller.
     * @param method The method will be called.
     * @param argsValue Parameters need be sent to the method.
     */
    public static void invoke(final Object caller, Method method, Object... argsValue) {
        boolean invokeSucc = false;
        if (method != null) {
            method.setAccessible(true);
            try {
                method.invoke(caller, argsValue);
                invokeSucc = true;
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
        if (!invokeSucc&&method!=null) {
            Log.d(LogUtil.BUGLE_TAG, "invoke method " + method.getName() + "fail.");
        }
    }
    /* Add by SPRD for Bug:504724 2015.12.18 End */

    // add for bug 564775 beign
    public static boolean isVowifiSmsEnable(int subId) {
        boolean isVowifiConnected = false;
        try {
            IImsServiceEx imsServiceEx = ImsManagerEx.getIImsServiceEx();
            if (imsServiceEx != null
                    && ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI == imsServiceEx.getCurrentImsFeature()) {
                isVowifiConnected = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(LogUtil.BUGLE_TAG, "isVowifiSmsEnable, subId/isVowifiConnected: " + subId + "/" + isVowifiConnected);
        return isVowifiConnected;
    }
    // add for bug 564775 beign

    //bug 950173 begin
    /**
     * @param context
     * @return return -1 when id not found
     */
    public static int getNavigationBarHeight(Context context){
        int resourceId = context.getResources().getIdentifier("navigation_bar_height","dimen", "android");
        if(resourceId != 0){
            try{
                return context.getResources().getDimensionPixelSize(resourceId);
            }catch (Resources.NotFoundException e){
                e.printStackTrace();
            }
        }
        return -1;
    }

    /**
     * @return Device has navigation bar or not.
     */
    public static boolean hasNavigationBar(Context context){
        try {
            return WindowManagerGlobal.getWindowManagerService().hasNavigationBar(context.getDisplayId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * @param context
     * @return Considering navigation-bar hidden or not
     */
    public static int getRealWindowHeight(Context context){
        //1283382 begin
        int cououttop = 0 ;
        DisplayInfo oDisplayInfo = new DisplayInfo();
        WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        boolean getdi = windowManager.getDefaultDisplay().getDisplayInfo(oDisplayInfo);
        if(getdi == true){
            cououttop = getCutoutHeight(oDisplayInfo);
            Log.d("Utils", "getRealWindowHeight oDisplayInfo getCutoutHeight:"+cououttop);
        }else{
            Log.d("Utils", "getRealWindowHeight oDisplayInfo false");
        }
        //1283382 end
        if(hasNavigationBar(context) && isNavigationBarShowing(context)){
            return context.getResources().getDisplayMetrics().heightPixels + cououttop;//1283382
        }else {
            //WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            Point point = new Point();
            windowManager.getDefaultDisplay().getRealSize(point);
            return point.y + cououttop;//1283382
        }
    }
    /**
     * @param context
     * @return Considering navigation-bar hidden or not
     */
    public static int getRealWindowWidth(Context context){
        if(hasNavigationBar(context) && isNavigationBarShowing(context)){
            return context.getResources().getDisplayMetrics().widthPixels;
        }else {
            WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            Point point = new Point();
            windowManager.getDefaultDisplay().getRealSize(point);
            return point.x;
        }
    }
    /**
     * @param context
     * @return true if don't support dynamic navigation bar
     */
    public static boolean isNavigationBarShowing(Context context){
        IWindowManager wms = IWindowManager.Stub.asInterface(ServiceManager.getService(Context.WINDOW_SERVICE));
        try {
            Method method = wms.getClass().getMethod("isNavigationBarShowing");
            return (boolean)method.invoke(wms);
        }catch (Exception e){
            e.printStackTrace();
        }
        return true;
    }
    //bug 950173 end

    //1283382 begin
    public static boolean hasCutout(DisplayInfo displayInfo) {
          if(displayInfo.displayCutout == null) {
             return false;
         } else if (displayInfo.displayCutout.isEmpty()) {
              return false;
          }
          return true;
      }

    public static int getCutoutHeight(DisplayInfo displayInfo) {
        if (hasCutout(displayInfo)) {
            return displayInfo.displayCutout.getSafeInsetTop() - displayInfo.displayCutout.getSafeInsetBottom();
        } else {
            return 0;
        }
    }
    //1283382 end
}
