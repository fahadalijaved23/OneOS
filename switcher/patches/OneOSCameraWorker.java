import android.os.*;
import java.io.*;
import java.lang.reflect.Method;

/**
 * Stands in for One UI's "media.camera.worker" (Samsung's
 * com.samsung.android.camera.ICameraServiceWorker) while the guest runs.
 *
 * One UI's cameraserver, shared with the guest, asks that service on camera
 * opens; only One UI's system_server registers it, and the switch stops that.
 * On the Z Fold8 Ultra the lookup fails at once and nothing changes, but on the
 * Galaxy S25 Ultra's firmware (0.4.33 report 04857daa) every connect waited 5 s
 * ("getCameraServiceWorker: Could not get camera service worker") with the Knox
 * stand-in already answering, and then refused the guest app: "Camera 0: Access
 * for ... has been restricted. uid active: true, privacy enabled: false", "Could
 * not initialize client from HAL" (Open Camera, Xiaomi's scanner).
 *
 * The interface (One UI framework.jar, ICameraServiceWorker$Stub): 1
 * pingForUpdate, 2 notifyCameraState(String,int,int,String,int), 3
 * queryPackageName(int uid, int pid) -> String, 4 acquireRequestInjector() ->
 * IBinder, 5 setDeviceOrientationListener(boolean), 6
 * getDeviceOrientationForDeviceInjector(String,int) -> int, 7
 * getDeviceInjectorOverride(String,int) -> boolean, 8 storeLoggingData(int,
 * String). Replies in AIDL's shape (status header, then the value): the
 * package name the guest's own package manager has for the uid (the guest's
 * apps are unknown to One UI), no injector, no override, orientation 0.
 *
 * Runs inside the guest (its framework, its package manager) and exits with
 * the guest's system_server (args[0]). Started with the Knox stand-in
 * (ONEOS_GUEST_EDM_STUB=1), only while nothing else serves the name.
 */
public final class OneOSCameraWorker {
 private static final String NAME = "media.camera.worker";
 private static final String DESCRIPTOR = "com.samsung.android.camera.ICameraServiceWorker";

 public static void main(String[] args) throws Exception {
  final int guest = Integer.parseInt(args[0]);
  final String name = args.length > 1 ? args[1] : NAME;
  Looper.prepareMainLooper();
  Binder b = new Binder() {
   @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
     throws RemoteException {
    if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(DESCRIPTOR); return true; }
    if (code < FIRST_CALL_TRANSACTION || code > FIRST_CALL_TRANSACTION + 7) return super.onTransact(code, data, reply, flags);
    try { data.enforceInterface(DESCRIPTOR); } catch (SecurityException e) { return false; }
    if (reply == null) return true;  // oneway
    reply.writeNoException();
    switch (code - FIRST_CALL_TRANSACTION + 1) {
     case 3: reply.writeString(packageFor(data.readInt())); break;
     case 4: reply.writeStrongBinder(null); break;
     case 6: reply.writeInt(0); break;
     case 7: reply.writeInt(0); break;  // boolean false
     default: break;                    // void
    }
    return true;
   }
  };
  Class<?> sm = Class.forName("android.os.ServiceManager");
  Method add = sm.getMethod("addService", String.class, IBinder.class, boolean.class, int.class);
  add.invoke(null, name, b, Boolean.FALSE, Integer.valueOf(1));
  Object got = sm.getMethod("checkService", String.class).invoke(null, name);
  if (got == null) throw new IllegalStateException("addService " + name + " did not take");
  System.out.println("READY " + name + " uid=" + android.os.Process.myUid() + " guest=" + guest);
  System.out.flush();
  final Handler h = new Handler(Looper.myLooper());
  h.postDelayed(new Runnable() { public void run() {
   if (!new File("/proc/" + guest).exists()) System.exit(0);
   h.postDelayed(this, 2000);
  } }, 2000);
  Looper.loop();
 }

 /** The guest package manager's first package for the uid, or null. */
 private static String packageFor(int uid) {
  try {
   Object pm = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null);
   String[] pkgs = (String[]) pm.getClass().getMethod("getPackagesForUid", int.class).invoke(pm, uid);
   if (pkgs != null && pkgs.length > 0) return pkgs[0];
   return (String) pm.getClass().getMethod("getNameForUid", int.class).invoke(pm, uid);
  } catch (Throwable t) {
   return null;
  }
 }
}
