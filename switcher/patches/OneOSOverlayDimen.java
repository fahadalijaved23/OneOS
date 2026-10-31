import android.content.om.FabricatedOverlay;
import android.content.om.OverlayManagerTransaction;
import android.os.IBinder;
import android.util.TypedValue;
import java.lang.reflect.Method;

/**
 * Registers a fabricated overlay with dimension values, which `cmd overlay
 * fabricate` cannot make: its values are integers only, and the framework takes
 * integer types 16-31 only ("dataType is out of range of [16, 31]"), so the
 * status bar height and corner radius overlays oneos_switch.sh made from One
 * UI's values (dimension, type 5) failed on every phone from 0.4.2x to 0.4.32
 * (sessions: "overlays on: OneOSCutout OneOSCutoutRect" only; the A56's clock
 * sat in its 118 px corner).
 *
 * FabricatedOverlay.setResourceValue(name, float, unit, config) makes a
 * dimension entry. The overlay belongs to com.android.shell, as the shell's own
 * fabricated overlays do, and is registered through IOverlayManager.commit with
 * a transaction that is not self-targeting (what `cmd overlay fabricate` does);
 * the switch then enables it with `cmd overlay enable` like the others.
 *
 * Runs inside the guest, as root, with the guest's own framework:
 *   CLASSPATH=/data/oneos_overlay_dimen.dex app_process /system/bin OneOSOverlayDimen \
 *       <overlay name> <target package> <package:dimen/name>=<px> ...
 *   ... OneOSOverlayDimen --unregister <overlay name>
 * Prints "registered <identifier>" (or "unregistered ..."), or the error and exits 1.
 */
public class OneOSOverlayDimen {
  public static void main(String[] args) {
    try {
      Class<?> b = Class.forName("android.content.om.OverlayManagerTransaction$Builder");
      Object builder = b.getConstructor().newInstance();
      if (args.length == 2 && args[0].equals("--unregister")) {
        Class<?> oi = Class.forName("android.content.om.OverlayIdentifier");
        Object id = oi.getConstructor(String.class, String.class).newInstance("com.android.shell", args[1]);
        b.getMethod("unregisterFabricatedOverlay", oi).invoke(builder, id);
        commit((OverlayManagerTransaction) b.getMethod("build").invoke(builder));
        System.out.println("unregistered " + id);
        return;
      }
      if (args.length < 3) throw new IllegalArgumentException("usage: <name> <target> <res>=<px> ... | --unregister <name>");
      FabricatedOverlay fo = new FabricatedOverlay(args[0], args[1]);
      FabricatedOverlay.class.getMethod("setOwningPackage", String.class).invoke(fo, "com.android.shell");
      for (int i = 2; i < args.length; i++) {
        int eq = args[i].lastIndexOf('=');
        if (eq <= 0) throw new IllegalArgumentException("not <res>=<px>: " + args[i]);
        fo.setResourceValue(args[i].substring(0, eq), Float.parseFloat(args[i].substring(eq + 1)),
            TypedValue.COMPLEX_UNIT_PX, null);
      }
      b.getMethod("registerFabricatedOverlay", FabricatedOverlay.class).invoke(builder, fo);
      commit((OverlayManagerTransaction) b.getMethod("build").invoke(builder));
      System.out.println("registered " + fo.getIdentifier());
    } catch (Throwable t) {
      Throwable c = t.getCause() != null ? t.getCause() : t;
      System.out.println("failed: " + c);
      System.exit(1);
    }
  }

  private static void commit(OverlayManagerTransaction tx) throws Exception {
    Class<?> sm = Class.forName("android.os.ServiceManager");
    IBinder binder = (IBinder) sm.getMethod("getService", String.class).invoke(null, "overlay");
    if (binder == null) throw new IllegalStateException("no overlay service");
    Object om = Class.forName("android.content.om.IOverlayManager$Stub")
        .getMethod("asInterface", IBinder.class).invoke(null, binder);
    Method commit = om.getClass().getMethod("commit", OverlayManagerTransaction.class);
    commit.invoke(om, tx);
  }
}
