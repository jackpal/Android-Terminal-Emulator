package jackpal.androidterm;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.IntentSender;
import android.os.*;
import android.support.annotation.NonNull;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public class TermExec {
    public static final String SERVICE_ACTION_V1 = "jackpal.androidterm.action.START_TERM.v1";

    private static Field descriptorField;

    private final List<String> command;
    private final Map<String, String> environment;

    static {
        System.loadLibrary("jackpal-termexec");
    }

    public TermExec(@NonNull String... command) {
        this(new ArrayList<>(Arrays.asList(command)));
    }

    public TermExec(@NonNull List<String> command) {
        this.command = command;
        this.environment = new Hashtable<>(System.getenv());
    }

    public @NonNull List<String> command() {
        return command;
    }

    public @NonNull Map<String, String> environment() {
        return environment;
    }

    public @NonNull TermExec command(@NonNull String... command) {
        return command(new ArrayList<>(Arrays.asList(command)));
    }

    public @NonNull TermExec command(List<String> command) {
        command.clear();
        command.addAll(command);
        return this;
    }

    public int start(@NonNull ParcelFileDescriptor ptmxFd) throws IOException {
        if (Looper.getMainLooper() == Looper.myLooper())
            throw new IllegalStateException("This method must not be called from the main thread!");

        if (command.size() == 0)
            throw new IllegalStateException("Empty command!");

        final String cmd = command.remove(0);
        final String[] cmdArray = command.toArray(new String[command.size()]);
        final String[] envArray = new String[environment.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            envArray[i++] = entry.getKey() + "=" + entry.getValue();
        }

        return createSubprocess(ptmxFd, cmd, cmdArray, envArray);
    }

    public static native int waitFor(int processId);

    static int createSubprocess(ParcelFileDescriptor masterFd, String cmd, String[] args, String[] envVars) throws IOException
    {
        final int integerFd;

        if (Build.VERSION.SDK_INT >= 12)
            integerFd = FdHelperHoneycomb.getFd(masterFd);
        else {
            try {
                if (descriptorField == null) {
                    descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
                    descriptorField.setAccessible(true);
                }

                integerFd = descriptorField.getInt(masterFd.getFileDescriptor());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IOException("Unable to obtain file descriptor on this OS version: " +
                        e.getLocalizedMessage());
            }
        }

        return createSubprocessInternal(cmd, args, envVars, integerFd);
    }

    private static native int createSubprocessInternal(String cmd, String[] args, String[] envVars, int masterFd);

    // prevents runtime errors on old API versions with ruthless verifier
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private static class FdHelperHoneycomb {
        static int getFd(ParcelFileDescriptor descriptor) {
            return descriptor.getFd();
        }
    }
}
