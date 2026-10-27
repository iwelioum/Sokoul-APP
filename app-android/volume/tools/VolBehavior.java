import android.os.IBinder;
import java.lang.reflect.Method;

/** Outil ponctuel (adb) : lit ou change le comportement de volume de la sortie HDMI. Usage : VolBehavior [get|0|1] */
public class VolBehavior {
    public static void main(String[] args) throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, "audio");
        Object audio = Class.forName("android.media.IAudioService$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
        Class<?> ada = Class.forName("android.media.AudioDeviceAttributes");
        // ROLE_OUTPUT = 2, AudioDeviceInfo.TYPE_HDMI = 9
        Object hdmi = ada.getConstructor(int.class, int.class, String.class).newInstance(2, 9, "");
        if (args.length > 0 && !args[0].equals("get")) {
            int v = Integer.parseInt(args[0]);
            for (Method m : audio.getClass().getMethods()) {
                if (!m.getName().equals("setDeviceVolumeBehavior")) continue;
                m.invoke(audio, hdmi, v, "com.android.shell");
            }
        }
        Method get = audio.getClass().getMethod("getDeviceVolumeBehavior", ada);
        System.out.println("hdmi behavior = " + get.invoke(audio, hdmi));
    }
}
